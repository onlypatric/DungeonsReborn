package dev.patric.dungeonsreborn.effects.combat;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import dev.patric.dungeonsreborn.effects.damage.DamageCause;
import dev.patric.dungeonsreborn.effects.damage.DamageType;
import dev.patric.dungeonsreborn.effects.projectile.ProjectileTelemetry;

public record CombatEventContext(
    long tick,
    CombatEventType eventType,
    LivingEntity attacker,
    LivingEntity victim,
    LivingEntity primaryTarget,
    Entity rawDamager,
    CombatEventSource source,
    double damage,
    boolean crit,
    boolean blocked,
    boolean dodged,
    DamageType damageType,
    DamageCause damageCause,
    String dotTag,
    String ccType,
    UUID projectileId,
    ProjectileFamily projectileFamily,
    String projectileType,
    String projectileKind,
    double projectileDistance,
    double projectileSpeed,
    double projectileDrawForce,
    int projectilePierceLevel,
    int projectileInGroundTicks,
    boolean projectileCritical,
    boolean projectileCharged,
    boolean projectilePiercing,
    boolean projectileShotFromCrossbow,
    boolean shooterIsPlayer,
    String hitBlockMaterial,
    String hitBlockTag,
    String hitBlockFace,
    Location impactLocation,
    Vector impactDirection,
    ProjectileTelemetry projectileTelemetry) {

  public CombatEventContext {
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(source, "source");
    if (!Double.isFinite(damage)) {
      damage = 0.0;
    }
    projectileFamily = projectileFamily == null ? ProjectileFamily.CUSTOM : projectileFamily;
    if (!Double.isFinite(projectileDistance)) {
      projectileDistance = 0.0;
    }
    if (!Double.isFinite(projectileSpeed)) {
      projectileSpeed = 0.0;
    }
    if (!Double.isFinite(projectileDrawForce)) {
      projectileDrawForce = 0.0;
    }
    projectilePierceLevel = Math.max(0, projectilePierceLevel);
    projectileInGroundTicks = Math.max(0, projectileInGroundTicks);
    if (impactLocation != null) {
      impactLocation = impactLocation.clone();
    }
    if (impactDirection != null) {
      impactDirection = impactDirection.clone();
      if (impactDirection.lengthSquared() > 1.0e-9) {
        impactDirection.normalize();
      }
    }
  }

  public CombatEventContext(
      long tick,
      CombatEventType eventType,
      LivingEntity attacker,
      LivingEntity victim,
      LivingEntity primaryTarget,
      Entity rawDamager,
      CombatEventSource source,
      double damage,
      boolean crit,
      boolean blocked,
      boolean dodged,
      DamageType damageType,
      DamageCause damageCause,
      String dotTag,
      String ccType) {
    this(
        tick,
        eventType,
        attacker,
        victim,
        primaryTarget,
        rawDamager,
        source,
        damage,
        crit,
        blocked,
        dodged,
        damageType,
        damageCause,
        dotTag,
        ccType,
        null,
        ProjectileFamily.CUSTOM,
        null,
        null,
        0.0,
        0.0,
        0.0,
        0,
        0,
        false,
        false,
        false,
        false,
        false,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public LivingEntity targetFor(CombatEventTargetBind bind) {
    if (bind == null) {
      return primaryTarget;
    }
    return switch (bind) {
      case ATTACKER -> attacker;
      case VICTIM -> victim;
      case PROJECTILE_TARGET, EVENT_PRIMARY -> primaryTarget;
    };
  }

  public LivingEntity defaultCaster() {
    if (attacker != null) {
      return attacker;
    }
    return victim;
  }
}
