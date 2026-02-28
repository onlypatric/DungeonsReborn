package dev.patric.dungeonsreborn.effects.combat;

import java.util.Locale;

import dev.patric.dungeonsreborn.effects.integration.EventTrigger;

public enum CombatEventType {
  ON_ATTACK_ATTEMPT,
  ON_ATTACK_HIT,
  ON_ATTACK_CRIT,
  ON_ATTACK_KILL,
  ON_HIT_TAKEN,
  ON_BLOCK,
  ON_PARRY,
  ON_DODGE,
  ON_PROJECTILE_LAUNCH_PRE,
  ON_PROJECTILE_LAUNCH,
  ON_PROJECTILE_TRAVEL_STEP,
  ON_PROJECTILE_COLLIDE_ENTITY_PRE,
  ON_PROJECTILE_COLLIDE_BLOCK_PRE,
  ON_PROJECTILE_HIT_ENTITY,
  ON_PROJECTILE_HIT_BLOCK,
  ON_PROJECTILE_PIERCE,
  ON_PROJECTILE_BOUNCE,
  ON_PROJECTILE_STUCK,
  ON_PROJECTILE_EXPIRE,
  ON_PROJECTILE_DEFLECT,
  ON_PROJECTILE_BLOCKED_SHIELD,
  ON_PROJECTILE_CANCELLED,
  ON_DOT_APPLY,
  ON_DOT_TICK,
  ON_DOT_EXPIRE,
  ON_CC_APPLY,
  ON_CC_EXPIRE,
  ON_EXECUTE_THRESHOLD,
  ON_SPRINT;

  public static CombatEventType parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("event: missing");
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    return switch (normalized) {
      case "ON_HIT" -> ON_ATTACK_HIT;
      case "ON_KILL" -> ON_ATTACK_KILL;
      case "ON_DODGE" -> ON_DODGE;
      case "ON_SPRINT" -> ON_SPRINT;
      default -> CombatEventType.valueOf(normalized);
    };
  }

  public static CombatEventType fromLegacy(EventTrigger trigger) {
    if (trigger == null) {
      return null;
    }
    return switch (trigger) {
      case ON_HIT -> ON_ATTACK_HIT;
      case ON_KILL -> ON_ATTACK_KILL;
      case ON_DODGE -> ON_DODGE;
      case ON_SPRINT -> ON_SPRINT;
    };
  }

  public boolean isPreEvent() {
    return switch (this) {
      case ON_PROJECTILE_LAUNCH_PRE, ON_PROJECTILE_COLLIDE_ENTITY_PRE, ON_PROJECTILE_COLLIDE_BLOCK_PRE -> true;
      default -> false;
    };
  }

  public boolean isProjectileEvent() {
    return name().startsWith("ON_PROJECTILE_");
  }
}
