package dev.patric.dungeonsreborn.quests;

public enum QuestGiverMode {
  FIXED,
  RANDOM_POOL;

  public static QuestGiverMode parse(String raw) {
    if (raw == null) {
      return FIXED;
    }
    String normalized = raw.trim().toLowerCase();
    return switch (normalized) {
      case "random", "random_pool", "pool" -> RANDOM_POOL;
      case "fixed", "ordered" -> FIXED;
      default -> FIXED;
    };
  }
}
