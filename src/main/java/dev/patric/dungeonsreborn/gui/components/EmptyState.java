package dev.patric.dungeonsreborn.gui.components;

import java.util.List;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import net.kyori.adventure.text.Component;

public final class EmptyState {
  private EmptyState() {
  }

  public static Function<Player, ItemStack> list() {
    return player -> GuiItems.named(
        Material.BARRIER,
        GuiI18n.tr(player, "gui.list.empty.title"),
        List.of(GuiI18n.tr(player, "gui.list.empty.hint")));
  }

  public static Function<Player, ItemStack> list(Component title, List<Component> lore) {
    return player -> GuiItems.named(Material.BARRIER, title, lore);
  }
}
