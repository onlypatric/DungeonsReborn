package dev.patric.dungeonsreborn.quests;

public record QuestFailSpec(boolean failOnDeath,
                            boolean failOnLeaveRegion,
                            long timeoutSeconds,
                            QuestRegion region) {
  public static QuestFailSpec none() {
    return new QuestFailSpec(false, false, 0L, null);
  }
}
