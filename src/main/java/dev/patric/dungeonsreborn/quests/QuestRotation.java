package dev.patric.dungeonsreborn.quests;

public enum QuestRotation {
  NONE,
  DAILY,
  WEEKLY,
  MONTHLY,
  SEASONAL;

  public static QuestRotation parse(String raw) {
    if (raw == null) {
      return NONE;
    }
    String normalized = raw.trim().toLowerCase();
    return switch (normalized) {
      case "daily", "day" -> DAILY;
      case "weekly", "week" -> WEEKLY;
      case "monthly", "month" -> MONTHLY;
      case "seasonal", "season", "quarterly", "quarter" -> SEASONAL;
      case "none", "off", "false" -> NONE;
      default -> NONE;
    };
  }
}
