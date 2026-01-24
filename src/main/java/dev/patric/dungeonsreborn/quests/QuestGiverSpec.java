package dev.patric.dungeonsreborn.quests;

import java.util.List;

public record QuestGiverSpec(
    String id,
    String title,
    List<String> dialogue,
    List<String> acceptDialogue,
    List<String> activeDialogue,
    List<String> turnInDialogue,
    List<String> completedDialogue,
    QuestDialogueTree dialogueTree,
    QuestGiverMode mode,
    int poolSize,
    List<String> quests,
    List<String> pool,
    QuestGiverFilter filter
) {
  public List<String> questIds() {
    return mode == QuestGiverMode.RANDOM_POOL ? pool : quests;
  }
}
