package dev.patric.dungeonsreborn.gui.components;

import java.util.Objects;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiComponent;

/**
 * A decorative component that simply renders an item.
 * <p>
 * Useful in layouts as a background fill or border element.
 */
public final class Fill implements GuiComponent {
  private final Function<Player, ItemStack> item;

  public Fill(ItemStack item) {
    this(p -> item);
  }

  public Fill(Function<Player, ItemStack> item) {
    this.item = Objects.requireNonNull(item, "item");
  }

  @Override
  public ItemStack render(Player player) {
    return item.apply(player);
  }
}

