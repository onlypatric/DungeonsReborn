package dev.patric.dungeonsreborn.quests;

import java.util.List;

public record QuestGiverSpec(
    String id,
    String title,
    List<String> dialogue,
    QuestGiverMode mode,
    int poolSize,
    List<String> quests,
    List<String> pool
) {
  public List<String> questIds() {
    return mode == QuestGiverMode.RANDOM_POOL ? pool : quests;
  }
}
