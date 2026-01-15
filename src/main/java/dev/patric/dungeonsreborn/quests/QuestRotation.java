package dev.patric.dungeonsreborn.quests;

public enum QuestRotation {
  NONE,
  DAILY,
  WEEKLY;

  public static QuestRotation parse(String raw) {
    if (raw == null) {
      return NONE;
    }
    String normalized = raw.trim().toLowerCase();
    return switch (normalized) {
      case "daily", "day" -> DAILY;
      case "weekly", "week" -> WEEKLY;
      case "none", "off", "false" -> NONE;
      default -> NONE;
    };
  }
}
