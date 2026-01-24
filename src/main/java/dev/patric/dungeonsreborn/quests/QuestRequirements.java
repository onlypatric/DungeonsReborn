package dev.patric.dungeonsreborn.quests;

import java.util.List;

import dev.patric.dungeonsreborn.shops.ShopAvailabilitySpec;

public record QuestRequirements(
    int level,
    List<String> quests,
    List<String> permissions,
    List<String> classIds,
    List<String> skillNodes,
    int minCustomLevel,
    long minCustomPoints,
    String factionId,
    int minFactionRank,
    List<QuestStageRequirement> questStages,
    List<String> acceptWorlds,
    List<QuestRegion> acceptRegions,
    List<String> turnInWorlds,
    List<QuestRegion> turnInRegions,
    ShopAvailabilitySpec availability,
    ShopAvailabilitySpec turnInAvailability
) {
  public static QuestRequirements empty() {
    return new QuestRequirements(0, List.of(), List.of(), List.of(), List.of(), 0, 0L, null, 0, List.of(),
        List.of(), List.of(), List.of(), List.of(), null, null);
  }
}
