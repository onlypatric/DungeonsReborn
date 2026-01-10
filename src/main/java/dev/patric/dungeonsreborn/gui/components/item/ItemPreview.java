package dev.patric.dungeonsreborn.gui.components.item;

import java.util.Objects;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiComponent;

/**
 * Simple read-only item display component.
 * <p>
 * Useful for showing selected/previewed items without manual slot rendering.
 */
public final class ItemPreview implements GuiComponent {
  private final Function<Player, ItemStack> item;
  private ItemStack placeholder;

  public ItemPreview(Function<Player, ItemStack> item) {
    this.item = Objects.requireNonNull(item, "item");
  }

  public ItemPreview placeholder(ItemStack placeholder) {
    this.placeholder = Objects.requireNonNull(placeholder, "placeholder");
    return this;
  }

  @Override
  public ItemStack render(Player player) {
    ItemStack value = item.apply(player);
    if (value == null || value.getType().isAir()) {
      return placeholder == null ? null : placeholder.clone();
    }
    return value.clone();
  }
}

