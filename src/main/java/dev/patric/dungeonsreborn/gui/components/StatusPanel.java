package dev.patric.dungeonsreborn.gui.components;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;

public final class StatusPanel {
  public enum Type {
    INFO,
    WARNING,
    ERROR
  }

  private StatusPanel() {
  }

  public static Function<Player, ItemStack> item(Type type, Component title, List<Component> lore) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(lore, "lore");
    GuiButtons.Type icon = switch (type) {
      case INFO -> GuiButtons.Type.INFO;
      case WARNING -> GuiButtons.Type.WARNING;
      case ERROR -> GuiButtons.Type.ERROR;
    };
    return player -> GuiButtons.item(icon, title, lore);
  }
}
