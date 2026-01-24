package dev.patric.dungeonsreborn.effects.mana;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

public interface ManaProvider {
  String DEFAULT_RESOURCE = "mana";

  double get(Player player, String resourceId);

  double getMax(Player player, String resourceId);

  void set(Player player, String resourceId, double value);

  void setMax(Player player, String resourceId, double max);

  /**
   * @return null if successful, or a player-facing error message if not.
   */
  Component tryConsume(Player player, String resourceId, double amount);

  default ResourceRules rules(Player player, String resourceId) {
    return ResourceRules.defaults(100.0);
  }

  default java.util.Set<String> resourceIds() {
    return java.util.Set.of(DEFAULT_RESOURCE);
  }

  default double get(Player player) {
    return get(player, DEFAULT_RESOURCE);
  }

  default double getMax(Player player) {
    return getMax(player, DEFAULT_RESOURCE);
  }

  default void set(Player player, double value) {
    set(player, DEFAULT_RESOURCE, value);
  }

  default void setMax(Player player, double max) {
    setMax(player, DEFAULT_RESOURCE, max);
  }

  /**
   * @return null if successful, or a player-facing error message if not.
   */
  default Component tryConsume(Player player, double amount) {
    return tryConsume(player, DEFAULT_RESOURCE, amount);
  }
}
