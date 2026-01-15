package dev.patric.dungeonsreborn.classes.skills;

import java.util.Set;
import java.util.UUID;

public interface ClassSkillRepository {
  Set<String> load(UUID uuid, String classId);

  void add(UUID uuid, String classId, String nodeId);

  void remove(UUID uuid, String classId, String nodeId);

  void clear(UUID uuid, String classId);
}
