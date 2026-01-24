package dev.patric.dungeonsreborn.classes.skills;

import java.util.Map;

public record ClassSkillPreset(String id, String name, Map<String, Integer> nodes, long updatedAt) {
  public ClassSkillPreset {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("preset id is required");
    }
    if (name == null || name.isBlank()) {
      name = id;
    }
    nodes = nodes == null ? Map.of() : Map.copyOf(nodes);
    updatedAt = Math.max(0L, updatedAt);
  }
}
