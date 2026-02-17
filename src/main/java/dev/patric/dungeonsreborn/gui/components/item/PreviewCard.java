package dev.patric.dungeonsreborn.gui.components.item;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiComponent;
import dev.patric.dungeonsreborn.gui.GuiItems;
import net.kyori.adventure.text.Component;

/**
 * Standardized preview card for detail menus (icon + title + summary lore).
 */
public final class PreviewCard implements GuiComponent {
  private final Function<Player, ItemStack> base;
  private final Function<Player, Component> title;
  private final Function<Player, List<Component>> lore;
  private boolean hideItemFlags = true;

  public static PreviewCard head(String headId, Function<Player, Component> title,
      Function<Player, List<Component>> lore) {
    Objects.requireNonNull(headId, "headId");
    return new PreviewCard(
        player -> GuiItems.head(headId, Component.text(" "), List.of(), false),
        title,
        lore);
  }

  public static PreviewCard item(Function<Player, ItemStack> base,
      Function<Player, Component> title,
      Function<Player, List<Component>> lore) {
    return new PreviewCard(base, title, lore);
  }

  private PreviewCard(Function<Player, ItemStack> base,
      Function<Player, Component> title,
      Function<Player, List<Component>> lore) {
    this.base = Objects.requireNonNull(base, "base");
    this.title = Objects.requireNonNull(title, "title");
    this.lore = Objects.requireNonNull(lore, "lore");
  }

  public PreviewCard hideItemFlags(boolean hide) {
    this.hideItemFlags = hide;
    return this;
  }

  @Override
  public ItemStack render(Player player) {
    ItemStack baseItem = base.apply(player);
    if (baseItem == null) {
      return null;
    }
    return GuiItems.named(baseItem,
        title.apply(player),
        lore.apply(player),
        hideItemFlags);
  }
}
