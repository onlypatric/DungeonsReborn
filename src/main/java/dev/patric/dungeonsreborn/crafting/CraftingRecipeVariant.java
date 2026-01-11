package dev.patric.dungeonsreborn.crafting;

import java.util.List;
import java.util.Objects;

public final class CraftingRecipeVariant {
  private final List<CraftingIngredientSpec> inputs;

  public CraftingRecipeVariant(List<CraftingIngredientSpec> inputs) {
    this.inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
  }

  public List<CraftingIngredientSpec> inputs() {
    return inputs;
  }
}
