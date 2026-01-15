package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.Locale;

public enum UpgradeActivator {
  RIGHT_CLICK,
  LEFT_CLICK,
  SHIFT_RIGHT_CLICK,
  SHIFT_LEFT_CLICK,
  PASSIVE;

  public static UpgradeActivator parse(String raw, String path) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException(path + ": activator is required");
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    return switch (normalized) {
      case "RIGHT_CLICK", "RIGHT", "RIGHTCLICK" -> RIGHT_CLICK;
      case "LEFT_CLICK", "LEFT", "LEFTCLICK" -> LEFT_CLICK;
      case "SHIFT_RIGHT", "SHIFT_RIGHT_CLICK", "SHIFT_RIGHTCLICK", "SNEAK_RIGHT" -> SHIFT_RIGHT_CLICK;
      case "SHIFT_LEFT", "SHIFT_LEFT_CLICK", "SHIFT_LEFTCLICK", "SNEAK_LEFT" -> SHIFT_LEFT_CLICK;
      case "PASSIVE" -> PASSIVE;
      default -> throw new IllegalArgumentException(path + ": invalid activator=" + raw
          + " (use RIGHT_CLICK, LEFT_CLICK, SHIFT_RIGHT_CLICK, SHIFT_LEFT_CLICK, PASSIVE)");
    };
  }
}
