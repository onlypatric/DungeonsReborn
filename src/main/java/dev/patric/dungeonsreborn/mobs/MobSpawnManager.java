package dev.patric.dungeonsreborn.mobs;

import java.util.ArrayList;
import java.util.Comparator;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.GameRule;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.util.Vector;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.logging.ServiceLogger;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class MobSpawnManager implements Listener {
  private static final long TICK_PERIOD = 20L;
  private static final long TETHER_TICK_PERIOD = 5L;
  private static final long HOLOGRAM_UPDATE_TICKS = 20L;
  private static final String DEFAULT_HOLOGRAM_FORMAT =
      "<gold>{mob}</gold> <gray>({alive}/{cap})</gray>\n<yellow>Next: {next}</yellow>";
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

  private static final class SpawnState {
    private final Set<UUID> alive = new HashSet<>();
    private long nextSpawnTick;
    private final Map<UUID, Long> outOfBoundsSince = new HashMap<>();
    private UUID hologramId;
    private long nextHologramTick;
  }

  public record SpawnerSnapshot(
      String id,
      String mobId,
      String world,
      double x,
      double y,
      double z,
      int alive,
      int maxAlive,
      long nextSpawnSeconds,
      boolean enabled) {
  }

  private final EffectsEngine engine;
  private final MobRegistry registry;
  private final ServiceLogger logger;
  private boolean debugSpawns;
  private final Map<String, MobSpawnSpec> spawns = new HashMap<>();
  private final Map<String, SpawnState> states = new HashMap<>();
  private final Map<UUID, String> entityToSpawn = new HashMap<>();
  private final Random rng = new Random();
  private final List<MobSpawnSpec> spawnListCache = new ArrayList<>();
  private boolean hasGroupCaps;
  private Set<String> enabledWorlds = Set.of();
  private int maxSpawnersPerTick = 0;
  private int tickCursor = 0;
  public MobSpawnManager(EffectsEngine engine, MobRegistry registry, ServiceLogger logger) {
    this.engine = Objects.requireNonNull(engine, "engine");
    this.registry = Objects.requireNonNull(registry, "registry");
    this.logger = Objects.requireNonNull(logger, "logger");
    engine.runRepeating(TICK_PERIOD, TICK_PERIOD, this::tick);
    engine.runRepeating(TETHER_TICK_PERIOD, TETHER_TICK_PERIOD, this::tickTether);
  }

  public void setDebugSpawns(boolean debugSpawns) {
    this.debugSpawns = debugSpawns;
  }

  public void reload(List<MobSpawnSpec> newSpawns, Set<String> enabledWorlds, boolean despawnOnReload) {
    if (despawnOnReload) {
      despawnAll();
    } else {
      clearAllHolograms();
    }
    Map<UUID, String> previousEntities = despawnOnReload ? Map.of() : new HashMap<>(entityToSpawn);
    Map<String, SpawnState> previousStates = despawnOnReload ? Map.of() : new HashMap<>(states);
    spawns.clear();
    states.clear();
    entityToSpawn.clear();
    spawnListCache.clear();
    hasGroupCaps = false;
    this.enabledWorlds = enabledWorlds == null ? Set.of() : Set.copyOf(enabledWorlds);
    for (MobSpawnSpec spec : newSpawns) {
      spawns.put(spec.id(), spec);
      spawnListCache.add(spec);
      if (spec.groupId() != null && !spec.groupId().isBlank()) {
        hasGroupCaps = true;
      }
      SpawnState state = new SpawnState();
      SpawnState previous = previousStates.get(spec.id());
      if (previous != null) {
        state.nextSpawnTick = previous.nextSpawnTick;
      }
      states.put(spec.id(), state);
    }
    if (!previousEntities.isEmpty()) {
      restoreActiveEntities(previousEntities);
    }
  }

  public int activeSpawns() {
    return spawns.size();
  }

  public void setMaxSpawnersPerTick(int maxSpawnersPerTick) {
    this.maxSpawnersPerTick = Math.max(0, maxSpawnersPerTick);
  }

  public MobSpawnSpec spawnSpec(String id) {
    if (id == null) {
      return null;
    }
    return spawns.get(id);
  }

  public boolean hasSpawn(String id) {
    if (id == null) {
      return false;
    }
    return spawns.containsKey(id);
  }

  public java.util.Set<String> spawnIds() {
    return java.util.Collections.unmodifiableSet(spawns.keySet());
  }

  public int despawnSpawn(String id) {
    SpawnState state = states.get(id);
    if (state == null) {
      return 0;
    }
    int removed = 0;
    for (UUID entityId : new ArrayList<>(state.alive)) {
      Entity entity = Bukkit.getEntity(entityId);
      if (entity != null) {
        entity.remove();
        removed++;
      }
      entityToSpawn.remove(entityId);
    }
    state.alive.clear();
    state.outOfBoundsSince.clear();
    removeHologram(state);
    return removed;
  }

  public boolean spawnOnce(String id) {
    MobSpawnSpec spec = spawns.get(id);
    if (spec == null) {
      return false;
    }
    if (!isWorldEnabled(spec.worldName())) {
      return false;
    }
    SpawnState state = states.computeIfAbsent(spec.id(), k -> new SpawnState());
    int alive = state.alive.size();
    int max = spec.maxAlive() <= 0 ? Integer.MAX_VALUE : spec.maxAlive();
    if (alive >= max) {
      return false;
    }
    MobSpawnGroupSpec group = pickGroup(spec);
    LivingEntity entity = spawn(spec, group);
    if (entity == null) {
      return false;
    }
    state.alive.add(entity.getUniqueId());
    entityToSpawn.put(entity.getUniqueId(), spec.id());
    scheduleNextSpawn(spec, state, engine.tickNow());
    return true;
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
      removeHologram(state);
    }
  }

  private void tick() {
    if (spawnListCache.isEmpty()) {
      return;
    }
    long now = engine.tickNow();
    Map<String, Integer> groupCounts = hasGroupCaps ? computeGroupCounts() : Map.of();
    int total = spawnListCache.size();
    int limit = maxSpawnersPerTick <= 0 || maxSpawnersPerTick >= total ? total : maxSpawnersPerTick;
    int start = total == 0 ? 0 : Math.floorMod(tickCursor, total);
    for (int i = 0; i < limit; i++) {
      MobSpawnSpec spec = spawnListCache.get((start + i) % total);
      SpawnState state = states.computeIfAbsent(spec.id(), k -> new SpawnState());
      if (!spec.enabled()) {
        updateHologram(spec, state, now);
        continue;
      }
      if (!isWorldEnabled(spec.worldName())) {
        updateHologram(spec, state, now);
        continue;
      }
      World world = Bukkit.getWorld(spec.worldName());
      if (world == null) {
        updateHologram(spec, state, now);
        continue;
      }
      if (world.getPlayers().isEmpty()) {
        updateHologram(spec, state, now);
        continue;
      }
      if (spec.respectDifficulty() && world.getDifficulty() == org.bukkit.Difficulty.PEACEFUL) {
        updateHologram(spec, state, now);
        continue;
      }
      if (spec.respectGameRules()) {
        Boolean mobsSpawning = world.getGameRuleValue(GameRule.DO_MOB_SPAWNING);
        if (Boolean.FALSE.equals(mobsSpawning)) {
          updateHologram(spec, state, now);
          continue;
        }
      }
      if (!isChunkLoaded(world, spec.location())) {
        updateHologram(spec, state, now);
        continue;
      }
      if (!hasNearbyPlayers(world, spec)) {
        updateHologram(spec, state, now);
        continue;
      }
      int alive = state.alive.size();
      if (spec.maxAlive() > 0 && alive >= spec.maxAlive()) {
        continue;
      }
      int groupAlive = 0;
      if (spec.groupId() != null && spec.groupMaxAlive() > 0) {
        groupAlive = groupCounts.getOrDefault(spec.groupId(), 0);
        if (groupAlive >= spec.groupMaxAlive()) {
          continue;
        }
      }
      if (state.nextSpawnTick > now) {
        continue;
      }
      MobSpawnGroupSpec group = pickGroup(spec);
      int desiredCount = group != null && group.count() != null ? group.count() : spec.count();
      int max = spec.maxAlive() <= 0 ? Integer.MAX_VALUE : spec.maxAlive();
      int toSpawn = Math.min(desiredCount, max - alive);
      if (spec.groupId() != null && spec.groupMaxAlive() > 0) {
        toSpawn = Math.min(toSpawn, spec.groupMaxAlive() - groupAlive);
      }
      if (toSpawn <= 0) {
        continue;
      }
      for (int j = 0; j < toSpawn; j++) {
        LivingEntity entity = spawn(spec, group);
        if (entity != null) {
          state.alive.add(entity.getUniqueId());
          entityToSpawn.put(entity.getUniqueId(), spec.id());
          if (spec.groupId() != null && spec.groupMaxAlive() > 0) {
            groupAlive++;
          }
        }
      }
      scheduleNextSpawn(spec, state, now);
      if (spec.groupId() != null && spec.groupMaxAlive() > 0) {
        groupCounts.put(spec.groupId(), groupAlive);
      }
      updateHologram(spec, state, now);
    }
    if (limit > 0 && total > 0) {
      tickCursor = (start + limit) % total;
    }
  }

  private void tickTether() {
    if (spawns.isEmpty()) {
      return;
    }
    long now = engine.tickNow();
    for (MobSpawnSpec spec : spawns.values()) {
      SpawnState state = states.computeIfAbsent(spec.id(), k -> new SpawnState());
      pruneDead(state);
      enforceTether(spec, state, now);
    }
  }

  private void pruneDead(SpawnState state) {
    state.alive.removeIf(id -> {
      Entity entity = Bukkit.getEntity(id);
      return entity == null || !entity.isValid();
    });
    state.outOfBoundsSince.keySet().retainAll(state.alive);
  }

  private LivingEntity spawn(MobSpawnSpec spec, MobSpawnGroupSpec group) {
    World world = Bukkit.getWorld(spec.worldName());
    if (world == null) {
      logger.warn("[Mobs] spawn: unknown world " + spec.worldName() + " for spawn " + spec.id());
      return null;
    }
    Location base = spec.location().clone();
    base.setWorld(world);
    Location spawnLoc = resolveSpawnLocation(world, base, spec.radius());
    if (spawnLoc == null) {
      spawnLoc = base;
    }
    try {
      String mobId = resolveMobId(spec, group);
      if (spec.beamEnabled() && spec.beamParticle() != null) {
        spawnBeam(world, base, spawnLoc, spec.beamParticle(), spec.beamStep());
      }
      LivingEntity entity = registry.spawn(mobId, spawnLoc);
      if (debugSpawns) {
        logger.debug("[Mobs] spawn: id=" + spec.id() + " mob=" + mobId
            + " world=" + spec.worldName() + " x=" + spawnLoc.getX() + " y=" + spawnLoc.getY() + " z=" + spawnLoc.getZ());
      }
      return entity;
    } catch (Exception ex) {
      logger.warn("[Mobs] spawn: failed id=" + spec.id() + " mob=" + spec.mobId()
          + " reason=" + ex.getMessage());
      return null;
    }
  }

  private MobSpawnGroupSpec pickGroup(MobSpawnSpec spec) {
    List<MobSpawnGroupSpec> groups = spec.groups();
    if (groups == null || groups.isEmpty()) {
      return null;
    }
    double total = 0.0;
    for (MobSpawnGroupSpec group : groups) {
      total += Math.max(0.0, group.chance());
    }
    if (total <= 0.0) {
      return null;
    }
    double roll = ThreadLocalRandom.current().nextDouble() * total;
    double acc = 0.0;
    for (MobSpawnGroupSpec group : groups) {
      acc += Math.max(0.0, group.chance());
      if (roll <= acc) {
        return group;
      }
    }
    return groups.get(groups.size() - 1);
  }

  private String resolveMobId(MobSpawnSpec spec, MobSpawnGroupSpec group) {
    if (group == null || group.mobs() == null || group.mobs().isEmpty()) {
      return spec.mobId();
    }
    double total = 0.0;
    for (MobSpawnGroupEntry entry : group.mobs()) {
      total += Math.max(0.0, entry.weight());
    }
    if (total <= 0.0) {
      return spec.mobId();
    }
    double roll = ThreadLocalRandom.current().nextDouble() * total;
    double acc = 0.0;
    for (MobSpawnGroupEntry entry : group.mobs()) {
      acc += Math.max(0.0, entry.weight());
      if (roll <= acc) {
        return entry.mobId();
      }
    }
    return group.mobs().get(group.mobs().size() - 1).mobId();
  }

  private void spawnBeam(World world, Location from, Location to, org.bukkit.Particle particle, double step) {
    double distance = from.distance(to);
    if (distance <= 0.0) {
      return;
    }
    double safeStep = Math.max(0.1, step);
    int points = Math.max(1, (int) Math.ceil(distance / safeStep));
    Vector dir = to.clone().subtract(from).toVector().multiply(1.0 / points);
    Location cursor = from.clone();
    for (int i = 0; i <= points; i++) {
      world.spawnParticle(particle, cursor, 1, 0.0, 0.0, 0.0, 0.0);
      cursor.add(dir);
    }
  }

  private void enforceTether(MobSpawnSpec spec, SpawnState state, long now) {
    if (spec.tetherRadius() <= 0.0 || spec.tetherAction() == null || spec.tetherAction() == MobSpawnTetherAction.NONE) {
      state.outOfBoundsSince.clear();
      return;
    }
    World world = Bukkit.getWorld(spec.worldName());
    if (world == null) {
      return;
    }
    Location home = spec.location().clone();
    home.setWorld(world);
    double radius = spec.tetherRadius();
    double radiusSq = radius * radius;
    long despawnTicks = spec.tetherDespawnTicks();
    List<UUID> ids = new ArrayList<>(state.alive);
    for (UUID id : ids) {
      Entity entity = Bukkit.getEntity(id);
      if (!(entity instanceof LivingEntity living) || !entity.isValid() || living.isDead()) {
        state.alive.remove(id);
        state.outOfBoundsSince.remove(id);
        continue;
      }
      if (!living.getWorld().equals(world)) {
        markOutOfBounds(spec, state, now, living);
        continue;
      }
      double distSq = living.getLocation().distanceSquared(home);
      if (distSq <= radiusSq) {
        state.outOfBoundsSince.remove(id);
        continue;
      }
      state.outOfBoundsSince.putIfAbsent(id, now);
      long since = state.outOfBoundsSince.getOrDefault(id, now);
      if (despawnTicks > 0L && now - since >= despawnTicks) {
        living.remove();
        state.alive.remove(id);
        state.outOfBoundsSince.remove(id);
        continue;
      }
      if (living instanceof Mob mob) {
        LivingEntity target = mob.getTarget();
        if (target != null && target.isValid() && !target.isDead() && target.getWorld().equals(world)) {
          double leashSq = radiusSq;
          if (spec.attackRadius() > 0.0) {
            leashSq = Math.max(leashSq, spec.attackRadius() * spec.attackRadius());
          }
          double targetDistSq = target.getLocation().distanceSquared(home);
          if (targetDistSq <= leashSq) {
            state.outOfBoundsSince.remove(id);
            continue;
          }
        }
      }
      switch (spec.tetherAction()) {
        case TELEPORT -> {
          Location target = randomWithinRadius(home, spec.radius());
          living.teleport(target);
          state.outOfBoundsSince.remove(id);
        }
        case PULL -> {
          Vector dir = home.toVector().subtract(living.getLocation().toVector());
          if (dir.lengthSquared() > 1e-6) {
            double dist = Math.sqrt(distSq);
            double over = Math.max(0.0, dist - radius);
            double speed = spec.tetherPullSpeed();
            if (over > 0.0) {
              speed = Math.min(1.5, speed + over * 0.08);
            }
            dir.normalize().multiply(speed);
            living.setVelocity(dir);
          }
        }
        case DESPAWN -> {
          if (despawnTicks <= 0L) {
            living.remove();
            state.alive.remove(id);
            state.outOfBoundsSince.remove(id);
          }
        }
        default -> {
        }
      }
    }
  }

  private void markOutOfBounds(MobSpawnSpec spec, SpawnState state, long now, LivingEntity living) {
    UUID id = living.getUniqueId();
    state.outOfBoundsSince.putIfAbsent(id, now);
    long despawnTicks = spec.tetherDespawnTicks();
    if (despawnTicks > 0L) {
      long since = state.outOfBoundsSince.getOrDefault(id, now);
      if (now - since >= despawnTicks) {
        living.remove();
        state.alive.remove(id);
        state.outOfBoundsSince.remove(id);
      }
    }
  }

  private Location randomWithinRadius(Location home, double radius) {
    if (radius <= 0.0) {
      return home.clone();
    }
    double angle = rng.nextDouble() * Math.PI * 2.0;
    double r = rng.nextDouble() * radius;
    Location out = home.clone();
    out.add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
    return out;
  }

  private Location resolveSpawnLocation(World world, Location base, double radius) {
    Location origin = base.clone();
    int attempts = 12;
    for (int i = 0; i < attempts; i++) {
      Location candidate = origin.clone();
      if (radius > 0.0) {
        double angle = rng.nextDouble() * Math.PI * 2.0;
        double r = rng.nextDouble() * radius;
        candidate.add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
      }
      Location safe = findSafeLocation(world, candidate, true);
      if (safe != null) {
        return safe;
      }
    }
    Location fallback = findSafeLocation(world, origin, false);
    return fallback == null ? origin : fallback;
  }

  private Location findSafeLocation(World world, Location candidate, boolean requireGround) {
    int x = candidate.getBlockX();
    int z = candidate.getBlockZ();
    int minY = world.getMinHeight();
    int maxY = world.getMaxHeight() - 2;
    int baseY = Math.max(minY + 1, Math.min(maxY, candidate.getBlockY()));
    int[] offsets = {0, 1, 2, -1, -2, -3, 3, 4};
    for (int offset : offsets) {
      int y = baseY + offset;
      if (y < minY + 1 || y > maxY) {
        continue;
      }
      if (isSafeAt(world, x, y, z, requireGround)) {
        return new Location(world, x + 0.5, y, z + 0.5, candidate.getYaw(), candidate.getPitch());
      }
    }
    return null;
  }

  private boolean isSafeAt(World world, int x, int y, int z, boolean requireGround) {
    org.bukkit.block.Block feet = world.getBlockAt(x, y, z);
    org.bukkit.block.Block head = world.getBlockAt(x, y + 1, z);
    if (!feet.isPassable() || !head.isPassable()) {
      return false;
    }
    if (!requireGround) {
      return true;
    }
    org.bukkit.block.Block ground = world.getBlockAt(x, y - 1, z);
    return ground.getType().isSolid();
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

  private boolean isChunkLoaded(World world, Location location) {
    if (world == null || location == null) {
      return false;
    }
    int blockX = (int) Math.floor(location.getX());
    int blockZ = (int) Math.floor(location.getZ());
    int chunkX = blockX >> 4;
    int chunkZ = blockZ >> 4;
    return world.isChunkLoaded(chunkX, chunkZ);
  }

  private boolean hasNearbyPlayers(World world, MobSpawnSpec spec) {
    double radius = spec.activationRadius();
    if (radius <= 0.0) {
      return true;
    }
    Location center = spec.location();
    if (center == null) {
      return false;
    }
    center = center.clone();
    center.setWorld(world);
    double radiusSq = radius * radius;
    for (Player player : world.getPlayers()) {
      if (player.getLocation().distanceSquared(center) <= radiusSq) {
        return true;
      }
    }
    return false;
  }

  @EventHandler
  public void onDeath(EntityDeathEvent event) {
    handleRemove(event.getEntity());
  }

  @EventHandler(ignoreCancelled = true)
  public void onDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Player player)) {
      return;
    }
    String spawnId = resolveSpawnId(event.getDamager());
    if (spawnId == null) {
      return;
    }
    MobSpawnSpec spec = spawns.get(spawnId);
    if (spec == null) {
      return;
    }
    if (spec.attackIgnorePlayers()) {
      event.setCancelled(true);
      return;
    }
    double radius = spec.attackRadius();
    if (radius <= 0.0) {
      return;
    }
    Location center = spec.location();
    if (center == null || player.getWorld() == null) {
      return;
    }
    World world = Bukkit.getWorld(spec.worldName());
    if (world == null || !player.getWorld().equals(world)) {
      return;
    }
    Location spawnLoc = center.clone();
    spawnLoc.setWorld(world);
    if (player.getLocation().distanceSquared(spawnLoc) > radius * radius) {
      event.setCancelled(true);
    }
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
      if (spec != null) {
        scheduleNextSpawn(spec, state, engine.tickNow());
      }
    }
  }

  private String resolveSpawnId(Entity damager) {
    if (damager == null) {
      return null;
    }
    String spawnId = entityToSpawn.get(damager.getUniqueId());
    if (spawnId != null) {
      return spawnId;
    }
    if (damager instanceof Projectile projectile) {
      Object shooter = projectile.getShooter();
      if (shooter instanceof Entity shooterEntity) {
        return entityToSpawn.get(shooterEntity.getUniqueId());
      }
    }
    return null;
  }

  public MobSpawnSpec spawnSpecForEntity(Entity entity) {
    if (entity == null) {
      return null;
    }
    String spawnId = entityToSpawn.get(entity.getUniqueId());
    return spawnId == null ? null : spawns.get(spawnId);
  }

  private void restoreActiveEntities(Map<UUID, String> previousEntities) {
    int restored = 0;
    for (Map.Entry<UUID, String> entry : previousEntities.entrySet()) {
      String spawnId = entry.getValue();
      MobSpawnSpec spec = spawns.get(spawnId);
      if (spec == null) {
        continue;
      }
      Entity entity = Bukkit.getEntity(entry.getKey());
      if (!(entity instanceof LivingEntity living) || !entity.isValid() || living.isDead()) {
        continue;
      }
      SpawnState state = states.computeIfAbsent(spawnId, k -> new SpawnState());
      state.alive.add(entity.getUniqueId());
      entityToSpawn.put(entity.getUniqueId(), spawnId);
      restored++;
    }
    if (restored > 0) {
      logger.info("[Mobs] spawn reload: restored " + restored + " active mobs");
    }
  }

  private Map<String, Integer> computeGroupCounts() {
    Map<String, Integer> counts = new HashMap<>();
    for (MobSpawnSpec spec : spawnListCache) {
      String groupId = spec.groupId();
      if (groupId == null || groupId.isBlank()) {
        continue;
      }
      SpawnState state = states.computeIfAbsent(spec.id(), k -> new SpawnState());
      int current = counts.getOrDefault(groupId, 0);
      counts.put(groupId, current + state.alive.size());
    }
    return counts;
  }

  private void scheduleNextSpawn(MobSpawnSpec spec, SpawnState state, long now) {
    if (spec.respawnTicks() <= 0L) {
      return;
    }
    long jitter = spec.respawnJitterTicks();
    long delta = 0L;
    if (jitter > 0L) {
      int bound = (int) Math.min(Integer.MAX_VALUE / 2, jitter * 2L + 1L);
      delta = rng.nextInt(bound) - jitter;
    }
    long next = spec.respawnTicks() + delta;
    if (next < 0L) {
      next = 0L;
    }
    state.nextSpawnTick = now + next;
  }

  public List<SpawnerSnapshot> snapshots() {
    long now = engine.tickNow();
    List<SpawnerSnapshot> out = new ArrayList<>();
    for (MobSpawnSpec spec : spawns.values()) {
      SpawnState state = states.get(spec.id());
      int alive = state == null ? 0 : state.alive.size();
      long nextSeconds = -1L;
      if (spec.respawnTicks() > 0L) {
        long nextTick = state == null ? 0L : state.nextSpawnTick;
        long delta = Math.max(0L, nextTick - now);
        nextSeconds = (delta + 19L) / 20L;
      }
      Location loc = spec.location();
      double x = loc == null ? 0.0 : loc.getX();
      double y = loc == null ? 0.0 : loc.getY();
      double z = loc == null ? 0.0 : loc.getZ();
      out.add(new SpawnerSnapshot(
          spec.id(),
          spec.mobId(),
          spec.worldName(),
          x,
          y,
          z,
          alive,
          spec.maxAlive(),
          nextSeconds,
          spec.enabled()));
    }
    out.sort(Comparator.comparing(SpawnerSnapshot::id));
    return out;
  }

  private void updateHologram(MobSpawnSpec spec, SpawnState state, long now) {
    if (!spec.hologramEnabled()) {
      removeHologram(state);
      return;
    }
    if (state.nextHologramTick > now && state.hologramId != null) {
      return;
    }
    World world = Bukkit.getWorld(spec.worldName());
    if (world == null) {
      removeHologram(state);
      return;
    }
    Location base = spec.location().clone();
    base.setWorld(world);
    base.add(0.0, spec.hologramOffsetY(), 0.0);
    TextDisplay display = null;
    if (state.hologramId != null) {
      Entity entity = Bukkit.getEntity(state.hologramId);
      if (entity instanceof TextDisplay existing && entity.isValid()) {
        display = existing;
      } else {
        state.hologramId = null;
      }
    }
    if (display == null) {
      display = world.spawn(base, TextDisplay.class, spawned -> {
        spawned.setPersistent(false);
        spawned.setBillboard(Display.Billboard.CENTER);
        spawned.setSeeThrough(true);
        spawned.setDefaultBackground(false);
      });
      state.hologramId = display.getUniqueId();
    } else {
      display.teleport(base);
    }
    String format = spec.hologramFormat();
    if (format == null || format.isBlank()) {
      format = DEFAULT_HOLOGRAM_FORMAT;
    }
    String mobLabel = spec.mobId();
    MobSpec mobSpec = registry.get(spec.mobId());
    if (mobSpec != null && mobSpec.displayName() != null) {
      mobLabel = PLAIN.serialize(mobSpec.displayName());
    }
    int alive = state.alive.size();
    String cap = spec.maxAlive() <= 0 ? "inf" : String.valueOf(spec.maxAlive());
    String nextText;
    if (!spec.enabled() || !isWorldEnabled(spec.worldName())) {
      nextText = "paused";
    } else if (spec.respawnTicks() <= 0L) {
      nextText = "paused";
    } else {
      long delta = Math.max(0L, state.nextSpawnTick - now);
      long seconds = (delta + 19L) / 20L;
      nextText = seconds + "s";
    }
    String raw = format
        .replace("{id}", spec.id())
        .replace("{mob}", mobLabel)
        .replace("{alive}", String.valueOf(alive))
        .replace("{cap}", cap)
        .replace("{next}", nextText);
    display.text(MobText.parse(raw));
    state.nextHologramTick = now + HOLOGRAM_UPDATE_TICKS;
  }

  private void removeHologram(SpawnState state) {
    if (state.hologramId == null) {
      return;
    }
    Entity entity = Bukkit.getEntity(state.hologramId);
    if (entity != null) {
      entity.remove();
    }
    state.hologramId = null;
    state.nextHologramTick = 0L;
  }

  private void clearAllHolograms() {
    for (SpawnState state : states.values()) {
      removeHologram(state);
    }
  }
}
