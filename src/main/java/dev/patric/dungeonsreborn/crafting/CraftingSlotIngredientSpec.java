package dev.patric.dungeonsreborn.crafting;

import java.util.Objects;

public final class CraftingSlotIngredientSpec {
  private final int slot;
  private final CraftingIngredientSpec ingredient;

  public CraftingSlotIngredientSpec(int slot, CraftingIngredientSpec ingredient) {
    if (slot < 0) {
      throw new IllegalArgumentException("slot must be >= 0");
    }
    this.slot = slot;
    this.ingredient = Objects.requireNonNull(ingredient, "ingredient");
  }

  public int slot() {
    return slot;
  }

  public CraftingIngredientSpec ingredient() {
    return ingredient;
  }
}
