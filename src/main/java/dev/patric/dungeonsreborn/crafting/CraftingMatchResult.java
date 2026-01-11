package dev.patric.dungeonsreborn.crafting;

import java.util.Map;
import java.util.Objects;

public record CraftingMatchResult(CraftingRecipeTemplate recipe,
                                  CraftingRecipeVariant variant,
                                  Map<Integer, Integer> consumed) {
  public CraftingMatchResult {
    Objects.requireNonNull(recipe, "recipe");
    Objects.requireNonNull(variant, "variant");
    Objects.requireNonNull(consumed, "consumed");
  }
}
