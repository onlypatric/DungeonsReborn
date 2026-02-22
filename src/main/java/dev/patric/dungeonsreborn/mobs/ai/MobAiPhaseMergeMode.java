package dev.patric.dungeonsreborn.mobs.ai;

import java.util.Locale;

public enum MobAiPhaseMergeMode {
  PATCH,
  REPLACE;

  public static MobAiPhaseMergeMode parse(String raw) {
    String key = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    if (key.isBlank()) {
      return PATCH;
    }
    try {
      return MobAiPhaseMergeMode.valueOf(key);
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid ai merge mode=" + raw);
    }
  }
}
