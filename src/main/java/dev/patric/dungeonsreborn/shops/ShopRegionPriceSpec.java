package dev.patric.dungeonsreborn.shops;

import dev.patric.dungeonsreborn.quests.QuestRegion;

public record ShopRegionPriceSpec(QuestRegion region, double multiplier, double taxRate) {
  public ShopRegionPriceSpec {
    if (region == null) {
      throw new IllegalArgumentException("region is required");
    }
    if (multiplier < 0.0) {
      throw new IllegalArgumentException("multiplier must be >= 0");
    }
    if (taxRate < 0.0) {
      throw new IllegalArgumentException("taxRate must be >= 0");
    }
  }
}
