package dev.patric.dungeonsreborn.gui.components;

import java.util.Objects;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiComponent;

public final class Label implements GuiComponent {
  private final Function<Player, ItemStack> item;

  public Label(Function<Player, ItemStack> item) {
    this.item = Objects.requireNonNull(item, "item");
  }

  public Label(ItemStack item) {
    this(p -> item);
  }

  @Override
  public ItemStack render(Player player) {
    return item.apply(player);
  }
}

