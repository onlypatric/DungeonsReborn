package dev.patric.dungeonsreborn.gui.components;

import java.util.List;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;

public final class LoadingIndicator {
  private LoadingIndicator() {
  }

  public static Function<Player, ItemStack> item() {
    return player -> GuiButtons.item(GuiButtons.Type.REFRESH,
        GuiI18n.tr(player, "gui.loading.title"),
        List.of(GuiI18n.tr(player, "gui.loading.hint")));
  }
}
