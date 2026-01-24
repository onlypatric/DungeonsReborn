package dev.patric.dungeonsreborn.quests;

public record QuestVisibilityCondition(
    String questId,
    QuestRequiredStatus required
) {
}
