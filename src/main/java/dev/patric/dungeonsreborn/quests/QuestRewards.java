package dev.patric.dungeonsreborn.quests;

import java.util.List;
import java.util.Map;

public record QuestRewards(int xp, int tokens, int compressed, int pallet, double mana,
                           Map<String, Double> resources, List<QuestRewardItem> items,
                           List<QuestRewardEntry> entries, List<QuestRewardPool> pools,
                           QuestRewardScaling scaling) {
  public static QuestRewards empty() {
    return new QuestRewards(0, 0, 0, 0, 0.0, Map.of(), List.of(), List.of(), List.of(), QuestRewardScaling.none());
  }
}
