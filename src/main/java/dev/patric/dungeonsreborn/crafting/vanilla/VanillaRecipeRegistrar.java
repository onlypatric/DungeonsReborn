package dev.patric.dungeonsreborn.crafting.vanilla;

import dev.patric.dungeonsreborn.crafting.CraftingIngredientSpec;
import dev.patric.dungeonsreborn.crafting.CraftingMatchType;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeSpec;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeTemplate;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeVariant;
import dev.patric.dungeonsreborn.crafting.CraftingSlotIngredientSpec;
import dev.patric.dungeonsreborn.crafting.CraftingYamlRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class VanillaRecipeRegistrar {
  private final JavaPlugin plugin;
  private final CraftingYamlRegistry registry;
  private final CraftingRuleEngine rules;
  private final Set<NamespacedKey> registered = new LinkedHashSet<>();
  private final Map<String, Set<NamespacedKey>> recipeKeysById = new LinkedHashMap<>();

  public VanillaRecipeRegistrar(JavaPlugin plugin, CraftingYamlRegistry registry, CraftingRuleEngine rules) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.registry = Objects.requireNonNull(registry, "registry");
    this.rules = Objects.requireNonNull(rules, "rules");
  }

  public void rebuild() {
    clear();
    for (CraftingRecipeTemplate template : registry.recipes().values()) {
      registerTemplate(template);
    }
  }

  public void clear() {
    for (NamespacedKey key : registered) {
      Bukkit.removeRecipe(key);
    }
    registered.clear();
    recipeKeysById.clear();
  }

  public Set<NamespacedKey> keysForRecipe(String recipeId) {
    if (recipeId == null || recipeId.isBlank()) {
      return Set.of();
    }
    Set<NamespacedKey> keys = recipeKeysById.get(recipeId);
    return keys == null ? Set.of() : Set.copyOf(keys);
  }

  private void registerTemplate(CraftingRecipeTemplate template) {
    if (template == null) {
      return;
    }
    CraftingRecipeSpec spec = template.spec();
    ItemStack result = rules.primaryOutput(template);
    if (result == null || result.getType().isAir()) {
      return;
    }
    int variantIndex = 0;
    for (CraftingRecipeVariant variant : spec.variants()) {
      NamespacedKey key = new NamespacedKey(plugin, "craft_" + spec.id() + "_" + variantIndex);
      variantIndex++;
      if (!isRepresentable(variant)) {
        continue;
      }
      boolean added = variant.isShaped()
          ? registerShaped(key, result, variant)
          : registerShapeless(key, result, variant);
      if (added) {
        registered.add(key);
        recipeKeysById.computeIfAbsent(spec.id(), ignored -> new LinkedHashSet<>()).add(key);
      }
    }
  }

  private boolean isRepresentable(CraftingRecipeVariant variant) {
    if (variant == null) {
      return false;
    }
    if (variant.isShaped() && variant.grid() == null) {
      return false;
    }
    if (variant.grid() != null && (variant.grid().width() > 3 || variant.grid().height() > 3)) {
      return false;
    }
    List<CraftingIngredientSpec> ingredients = new ArrayList<>();
    if (variant.isShaped()) {
      for (CraftingSlotIngredientSpec slot : variant.slots()) {
        if (slot != null) {
          ingredients.add(slot.ingredient());
        }
      }
    } else {
      ingredients.addAll(variant.inputs());
    }
    if (ingredients.isEmpty()) {
      return false;
    }
    for (CraftingIngredientSpec ingredient : ingredients) {
      if (!isRepresentableIngredient(ingredient)) {
        return false;
      }
    }
    return true;
  }

  private boolean isRepresentableIngredient(CraftingIngredientSpec ingredient) {
    if (ingredient == null || ingredient.predicate() != null || ingredient.returnItem() != null) {
      return false;
    }
    return switch (ingredient.type()) {
      case MATERIAL -> ingredient.material() != null;
      case ITEM_ID, UPGRADE_ID -> registry.resolveItemTemplate(ingredient.itemId()) != null;
      default -> false;
    };
  }

  private boolean registerShaped(NamespacedKey key, ItemStack result, CraftingRecipeVariant variant) {
    if (variant.grid() == null) {
      return false;
    }
    int width = variant.grid().width();
    int height = variant.grid().height();
    char[][] chars = new char[height][width];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        chars[y][x] = ' ';
      }
    }

    Map<String, Character> symbolByIngredient = new LinkedHashMap<>();
    Map<Character, RecipeChoice> choices = new LinkedHashMap<>();
    char next = 'A';
    for (CraftingSlotIngredientSpec slot : variant.slots()) {
      if (slot == null || slot.ingredient() == null) {
        continue;
      }
      int index = slot.slot();
      int x = index % width;
      int y = index / width;
      if (x < 0 || x >= width || y < 0 || y >= height) {
        return false;
      }
      String signature = ingredientSignature(slot.ingredient());
      Character symbol = symbolByIngredient.get(signature);
      if (symbol == null) {
        if (next > 'Z') {
          return false;
        }
        symbol = next++;
        RecipeChoice choice = toChoice(slot.ingredient());
        if (choice == null) {
          return false;
        }
        symbolByIngredient.put(signature, symbol);
        choices.put(symbol, choice);
      }
      chars[y][x] = symbol;
    }
    String[] rows = new String[height];
    for (int y = 0; y < height; y++) {
      rows[y] = new String(chars[y]);
    }
    try {
      ShapedRecipe recipe = new ShapedRecipe(key, result.clone());
      recipe.shape(rows);
      for (Map.Entry<Character, RecipeChoice> entry : choices.entrySet()) {
        recipe.setIngredient(entry.getKey(), entry.getValue());
      }
      return Bukkit.addRecipe(recipe);
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean registerShapeless(NamespacedKey key, ItemStack result, CraftingRecipeVariant variant) {
    try {
      ShapelessRecipe recipe = new ShapelessRecipe(key, result.clone());
      for (CraftingIngredientSpec ingredient : variant.inputs()) {
        RecipeChoice choice = toChoice(ingredient);
        if (choice == null) {
          return false;
        }
        for (int i = 0; i < ingredient.amount(); i++) {
          recipe.addIngredient(choice);
        }
      }
      return Bukkit.addRecipe(recipe);
    } catch (Exception ignored) {
      return false;
    }
  }

  private String ingredientSignature(CraftingIngredientSpec ingredient) {
    if (ingredient == null) {
      return "";
    }
    if (ingredient.type() == CraftingMatchType.MATERIAL) {
      return "M:" + ingredient.material().name();
    }
    return "I:" + ingredient.itemId();
  }

  private RecipeChoice toChoice(CraftingIngredientSpec ingredient) {
    if (ingredient == null) {
      return null;
    }
    if (ingredient.type() == CraftingMatchType.MATERIAL) {
      Material material = ingredient.material();
      return material == null ? null : new RecipeChoice.MaterialChoice(material);
    }
    if (ingredient.type() == CraftingMatchType.ITEM_ID || ingredient.type() == CraftingMatchType.UPGRADE_ID) {
      ItemStack item = registry.resolveItemTemplate(ingredient.itemId());
      if (item == null || item.getType().isAir()) {
        return null;
      }
      item.setAmount(1);
      return new RecipeChoice.ExactChoice(item);
    }
    return null;
  }
}
