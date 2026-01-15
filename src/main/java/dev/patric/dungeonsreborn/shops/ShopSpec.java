package dev.patric.dungeonsreborn.shops;

import java.util.List;
import java.util.Set;

public record ShopSpec(
    String id,
    String title,
    boolean enabled,
    ShopIngredientSpec icon,
    String permission,
    long cooldownTicks,
    Set<String> worlds,
    ShopStockSpec stock,
    List<ShopTradeSpec> trades
) {
  public ShopSpec {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("shop id is required");
    }
    if (cooldownTicks < 0) {
      throw new IllegalArgumentException("cooldownTicks must be >= 0");
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
  }
}
