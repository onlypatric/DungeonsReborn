package dev.patric.dungeonsreborn.gui;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

public final class GuiItems {
  private GuiItems() {
  }

  public static ItemStack named(Material material, Component name) {
    return named(material, name, List.of(), true);
  }

  public static ItemStack named(Material material, Component name, List<Component> lore) {
    return named(material, name, lore, true);
  }

  /**
   * Builds a named GUI item with optional lore, optionally hiding tooltip sections via {@link ItemFlag}s.
   *
   * @param hideItemFlags when true, hides most vanilla tooltip lines (attributes, enchants, etc.)
   */
  public static ItemStack named(Material material, Component name, List<Component> lore, boolean hideItemFlags) {
    Objects.requireNonNull(material, "material");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(lore, "lore");

    GuiItem item = GuiItem.of(material).displayName(name);
    if (hideItemFlags) {
      item.flags(ItemFlag.values());
    }
    if (!lore.isEmpty()) {
      item.lore(lore);
    }
    return item.build();
  }

  public static ItemStack namedHidden(Material material, Component name) {
    return named(material, name, List.of(), true);
  }

  public static ItemStack namedHidden(Material material, Component name, List<Component> lore) {
    return named(material, name, lore, true);
  }

  public static ItemStack namedShown(Material material, Component name) {
    return named(material, name, List.of(), false);
  }

  public static ItemStack namedShown(Material material, Component name, List<Component> lore) {
    return named(material, name, lore, false);
  }

  public static ItemStack blankPane(Material material) {
    return named(material, Component.text(" "));
  }
}
