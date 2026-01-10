package dev.patric.dungeonsreborn.gui.components;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiComponent;

/**
 * A decorative component that renders nothing.
 * <p>
 * If the window has a background, it will show through.
 */
public final class Spacer implements GuiComponent {
  @Override
  public ItemStack render(Player player) {
    return null;
  }
}

