package dev.patric.dungeonsreborn.mobs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.logging.ServiceLogger;

public final class TrialSpawnerManager implements Listener {
  private static final long TICK_PERIOD = 20L;
  private static final long STALE_RESET_TICKS = 20L * 30L;
  private static final long FAIL_RETRY_TICKS = 20L * 2L;

  private static final class TrialState {
    private final Set<UUID> alive = new HashSet<>();
    private long nextSpawnTick;
    private int currentWave;
    private int spawnedThisWave;
    private boolean running;
    private boolean ominous;
    private long lastPlayerSeenTick;
    private long startedAtTick;
  }

  private final EffectsEngine engine;
  private final MobRegistry registry;
  private final MobYamlRegistry yaml;
  private final TrialSpawnerBlockStore store;
  private final ServiceLogger logger;
  private final Random rng = new Random();
  private final Map<String, TrialState> states = new HashMap<>();
  private final Map<UUID, String> entityToSpawnerKey = new HashMap<>();

  public TrialSpawnerManager(EffectsEngine engine, MobRegistry registry, MobYamlRegistry yaml,
      TrialSpawnerBlockStore store, ServiceLogger logger) {
    this.engine = Objects.requireNonNull(engine, "engine");
    this.registry = Objects.requireNonNull(registry, "registry");
    this.yaml = Objects.requireNonNull(yaml, "yaml");
    this.store = Objects.requireNonNull(store, "store");
    this.logger = Objects.requireNonNull(logger, "logger");
    engine.runRepeating(TICK_PERIOD, TICK_PERIOD, this::tick);
  }

  public void onTrialSpawnerPlaced(Block block) {
    if (block == null || block.getWorld() == null) {
      return;
    }
    states.remove(keyFor(block));
  }

  public void onTrialSpawnerRemoved(Block block) {
    if (block == null || block.getWorld() == null) {
      return;
    }
    String key = keyFor(block);
    TrialState state = states.remove(key);
    if (state == null) {
      return;
    }
    for (UUID entityId : new ArrayList<>(state.alive)) {
      Entity entity = Bukkit.getEntity(entityId);
      if (entity != null) {
        entity.remove();
      }
      entityToSpawnerKey.remove(entityId);
    }
    state.alive.clear();
  }

  @EventHandler
  public void onDeath(EntityDeathEvent event) {
    handleRemove(event.getEntity());
  }

  @EventHandler
  public void onRemove(EntityRemoveFromWorldEvent event) {
    handleRemove(event.getEntity());
  }

  private void handleRemove(Entity entity) {
    String spawnerKey = entityToSpawnerKey.remove(entity.getUniqueId());
    if (spawnerKey == null) {
      return;
    }
    TrialState state = states.get(spawnerKey);
    if (state != null) {
      state.alive.remove(entity.getUniqueId());
    }
  }

  private void tick() {
    long now = engine.tickNow();
    for (TrialSpawnerBlockStore.Entry entry : store.entries()) {
      World world = Bukkit.getWorld(entry.world());
      if (world == null) {
        continue;
      }
      Block block = world.getBlockAt(entry.x(), entry.y(), entry.z());
      if (block.getType() != org.bukkit.Material.TRIAL_SPAWNER) {
        continue;
      }
      TrialSpawnerSpec spec = yaml.trialSpawnerSpec(entry.trialSpawnerId());
      if (spec == null) {
        continue;
      }
      String key = keyFor(entry.world(), entry.x(), entry.y(), entry.z());
      TrialState state = states.computeIfAbsent(key, unused -> new TrialState());
      pruneDead(state);
      boolean ominous = false;
      org.bukkit.block.BlockState blockState = block.getState();
      if (blockState instanceof org.bukkit.block.TrialSpawner trialBlock) {
        ominous = trialBlock.isOminous();
      }
      if (!state.running) {
        List<Player> nearby = nearbyPlayers(block.getLocation(), spec.activationRange());
        if (nearby.size() < spec.requiredPlayers()) {
          continue;
        }
        startEncounter(state, now, ominous);
      }
      if (state.running) {
        enforceAntiExploit(state, block.getLocation(), spec.activationRange(), now);
        List<Player> nearby = nearbyPlayers(block.getLocation(), spec.activationRange());
        if (!nearby.isEmpty()) {
          state.lastPlayerSeenTick = now;
        } else if (now - state.lastPlayerSeenTick >= STALE_RESET_TICKS) {
          resetEncounter(state);
          continue;
        }
        TrialSpawnerProfile profile = resolveProfile(spec, state.ominous);
        int waves = profile.waves() != null ? profile.waves() : spec.waves();
        int simultaneous = profile.simultaneous() != null ? profile.simultaneous() : spec.simultaneous();
        int cooldownTicks = profile.cooldownTicks() != null ? profile.cooldownTicks() : spec.cooldownTicks();

        if (state.currentWave > waves) {
          if (state.alive.isEmpty()) {
            completeEncounter(block.getLocation(), spec, profile);
            resetEncounter(state);
          }
          continue;
        }
        if (state.nextSpawnTick > now) {
          continue;
        }
        while (state.spawnedThisWave < simultaneous && state.alive.size() < simultaneous) {
          LivingEntity entity = spawnMob(block.getLocation(), spec, profile);
          if (entity == null) {
            state.nextSpawnTick = now + FAIL_RETRY_TICKS;
            break;
          }
          state.alive.add(entity.getUniqueId());
          entityToSpawnerKey.put(entity.getUniqueId(), key);
          state.spawnedThisWave++;
        }
        if (state.spawnedThisWave >= simultaneous && state.alive.isEmpty()) {
          state.currentWave++;
          state.spawnedThisWave = 0;
          state.nextSpawnTick = now + Math.max(0, cooldownTicks);
        }
      }
    }
  }

  private void startEncounter(TrialState state, long now, boolean ominous) {
    state.running = true;
    state.ominous = ominous;
    state.currentWave = 1;
    state.spawnedThisWave = 0;
    state.nextSpawnTick = now;
    state.lastPlayerSeenTick = now;
    state.startedAtTick = now;
  }

  private void resetEncounter(TrialState state) {
    for (UUID entityId : new ArrayList<>(state.alive)) {
      Entity entity = Bukkit.getEntity(entityId);
      if (entity != null) {
        entity.remove();
      }
      entityToSpawnerKey.remove(entityId);
    }
    state.alive.clear();
    state.running = false;
    state.ominous = false;
    state.currentWave = 0;
    state.spawnedThisWave = 0;
    state.nextSpawnTick = 0L;
    state.lastPlayerSeenTick = 0L;
    state.startedAtTick = 0L;
  }

  private void pruneDead(TrialState state) {
    state.alive.removeIf(id -> {
      Entity entity = Bukkit.getEntity(id);
      if (entity == null || !entity.isValid()) {
        entityToSpawnerKey.remove(id);
        return true;
      }
      return false;
    });
  }

  private TrialSpawnerProfile resolveProfile(TrialSpawnerSpec spec, boolean ominous) {
    if (!ominous || spec.ominousProfile() == null) {
      return new TrialSpawnerProfile(List.of(), null, null, null, null);
    }
    return spec.ominousProfile();
  }

  private LivingEntity spawnMob(Location source, TrialSpawnerSpec spec, TrialSpawnerProfile profile) {
    World world = source.getWorld();
    if (world == null) {
      return null;
    }
    List<TrialSpawnerMobEntry> pool = profile.mobPool().isEmpty() ? spec.mobPool() : profile.mobPool();
    String mobId = pickWeightedMob(pool);
    if (mobId == null) {
      return null;
    }
    Location base = source.clone().add(0.5, 0.1, 0.5);
    Location spawnLoc = base.clone();
    double r = 1.5 + rng.nextDouble() * 2.0;
    double angle = rng.nextDouble() * Math.PI * 2.0;
    spawnLoc.add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
    return registry.spawn(mobId, spawnLoc);
  }

  private String pickWeightedMob(List<TrialSpawnerMobEntry> pool) {
    if (pool == null || pool.isEmpty()) {
      return null;
    }
    double total = 0.0;
    for (TrialSpawnerMobEntry entry : pool) {
      total += Math.max(0.0, entry.weight());
    }
    if (total <= 0.0) {
      return pool.get(0).mobId();
    }
    double roll = ThreadLocalRandom.current().nextDouble() * total;
    double acc = 0.0;
    for (TrialSpawnerMobEntry entry : pool) {
      acc += Math.max(0.0, entry.weight());
      if (roll <= acc) {
        return entry.mobId();
      }
    }
    return pool.get(pool.size() - 1).mobId();
  }

  private void completeEncounter(Location source, TrialSpawnerSpec spec, TrialSpawnerProfile profile) {
    String poolId = profile.keyLootPool() == null || profile.keyLootPool().isBlank()
        ? spec.keyLootPool()
        : profile.keyLootPool();
    MobLootSpec loot = yaml.lootPool(poolId);
    if (loot == null) {
      return;
    }
    dropLootSpec(source.clone().add(0.5, 0.5, 0.5), loot, new HashSet<>());
  }

  private void dropLootSpec(Location loc, MobLootSpec loot, Set<String> visitedPools) {
    Random localRng = rng;
    for (MobDropSpec drop : loot.guaranteed()) {
      int amount = drop.rollAmount(localRng);
      if (amount <= 0) {
        continue;
      }
      ItemStack stack = drop.item().clone();
      stack.setAmount(Math.max(1, Math.min(stack.getMaxStackSize(), amount)));
      loc.getWorld().dropItemNaturally(loc, stack);
    }
    int rolls = loot.rolls() + loot.bonusRolls();
    for (int i = 0; i < rolls; i++) {
      for (MobDropSpec drop : loot.drops()) {
        int amount = drop.rollAmount(localRng);
        if (amount <= 0) {
          continue;
        }
        ItemStack stack = drop.item().clone();
        stack.setAmount(Math.max(1, Math.min(stack.getMaxStackSize(), amount)));
        loc.getWorld().dropItemNaturally(loc, stack);
      }
    }
    for (MobLootPoolRef ref : loot.pools()) {
      if (ref == null || !visitedPools.add(ref.poolId())) {
        continue;
      }
      if (ref.chance() < 1.0 && localRng.nextDouble() > ref.chance()) {
        continue;
      }
      MobLootSpec nested = yaml.lootPool(ref.poolId());
      if (nested == null) {
        continue;
      }
      dropLootSpec(loc, nested, visitedPools);
    }
  }

  private void enforceAntiExploit(TrialState state, Location source, double activationRange, long now) {
    World world = source.getWorld();
    if (world == null) {
      return;
    }
    double leash = Math.max(activationRange * 1.75, 12.0);
    double leashSq = leash * leash;
    for (UUID entityId : new ArrayList<>(state.alive)) {
      Entity entity = Bukkit.getEntity(entityId);
      if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
        state.alive.remove(entityId);
        entityToSpawnerKey.remove(entityId);
        continue;
      }
      if (!living.getWorld().equals(world) || living.getLocation().distanceSquared(source) > leashSq) {
        living.remove();
        state.alive.remove(entityId);
        entityToSpawnerKey.remove(entityId);
      }
    }
    if (now - state.startedAtTick > 20L * 60L * 5L) {
      resetEncounter(state);
    }
  }

  private List<Player> nearbyPlayers(Location center, double radius) {
    if (center == null || center.getWorld() == null) {
      return List.of();
    }
    World world = center.getWorld();
    double radiusSq = radius * radius;
    List<Player> out = new ArrayList<>();
    for (Player player : world.getPlayers()) {
      if (player.getLocation().distanceSquared(center) <= radiusSq) {
        out.add(player);
      }
    }
    return out;
  }

  private static String keyFor(Block block) {
    return keyFor(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
  }

  private static String keyFor(String world, int x, int y, int z) {
    return world + ":" + x + ":" + y + ":" + z;
  }
}
