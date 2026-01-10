package dev.patric.dungeonsreborn.effects.integration;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@FunctionalInterface
public interface ItemMatcher {
  boolean matches(Player player, ItemStack item);
}

