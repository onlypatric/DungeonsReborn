package dev.patric.dungeonsreborn.quests;

import java.util.Locale;

public enum QuestRequiredStatus {
  AVAILABLE,
  ACTIVE,
  TURNIN,
  COMPLETED,
  FAILED,
  COOLDOWN,
  LOCKED;

  public static QuestRequiredStatus parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String key = raw.trim().toUpperCase(Locale.ROOT)
        .replace('-', '_')
        .replace(' ', '_');
    return switch (key) {
      case "AVAILABLE" -> AVAILABLE;
      case "ACTIVE", "IN_PROGRESS" -> ACTIVE;
      case "TURN_IN", "TURNIN", "READY", "READY_TO_TURN_IN", "READY_TO_TURNIN" -> TURNIN;
      case "COMPLETED", "DONE" -> COMPLETED;
      case "FAILED" -> FAILED;
      case "COOLDOWN" -> COOLDOWN;
      case "LOCKED" -> LOCKED;
      default -> null;
    };
  }
}
