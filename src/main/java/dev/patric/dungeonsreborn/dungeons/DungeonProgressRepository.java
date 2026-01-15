package dev.patric.dungeonsreborn.dungeons;

import java.util.UUID;

public interface DungeonProgressRepository {
  int maxCompleted(UUID uuid, String dungeonId);

  void recordCompletion(UUID uuid, String dungeonId, int level);
}
