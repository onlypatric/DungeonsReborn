package dev.patric.dungeonsreborn.crafting;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CraftingMatchResult(CraftingRecipeTemplate recipe,
                                  CraftingRecipeVariant variant,
                                  Map<Integer, Integer> consumed,
                                  List<CraftingConsumedSlot> consumedSlots) {
  public CraftingMatchResult {
    Objects.requireNonNull(recipe, "recipe");
    Objects.requireNonNull(variant, "variant");
    Objects.requireNonNull(consumed, "consumed");
    Objects.requireNonNull(consumedSlots, "consumedSlots");
  }
}
