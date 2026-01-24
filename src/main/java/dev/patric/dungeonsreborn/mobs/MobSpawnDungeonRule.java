package dev.patric.dungeonsreborn.mobs;

import java.util.Locale;

public enum MobSpawnDungeonRule {
  ANY,
  REQUIRE_ACTIVE,
  REQUIRE_INACTIVE;

  public static MobSpawnDungeonRule parse(String raw, String path) {
    if (raw == null || raw.isBlank()) {
      return ANY;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "any", "all", "either" -> ANY;
      case "active", "require_active", "require-active", "on" -> REQUIRE_ACTIVE;
      case "inactive", "require_inactive", "require-inactive", "off" -> REQUIRE_INACTIVE;
      default -> throw new IllegalArgumentException(path + ": unknown dungeon rule " + raw);
    };
  }
}
