package dev.patric.dungeonsreborn.effects.combat;

import java.util.Objects;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import dev.patric.dungeonsreborn.effects.damage.DamageCause;
import dev.patric.dungeonsreborn.effects.damage.DamageType;

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
    String ccType) {

  public CombatEventContext {
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(source, "source");
    if (!Double.isFinite(damage)) {
      damage = 0.0;
    }
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

