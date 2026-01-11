package dev.patric.dungeonsreborn.crafting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.inventory.ItemStack;

public final class CraftingMatcher {
  private CraftingMatcher() {
  }

  public static CraftingMatchResult match(ItemStack[] inputs, Iterable<CraftingRecipeTemplate> recipes) {
    Objects.requireNonNull(inputs, "inputs");
    Objects.requireNonNull(recipes, "recipes");
    List<SlotEntry> slots = nonEmptySlots(inputs);
    if (slots.isEmpty()) {
      return null;
    }
    CraftingMatchResult best = null;
    Score bestScore = null;
    for (CraftingRecipeTemplate recipe : recipes) {
      CraftingRecipeSpec spec = recipe.spec();
      for (CraftingRecipeVariant variant : spec.variants()) {
        CraftingMatchResult match = matchVariant(slots, recipe, variant);
        if (match == null) {
          continue;
        }
        Score score = score(match);
        if (bestScore == null || score.betterThan(bestScore)) {
          best = match;
          bestScore = score;
        }
      }
    }
    return best;
  }

  private static CraftingMatchResult matchVariant(List<SlotEntry> slots, CraftingRecipeTemplate recipe,
                                                  CraftingRecipeVariant variant) {
    List<CraftingIngredientSpec> inputs = variant.inputs();
    if (inputs.isEmpty()) {
      return null;
    }
    if (!allInputsMatchAllowed(slots, inputs)) {
      return null;
    }
    List<CraftingIngredientSpec> sorted = new ArrayList<>(inputs);
    sorted.sort(Comparator
        .comparingInt((CraftingIngredientSpec spec) -> specificity(spec.type()))
        .thenComparing(Comparator.comparingInt(CraftingIngredientSpec::amount).reversed()));

    List<SlotEntry> working = new ArrayList<>(slots.size());
    for (SlotEntry slot : slots) {
      working.add(slot.copy());
    }

    Map<Integer, Integer> consumption = new HashMap<>();
    for (CraftingIngredientSpec ingredient : sorted) {
      int needed = ingredient.amount();
      for (SlotEntry slot : working) {
        if (needed <= 0) {
          break;
        }
        if (!ingredient.matches(slot.stack)) {
          continue;
        }
        int take = Math.min(needed, slot.remaining);
        if (take <= 0) {
          continue;
        }
        slot.remaining -= take;
        needed -= take;
        consumption.put(slot.index, consumption.getOrDefault(slot.index, 0) + take);
      }
      if (needed > 0) {
        return null;
      }
    }
    return new CraftingMatchResult(recipe, variant, consumption);
  }

  private static boolean allInputsMatchAllowed(List<SlotEntry> slots, List<CraftingIngredientSpec> ingredients) {
    for (SlotEntry slot : slots) {
      boolean matched = false;
      for (CraftingIngredientSpec ingredient : ingredients) {
        if (ingredient.matches(slot.stack)) {
          matched = true;
          break;
        }
      }
      if (!matched) {
        return false;
      }
    }
    return true;
  }

  private static int specificity(CraftingMatchType type) {
    return switch (type) {
      case ITEM_ID -> 5;
      case TAG -> 4;
      case MATERIAL -> 3;
      case CATEGORY -> 2;
      case ANY -> 1;
    };
  }

  private static List<SlotEntry> nonEmptySlots(ItemStack[] inputs) {
    List<SlotEntry> slots = new ArrayList<>();
    for (int i = 0; i < inputs.length; i++) {
      ItemStack stack = inputs[i];
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      int amount = Math.max(0, stack.getAmount());
      if (amount <= 0) {
        continue;
      }
      slots.add(new SlotEntry(i, stack.clone(), amount));
    }
    return slots;
  }

  private static Score score(CraftingMatchResult match) {
    int consumed = 0;
    for (int amount : match.consumed().values()) {
      consumed += Math.max(0, amount);
    }
    int specificity = 0;
    int ingredients = match.variant().inputs().size();
    for (CraftingIngredientSpec spec : match.variant().inputs()) {
      specificity += specificity(spec.type()) * Math.max(1, spec.amount());
    }
    return new Score(consumed, specificity, ingredients);
  }

  private record Score(int consumed, int specificity, int ingredients) {
    boolean betterThan(Score other) {
      if (consumed != other.consumed) {
        return consumed > other.consumed;
      }
      if (specificity != other.specificity) {
        return specificity > other.specificity;
      }
      return ingredients > other.ingredients;
    }
  }

  private static final class SlotEntry {
    private final int index;
    private final ItemStack stack;
    private int remaining;

    private SlotEntry(int index, ItemStack stack, int remaining) {
      this.index = index;
      this.stack = stack;
      this.remaining = remaining;
    }

    private SlotEntry copy() {
      return new SlotEntry(index, stack, remaining);
    }
  }
}
