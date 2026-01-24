package dev.patric.dungeonsreborn.shops;

import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.items.ItemMarkers;

public record ShopCurrencySpec(String id, ItemStack item, NamespacedKey markerKey) {
  public ShopCurrencySpec {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("currency id is required");
    }
    if (markerKey == null) {
      throw new IllegalArgumentException("currency markerKey is required");
    }
    if (item != null) {
      item = item.clone();
    }
  }

  public boolean matches(ItemStack stack) {
    if (stack == null) {
      return false;
    }
    if (markerKey != null && ItemMarkers.has(stack, markerKey)) {
      return true;
    }
    if (item == null) {
      return false;
    }
    ItemStack base = item.clone();
    base.setAmount(1);
    ItemStack compare = stack.clone();
    compare.setAmount(1);
    return compare.isSimilar(base);
  }

  public ItemStack resolve(int amount) {
    if (item == null) {
      return null;
    }
    ItemStack out = item.clone();
    out.setAmount(Math.max(1, amount));
    return out;
  }

  public ShopCurrencySpec withItem(ItemStack updated) {
    return new ShopCurrencySpec(id, Objects.requireNonNull(updated, "item"), markerKey);
  }
}
