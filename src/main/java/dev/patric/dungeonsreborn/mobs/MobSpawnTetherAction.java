package dev.patric.dungeonsreborn.mobs;

public enum MobSpawnTetherAction {
  NONE,
  PULL,
  TELEPORT,
  DESPAWN;

  public static MobSpawnTetherAction parse(String raw, String path) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String normalized = raw.trim().toUpperCase();
    try {
      return MobSpawnTetherAction.valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(path + ": invalid tether action " + raw);
    }
  }
}
