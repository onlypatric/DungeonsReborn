package dev.patric.dungeonsreborn.quests;

public record QuestRewardTitle(String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
  public QuestRewardTitle {
    fadeInTicks = Math.max(0, fadeInTicks);
    stayTicks = Math.max(0, stayTicks);
    fadeOutTicks = Math.max(0, fadeOutTicks);
  }
}
