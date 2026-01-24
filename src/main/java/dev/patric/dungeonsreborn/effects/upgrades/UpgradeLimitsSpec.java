package dev.patric.dungeonsreborn.effects.upgrades;

public record UpgradeLimitsSpec(
    String category,
    boolean exclusive,
    int tier,
    int maxTier,
    int maxPerItem,
    int maxCopies,
    double diminishingFactor,
    double diminishingFloor
) {
  public UpgradeLimitsSpec {
    if (tier < 1) {
      throw new IllegalArgumentException("tier must be >= 1");
    }
    if (maxTier < 0) {
      throw new IllegalArgumentException("maxTier must be >= 0");
    }
    if (maxPerItem < 0) {
      throw new IllegalArgumentException("maxPerItem must be >= 0");
    }
    if (maxCopies < 0) {
      throw new IllegalArgumentException("maxCopies must be >= 0");
    }
    if (!Double.isFinite(diminishingFactor) || diminishingFactor <= 0.0) {
      throw new IllegalArgumentException("diminishingFactor must be > 0");
    }
    if (!Double.isFinite(diminishingFloor) || diminishingFloor < 0.0) {
      throw new IllegalArgumentException("diminishingFloor must be >= 0");
    }
  }

  public static UpgradeLimitsSpec none() {
    return new UpgradeLimitsSpec(null, false, 1, 0, 0, 0, 1.0, 0.0);
  }

  public boolean isEmpty() {
    return (category == null || category.isBlank())
        && !exclusive
        && tier == 1
        && maxTier == 0
        && maxPerItem == 0
        && maxCopies == 0
        && Math.abs(diminishingFactor - 1.0) < 1e-9
        && Math.abs(diminishingFloor) < 1e-9;
  }
}
