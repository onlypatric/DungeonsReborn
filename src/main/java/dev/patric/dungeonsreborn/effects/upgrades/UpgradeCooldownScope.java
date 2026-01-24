package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.Locale;

public enum UpgradeCooldownScope {
  PER_PLAYER,
  PER_UPGRADE,
  PER_ITEM;

  public static UpgradeCooldownScope parse(String raw, String path) {
    if (raw == null || raw.isBlank()) {
      return PER_PLAYER;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    return switch (normalized) {
      case "PER_PLAYER", "PLAYER" -> PER_PLAYER;
      case "PER_UPGRADE", "UPGRADE" -> PER_UPGRADE;
      case "PER_ITEM", "ITEM" -> PER_ITEM;
      default -> throw new IllegalArgumentException(path + ": invalid cooldownScope=" + raw
          + " (use PER_PLAYER, PER_UPGRADE, PER_ITEM)");
    };
  }
}
