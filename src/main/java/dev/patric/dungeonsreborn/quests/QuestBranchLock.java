package dev.patric.dungeonsreborn.quests;

public enum QuestBranchLock {
  COMPLETED,
  ACTIVE,
  ANY;

  public static QuestBranchLock parse(String raw) {
    if (raw == null) {
      return COMPLETED;
    }
    String normalized = raw.trim().toLowerCase();
    return switch (normalized) {
      case "active" -> ACTIVE;
      case "any", "all", "both" -> ANY;
      case "completed", "complete", "done" -> COMPLETED;
      default -> COMPLETED;
    };
  }
}
