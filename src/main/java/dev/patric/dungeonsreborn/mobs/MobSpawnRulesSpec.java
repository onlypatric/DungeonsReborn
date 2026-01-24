package dev.patric.dungeonsreborn.mobs;

import java.util.List;
import java.util.Set;

import org.bukkit.NamespacedKey;

public record MobSpawnRulesSpec(
    Set<NamespacedKey> allowedBiomes,
    Set<NamespacedKey> excludedBiomes,
    List<MobSpawnTimeWindow> timeWindows,
    Integer minY,
    Integer maxY,
    List<MobSpawnRegionSpec> regions,
    int minPlayerLevel,
    int maxPlayerLevel,
    int minPartySize,
    int maxPartySize,
    int minPlayers,
    int maxPlayers,
    MobSpawnDungeonRule dungeonRule) {

  public boolean isEmpty() {
    return (allowedBiomes == null || allowedBiomes.isEmpty())
        && (excludedBiomes == null || excludedBiomes.isEmpty())
        && (timeWindows == null || timeWindows.isEmpty())
        && minY == null
        && maxY == null
        && (regions == null || regions.isEmpty())
        && minPlayerLevel <= 0
        && maxPlayerLevel <= 0
        && minPartySize <= 0
        && maxPartySize <= 0
        && minPlayers <= 0
        && maxPlayers <= 0
        && (dungeonRule == null || dungeonRule == MobSpawnDungeonRule.ANY);
  }
}
