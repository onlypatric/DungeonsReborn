package dev.patric.dungeonsreborn.quests;

public record QuestRewardScaling(double levelFactor,
                                 double partyFactor,
                                 double minMultiplier,
                                 double maxMultiplier,
                                 boolean applyToItems) {
  public static QuestRewardScaling none() {
    return new QuestRewardScaling(0.0, 0.0, 1.0, 1.0, false);
  }
}
