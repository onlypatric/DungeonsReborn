package dev.patric.dungeonsreborn.quests;

import java.util.Locale;

public enum QuestPartyRole {
  ANY,
  LEADER,
  MEMBER,
  SOLO;

  public static QuestPartyRole parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return ANY;
    }
    String key = raw.trim().toUpperCase(Locale.ROOT);
    return switch (key) {
      case "LEADER", "LEADER_ONLY" -> LEADER;
      case "MEMBER", "MEMBER_ONLY" -> MEMBER;
      case "SOLO", "ALONE" -> SOLO;
      default -> ANY;
    };
  }
}
