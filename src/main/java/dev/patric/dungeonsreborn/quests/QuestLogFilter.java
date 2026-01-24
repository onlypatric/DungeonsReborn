package dev.patric.dungeonsreborn.quests;

import java.util.Locale;

public record QuestLogFilter(
    String query,
    String category,
    String tier,
    QuestRewardTag reward
) {
  public static QuestLogFilter none() {
    return new QuestLogFilter("", null, null, QuestRewardTag.ANY);
  }

  public static QuestLogFilter parse(String rawQuery, QuestRewardTag reward) {
    if (rawQuery == null) {
      return new QuestLogFilter("", null, null, reward == null ? QuestRewardTag.ANY : reward);
    }
    String query = rawQuery.trim();
    String category = null;
    String tier = null;
    String[] tokens = query.split("\\s+");
    StringBuilder remainder = new StringBuilder();
    for (String token : tokens) {
      String lower = token.toLowerCase(Locale.ROOT);
      if (lower.startsWith("cat:") || lower.startsWith("category:")) {
        category = token.substring(token.indexOf(':') + 1).trim();
        continue;
      }
      if (lower.startsWith("tier:")) {
        tier = token.substring(token.indexOf(':') + 1).trim();
        continue;
      }
      if (!token.isBlank()) {
        if (remainder.length() > 0) {
          remainder.append(' ');
        }
        remainder.append(token);
      }
    }
    return new QuestLogFilter(remainder.toString(), category, tier, reward == null ? QuestRewardTag.ANY : reward);
  }
}
