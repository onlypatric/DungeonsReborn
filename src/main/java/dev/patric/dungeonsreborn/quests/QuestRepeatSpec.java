package dev.patric.dungeonsreborn.quests;

public record QuestRepeatSpec(int dailyLimit, int weeklyLimit) {
  public static QuestRepeatSpec none() {
    return new QuestRepeatSpec(0, 0);
  }
}
