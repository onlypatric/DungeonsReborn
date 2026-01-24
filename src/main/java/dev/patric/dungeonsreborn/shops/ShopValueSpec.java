package dev.patric.dungeonsreborn.shops;

import java.util.OptionalInt;
import java.util.function.Function;

import org.bukkit.inventory.ItemStack;

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
    return ingredient.matches(stack, itemResolver, tokenSpec);
  }

}
