package dev.patric.dungeonsreborn.quests;

import java.util.List;

public record QuestRewards(int xp, int tokens, int compressed, int pallet, List<QuestRewardItem> items) {
  public static QuestRewards empty() {
    return new QuestRewards(0, 0, 0, 0, List.of());
  }
}
