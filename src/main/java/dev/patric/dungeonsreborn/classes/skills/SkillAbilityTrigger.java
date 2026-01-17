package dev.patric.dungeonsreborn.classes.skills;

import java.util.Locale;

public enum SkillAbilityTrigger {
  PASSIVE,
  LEFT_CLICK,
  RIGHT_CLICK;

  public static SkillAbilityTrigger parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return PASSIVE;
    }
    String key = raw.trim().toUpperCase(Locale.ROOT);
    return switch (key) {
      case "LEFT", "LEFT_CLICK" -> LEFT_CLICK;
      case "RIGHT", "RIGHT_CLICK" -> RIGHT_CLICK;
      case "PASSIVE" -> PASSIVE;
      default -> PASSIVE;
    };
  }
}
