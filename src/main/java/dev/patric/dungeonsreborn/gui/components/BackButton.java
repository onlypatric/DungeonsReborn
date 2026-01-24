package dev.patric.dungeonsreborn.gui.components;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;

/**
 * A simple navigation button that closes the current window (returning to the previous one if present).
 */
public final class BackButton extends Button {
  public BackButton() {
    this(p -> GuiButtons.item(GuiButtons.Type.BACK, Locales.component(p, "gui.button.back")), true);
  }

  public BackButton(Component title) {
    this(p -> GuiItems.head("LEFT", Objects.requireNonNull(title, "title"), List.of()), true);
  }

  public BackButton(Function<Player, ItemStack> item) {
    this(item, true);
  }

  public BackButton(Function<Player, ItemStack> item, boolean autoDescribeInLore) {
    super(item, ctx -> ctx.close());
    autoDescribeInLore(autoDescribeInLore);
  }

  public static BackButton withLore(Component title, List<Component> lore) {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(lore, "lore");
    return new BackButton(p -> GuiItems.head("LEFT", title, lore), true);
  }
}
