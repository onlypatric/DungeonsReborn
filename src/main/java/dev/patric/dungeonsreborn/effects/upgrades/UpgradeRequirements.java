package dev.patric.dungeonsreborn.effects.upgrades;

public record UpgradeRequirements(
    int minXp,
    int consumeXp,
    int minTotalXp,
    int consumeTotalXp,
    double minProgress,
    double consumeProgress,
    double minMaxMana
) {
  public UpgradeRequirements {
    if (minXp < 0) {
      throw new IllegalArgumentException("minXp must be >= 0");
    }
    if (consumeXp < 0) {
      throw new IllegalArgumentException("consumeXp must be >= 0");
    }
    if (minTotalXp < 0) {
      throw new IllegalArgumentException("minTotalXp must be >= 0");
    }
    if (consumeTotalXp < 0) {
      throw new IllegalArgumentException("consumeTotalXp must be >= 0");
    }
    if (!Double.isFinite(minProgress) || minProgress < 0 || minProgress > 1) {
      throw new IllegalArgumentException("minProgress must be between 0 and 1");
    }
    if (!Double.isFinite(consumeProgress) || consumeProgress < 0 || consumeProgress > 1) {
      throw new IllegalArgumentException("consumeProgress must be between 0 and 1");
    }
    if (!Double.isFinite(minMaxMana) || minMaxMana < 0) {
      throw new IllegalArgumentException("minMaxMana must be >= 0");
    }
  }

  public static UpgradeRequirements none() {
    return new UpgradeRequirements(0, 0, 0, 0, 0.0, 0.0, 0.0);
  }
}
