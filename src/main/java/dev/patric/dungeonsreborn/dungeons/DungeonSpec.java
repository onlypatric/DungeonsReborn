package dev.patric.dungeonsreborn.dungeons;

import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;

public record DungeonSpec(
    String id,
    Component name,
    String world,
    DungeonRegion region,
    DungeonEntry entry,
    DungeonQueueConfig queue,
    Map<Integer, DungeonLevel> levels
) {
  public record DungeonQueueConfig(int maxSizePerLevel, int entryTimeoutSeconds) {
  }

  public record DungeonPoint(int x, int y, int z) {
  }

  public record DungeonRegion(DungeonPoint min, DungeonPoint max) {
  }

  public record DungeonEntry(DungeonPoint spawn, DungeonPoint exit) {
  }

  public record IntRange(int min, int max) {
  }

  public record DungeonExtraLoot(String itemId, int chancePercent) {
  }

  public record DungeonCheckpoint(boolean enabled, boolean onWave, DungeonPoint location) {
  }

  public record DungeonModifiers(double healthMultiplier, double damageMultiplier, List<String> affixes) {
    public DungeonModifiers {
      affixes = affixes == null ? List.of() : List.copyOf(affixes);
    }
  }

  public record DungeonReward(IntRange tokens, int skillPoints, List<DungeonExtraLoot> extraLoot) {
  }

  public record DungeonSpawnPoint(String id, DungeonPoint pos) {
  }

  public record DungeonWave(List<String> mobs) {
  }

  public record DungeonLevel(
      int level,
      int queueTokens,
      int waitSeconds,
      int timeLimitSeconds,
      DungeonCheckpoint checkpoint,
      DungeonModifiers modifiers,
      List<DungeonSpawnPoint> spawnPoints,
      Map<String, String> spawnOverrides,
      List<DungeonWave> waves,
      DungeonWave bossWave,
      String bossMob,
      DungeonReward rewards
  ) {
  }
}
