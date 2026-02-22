package dev.patric.dungeonsreborn.effects.combat;

import java.util.Locale;

public enum CombatEventTargetBind {
  ATTACKER,
  VICTIM,
  PROJECTILE_TARGET,
  EVENT_PRIMARY;

  public static CombatEventTargetBind parse(String raw, CombatEventTargetBind def) {
    if (raw == null || raw.isBlank()) {
      return def;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    return CombatEventTargetBind.valueOf(normalized);
  }
}

