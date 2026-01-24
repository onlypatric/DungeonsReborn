package dev.patric.dungeonsreborn.quests;

import java.util.List;

public record QuestDialogueChoice(
    String text,
    String next,
    List<QuestDialogueCondition> conditions
) {
  public QuestDialogueChoice {
    conditions = conditions == null ? List.of() : List.copyOf(conditions);
  }
}
