package dev.patric.dungeonsreborn.effects.combat;

import java.util.Locale;

public enum CombatEventOriginBind {
  IMPACT,
  ATTACKER,
  VICTIM,
  EVENT_PRIMARY,
  LEGACY_ORIGIN;

  public static CombatEventOriginBind parse(String raw, CombatEventOriginBind def) {
    if (raw == null || raw.isBlank()) {
      return def;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    return switch (normalized) {
      case "ORIGIN", "LEGACY", "LEGACYORIGIN" -> LEGACY_ORIGIN;
      default -> CombatEventOriginBind.valueOf(normalized);
    };
  }
}
