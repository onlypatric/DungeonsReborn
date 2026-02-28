package dev.patric.dungeonsreborn.effects.combat;

import java.util.Locale;

public enum ProjectileFamily {
  VANILLA,
  CUSTOM;

  public static ProjectileFamily parse(String raw, ProjectileFamily def) {
    if (raw == null || raw.isBlank()) {
      return def;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    return ProjectileFamily.valueOf(normalized);
  }
}
