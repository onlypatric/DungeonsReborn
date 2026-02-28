package dev.patric.dungeonsreborn.effects.combat;

import java.util.Locale;

public enum CombatEventPhase {
  PRE,
  POST;

  public static CombatEventPhase parse(String raw, CombatEventPhase def) {
    if (raw == null || raw.isBlank()) {
      return def;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    return CombatEventPhase.valueOf(normalized);
  }
}
