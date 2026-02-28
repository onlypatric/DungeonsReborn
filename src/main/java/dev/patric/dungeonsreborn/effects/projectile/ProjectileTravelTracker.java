package dev.patric.dungeonsreborn.effects.projectile;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.combat.CombatEventContext;
import dev.patric.dungeonsreborn.effects.combat.CombatEventSource;
import dev.patric.dungeonsreborn.effects.combat.CombatEventType;
import dev.patric.dungeonsreborn.effects.combat.ProjectileFamily;
import dev.patric.dungeonsreborn.effects.damage.DamageCause;

public final class ProjectileTravelTracker implements Runnable {
  private final EffectsEngine engine;
  private final ProjectileTelemetryStore telemetryStore;

  private final Map<UUID, Long> lastTravelDispatchTick = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> inGroundTicks = new ConcurrentHashMap<>();

  private volatile boolean enabled = true;
  private volatile boolean travelStepDefaultEnabled;
  private volatile int travelStepDefaultIntervalTicks = 3;

  public ProjectileTravelTracker(EffectsEngine engine, ProjectileTelemetryStore telemetryStore) {
    this.engine = Objects.requireNonNull(engine, "engine");
    this.telemetryStore = Objects.requireNonNull(telemetryStore, "telemetryStore");
  }

  public void configure(boolean enabled, boolean travelStepDefaultEnabled, int travelStepDefaultIntervalTicks) {
    this.enabled = enabled;
    this.travelStepDefaultEnabled = travelStepDefaultEnabled;
    this.travelStepDefaultIntervalTicks = Math.max(1, travelStepDefaultIntervalTicks);
  }

  @Override
  public void run() {
    if (!enabled) {
      return;
    }
    long tickNow = engine.tickNow();
    int cleaned = telemetryStore.cleanup(tickNow);
    engine.combatDispatcher().metrics().addStaleProjectilesCleaned(cleaned);
    engine.combatDispatcher().metrics().setActiveProjectiles(telemetryStore.size());

    if (!travelStepDefaultEnabled || !engine.combatDispatcher().hasBindings(CombatEventType.ON_PROJECTILE_TRAVEL_STEP)) {
      return;
    }

    for (ProjectileTelemetry previous : telemetryStore.snapshot()) {
      UUID projectileId = previous.projectileId();
      Entity entity = Bukkit.getEntity(projectileId);
      if (!(entity instanceof Projectile projectile) || !entity.isValid() || entity.isDead()) {
        dispatchExpire(previous, tickNow);
        cleanupEntity(projectileId);
        continue;
      }

      long last = lastTravelDispatchTick.getOrDefault(projectileId, 0L);
      if (tickNow - last < travelStepDefaultIntervalTicks) {
        continue;
      }

      Location location = projectile.getLocation();
      Vector velocity = projectile.getVelocity();
      LivingEntity shooter = projectile.getShooter() instanceof LivingEntity living ? living : previous.shooter();
      int inGround = resolveInGroundTicks(projectile);

      ProjectileTelemetry telemetry = ProjectileTelemetry.builder(projectileId, ProjectileFamily.VANILLA, projectile.getType().name())
          .kind(previous.projectileKind())
          .shooter(shooter)
          .impact(location, velocity)
          .movement(previous.distance() + velocity.length(), velocity.length())
          .drawForce(previous.drawForce())
          .pierce(projectile instanceof AbstractArrow arrow ? arrow.getPierceLevel() : previous.pierceLevel(),
              projectile instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0)
          .inGroundTicks(inGround)
          .critical(projectile instanceof AbstractArrow arrow && arrow.isCritical())
          .charged(previous.charged())
          .shotFromCrossbow(projectile instanceof AbstractArrow arrow && arrow.isShotFromCrossbow())
          .build();

      telemetryStore.upsert(telemetry, tickNow);
      lastTravelDispatchTick.put(projectileId, tickNow);

      engine.combatDispatcher().dispatch(new CombatEventContext(
          tickNow,
          CombatEventType.ON_PROJECTILE_TRAVEL_STEP,
          telemetry.shooter(),
          null,
          null,
          projectile,
          CombatEventSource.PROJECTILE,
          0.0,
          telemetry.critical(),
          false,
          false,
          null,
          DamageCause.PROJECTILE,
          null,
          null,
          telemetry.projectileId(),
          telemetry.family(),
          telemetry.projectileType(),
          telemetry.projectileKind(),
          telemetry.distance(),
          telemetry.speed(),
          telemetry.drawForce(),
          telemetry.pierceLevel(),
          telemetry.inGroundTicks(),
          telemetry.critical(),
          telemetry.charged(),
          telemetry.piercing(),
          telemetry.shotFromCrossbow(),
          telemetry.shooterIsPlayer(),
          telemetry.hitBlockMaterial() == null ? null : telemetry.hitBlockMaterial().name(),
          null,
          telemetry.hitBlockFace() == null ? null : telemetry.hitBlockFace().name(),
          telemetry.impactLocation(),
          telemetry.impactDirection(),
          telemetry));
    }
  }

  private void dispatchExpire(ProjectileTelemetry telemetry, long tickNow) {
    if (!engine.combatDispatcher().hasBindings(CombatEventType.ON_PROJECTILE_EXPIRE)) {
      return;
    }
    engine.combatDispatcher().dispatch(new CombatEventContext(
        tickNow,
        CombatEventType.ON_PROJECTILE_EXPIRE,
        telemetry.shooter(),
        telemetry.victim(),
        telemetry.victim(),
        null,
        CombatEventSource.PROJECTILE,
        0.0,
        telemetry.critical(),
        false,
        false,
        null,
        DamageCause.PROJECTILE,
        null,
        null,
        telemetry.projectileId(),
        telemetry.family(),
        telemetry.projectileType(),
        telemetry.projectileKind(),
        telemetry.distance(),
        telemetry.speed(),
        telemetry.drawForce(),
        telemetry.pierceLevel(),
        telemetry.inGroundTicks(),
        telemetry.critical(),
        telemetry.charged(),
        telemetry.piercing(),
        telemetry.shotFromCrossbow(),
        telemetry.shooterIsPlayer(),
        telemetry.hitBlockMaterial() == null ? null : telemetry.hitBlockMaterial().name(),
        null,
        telemetry.hitBlockFace() == null ? null : telemetry.hitBlockFace().name(),
        telemetry.impactLocation(),
        telemetry.impactDirection(),
        telemetry));
  }

  private int resolveInGroundTicks(Projectile projectile) {
    if (!(projectile instanceof AbstractArrow arrow) || !arrow.isInBlock()) {
      inGroundTicks.remove(projectile.getUniqueId());
      return 0;
    }
    return inGroundTicks.merge(projectile.getUniqueId(), 1, Integer::sum);
  }

  private void cleanupEntity(UUID projectileId) {
    telemetryStore.remove(projectileId);
    inGroundTicks.remove(projectileId);
    lastTravelDispatchTick.remove(projectileId);
  }
}
