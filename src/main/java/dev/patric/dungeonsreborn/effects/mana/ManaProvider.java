package dev.patric.dungeonsreborn.effects.mana;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

public interface ManaProvider {
  double get(Player player);

  double getMax(Player player);

  void set(Player player, double value);

  void setMax(Player player, double max);

  /**
   * @return null if successful, or a player-facing error message if not.
   */
  Component tryConsume(Player player, double amount);
}

