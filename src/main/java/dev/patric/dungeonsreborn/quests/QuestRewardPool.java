package dev.patric.dungeonsreborn.quests;

import java.util.List;

public record QuestRewardPool(String id, int rolls, boolean unique, List<QuestRewardEntry> entries) {
  public QuestRewardPool {
    rolls = Math.max(1, rolls);
    entries = entries == null ? List.of() : List.copyOf(entries);
  }
}
