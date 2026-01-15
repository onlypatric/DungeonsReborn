package dev.patric.dungeonsreborn.quests;

import java.util.Map;
import java.util.UUID;

public interface QuestRepository {
  Map<String, QuestPlayerQuest> load(UUID playerId);

  void upsertQuest(UUID playerId, QuestPlayerQuest quest);

  void setProgress(UUID playerId, String questId, int objectiveIndex, int progress);
}
