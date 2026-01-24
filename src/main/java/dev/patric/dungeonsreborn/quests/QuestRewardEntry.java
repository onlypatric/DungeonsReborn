package dev.patric.dungeonsreborn.quests;

import java.util.Objects;

public record QuestRewardEntry(QuestRewardEntryType type,
                               String id,
                               double amount,
                               QuestRewardItem item,
                               QuestRewardTitle title,
                               QuestRewardBuff buff,
                               int weight,
                               double chance) {
  public QuestRewardEntry {
    Objects.requireNonNull(type, "type");
    weight = Math.max(1, weight);
    if (!Double.isFinite(chance) || chance < 0.0) {
      chance = 0.0;
    } else if (chance > 1.0) {
      chance = 1.0;
    }
    if (!Double.isFinite(amount) || amount < 0.0) {
      amount = 0.0;
    }
  }
}
