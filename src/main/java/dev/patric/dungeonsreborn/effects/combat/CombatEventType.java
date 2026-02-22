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
  ON_PROJECTILE_HIT_ENTITY,
  ON_PROJECTILE_HIT_BLOCK,
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
}

