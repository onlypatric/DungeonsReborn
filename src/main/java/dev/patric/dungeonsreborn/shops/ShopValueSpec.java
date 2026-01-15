package dev.patric.dungeonsreborn.shops;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Function;

import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.items.ItemMarkers;

public record ShopValueSpec(ShopIngredientSpec ingredient, int value) {
  public ShopValueSpec {
    if (ingredient == null) {
      throw new IllegalArgumentException("ingredient is required");
    }
    if (value <= 0) {
      throw new IllegalArgumentException("value must be > 0");
    }
  }

  public OptionalInt valueFor(ItemStack stack, ShopTokenSpec tokenSpec, Function<String, ItemStack> itemResolver) {
    if (!matches(stack, tokenSpec, itemResolver)) {
      return OptionalInt.empty();
    }
    int amount = Math.max(1, stack.getAmount());
    return OptionalInt.of(Math.max(1, value) * amount);
  }

  private boolean matches(ItemStack stack, ShopTokenSpec tokenSpec, Function<String, ItemStack> itemResolver) {
    if (stack == null) {
      return false;
    }
    return switch (ingredient.type()) {
      case TOKEN -> tokenSpec != null && tokenSpec.markerKey() != null
          && ItemMarkers.has(stack, tokenSpec.markerKey());
      case MATERIAL -> stack.getType() == ingredient.material();
      case ITEM_ID -> isSimilar(stack, itemResolver == null ? null : itemResolver.apply(ingredient.itemId()));
      case ITEMSTACK -> isSimilar(stack, ingredient.item());
    };
  }

  private static boolean isSimilar(ItemStack item, ItemStack template) {
    if (item == null || template == null) {
      return false;
    }
    if (item == template) {
      return true;
    }
    return Objects.equals(item.getType(), template.getType()) && item.isSimilar(template);
  }
}
