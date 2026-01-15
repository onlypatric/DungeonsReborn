package dev.patric.dungeonsreborn.classes.skills;

import java.util.Locale;

public enum SkillNodeType {
  STAT,
  ATTRIBUTE,
  POTION,
  CUSTOM;

  public static SkillNodeType parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return CUSTOM;
    }
    String key = raw.trim().toUpperCase(Locale.ROOT);
    try {
      return SkillNodeType.valueOf(key);
    } catch (Exception ex) {
      return CUSTOM;
    }
  }
}
