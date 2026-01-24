package dev.patric.dungeonsreborn.classes.skills;

import java.util.UUID;

public interface ClassSkillRepository {
  java.util.Map<String, Integer> load(UUID uuid, String classId);

  default int rank(UUID uuid, String classId, String nodeId) {
    if (nodeId == null) {
      return 0;
    }
    return load(uuid, classId).getOrDefault(nodeId, 0);
  }

  void setRank(UUID uuid, String classId, String nodeId, int rank);

  void remove(UUID uuid, String classId, String nodeId);

  void clear(UUID uuid, String classId);
}
