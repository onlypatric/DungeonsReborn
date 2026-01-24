package dev.patric.dungeonsreborn.classes.skills;

public record SkillAttributeSpec(String attribute, double amount, String operation, SkillScalingSpec scaling) {
  public SkillScalingSpec scalingOrDefault() {
    return scaling == null ? SkillScalingSpec.flat() : scaling;
  }
}
