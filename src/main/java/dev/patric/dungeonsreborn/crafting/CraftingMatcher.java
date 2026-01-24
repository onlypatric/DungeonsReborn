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
        CraftingMatchResult match = matchVariant(inputs, slots, recipe, variant);
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

  public static List<CraftingMatchResult> matchAll(ItemStack[] inputs, Iterable<CraftingRecipeTemplate> recipes) {
    Objects.requireNonNull(inputs, "inputs");
    Objects.requireNonNull(recipes, "recipes");
    List<SlotEntry> slots = nonEmptySlots(inputs);
    if (slots.isEmpty()) {
      return List.of();
    }
    List<MatchScore> matches = new ArrayList<>();
    for (CraftingRecipeTemplate recipe : recipes) {
      CraftingRecipeSpec spec = recipe.spec();
      for (CraftingRecipeVariant variant : spec.variants()) {
        CraftingMatchResult match = matchVariant(inputs, slots, recipe, variant);
        if (match == null) {
          continue;
        }
        matches.add(new MatchScore(match, score(match)));
      }
    }
    if (matches.isEmpty()) {
      return List.of();
    }
    matches.sort((a, b) -> b.score().compareTo(a.score()));
    List<CraftingMatchResult> results = new ArrayList<>(matches.size());
    for (MatchScore entry : matches) {
      results.add(entry.match());
    }
    return results;
  }

  private static CraftingMatchResult matchVariant(ItemStack[] inputsArray, List<SlotEntry> slots,
                                                  CraftingRecipeTemplate recipe, CraftingRecipeVariant variant) {
    if (variant.isShaped()) {
      CraftingMatchResult shaped = matchShaped(inputsArray, recipe, variant);
      if (shaped != null) {
        return shaped;
      }
      return null;
    }
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
    List<CraftingConsumedSlot> consumedSlots = new ArrayList<>();
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
        consumedSlots.add(new CraftingConsumedSlot(slot.index, ingredient, take));
      }
      if (needed > 0) {
        return null;
      }
    }
    if (!variant.allowOverflow()) {
      for (SlotEntry slot : working) {
        if (slot.remaining > 0) {
          return null;
        }
      }
    }
    return new CraftingMatchResult(recipe, variant, consumption, consumedSlots);
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
      case UPGRADE_ID -> 5;
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
    List<CraftingIngredientSpec> ingredientsList = match.variant().inputs();
    if (ingredientsList.isEmpty()) {
      ingredientsList = new ArrayList<>();
      for (CraftingSlotIngredientSpec slot : match.variant().slots()) {
        ingredientsList.add(slot.ingredient());
      }
    }
    int ingredients = ingredientsList.size();
    for (CraftingIngredientSpec spec : ingredientsList) {
      specificity += specificity(spec.type()) * Math.max(1, spec.amount());
    }
    return new Score(match.variant().priority(), consumed, specificity, ingredients);
  }

  private record Score(int priority, int consumed, int specificity, int ingredients) implements Comparable<Score> {
    boolean betterThan(Score other) {
      if (priority != other.priority) {
        return priority > other.priority;
      }
      if (consumed != other.consumed) {
        return consumed > other.consumed;
      }
      if (specificity != other.specificity) {
        return specificity > other.specificity;
      }
      return ingredients > other.ingredients;
    }

    @Override
    public int compareTo(Score other) {
      if (priority != other.priority) {
        return Integer.compare(priority, other.priority);
      }
      if (consumed != other.consumed) {
        return Integer.compare(consumed, other.consumed);
      }
      if (specificity != other.specificity) {
        return Integer.compare(specificity, other.specificity);
      }
      return Integer.compare(ingredients, other.ingredients);
    }
  }

  private record MatchScore(CraftingMatchResult match, Score score) {
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

  private static CraftingMatchResult matchShaped(ItemStack[] inputs, CraftingRecipeTemplate recipe,
                                                 CraftingRecipeVariant variant) {
    GridSize grid = GridSize.fromInputs(inputs);
    if (grid == null) {
      return null;
    }
    List<CraftingSlotIngredientSpec> slots = variant.slots();
    CraftingGridSpec pattern = variant.grid();
    if (pattern != null) {
      return matchPattern(inputs, grid, pattern, slots, recipe, variant);
    }
    return matchSlotConstraints(inputs, grid, slots, recipe, variant);
  }

  private static CraftingMatchResult matchSlotConstraints(ItemStack[] inputs, GridSize grid,
                                                          List<CraftingSlotIngredientSpec> slots,
                                                          CraftingRecipeTemplate recipe, CraftingRecipeVariant variant) {
    Map<Integer, Integer> consumption = new HashMap<>();
    List<CraftingConsumedSlot> consumedSlots = new ArrayList<>();
    for (CraftingSlotIngredientSpec slot : slots) {
      int index = slot.slot();
      if (index < 0 || index >= inputs.length) {
        return null;
      }
      ItemStack stack = inputs[index];
      if (!slot.ingredient().matches(stack)) {
        return null;
      }
      int needed = slot.ingredient().amount();
      int available = stack == null ? 0 : stack.getAmount();
      if (available < needed) {
        return null;
      }
      consumption.put(index, consumption.getOrDefault(index, 0) + needed);
      consumedSlots.add(new CraftingConsumedSlot(index, slot.ingredient(), needed));
    }
    if (!variant.allowOverflow()) {
      for (int i = 0; i < inputs.length; i++) {
        ItemStack stack = inputs[i];
        if (stack == null || stack.getType().isAir()) {
          continue;
        }
        int remaining = stack.getAmount() - consumption.getOrDefault(i, 0);
        if (remaining > 0) {
          return null;
        }
      }
    }
    return new CraftingMatchResult(recipe, variant, consumption, consumedSlots);
  }

  private static CraftingMatchResult matchPattern(ItemStack[] inputs, GridSize grid, CraftingGridSpec pattern,
                                                  List<CraftingSlotIngredientSpec> slots, CraftingRecipeTemplate recipe,
                                                  CraftingRecipeVariant variant) {
    if (slots.isEmpty()) {
      return null;
    }
    int patternWidth = pattern.width();
    int patternHeight = pattern.height();
    if (patternWidth > grid.width || patternHeight > grid.height) {
      return null;
    }
    List<Transform> transforms = Transform.transforms(pattern.allowMirror(), pattern.allowRotate());
    for (Transform transform : transforms) {
      int maxOffsetX = grid.width - transform.width(patternWidth, patternHeight);
      int maxOffsetY = grid.height - transform.height(patternWidth, patternHeight);
      for (int offsetY = 0; offsetY <= maxOffsetY; offsetY++) {
        for (int offsetX = 0; offsetX <= maxOffsetX; offsetX++) {
          CraftingMatchResult match = matchPatternAt(inputs, grid, patternWidth, patternHeight, slots,
              transform, offsetX, offsetY, variant, recipe);
          if (match != null) {
            return match;
          }
        }
      }
    }
    return null;
  }

  private static CraftingMatchResult matchPatternAt(ItemStack[] inputs, GridSize grid, int patternWidth, int patternHeight,
                                                    List<CraftingSlotIngredientSpec> slots, Transform transform,
                                                    int offsetX, int offsetY, CraftingRecipeVariant variant,
                                                    CraftingRecipeTemplate recipe) {
    Map<Integer, Integer> consumption = new HashMap<>();
    List<CraftingConsumedSlot> consumedSlots = new ArrayList<>();
    boolean[][] patternMask = new boolean[grid.height][grid.width];
    for (CraftingSlotIngredientSpec slot : slots) {
      int slotIndex = slot.slot();
      int patternX = slotIndex % patternWidth;
      int patternY = slotIndex / patternWidth;
      if (patternY >= patternHeight) {
        return null;
      }
      Point transformed = transform.apply(patternX, patternY, patternWidth, patternHeight);
      int gridX = offsetX + transformed.x;
      int gridY = offsetY + transformed.y;
      if (gridX < 0 || gridX >= grid.width || gridY < 0 || gridY >= grid.height) {
        return null;
      }
      int inputIndex = gridY * grid.width + gridX;
      patternMask[gridY][gridX] = true;
      ItemStack stack = inputs[inputIndex];
      if (!slot.ingredient().matches(stack)) {
        return null;
      }
      int needed = slot.ingredient().amount();
      int available = stack == null ? 0 : stack.getAmount();
      if (available < needed) {
        return null;
      }
      consumption.put(inputIndex, consumption.getOrDefault(inputIndex, 0) + needed);
      consumedSlots.add(new CraftingConsumedSlot(inputIndex, slot.ingredient(), needed));
    }
    if (!variant.allowOverflow()) {
      for (int y = 0; y < grid.height; y++) {
        for (int x = 0; x < grid.width; x++) {
          int index = y * grid.width + x;
          ItemStack stack = inputs[index];
          if (stack == null || stack.getType().isAir()) {
            continue;
          }
          int remaining = stack.getAmount() - consumption.getOrDefault(index, 0);
          if (remaining <= 0) {
            continue;
          }
          if (!patternMask[y][x]) {
            return null;
          }
        }
      }
    }
    return new CraftingMatchResult(recipe, variant, consumption, consumedSlots);
  }

  private record GridSize(int width, int height) {
    static GridSize fromInputs(ItemStack[] inputs) {
      int size = inputs.length;
      int width = (int) Math.round(Math.sqrt(size));
      if (width <= 0 || width * width != size) {
        return null;
      }
      return new GridSize(width, width);
    }
  }

  private record Point(int x, int y) {}

  private enum Transform {
    IDENTITY,
    ROT90,
    ROT180,
    ROT270,
    MIRROR,
    MIRROR_ROT90,
    MIRROR_ROT180,
    MIRROR_ROT270;

    static List<Transform> transforms(boolean mirror, boolean rotate) {
      List<Transform> out = new ArrayList<>();
      out.add(IDENTITY);
      if (rotate) {
        out.add(ROT90);
        out.add(ROT180);
        out.add(ROT270);
      }
      if (mirror) {
        out.add(MIRROR);
        if (rotate) {
          out.add(MIRROR_ROT90);
          out.add(MIRROR_ROT180);
          out.add(MIRROR_ROT270);
        }
      }
      return out;
    }

    int width(int baseWidth, int baseHeight) {
      return switch (this) {
        case ROT90, ROT270, MIRROR_ROT90, MIRROR_ROT270 -> baseHeight;
        default -> baseWidth;
      };
    }

    int height(int baseWidth, int baseHeight) {
      return switch (this) {
        case ROT90, ROT270, MIRROR_ROT90, MIRROR_ROT270 -> baseWidth;
        default -> baseHeight;
      };
    }

    Point apply(int x, int y, int width, int height) {
      return switch (this) {
        case IDENTITY -> new Point(x, y);
        case ROT90 -> new Point(height - 1 - y, x);
        case ROT180 -> new Point(width - 1 - x, height - 1 - y);
        case ROT270 -> new Point(y, width - 1 - x);
        case MIRROR -> new Point(width - 1 - x, y);
        case MIRROR_ROT90 -> new Point(height - 1 - y, width - 1 - x);
        case MIRROR_ROT180 -> new Point(x, height - 1 - y);
        case MIRROR_ROT270 -> new Point(y, x);
      };
    }
  }
}
