package dev.patric.dungeonsreborn.quests;

import java.util.List;

public record QuestRequirements(int level, List<String> quests) {
  public static QuestRequirements empty() {
    return new QuestRequirements(0, List.of());
  }
}
