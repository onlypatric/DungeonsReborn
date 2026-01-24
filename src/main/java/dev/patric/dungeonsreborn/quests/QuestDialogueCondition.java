package dev.patric.dungeonsreborn.quests;

public record QuestDialogueCondition(
    String questId,
    QuestRequiredStatus required
) {
}
