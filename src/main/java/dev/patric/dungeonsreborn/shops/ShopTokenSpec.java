package dev.patric.dungeonsreborn.shops;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

public record ShopTokenSpec(ItemStack item, NamespacedKey markerKey) {
  public static final NamespacedKey DEFAULT_MARKER = new NamespacedKey("dungeonsreborn", "shop_token");

  public ShopTokenSpec {
    if (item != null) {
      item = item.clone();
    }
    if (markerKey == null) {
      markerKey = DEFAULT_MARKER;
    }
  }
}
