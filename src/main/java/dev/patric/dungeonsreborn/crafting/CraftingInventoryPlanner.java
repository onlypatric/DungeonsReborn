package dev.patric.dungeonsreborn.crafting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;


public final class CraftingInventoryPlanner {
  private CraftingInventoryPlanner() {
  }

  public static Map<Integer, Integer> plan(ItemStack[] storage, CraftingRecipeVariant variant) {
    List<CraftingIngredientSpec> inputs = new ArrayList<>(variant.inputs());
    if (inputs.isEmpty()) {
      for (CraftingSlotIngredientSpec slot : variant.slots()) {
        inputs.add(slot.ingredient());
      }
    }
    return plan(storage, inputs);
  }

  public static Map<Integer, Integer> plan(ItemStack[] storage, List<CraftingIngredientSpec> inputs) {
    if (inputs.isEmpty()) {
      return null;
    }
    List<SlotEntry> slots = new ArrayList<>();
    for (int i = 0; i < storage.length; i++) {
      ItemStack stack = storage[i];
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      int amount = Math.max(0, stack.getAmount());
      if (amount <= 0) {
        continue;
      }
      slots.add(new SlotEntry(i, stack, amount));
    }
    if (slots.isEmpty()) {
      return null;
    }

    List<CraftingIngredientSpec> sorted = new ArrayList<>(inputs);
    sorted.sort(Comparator
        .comparingInt((CraftingIngredientSpec spec) -> specificity(spec.type()))
        .thenComparing(Comparator.comparingInt(CraftingIngredientSpec::amount).reversed()));

    Map<Integer, Integer> consumption = new HashMap<>();
    for (CraftingIngredientSpec ingredient : sorted) {
      int needed = ingredient.amount();
      for (SlotEntry slot : slots) {
        if (needed <= 0) {
          break;
        }
        if (!matches(ingredient, slot.stack)) {
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
    return consumption;
  }

  public static List<ItemStack> materialize(ItemStack[] storage, Map<Integer, Integer> consumption) {
    List<ItemStack> items = new ArrayList<>();
    for (int i = 0; i < storage.length; i++) {
      int amount = consumption.getOrDefault(i, 0);
      if (amount <= 0) {
        continue;
      }
      ItemStack stack = storage[i];
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      ItemStack copy = stack.clone();
      copy.setAmount(Math.min(amount, stack.getAmount()));
      items.add(copy);
    }
    return items;
  }

  public static ItemStack[] apply(ItemStack[] storage, Map<Integer, Integer> consumption) {
    ItemStack[] result = new ItemStack[storage.length];
    for (int i = 0; i < storage.length; i++) {
      ItemStack stack = storage[i];
      if (stack == null || stack.getType().isAir()) {
        result[i] = null;
        continue;
      }
      int consume = consumption.getOrDefault(i, 0);
      if (consume <= 0) {
        result[i] = stack.clone();
        continue;
      }
      int remaining = stack.getAmount() - consume;
      if (remaining <= 0) {
        result[i] = null;
      } else {
        ItemStack copy = stack.clone();
        copy.setAmount(remaining);
        result[i] = copy;
      }
    }
    return result;
  }

  private static boolean matches(CraftingIngredientSpec ingredient, ItemStack stack) {
    return ingredient.matches(stack);
  }

  private static int specificity(CraftingMatchType type) {
    return switch (type) {
      case ITEM_ID -> 5;
      case UPGRADE_ID -> 5;
      case TAG -> 4;
      case MATERIAL -> 3;
      case CATEGORY -> 2;
      case ANY -> 1;
    };
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
  }
}
