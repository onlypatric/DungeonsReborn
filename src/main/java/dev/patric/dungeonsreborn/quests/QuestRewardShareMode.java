package dev.patric.dungeonsreborn.quests;

import java.util.Locale;

public enum QuestRewardShareMode {
  NONE,
  LEADER_ONLY,
  ROLL,
  SPLIT;

  public static QuestRewardShareMode fromString(String raw, QuestRewardShareMode fallback) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      return QuestRewardShareMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return fallback;
    }
  }
}
