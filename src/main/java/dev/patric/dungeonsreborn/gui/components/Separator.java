package dev.patric.dungeonsreborn.gui.components;

import java.util.Objects;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiComponent;
import dev.patric.dungeonsreborn.gui.GuiItems;

/**
 * A simple decorative separator, typically a glass pane line.
 */
public final class Separator implements GuiComponent {
  private final Function<Player, ItemStack> item;

  public Separator(ItemStack item) {
    Objects.requireNonNull(item, "item");
    this.item = p -> item;
  }

  public Separator(Function<Player, ItemStack> item) {
    this.item = Objects.requireNonNull(item, "item");
  }

  public static Separator pane(Material paneMaterial) {
    return new Separator(GuiItems.blankPane(paneMaterial));
  }

  @Override
  public ItemStack render(Player player) {
    return item.apply(player);
  }
}

