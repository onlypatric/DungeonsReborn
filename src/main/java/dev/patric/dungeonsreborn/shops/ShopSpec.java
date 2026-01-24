package dev.patric.dungeonsreborn.shops;

import java.util.List;
import java.util.Set;

public record ShopSpec(
    String id,
    String title,
    boolean enabled,
    ShopIngredientSpec icon,
    String permission,
    java.util.List<ShopRequirementSpec> requirements,
    java.util.List<ShopRequirementSpec> visibilityRequirements,
    ShopAvailabilitySpec availability,
    long cooldownTicks,
    Set<String> worlds,
    ShopStockSpec stock,
    double taxRate,
    java.util.Map<String, Double> worldMultipliers,
    java.util.List<ShopRegionPriceSpec> regionPrices,
    ShopPriceModifiers priceModifiers,
    List<ShopTradeSpec> trades
) {
  public ShopSpec {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("shop id is required");
    }
    if (cooldownTicks < 0) {
      throw new IllegalArgumentException("cooldownTicks must be >= 0");
    }
    if (requirements == null) {
      requirements = java.util.List.of();
    } else {
      requirements = java.util.List.copyOf(requirements);
    }
    if (visibilityRequirements == null) {
      visibilityRequirements = java.util.List.of();
    } else {
      visibilityRequirements = java.util.List.copyOf(visibilityRequirements);
    }
    if (worlds == null) {
      worlds = Set.of();
    }
    if (trades == null) {
      trades = List.of();
    }
    if (stock != null && !stock.enabled()) {
      stock = null;
    }
    if (taxRate < 0.0) {
      taxRate = 0.0;
    }
    if (worldMultipliers == null) {
      worldMultipliers = java.util.Map.of();
    } else {
      worldMultipliers = java.util.Map.copyOf(worldMultipliers);
    }
    if (regionPrices == null) {
      regionPrices = java.util.List.of();
    } else {
      regionPrices = java.util.List.copyOf(regionPrices);
    }
    if (priceModifiers == null) {
      priceModifiers = ShopPriceModifiers.empty();
    }
  }
}
