package dev.patric.dungeonsreborn.shops;

public record ShopTradeSpec(
    ShopIngredientSpec buyA,
    ShopIngredientSpec buyB,
    ShopIngredientSpec sell,
    int maxUses,
    boolean experienceReward,
    float priceMultiplier,
    java.util.List<String> previewLore,
    ShopDynamicPriceSpec dynamicPrice
) {
  public ShopTradeSpec {
    if (buyA == null) {
      throw new IllegalArgumentException("buyA is required");
    }
    if (sell == null) {
      throw new IllegalArgumentException("sell is required");
    }
    if (maxUses < 0) {
      throw new IllegalArgumentException("maxUses must be >= 0");
    }
    if (previewLore == null) {
      previewLore = java.util.List.of();
    } else {
      previewLore = java.util.List.copyOf(previewLore);
    }
  }
}
