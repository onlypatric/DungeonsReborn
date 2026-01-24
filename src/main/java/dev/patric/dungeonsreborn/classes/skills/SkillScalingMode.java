package dev.patric.dungeonsreborn.classes.skills;

import java.util.Locale;

public enum SkillScalingMode {
  FLAT,
  PER_RANK,
  PERCENT,
  CURVE;

  public static SkillScalingMode parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return FLAT;
    }
    return switch (raw.trim().toUpperCase(Locale.ROOT)) {
      case "PER_RANK", "PER_LEVEL", "PER_TIER", "LINEAR" -> PER_RANK;
      case "PERCENT", "PERCENT_PER_RANK", "PCT" -> PERCENT;
      case "CURVE", "CURVED" -> CURVE;
      default -> FLAT;
    };
  }
}
