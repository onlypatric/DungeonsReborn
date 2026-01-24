package dev.patric.dungeonsreborn.crafting;

import java.util.List;
import java.util.Objects;

public final class CraftingRecipeVariant {
  private final List<CraftingIngredientSpec> inputs;
  private final List<CraftingSlotIngredientSpec> slots;
  private final CraftingGridSpec grid;
  private final boolean strict;
  private final boolean allowOverflow;
  private final int priority;

  public CraftingRecipeVariant(List<CraftingIngredientSpec> inputs,
                               List<CraftingSlotIngredientSpec> slots,
                               CraftingGridSpec grid,
                               boolean strict,
                               boolean allowOverflow,
                               int priority) {
    this.inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
    this.slots = List.copyOf(slots == null ? List.of() : slots);
    this.grid = grid;
    this.strict = strict;
    this.allowOverflow = allowOverflow;
    this.priority = priority;
  }

  public List<CraftingIngredientSpec> inputs() {
    return inputs;
  }

  public List<CraftingSlotIngredientSpec> slots() {
    return slots;
  }

  public CraftingGridSpec grid() {
    return grid;
  }

  public boolean strict() {
    return strict;
  }

  public boolean allowOverflow() {
    return allowOverflow;
  }

  public int priority() {
    return priority;
  }

  public boolean isShaped() {
    return grid != null || !slots.isEmpty();
  }
}
