package dev.patric.dungeonsreborn.shops;

import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record ShopIngredientSpec(
    ShopIngredientType type,
    String itemId,
    Material material,
    ItemStack item,
    int amount
) {
  public ShopIngredientSpec {
    if (type == null) {
      throw new IllegalArgumentException("ingredient type is required");
    }
    if (amount <= 0) {
      throw new IllegalArgumentException("ingredient amount must be > 0");
    }
    if (item != null) {
      item = item.clone();
    }
    switch (type) {
      case TOKEN -> {
      }
      case ITEM_ID -> {
        if (itemId == null || itemId.isBlank()) {
          throw new IllegalArgumentException("itemId is required");
        }
      }
      case MATERIAL -> {
        if (material == null) {
          throw new IllegalArgumentException("material is required");
        }
      }
      case ITEMSTACK -> {
        if (item == null) {
          throw new IllegalArgumentException("item is required");
        }
      }
    }
  }

  public ItemStack resolve(Function<String, ItemStack> itemResolver, ShopTokenSpec tokenSpec) {
    return switch (type) {
      case TOKEN -> tokenSpec == null || tokenSpec.item() == null ? null : withAmount(tokenSpec.item(), amount);
      case ITEM_ID -> itemResolver == null ? null : withAmount(itemResolver.apply(itemId), amount);
      case MATERIAL -> new ItemStack(material, amount);
      case ITEMSTACK -> withAmount(item, amount);
    };
  }

  private static ItemStack withAmount(ItemStack base, int amount) {
    if (base == null) {
      return null;
    }
    ItemStack out = base.clone();
    out.setAmount(amount);
    return out;
  }
}
