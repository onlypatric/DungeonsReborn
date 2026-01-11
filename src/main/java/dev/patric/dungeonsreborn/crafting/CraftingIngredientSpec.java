package dev.patric.dungeonsreborn.crafting;

import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.items.ItemMarkers;

public final class CraftingIngredientSpec {
  private final CraftingMatchType type;
  private final String itemId;
  private final NamespacedKey tag;
  private final Material material;
  private final CraftingItemCategory category;
  private final int amount;

  public CraftingIngredientSpec(CraftingMatchType type, String itemId, NamespacedKey tag,
                                Material material, CraftingItemCategory category, int amount) {
    this.type = Objects.requireNonNull(type, "type");
    this.itemId = itemId;
    this.tag = tag;
    this.material = material;
    this.category = category == null ? CraftingItemCategory.ANY : category;
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    this.amount = amount;
  }

  public CraftingMatchType type() {
    return type;
  }

  public String itemId() {
    return itemId;
  }

  public NamespacedKey tag() {
    return tag;
  }

  public Material material() {
    return material;
  }

  public CraftingItemCategory category() {
    return category;
  }

  public int amount() {
    return amount;
  }

  public boolean matches(ItemStack stack) {
    if (stack == null || stack.getType().isAir()) {
      return false;
    }
    return switch (type) {
      case ANY -> true;
      case ITEM_ID -> itemId != null && itemId.equals(ItemMarkers.getItemId(stack));
      case TAG -> tag != null && ItemMarkers.has(stack, tag);
      case MATERIAL -> material != null && stack.getType() == material;
      case CATEGORY -> category.matches(stack.getType());
    };
  }
}
