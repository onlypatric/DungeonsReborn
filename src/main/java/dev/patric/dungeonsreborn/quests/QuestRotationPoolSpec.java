package dev.patric.dungeonsreborn.quests;

import java.util.List;

public record QuestRotationPoolSpec(String id,
                                    QuestRotation rotation,
                                    QuestRotationPoolScope scope,
                                    int size,
                                    List<String> questIds) {
  public QuestRotationPoolSpec {
    rotation = rotation == null ? QuestRotation.NONE : rotation;
    scope = scope == null ? QuestRotationPoolScope.GLOBAL : scope;
    size = Math.max(0, size);
    questIds = questIds == null ? List.of() : List.copyOf(questIds);
  }
}
