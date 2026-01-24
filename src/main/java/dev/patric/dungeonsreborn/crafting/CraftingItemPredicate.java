package dev.patric.dungeonsreborn.crafting;

import org.bukkit.inventory.ItemStack;

@FunctionalInterface
public interface CraftingItemPredicate {
  boolean matches(ItemStack stack);
}
