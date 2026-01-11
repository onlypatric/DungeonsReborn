package dev.patric.dungeonsreborn.crafting;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface CraftingGuiSession {
  void onDisconnect(Player player);
}
