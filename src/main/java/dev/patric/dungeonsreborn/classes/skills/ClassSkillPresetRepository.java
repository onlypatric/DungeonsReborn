package dev.patric.dungeonsreborn.classes.skills;

import java.util.List;
import java.util.UUID;

public interface ClassSkillPresetRepository {
  List<ClassSkillPreset> list(UUID uuid, String classId);

  ClassSkillPreset load(UUID uuid, String classId, String presetId);

  void save(UUID uuid, String classId, ClassSkillPreset preset);

  void delete(UUID uuid, String classId, String presetId);
}
