package dev.patric.dungeonsreborn.classes.skills;

import java.util.Locale;

public enum SkillAbilityTrigger {
  PASSIVE,
  LEFT_CLICK,
  RIGHT_CLICK,
  ON_HIT,
  ON_KILL,
  ON_DODGE,
  ON_SPRINT;

  public static SkillAbilityTrigger parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return PASSIVE;
    }
    String key = raw.trim().toUpperCase(Locale.ROOT);
    return switch (key) {
      case "LEFT", "LEFT_CLICK" -> LEFT_CLICK;
      case "RIGHT", "RIGHT_CLICK" -> RIGHT_CLICK;
      case "PASSIVE" -> PASSIVE;
      case "HIT", "ON_HIT", "ONHIT" -> ON_HIT;
      case "KILL", "ON_KILL", "ONKILL" -> ON_KILL;
      case "DODGE", "ON_DODGE", "ONDODGE" -> ON_DODGE;
      case "SPRINT", "ON_SPRINT", "ONSPRINT" -> ON_SPRINT;
      default -> PASSIVE;
    };
  }
}
