package dev.patric.dungeonsreborn.shops;

import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

public record ShopTokenTierSpec(String id, ItemStack item, NamespacedKey markerKey) {
  public ShopTokenTierSpec {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(markerKey, "markerKey");
    if (item != null) {
      item = item.clone();
    }
  }
}
