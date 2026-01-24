package dev.patric.dungeonsreborn.quests;

import java.util.List;

public record QuestDialogueNode(
    String id,
    List<String> lines,
    List<QuestDialogueChoice> choices,
    List<QuestDialogueCondition> conditions
) {
  public QuestDialogueNode {
    lines = lines == null ? List.of() : List.copyOf(lines);
    choices = choices == null ? List.of() : List.copyOf(choices);
    conditions = conditions == null ? List.of() : List.copyOf(conditions);
  }
}
