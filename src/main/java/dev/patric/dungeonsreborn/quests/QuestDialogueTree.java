package dev.patric.dungeonsreborn.quests;

import java.util.LinkedHashMap;
import java.util.Map;

public record QuestDialogueTree(
    String start,
    Map<String, QuestDialogueNode> nodes
) {
  public QuestDialogueTree {
    nodes = nodes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(nodes));
  }
}
