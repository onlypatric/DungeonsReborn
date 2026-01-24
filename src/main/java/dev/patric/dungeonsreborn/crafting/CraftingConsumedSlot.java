package dev.patric.dungeonsreborn.crafting;

import java.util.Objects;

public record CraftingConsumedSlot(int slot, CraftingIngredientSpec ingredient, int amount) {
  public CraftingConsumedSlot {
    Objects.requireNonNull(ingredient, "ingredient");
  }
}
