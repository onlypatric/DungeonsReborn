package dev.patric.dungeonsreborn.classes.skills;

public record SkillStatSpec(String stat, double amount, SkillScalingSpec scaling) {
  public SkillScalingSpec scalingOrDefault() {
    return scaling == null ? SkillScalingSpec.flat() : scaling;
  }
}
