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

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import dev.patric.dungeonsreborn.effects.EffectsEngine;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;

public final class MobSpawnManager implements Listener {
  private static final long TICK_PERIOD = 20L;

  private static final class SpawnState {
    private final Set<UUID> alive = new HashSet<>();
    private long nextSpawnTick;
  }

  private final EffectsEngine engine;
  private final MobRegistry registry;
  private final Map<String, MobSpawnSpec> spawns = new HashMap<>();
  private final Map<String, SpawnState> states = new HashMap<>();
  private final Map<UUID, String> entityToSpawn = new HashMap<>();
  private final Random rng = new Random();
  private Set<String> enabledWorlds = Set.of();
  public MobSpawnManager(EffectsEngine engine, MobRegistry registry) {
    this.engine = Objects.requireNonNull(engine, "engine");
    this.registry = Objects.requireNonNull(registry, "registry");
    engine.runRepeating(TICK_PERIOD, TICK_PERIOD, this::tick);
  }

  public void reload(List<MobSpawnSpec> newSpawns, Set<String> enabledWorlds, boolean despawnOnReload) {
    if (despawnOnReload) {
      despawnAll();
    }
    spawns.clear();
    states.clear();
    entityToSpawn.clear();
    this.enabledWorlds = enabledWorlds == null ? Set.of() : Set.copyOf(enabledWorlds);
    for (MobSpawnSpec spec : newSpawns) {
      spawns.put(spec.id(), spec);
      states.put(spec.id(), new SpawnState());
    }
  }

  public int activeSpawns() {
    return spawns.size();
  }

  public void despawnAll() {
    for (UUID id : new ArrayList<>(entityToSpawn.keySet())) {
      Entity entity = Bukkit.getEntity(id);
      if (entity != null) {
        entity.remove();
      }
    }
    entityToSpawn.clear();
    for (SpawnState state : states.values()) {
      state.alive.clear();
      state.nextSpawnTick = 0L;
    }
  }

  private void tick() {
    if (spawns.isEmpty()) {
      return;
    }
    long now = engine.tickNow();
    for (MobSpawnSpec spec : spawns.values()) {
      if (!spec.enabled()) {
        continue;
      }
      if (!isWorldEnabled(spec.worldName())) {
        continue;
      }
      SpawnState state = states.computeIfAbsent(spec.id(), k -> new SpawnState());
      pruneDead(state);
      int alive = state.alive.size();
      if (spec.maxAlive() > 0 && alive >= spec.maxAlive()) {
        continue;
      }
      if (state.nextSpawnTick > now) {
        continue;
      }
      int max = spec.maxAlive() <= 0 ? Integer.MAX_VALUE : spec.maxAlive();
      int toSpawn = Math.min(spec.count(), max - alive);
      if (toSpawn <= 0) {
        continue;
      }
      for (int i = 0; i < toSpawn; i++) {
        LivingEntity entity = spawn(spec);
        if (entity != null) {
          state.alive.add(entity.getUniqueId());
          entityToSpawn.put(entity.getUniqueId(), spec.id());
        }
      }
      if (spec.respawnTicks() > 0) {
        state.nextSpawnTick = now + spec.respawnTicks();
      }
    }
  }

  private void pruneDead(SpawnState state) {
    state.alive.removeIf(id -> {
      Entity entity = Bukkit.getEntity(id);
      return entity == null || !entity.isValid();
    });
  }

  private LivingEntity spawn(MobSpawnSpec spec) {
    World world = Bukkit.getWorld(spec.worldName());
    if (world == null) {
      return null;
    }
    Location base = spec.location().clone();
    base.setWorld(world);
    if (spec.radius() > 0.0) {
      double angle = rng.nextDouble() * Math.PI * 2.0;
      double r = rng.nextDouble() * spec.radius();
      base.add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
    }
    try {
      return registry.spawn(spec.mobId(), base);
    } catch (Exception ex) {
      return null;
    }
  }

  private boolean isWorldEnabled(String worldName) {
    if (enabledWorlds == null || enabledWorlds.isEmpty()) {
      return true;
    }
    if (worldName == null) {
      return false;
    }
    return enabledWorlds.contains(worldName.toLowerCase());
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
    String spawnId = entityToSpawn.remove(entity.getUniqueId());
    if (spawnId == null) {
      return;
    }
    SpawnState state = states.get(spawnId);
    if (state != null) {
      state.alive.remove(entity.getUniqueId());
      MobSpawnSpec spec = spawns.get(spawnId);
      if (spec != null && spec.respawnTicks() > 0) {
        state.nextSpawnTick = engine.tickNow() + spec.respawnTicks();
      }
    }
  }
}
