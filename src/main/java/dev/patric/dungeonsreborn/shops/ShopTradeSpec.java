package dev.patric.dungeonsreborn.shops;

public record ShopTradeSpec(
    java.util.List<ShopIngredientSpec> buys,
    java.util.List<ShopIngredientSpec> sells,
    int maxUses,
    int minLevel,
    java.util.List<ShopRequirementSpec> requirements,
    java.util.List<ShopRequirementSpec> visibilityRequirements,
    ShopAvailabilitySpec availability,
    boolean experienceReward,
    float priceMultiplier,
    java.util.List<String> previewLore,
    ShopDynamicPriceSpec dynamicPrice,
    ShopPriceModifiers priceModifiers,
    ShopStockSpec stock,
    boolean buyback
) {
  public ShopTradeSpec {
    if (buys == null || buys.isEmpty()) {
      throw new IllegalArgumentException("buys is required");
    }
    if (sells == null || sells.isEmpty()) {
      throw new IllegalArgumentException("sells is required");
    }
    if (maxUses < 0) {
      throw new IllegalArgumentException("maxUses must be >= 0");
    }
    if (minLevel < 0) {
      throw new IllegalArgumentException("minLevel must be >= 0");
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
    if (previewLore == null) {
      previewLore = java.util.List.of();
    } else {
      previewLore = java.util.List.copyOf(previewLore);
    }
    if (priceModifiers == null) {
      priceModifiers = ShopPriceModifiers.empty();
    }
    if (stock != null && !stock.enabled()) {
      stock = null;
    }
    buys = java.util.List.copyOf(buys);
    sells = java.util.List.copyOf(sells);
  }

  public ShopIngredientSpec buyA() {
    return buys.isEmpty() ? null : buys.get(0);
  }

  public ShopIngredientSpec buyB() {
    return buys.size() > 1 ? buys.get(1) : null;
  }

  public ShopIngredientSpec sell() {
    return sells.isEmpty() ? null : sells.get(0);
  }
}
