package dev.patric.dungeonsreborn.effects.minions;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import net.kyori.adventure.text.Component;
import org.bukkit.potion.PotionEffect;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.damage.DamageType;
import dev.patric.dungeonsreborn.effects.mana.ManaProvider;
import dev.patric.dungeonsreborn.mobs.MobMarkers;
import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.DungeonsRebornPlugin;
import dev.patric.dungeonsreborn.progression.PlayerProgression;
import dev.patric.dungeonsreborn.progression.ProgressionService;

public final class MinionManager implements Listener {
  private record MinionInstance(
      String id,
      String mobId,
      UUID ownerId,
      long expiresTick,
      boolean despawnOnLogout,
      MinionMode modeOverride,
      MinionTargetRules targetRules,
      String mainAttackOverride,
      String secondaryAttackOverride,
      boolean disableBasePassives,
      boolean disableBaseAttacks,
      boolean disableBaseAi) {
  }

  public record MinionLoadoutSnapshot(String minionId, String mobId, int count, String mainAttackOverride,
                                      String secondaryAttackOverride, boolean disableBasePassives,
                                      boolean disableBaseAttacks, boolean disableBaseAi, MinionMode modeOverride) {
  }

  private final EffectsEngine engine;
  private final MobRegistry mobs;
  private final Random rng = new Random();
  private final Map<UUID, MinionInstance> byEntity = new ConcurrentHashMap<>();
  private final Map<UUID, java.util.Set<UUID>> byOwner = new ConcurrentHashMap<>();
  private final Map<UUID, MinionMode> modeByOwner = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> lastAttackerByOwner = new ConcurrentHashMap<>();
  private final AtomicLong spawnedCount = new AtomicLong();
  private final AtomicLong despawnedCount = new AtomicLong();
  private volatile int maxPerOwner;

  public MinionManager(EffectsEngine engine, MobRegistry mobs) {
    this.engine = Objects.requireNonNull(engine, "engine");
    this.mobs = Objects.requireNonNull(mobs, "mobs");
  }

  public void setMaxPerOwner(int maxPerOwner) {
    this.maxPerOwner = Math.max(0, maxPerOwner);
  }

  public List<LivingEntity> summon(MinionSpec spec, Location origin) {
    return summon(spec, origin, null);
  }

  public List<LivingEntity> summon(MinionSpec spec, Location origin, Consumer<LivingEntity> onSpawn) {
    Objects.requireNonNull(spec, "spec");
    Objects.requireNonNull(origin, "origin");
    if (spec.ownerId() != null && spec.id() != null && !spec.id().isBlank()) {
      dismissById(spec.ownerId(), spec.id());
    }
    List<LivingEntity> spawned = java.util.Collections.synchronizedList(new ArrayList<>());
    int available = resolveAvailableSlots(spec.ownerId(), spec.count());
    if (available <= 0) {
      return spawned;
    }
    MinionSummonSpec summonSpec = spec.summonSpec();
    int waves = Math.min(Math.max(1, summonSpec.waves()), available);
    int baseCount = available / waves;
    int remainder = available % waves;
    long interval = summonSpec.waveIntervalTicks();
    for (int waveIndex = 0; waveIndex < waves; waveIndex++) {
      int waveCount = baseCount + (waveIndex < remainder ? 1 : 0);
      long delay = interval <= 0L ? 0L : interval * waveIndex;
      Runnable task = () -> spawnWave(spec, origin, waveCount, summonSpec, spawned, onSpawn);
      if (delay <= 0L) {
        task.run();
      } else {
        engine.runLater(delay, task);
      }
    }
    return spawned;
  }

  public void restorePersistentMinions() {
    long now = System.currentTimeMillis();
    long tickNow = engine.tickNow();
    for (org.bukkit.World world : Bukkit.getWorlds()) {
      for (LivingEntity living : world.getLivingEntities()) {
        String minionId = MobMarkers.getMinionId(living);
        if (minionId == null || !MobMarkers.isMinionPersistent(living)) {
          continue;
        }
        String mobId = MobMarkers.getMobId(living);
        if (mobId == null) {
          continue;
        }
        Long expiresAt = MobMarkers.getMinionExpiresAt(living);
        if (expiresAt != null && expiresAt > 0L && expiresAt <= now) {
          despawn(living.getUniqueId());
          continue;
        }
        long remainingTicks = expiresAt == null || expiresAt <= 0L ? 20L * 30L : Math.max(1L, (expiresAt - now) / 50L);
        long expiresTick = tickNow + remainingTicks;
        UUID ownerId = MobMarkers.getOwner(living);
        boolean despawnOnLogout = Boolean.TRUE.equals(MobMarkers.getMinionDespawnOnLogout(living));
        MinionMode modeOverride = MobMarkers.getMinionMode(living);
        boolean allowPvp = Boolean.TRUE.equals(MobMarkers.getMinionAllowPvp(living));
        boolean allowParty = Boolean.TRUE.equals(MobMarkers.getMinionAllowPartyTargets(living));
        Boolean shareOwnerAggroRaw = MobMarkers.getMinionShareOwnerAggro(living);
        boolean shareOwnerAggro = shareOwnerAggroRaw == null || shareOwnerAggroRaw;
        Double radiusOverride = MobMarkers.getMinionTargetRadius(living);
        double maxDistanceFromOwner = radiusOverride == null ? 0.0 : Math.max(0.0, radiusOverride);
        MinionTargetRules targetRules = new MinionTargetRules(allowPvp, allowParty, shareOwnerAggro, maxDistanceFromOwner);
        String mainAttackOverride = MobMarkers.getMinionMainAttack(living);
        String secondaryAttackOverride = MobMarkers.getMinionSecondaryAttack(living);
        boolean disableBasePassives = Boolean.TRUE.equals(MobMarkers.getMinionDisableBasePassives(living));
        boolean disableBaseAttacks = Boolean.TRUE.equals(MobMarkers.getMinionDisableBaseAttacks(living));
        boolean disableBaseAi = Boolean.TRUE.equals(MobMarkers.getMinionDisableBaseAi(living));
        String nameOverride = MobMarkers.getMinionNameOverride(living);
        Boolean glowOverride = MobMarkers.getMinionGlowOverride(living);
        register(living.getUniqueId(), new MinionInstance(minionId, mobId, ownerId, expiresTick, despawnOnLogout,
            modeOverride, targetRules, mainAttackOverride, secondaryAttackOverride,
            disableBasePassives, disableBaseAttacks, disableBaseAi));
        if (nameOverride != null) {
          living.customName(Component.text(nameOverride));
          living.setCustomNameVisible(true);
        }
        if (glowOverride != null) {
          living.setGlowing(glowOverride);
        }
        engine.runLater(remainingTicks, () -> despawn(living.getUniqueId()));
      }
    }
  }

  public int recall(UUID ownerId, Location origin) {
    Objects.requireNonNull(ownerId, "ownerId");
    Objects.requireNonNull(origin, "origin");
    java.util.Set<UUID> set = byOwner.get(ownerId);
    if (set == null || set.isEmpty()) {
      return 0;
    }
    int moved = 0;
    for (UUID entityId : List.copyOf(set)) {
      Entity entity = Bukkit.getEntity(entityId);
      if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
        continue;
      }
      living.teleport(offset(origin, 1.5));
      moved++;
    }
    sendStatus(ownerId);
    return moved;
  }

  public int dismiss(UUID ownerId) {
    Objects.requireNonNull(ownerId, "ownerId");
    java.util.Set<UUID> set = byOwner.get(ownerId);
    if (set == null || set.isEmpty()) {
      return 0;
    }
    int removed = 0;
    for (UUID entityId : List.copyOf(set)) {
      if (despawn(entityId)) {
        removed++;
      }
    }
    sendStatus(ownerId);
    return removed;
  }

  public int dismissById(UUID ownerId, String minionId) {
    Objects.requireNonNull(ownerId, "ownerId");
    Objects.requireNonNull(minionId, "minionId");
    java.util.Set<UUID> set = byOwner.get(ownerId);
    if (set == null || set.isEmpty()) {
      return 0;
    }
    int removed = 0;
    for (UUID entityId : List.copyOf(set)) {
      MinionInstance inst = byEntity.get(entityId);
      if (inst != null && minionId.equals(inst.id())) {
        if (despawn(entityId)) {
          removed++;
        }
      }
    }
    sendStatus(ownerId);
    return removed;
  }

  public MinionMode mode(UUID ownerId) {
    if (ownerId == null) {
      return MinionMode.AGGRESSIVE;
    }
    return modeByOwner.getOrDefault(ownerId, MinionMode.AGGRESSIVE);
  }

  public void setMode(UUID ownerId, MinionMode mode) {
    if (ownerId == null) {
      return;
    }
    if (mode == null) {
      modeByOwner.remove(ownerId);
    } else {
      modeByOwner.put(ownerId, mode);
    }
  }

  public UUID ownerLastAttacker(UUID ownerId) {
    if (ownerId == null) {
      return null;
    }
    return lastAttackerByOwner.get(ownerId);
  }

  public List<UUID> minionsFor(UUID ownerId) {
    java.util.Set<UUID> set = byOwner.get(ownerId);
    if (set == null) {
      return List.of();
    }
    return List.copyOf(set);
  }

  public boolean transferEntityOwnership(UUID entityId, UUID newOwnerId) {
    MinionInstance inst = byEntity.get(entityId);
    if (inst == null) {
      return false;
    }
    if (inst.ownerId() != null) {
      java.util.Set<UUID> set = byOwner.get(inst.ownerId());
      if (set != null) {
        set.remove(entityId);
        if (set.isEmpty()) {
          byOwner.remove(inst.ownerId());
        }
      }
    }
    MinionInstance updated = new MinionInstance(inst.id(), inst.mobId(), newOwnerId, inst.expiresTick(),
        inst.despawnOnLogout(), inst.modeOverride(), inst.targetRules(), inst.mainAttackOverride(),
        inst.secondaryAttackOverride(), inst.disableBasePassives(), inst.disableBaseAttacks(), inst.disableBaseAi());
    byEntity.put(entityId, updated);
    if (newOwnerId != null) {
      byOwner.computeIfAbsent(newOwnerId, k -> java.util.Collections.newSetFromMap(new ConcurrentHashMap<>())).add(entityId);
    }
    Entity entity = Bukkit.getEntity(entityId);
    if (entity != null) {
      MobMarkers.setOwner(entity, newOwnerId);
    }
    return true;
  }

  public int transferOwnership(UUID fromOwnerId, UUID toOwnerId) {
    java.util.Set<UUID> set = byOwner.get(fromOwnerId);
    if (set == null || set.isEmpty()) {
      return 0;
    }
    int moved = 0;
    for (UUID entityId : List.copyOf(set)) {
      if (transferEntityOwnership(entityId, toOwnerId)) {
        moved++;
      }
    }
    return moved;
  }

  public int activeCount() {
    return byEntity.size();
  }

  public long spawnedCount() {
    return spawnedCount.get();
  }

  public long despawnedCount() {
    return despawnedCount.get();
  }

  public Map<UUID, List<UUID>> ownersSnapshot() {
    Map<UUID, List<UUID>> out = new java.util.LinkedHashMap<>();
    for (Map.Entry<UUID, java.util.Set<UUID>> entry : byOwner.entrySet()) {
      out.put(entry.getKey(), List.copyOf(entry.getValue()));
    }
    return out;
  }

  public boolean hasMob(String id) {
    return id != null && mobs.has(id);
  }

  public boolean disableBasePassives(UUID entityId) {
    MinionInstance inst = entityId == null ? null : byEntity.get(entityId);
    return inst != null && inst.disableBasePassives();
  }

  public boolean disableBaseAttacks(UUID entityId) {
    MinionInstance inst = entityId == null ? null : byEntity.get(entityId);
    return inst != null && inst.disableBaseAttacks();
  }

  public boolean disableBaseAi(UUID entityId) {
    MinionInstance inst = entityId == null ? null : byEntity.get(entityId);
    return inst != null && inst.disableBaseAi();
  }

  public MinionMode mode(UUID entityId, UUID ownerId) {
    MinionInstance inst = entityId == null ? null : byEntity.get(entityId);
    if (inst != null && inst.modeOverride() != null) {
      return inst.modeOverride();
    }
    if (ownerId == null) {
      return MinionMode.AGGRESSIVE;
    }
    return modeByOwner.getOrDefault(ownerId, MinionMode.AGGRESSIVE);
  }

  public MinionTargetRules targetRules(UUID entityId) {
    MinionInstance inst = entityId == null ? null : byEntity.get(entityId);
    if (inst == null || inst.targetRules() == null) {
      return MinionTargetRules.DEFAULT;
    }
    return inst.targetRules();
  }

  public String resolveAttackOverride(UUID entityId, boolean main, String fallback) {
    MinionInstance inst = entityId == null ? null : byEntity.get(entityId);
    if (inst == null) {
      return fallback;
    }
    String override = main ? inst.mainAttackOverride() : inst.secondaryAttackOverride();
    return override == null ? fallback : override;
  }

  public MinionLoadoutSnapshot loadout(UUID ownerId, String minionId) {
    Objects.requireNonNull(ownerId, "ownerId");
    Objects.requireNonNull(minionId, "minionId");
    String normalizedId = minionId.trim().toLowerCase(java.util.Locale.ROOT);
    List<UUID> ids = minionsFor(ownerId);
    if (ids.isEmpty()) {
      return null;
    }
    int count = 0;
    String mobId = null;
    String mainAttack = null;
    String secondaryAttack = null;
    boolean disablePassives = false;
    boolean disableAttacks = false;
    boolean disableAi = false;
    MinionMode mode = null;
    for (UUID entityId : ids) {
      Entity entity = Bukkit.getEntity(entityId);
      if (!(entity instanceof LivingEntity living)) {
        continue;
      }
      String id = MobMarkers.getMinionId(living);
      if (id == null || !id.equalsIgnoreCase(normalizedId)) {
        continue;
      }
      count++;
      if (mobId == null) {
        mobId = MobMarkers.getMobId(living);
      }
      if (mainAttack == null) {
        mainAttack = MobMarkers.getMinionMainAttack(living);
      }
      if (secondaryAttack == null) {
        secondaryAttack = MobMarkers.getMinionSecondaryAttack(living);
      }
      if (!disablePassives) {
        disablePassives = Boolean.TRUE.equals(MobMarkers.getMinionDisableBasePassives(living));
      }
      if (!disableAttacks) {
        disableAttacks = Boolean.TRUE.equals(MobMarkers.getMinionDisableBaseAttacks(living));
      }
      if (!disableAi) {
        disableAi = Boolean.TRUE.equals(MobMarkers.getMinionDisableBaseAi(living));
      }
      if (mode == null) {
        mode = MobMarkers.getMinionMode(living);
      }
    }
    if (count == 0) {
      return null;
    }
    return new MinionLoadoutSnapshot(normalizedId, mobId, count, mainAttack, secondaryAttack,
        disablePassives, disableAttacks, disableAi, mode);
  }

  public int updateMinionLoadout(UUID ownerId, String minionId, String mainAttackOverride,
                                 String secondaryAttackOverride, Boolean disableBasePassives,
                                 Boolean disableBaseAttacks, Boolean disableBaseAi, MinionMode modeOverride) {
    Objects.requireNonNull(ownerId, "ownerId");
    Objects.requireNonNull(minionId, "minionId");
    String normalizedId = minionId.trim().toLowerCase(java.util.Locale.ROOT);
    int updated = 0;
    for (UUID entityId : minionsFor(ownerId)) {
      Entity entity = Bukkit.getEntity(entityId);
      if (!(entity instanceof LivingEntity living)) {
        continue;
      }
      String id = MobMarkers.getMinionId(living);
      if (id == null || !id.equalsIgnoreCase(normalizedId)) {
        continue;
      }
      if (mainAttackOverride != null) {
        MobMarkers.setMinionMainAttack(living, mainAttackOverride);
      }
      if (secondaryAttackOverride != null) {
        MobMarkers.setMinionSecondaryAttack(living, secondaryAttackOverride);
      }
      if (disableBasePassives != null) {
        MobMarkers.setMinionDisableBasePassives(living, disableBasePassives);
      }
      if (disableBaseAttacks != null) {
        MobMarkers.setMinionDisableBaseAttacks(living, disableBaseAttacks);
      }
      if (disableBaseAi != null) {
        MobMarkers.setMinionDisableBaseAi(living, disableBaseAi);
      }
      if (modeOverride != null) {
        MobMarkers.setMinionMode(living, modeOverride);
      }
      byEntity.computeIfPresent(entityId, (key, inst) -> new MinionInstance(
          inst.id(),
          inst.mobId(),
          inst.ownerId(),
          inst.expiresTick(),
          inst.despawnOnLogout(),
          modeOverride != null ? modeOverride : inst.modeOverride(),
          inst.targetRules(),
          mainAttackOverride != null ? mainAttackOverride : inst.mainAttackOverride(),
          secondaryAttackOverride != null ? secondaryAttackOverride : inst.secondaryAttackOverride(),
          disableBasePassives != null ? disableBasePassives : inst.disableBasePassives(),
          disableBaseAttacks != null ? disableBaseAttacks : inst.disableBaseAttacks(),
          disableBaseAi != null ? disableBaseAi : inst.disableBaseAi()));
      updated++;
    }
    return updated;
  }

  public boolean despawn(UUID entityId) {
    MinionInstance inst = byEntity.remove(entityId);
    if (inst == null) {
      return false;
    }
    if (inst.ownerId() != null) {
      java.util.Set<UUID> set = byOwner.get(inst.ownerId());
      if (set != null) {
        set.remove(entityId);
        if (set.isEmpty()) {
          byOwner.remove(inst.ownerId());
        }
      }
    }
    Entity entity = Bukkit.getEntity(entityId);
    if (entity instanceof LivingEntity living) {
      mobs.playMinionDespawnFx(living);
      living.remove();
    } else if (entity != null) {
      entity.remove();
    }
    despawnedCount.incrementAndGet();
    sendStatus(inst.ownerId());
    return true;
  }

  private void register(UUID entityId, MinionInstance inst) {
    byEntity.put(entityId, inst);
    if (inst.ownerId() != null) {
      byOwner.computeIfAbsent(inst.ownerId(), k -> java.util.Collections.newSetFromMap(new ConcurrentHashMap<>())).add(entityId);
    }
    spawnedCount.incrementAndGet();
  }

  private int resolveAvailableSlots(UUID ownerId, int requested) {
    if (requested <= 0) {
      return 0;
    }
    int limit = maxPerOwner;
    if (limit <= 0 || ownerId == null) {
      return requested;
    }
    int current = minionsFor(ownerId).size();
    int available = limit - current;
    if (available <= 0) {
      return 0;
    }
    return Math.min(requested, available);
  }

  private Location offset(Location origin, double radius) {
    if (radius <= 0.0) {
      return origin.clone();
    }
    double angle = rng.nextDouble() * Math.PI * 2.0;
    double r = rng.nextDouble() * radius;
    double dx = Math.cos(angle) * r;
    double dz = Math.sin(angle) * r;
    return origin.clone().add(dx, 0.0, dz);
  }

  private void spawnWave(MinionSpec spec, Location origin, int requested, MinionSummonSpec summonSpec,
                         List<LivingEntity> spawned, Consumer<LivingEntity> onSpawn) {
    int available = resolveAvailableSlots(spec.ownerId(), requested);
    if (available <= 0) {
      return;
    }
    List<Location> locations = resolveSpawnLocations(origin, available, summonSpec, spec.spawnRadius());
    for (Location base : locations) {
      Location spawn = summonSpec.safeSpawn()
          ? findSafeSpawn(origin, base, summonSpec.maxSpawnAttempts())
          : base;
      LivingEntity entity = spawnMinion(spec, spawn);
      if (entity == null) {
        continue;
      }
      if (onSpawn != null) {
        onSpawn.accept(entity);
      }
      spawned.add(entity);
    }
    sendStatus(spec.ownerId());
  }

  private LivingEntity spawnMinion(MinionSpec spec, Location spawn) {
    LivingEntity entity = mobs.spawn(spec.mobId(), spawn, spec.ownerId());
    if (entity == null) {
      return null;
    }
    MobMarkers.setMinionId(entity, spec.id());
    if (spec.persistent()) {
      long expiresAt = System.currentTimeMillis() + (spec.durationTicks() * 50L);
      MobMarkers.setMinionPersistent(entity, true);
      MobMarkers.setMinionExpiresAt(entity, expiresAt);
      MobMarkers.setMinionDespawnOnLogout(entity, spec.despawnOnOwnerLogout());
      if (spec.mode() != null) {
        MobMarkers.setMinionMode(entity, spec.mode());
      }
      MobMarkers.setMinionAllowPvp(entity, spec.targetRules().allowPvp());
      MobMarkers.setMinionAllowPartyTargets(entity, spec.targetRules().allowPartyTargets());
      MobMarkers.setMinionShareOwnerAggro(entity, spec.targetRules().shareOwnerAggro());
      MobMarkers.setMinionTargetRadius(entity, spec.targetRules().maxDistanceFromOwner());
      MobMarkers.setMinionMainAttack(entity, spec.mainAttackOverride());
      MobMarkers.setMinionSecondaryAttack(entity, spec.secondaryAttackOverride());
      MobMarkers.setMinionDisableBasePassives(entity, spec.disableBasePassives());
      MobMarkers.setMinionDisableBaseAttacks(entity, spec.disableBaseAttacks());
      MobMarkers.setMinionDisableBaseAi(entity, spec.disableBaseAi());
      MobMarkers.setMinionNameOverride(entity, spec.nameOverride());
      MobMarkers.setMinionGlowOverride(entity, spec.glowOverride());
    }
    long expires = engine.tickNow() + spec.durationTicks();
    register(entity.getUniqueId(), new MinionInstance(spec.id(), spec.mobId(), spec.ownerId(), expires,
        spec.despawnOnOwnerLogout(), spec.mode(), spec.targetRules(), spec.mainAttackOverride(),
        spec.secondaryAttackOverride(), spec.disableBasePassives(), spec.disableBaseAttacks(), spec.disableBaseAi()));
    if (spec.mode() != null && spec.ownerId() != null) {
      modeByOwner.put(spec.ownerId(), spec.mode());
    }
    applyScaling(entity, spec);
    applyStatOverrides(entity, spec);
    applyOwnerEffects(entity, spec);
    applyResistances(entity, spec);
    applyCosmetics(entity, spec);
    scheduleParticles(entity, spec, expires);
    schedulePassives(entity, spec, expires);
    scheduleSpecialAttacks(entity, spec, expires);
    engine.runLater(spec.durationTicks(), () -> despawn(entity.getUniqueId()));
    return entity;
  }

  private List<Location> resolveSpawnLocations(Location origin, int count, MinionSummonSpec summonSpec,
                                               double fallbackRadius) {
    List<Location> locations = new ArrayList<>(count);
    if (count <= 0) {
      return locations;
    }
    double radius = summonSpec.formationRadius() > 0.0 ? summonSpec.formationRadius() : fallbackRadius;
    MinionFormation formation = summonSpec.formation();
    if (formation == null) {
      formation = MinionFormation.RANDOM;
    }
    if (radius <= 0.0) {
      for (int i = 0; i < count; i++) {
        locations.add(origin.clone());
      }
      return locations;
    }
    switch (formation) {
      case CIRCLE -> {
        if (count == 1) {
          locations.add(origin.clone());
          return locations;
        }
        for (int i = 0; i < count; i++) {
          double angle = (Math.PI * 2.0) * i / count;
          double dx = Math.cos(angle) * radius;
          double dz = Math.sin(angle) * radius;
          locations.add(origin.clone().add(dx, 0.0, dz));
        }
      }
      case LINE -> {
        if (count == 1) {
          locations.add(origin.clone());
          return locations;
        }
        Vector dir = origin.getDirection().clone();
        dir.setY(0.0);
        if (dir.lengthSquared() < 1.0e-6) {
          dir = new Vector(1.0, 0.0, 0.0);
        } else {
          dir.normalize();
        }
        double length = radius * 2.0;
        double step = count == 1 ? 0.0 : length / (count - 1);
        double start = -length / 2.0;
        for (int i = 0; i < count; i++) {
          double offset = start + (step * i);
          Vector delta = dir.clone().multiply(offset);
          locations.add(origin.clone().add(delta));
        }
      }
      case CONE -> {
        if (count == 1) {
          locations.add(origin.clone());
          return locations;
        }
        Vector dir = origin.getDirection().clone();
        dir.setY(0.0);
        if (dir.lengthSquared() < 1.0e-6) {
          dir = new Vector(0.0, 0.0, 1.0);
        } else {
          dir.normalize();
        }
        double spread = Math.toRadians(60.0);
        double step = count == 1 ? 0.0 : spread / (count - 1);
        double start = -spread / 2.0;
        for (int i = 0; i < count; i++) {
          double angle = start + (step * i);
          double cos = Math.cos(angle);
          double sin = Math.sin(angle);
          double x = dir.getX() * cos - dir.getZ() * sin;
          double z = dir.getX() * sin + dir.getZ() * cos;
          Vector delta = new Vector(x, 0.0, z).normalize().multiply(radius);
          locations.add(origin.clone().add(delta));
        }
      }
      case GRID -> {
        if (count == 1) {
          locations.add(origin.clone());
          return locations;
        }
        int size = (int) Math.ceil(Math.sqrt(count));
        double step = size <= 1 ? 0.0 : radius / (size - 1);
        double offset = (size - 1) / 2.0;
        int placed = 0;
        for (int row = 0; row < size && placed < count; row++) {
          for (int col = 0; col < size && placed < count; col++) {
            double dx = (col - offset) * step;
            double dz = (row - offset) * step;
            locations.add(origin.clone().add(dx, 0.0, dz));
            placed++;
          }
        }
      }
      case RANDOM -> {
        for (int i = 0; i < count; i++) {
          locations.add(offset(origin, radius));
        }
      }
      default -> {
        for (int i = 0; i < count; i++) {
          locations.add(offset(origin, radius));
        }
      }
    }
    return locations;
  }

  private Location findSafeSpawn(Location origin, Location candidate, int attempts) {
    Location best = candidate;
    if (attempts <= 0) {
      return best;
    }
    for (int i = 0; i < attempts; i++) {
      Location check = i == 0 ? best : offset(candidate, 0.75);
      if (isSafeLocation(check)) {
        return check;
      }
    }
    return best;
  }

  private boolean isSafeLocation(Location location) {
    if (location.getWorld() == null) {
      return false;
    }
    Block base = location.getBlock();
    Block above = base.getRelative(BlockFace.UP);
    return base.isPassable() && above.isPassable();
  }

  @EventHandler
  public void onDeath(EntityDeathEvent event) {
    LivingEntity entity = event.getEntity();
    if (MobMarkers.getMinionId(entity) == null) {
      return;
    }
    despawn(entity.getUniqueId());
  }

  @EventHandler
  public void onRemove(EntityRemoveFromWorldEvent event) {
    Entity entity = event.getEntity();
    if (MobMarkers.getMinionId(entity) == null) {
      return;
    }
    despawn(entity.getUniqueId());
  }

  @EventHandler
  public void onOwnerQuit(PlayerQuitEvent event) {
    UUID ownerId = event.getPlayer().getUniqueId();
    java.util.Set<UUID> set = byOwner.get(ownerId);
    if (set == null || set.isEmpty()) {
      return;
    }
    for (UUID entityId : List.copyOf(set)) {
      MinionInstance inst = byEntity.get(entityId);
      if (inst != null && inst.despawnOnLogout()) {
        despawn(entityId);
      }
    }
    modeByOwner.remove(ownerId);
    lastAttackerByOwner.remove(ownerId);
  }

  @EventHandler
  public void onOwnerWorldChange(PlayerChangedWorldEvent event) {
    UUID ownerId = event.getPlayer().getUniqueId();
    java.util.Set<UUID> set = byOwner.get(ownerId);
    if (set == null || set.isEmpty()) {
      return;
    }
    for (UUID entityId : List.copyOf(set)) {
      despawn(entityId);
    }
    sendStatus(ownerId);
  }

  @EventHandler
  public void onChunkUnload(ChunkUnloadEvent event) {
    for (Entity entity : event.getChunk().getEntities()) {
      if (entity == null) {
        continue;
      }
      if (MobMarkers.getMinionId(entity) == null) {
        continue;
      }
      despawn(entity.getUniqueId());
    }
  }

  @EventHandler
  public void onDamage(EntityDamageByEntityEvent event) {
    LivingEntity damager = resolveDamager(event.getDamager());
    if (damager == null) {
      return;
    }
    if (MobMarkers.getMinionId(damager) == null) {
      return;
    }
    UUID ownerId = MobMarkers.getOwner(damager);
    if (ownerId == null) {
      return;
    }
    Entity target = event.getEntity();
    if (target.getUniqueId().equals(ownerId)) {
      event.setCancelled(true);
      return;
    }
    if (target instanceof Player) {
      event.setCancelled(true);
      return;
    }
    if (target instanceof LivingEntity living) {
      UUID targetOwner = MobMarkers.getOwner(living);
      if (targetOwner != null && targetOwner.equals(ownerId)) {
        event.setCancelled(true);
      }
    }
  }

  @EventHandler
  public void onOwnerDamaged(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Player player)) {
      return;
    }
    LivingEntity attacker = resolveDamager(event.getDamager());
    if (attacker == null) {
      return;
    }
    lastAttackerByOwner.put(player.getUniqueId(), attacker.getUniqueId());
  }

  private LivingEntity resolveDamager(Entity damager) {
    if (damager instanceof LivingEntity living) {
      return living;
    }
    if (damager instanceof Projectile projectile) {
      Object shooter = projectile.getShooter();
      if (shooter instanceof LivingEntity living) {
        return living;
      }
    }
    return null;
  }

  private void applyScaling(LivingEntity entity, MinionSpec spec) {
    MinionScaling scaling = spec.scaling();
    MinionOwnerScalingSpec ownerScaling = spec.ownerScaling();
    MinionScalingLimits limits = spec.scalingLimits();
    if ((scaling == null || !scaling.isEnabled()) && (ownerScaling == null || !ownerScaling.isEnabled())) {
      return;
    }
    LivingEntity owner = resolveOwner(spec.ownerId());
    if (owner == null) {
      return;
    }
    int level = owner instanceof Player player ? player.getLevel() : 0;
    double ownerMaxHealth = 0.0;
    AttributeInstance ownerHealth = owner.getAttribute(Attribute.MAX_HEALTH);
    if (ownerHealth != null) {
      ownerMaxHealth = ownerHealth.getValue();
    }
    double ownerMaxMana = 0.0;
    if (owner instanceof Player player) {
      ManaProvider mana = engine.manaProvider();
      if (mana != null) {
        ownerMaxMana = Math.max(0.0, mana.getMax(player));
      }
    }

    double bonusHealth = 0.0;
    double bonusDamage = 0.0;
    if (scaling != null) {
      bonusHealth += scaling.healthPerLevel() * level
          + scaling.healthPerMaxHealth() * ownerMaxHealth
          + scaling.healthPerManaMax() * ownerMaxMana;
      bonusDamage += scaling.damagePerLevel() * level
          + scaling.damagePerMaxHealth() * ownerMaxHealth
          + scaling.damagePerManaMax() * ownerMaxMana;
    }
    if (ownerScaling != null && ownerScaling.isEnabled()) {
      PlayerProgression progression = null;
      if (owner instanceof Player player && engine.plugin() instanceof DungeonsRebornPlugin dr) {
        ProgressionService progressionService = dr.progressionService();
        if (progressionService != null) {
          progression = progressionService.getOrCreate(player.getUniqueId());
        }
      }
      int strength = progression == null ? 0 : progression.strength();
      int dexterity = progression == null ? 0 : progression.dexterity();
      int intelligence = progression == null ? 0 : progression.intelligence();
      int vitality = progression == null ? 0 : progression.vitality();
      bonusHealth += ownerScaling.levelMultiplier() * level
          + ownerScaling.strengthMultiplier() * strength
          + ownerScaling.dexterityMultiplier() * dexterity
          + ownerScaling.intelligenceMultiplier() * intelligence
          + ownerScaling.vitalityMultiplier() * vitality
          + ownerScaling.maxHealthMultiplier() * ownerMaxHealth
          + ownerScaling.maxManaMultiplier() * ownerMaxMana;
      bonusDamage += ownerScaling.levelMultiplier() * level
          + ownerScaling.strengthMultiplier() * strength
          + ownerScaling.dexterityMultiplier() * dexterity
          + ownerScaling.intelligenceMultiplier() * intelligence
          + ownerScaling.vitalityMultiplier() * vitality
          + ownerScaling.maxHealthMultiplier() * ownerMaxHealth
          + ownerScaling.maxManaMultiplier() * ownerMaxMana;
    }
    if (limits != null && limits.isEnabled()) {
      bonusHealth = applyScalingCap(bonusHealth, limits.maxBonusHealth(), limits.decayExponent());
      bonusDamage = applyScalingCap(bonusDamage, limits.maxBonusDamage(), limits.decayExponent());
    }

    if (Math.abs(bonusHealth) > 1e-9) {
      AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
      if (maxHealth != null) {
        double next = Math.max(1.0, maxHealth.getBaseValue() + bonusHealth);
        maxHealth.setBaseValue(next);
        entity.setHealth(Math.min(entity.getHealth(), next));
      }
    }
    if (Math.abs(bonusDamage) > 1e-9) {
      AttributeInstance attack = entity.getAttribute(Attribute.ATTACK_DAMAGE);
      if (attack != null) {
        attack.setBaseValue(Math.max(0.0, attack.getBaseValue() + bonusDamage));
      }
    }
  }

  private static double applyScalingCap(double value, double cap, double decayExponent) {
    if (!Double.isFinite(value)) {
      return 0.0;
    }
    if (cap <= 0.0) {
      return value;
    }
    double sign = Math.signum(value);
    double abs = Math.abs(value);
    if (abs <= cap) {
      return value;
    }
    double exponent = decayExponent <= 0.0 ? 1.0 : decayExponent;
    double ratio = abs / cap;
    double decayed = cap * (1.0 - Math.exp(-Math.pow(ratio, exponent)));
    return decayed * sign;
  }

  private void applyOwnerEffects(LivingEntity entity, MinionSpec spec) {
    if (!spec.sharePotionEffects()) {
      return;
    }
    LivingEntity owner = resolveOwner(spec.ownerId());
    if (owner == null) {
      return;
    }
    for (PotionEffect effect : owner.getActivePotionEffects()) {
      if (effect == null) {
        continue;
      }
      PotionEffect cloned = new PotionEffect(effect.getType(), effect.getDuration(), effect.getAmplifier(),
          effect.isAmbient(), effect.hasParticles(), effect.hasIcon());
      entity.addPotionEffect(cloned);
    }
  }

  private void applyStatOverrides(LivingEntity entity, MinionSpec spec) {
    if (spec.statOverrides().isEmpty()) {
      return;
    }
    for (Map.Entry<Attribute, Double> entry : spec.statOverrides().entrySet()) {
      AttributeInstance inst = entity.getAttribute(entry.getKey());
      if (inst == null) {
        continue;
      }
      double value = entry.getValue();
      if (!Double.isFinite(value)) {
        continue;
      }
      inst.setBaseValue(value);
      if (entry.getKey() == Attribute.MAX_HEALTH) {
        entity.setHealth(Math.min(entity.getHealth(), value));
      }
    }
  }

  private void applyCosmetics(LivingEntity entity, MinionSpec spec) {
    if (spec.nameOverride() != null) {
      entity.customName(Component.text(spec.nameOverride()));
      entity.setCustomNameVisible(true);
    }
    if (spec.glowOverride() != null) {
      entity.setGlowing(spec.glowOverride());
    }
  }

  private void applyResistances(LivingEntity entity, MinionSpec spec) {
    if (spec.resistances().isEmpty() && spec.immunities().isEmpty()) {
      return;
    }
    UUID entityId = entity.getUniqueId();
    for (Map.Entry<DamageType, Double> entry : spec.resistances().entrySet()) {
      engine.setResistance(entityId, entry.getKey(), entry.getValue());
    }
    if (!spec.immunities().isEmpty()) {
      EnumSet<DamageType> immune = EnumSet.copyOf(spec.immunities());
      for (DamageType type : immune) {
        engine.setResistance(entityId, type, 0.0);
      }
    }
  }

  private LivingEntity resolveOwner(UUID ownerId) {
    if (ownerId == null) {
      return null;
    }
    Entity entity = Bukkit.getEntity(ownerId);
    if (entity instanceof LivingEntity living && living.isValid() && !living.isDead()) {
      return living;
    }
    return null;
  }

  private void schedulePassives(LivingEntity entity, MinionSpec spec, long expiresTick) {
    if (spec.passives().isEmpty()) {
      return;
    }
    for (MinionPassiveSpec passive : spec.passives()) {
      engine.runLater(passive.periodTicks(), () -> tickPassive(entity.getUniqueId(), passive, expiresTick));
    }
  }

  private void scheduleParticles(LivingEntity entity, MinionSpec spec, long expiresTick) {
    if (spec.particles() == null || spec.particlesPeriodTicks() <= 0L) {
      return;
    }
    engine.runLater(spec.particlesPeriodTicks(), () -> tickParticles(entity.getUniqueId(), spec, expiresTick));
  }

  private void tickParticles(UUID entityId, MinionSpec spec, long expiresTick) {
    MinionInstance inst = byEntity.get(entityId);
    if (inst == null || engine.tickNow() >= expiresTick) {
      return;
    }
    Entity entity = Bukkit.getEntity(entityId);
    if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
      return;
    }
    if (spec.particles() != null) {
      spec.particles().spawn(living.getLocation());
    }
    engine.runLater(spec.particlesPeriodTicks(), () -> tickParticles(entityId, spec, expiresTick));
  }

  private void tickPassive(UUID entityId, MinionPassiveSpec passive, long expiresTick) {
    MinionInstance inst = byEntity.get(entityId);
    if (inst == null || engine.tickNow() >= expiresTick) {
      return;
    }
    Entity entity = Bukkit.getEntity(entityId);
    if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
      return;
    }
    castAbility(living, inst.ownerId(), null, passive.abilityId(), 1.0, 0.0);
    engine.runLater(passive.periodTicks(), () -> tickPassive(entityId, passive, expiresTick));
  }

  private void scheduleSpecialAttacks(LivingEntity entity, MinionSpec spec, long expiresTick) {
    if (spec.specialAttacks().isEmpty()) {
      return;
    }
    for (MinionSpecialAttackSpec attack : spec.specialAttacks()) {
      engine.runLater(attack.cooldownTicks(), () -> tickSpecialAttack(entity.getUniqueId(), attack, expiresTick));
    }
  }

  private void tickSpecialAttack(UUID entityId, MinionSpecialAttackSpec attack, long expiresTick) {
    MinionInstance inst = byEntity.get(entityId);
    if (inst == null || engine.tickNow() >= expiresTick) {
      return;
    }
    Entity entity = Bukkit.getEntity(entityId);
    if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
      return;
    }
    LivingEntity target = null;
    if (living instanceof Mob mob) {
      target = mob.getTarget();
    }
    if (attack.requireTarget() && target == null) {
      engine.runLater(attack.cooldownTicks(), () -> tickSpecialAttack(entityId, attack, expiresTick));
      return;
    }
    if (attack.chance() < 1.0 && rng.nextDouble() > attack.chance()) {
      engine.runLater(attack.cooldownTicks(), () -> tickSpecialAttack(entityId, attack, expiresTick));
      return;
    }
    castAbility(living, inst.ownerId(), target, attack.abilityId(), attack.costMultiplier(), attack.costAdd());
    engine.runLater(attack.cooldownTicks(), () -> tickSpecialAttack(entityId, attack, expiresTick));
  }

  private void castAbility(LivingEntity caster, UUID ownerId, LivingEntity target, String abilityId,
                           double costMultiplier, double costAdd) {
    Location origin = caster.getEyeLocation();
    Vector direction;
    if (target != null) {
      direction = target.getEyeLocation().toVector().subtract(origin.toVector());
      if (direction.lengthSquared() < 1e-9) {
        direction = origin.getDirection();
      } else {
        direction.normalize();
      }
    } else {
      direction = origin.getDirection();
    }
    engine.castWithContext(abilityId, caster, origin, direction, null, ctx -> {
      if (ownerId != null) {
        ctx.state().put(dev.patric.dungeonsreborn.effects.Vars.MOB_OWNER, ownerId);
      }
      if (target != null) {
        ctx.state().put(dev.patric.dungeonsreborn.effects.Vars.MOB_TARGET, target);
      }
      if (Double.isFinite(costMultiplier) && costMultiplier != 1.0) {
        ctx.variables().put("minion_mana_mult", costMultiplier);
      }
      if (Double.isFinite(costAdd) && Math.abs(costAdd) > 1e-9) {
        ctx.variables().put("minion_mana_add", costAdd);
      }
      ctx.state().put(dev.patric.dungeonsreborn.effects.Vars.MOB_ID, MobMarkers.getMobId(caster));
      String minionId = MobMarkers.getMinionId(caster);
      if (minionId != null) {
        ctx.state().put(dev.patric.dungeonsreborn.effects.Vars.MINION_ID, minionId);
      }
    });
  }

  private void sendStatus(UUID ownerId) {
    if (ownerId == null) {
      return;
    }
    Player player = Bukkit.getPlayer(ownerId);
    if (player == null || !player.isOnline()) {
      return;
    }
    int count = minionsFor(ownerId).size();
    player.sendActionBar(Component.text("§aMinions: §f" + count));
  }
}
