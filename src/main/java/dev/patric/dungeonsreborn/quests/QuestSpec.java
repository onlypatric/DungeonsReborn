package dev.patric.dungeonsreborn.quests;

import java.util.List;

public record QuestSpec(
    String id,
    String name,
    boolean enabled,
    List<String> description,
    QuestRequirements requirements,
    QuestRewards rewards,
    List<QuestObjectiveSpec> objectives,
    long cooldownSeconds,
    QuestRotation rotation,
    QuestRepeatSpec repeat,
    long progressThrottleSeconds,
    QuestPartyShareSpec partyShare,
    boolean partyLocked,
    String rotationPool,
    String branchId,
    QuestBranchLock branchLock,
    QuestFailSpec fail,
    QuestVisibilitySpec visibility,
    List<String> categories,
    String tier,
    List<String> tags
) {
}
