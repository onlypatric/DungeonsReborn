package dev.patric.dungeonsreborn.effects.combat;

import java.util.Locale;

public enum CombatCooldownScope {
  PER_PLAYER,
  PER_TARGET,
  PER_ABILITY;

  public static CombatCooldownScope parse(String raw, CombatCooldownScope def) {
    if (raw == null || raw.isBlank()) {
      return def;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    return switch (normalized) {
      case "PLAYER" -> PER_PLAYER;
      case "TARGET" -> PER_TARGET;
      case "ABILITY" -> PER_ABILITY;
      default -> CombatCooldownScope.valueOf(normalized);
    };
  }
}

