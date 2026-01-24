package dev.patric.dungeonsreborn.crafting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;

public final class CraftingRecipeIndex {
  private final Map<String, List<CraftingRecipeTemplate>> byItemId = new ConcurrentHashMap<>();
  private final Map<Material, List<CraftingRecipeTemplate>> byMaterial = new ConcurrentHashMap<>();
  private final List<CraftingRecipeTemplate> fallback = new ArrayList<>();

  public void clear() {
    byItemId.clear();
    byMaterial.clear();
    fallback.clear();
  }

  public void register(CraftingRecipeTemplate recipe) {
    if (recipe == null) {
      return;
    }
    @SuppressWarnings("unused")
    boolean added = false;
    for (CraftingRecipeVariant variant : recipe.spec().variants()) {
      for (CraftingIngredientSpec ingredient : gatherIngredients(variant)) {
        if (ingredient == null) {
          continue;
        }
        switch (ingredient.type()) {
          case ITEM_ID -> {
            String id = ingredient.itemId();
            if (id != null && !id.isBlank()) {
              String normalized = Ids.normalize(id);
              byItemId.computeIfAbsent(normalized, key -> new ArrayList<>()).add(recipe);
              added = true;
            }
          }
          case MATERIAL -> {
            Material material = ingredient.material();
            if (material != null) {
              byMaterial.computeIfAbsent(material, key -> new ArrayList<>()).add(recipe);
              added = true;
            }
          }
          default -> {
            // Keep recipe in fallback for non-indexable inputs.
          }
        }
      }
    }
    fallback.add(recipe);
  }

  public Collection<CraftingRecipeTemplate> candidates(ItemStack[] inputs) {
    Set<CraftingRecipeTemplate> results = new LinkedHashSet<>();
    if (inputs != null) {
      for (ItemStack stack : inputs) {
        if (stack == null || stack.getType().isAir()) {
          continue;
        }
        String itemId = ItemMarkers.getItemId(stack);
        if (itemId != null) {
          List<CraftingRecipeTemplate> entries = byItemId.get(Ids.normalize(itemId));
          if (entries != null) {
            results.addAll(entries);
          }
        }
        List<CraftingRecipeTemplate> materialEntries = byMaterial.get(stack.getType());
        if (materialEntries != null) {
          results.addAll(materialEntries);
        }
      }
    }
    results.addAll(fallback);
    return results;
  }

  private List<CraftingIngredientSpec> gatherIngredients(CraftingRecipeVariant variant) {
    List<CraftingIngredientSpec> ingredients = new ArrayList<>(variant.inputs());
    if (ingredients.isEmpty()) {
      for (CraftingSlotIngredientSpec slot : variant.slots()) {
        if (slot != null) {
          ingredients.add(slot.ingredient());
        }
      }
    }
    return ingredients;
  }
}
