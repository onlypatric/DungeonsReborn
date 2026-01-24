package dev.patric.dungeonsreborn.classes.skills;

import java.util.List;

import dev.patric.dungeonsreborn.classes.ClassBonusSpec;

public record SkillSynergySpec(String id, List<String> requires, ClassBonusSpec bonuses) {
  public List<String> requiresOrEmpty() {
    return requires == null ? List.of() : requires;
  }
}
