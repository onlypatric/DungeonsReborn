package dev.patric.dungeonsreborn.quests;

public enum QuestRotationPoolScope {
  GLOBAL,
  PLAYER;

  public static QuestRotationPoolScope parse(String raw) {
    if (raw == null) {
      return GLOBAL;
    }
    String normalized = raw.trim().toLowerCase();
    return switch (normalized) {
      case "player", "per_player", "per-player", "personal" -> PLAYER;
      case "global", "server", "shared" -> GLOBAL;
      default -> GLOBAL;
    };
  }
}
