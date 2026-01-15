package dev.patric.dungeonsreborn.shops;

public record ShopDynamicPriceSpec(
    ShopDynamicPriceMode mode,
    double minMultiplier,
    double maxMultiplier,
    long periodSeconds
) {
  public ShopDynamicPriceSpec {
    if (mode == null) {
      throw new IllegalArgumentException("mode is required");
    }
    if (minMultiplier < 0.0 || maxMultiplier < 0.0) {
      throw new IllegalArgumentException("multiplier must be >= 0");
    }
    if (maxMultiplier < minMultiplier) {
      maxMultiplier = minMultiplier;
    }
    if (periodSeconds < 0) {
      throw new IllegalArgumentException("periodSeconds must be >= 0");
    }
  }
}
