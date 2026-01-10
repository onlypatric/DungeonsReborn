package dev.patric.dungeonsreborn.gui;

import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public interface GuiComponent {
  /**
   * Renders the item to show in the inventory.
   * <p>
   * Return {@code null} to leave the slot empty.
   */
  ItemStack render(Player player);

  /**
   * Called when the player clicks the slot this component is placed in.
   */
  default void onClick(Window.ClickContext ctx) {
  }

  /**
   * Called when a component is placed into a window slot.
   * <p>
   * Useful for components that need to know their slot index (e.g. storage slots).
   */
  default void mounted(Window window, int slot) {
  }

  /**
   * If true, the click event for this slot is not cancelled and vanilla inventory behavior is allowed.
   * <p>
   * Use {@link #afterVanillaClick(Window.ClickContext)} to react after the click has applied.
   */
  default boolean allowVanillaClicks() {
    return false;
  }

  /**
   * Called right before vanilla click handling is allowed for this component.
   * <p>
   * Returning {@code false} will cancel the click.
   * <p>
   * Useful for "vanilla-mode" storage slots that still want acceptance rules.
   */
  default boolean beforeVanillaClick(Window.ClickContext ctx) {
    return true;
  }

  /**
   * Called one tick after a vanilla click has been allowed for this component.
   */
  default void afterVanillaClick(Window.ClickContext ctx) {
  }

  /**
   * If true, drag events affecting this slot may be allowed (if the window's drag-cancel policy permits it).
   */
  default boolean allowVanillaDrags() {
    return allowVanillaClicks();
  }

  /**
   * Called right before vanilla drag handling is allowed for this component.
   * <p>
   * Returning {@code false} will cancel the drag.
   */
  default boolean beforeVanillaDrag(Window window, Player player, InventoryDragEvent event, Set<Integer> rawSlots) {
    return true;
  }

  /**
   * Called one tick after a vanilla drag affecting this component has been allowed.
   *
   * @param rawSlots the raw slots touched by the drag (from {@link InventoryDragEvent#getRawSlots()}).
   */
  default void afterVanillaDrag(Window window, Player player, InventoryDragEvent event, Set<Integer> rawSlots) {
  }
}
