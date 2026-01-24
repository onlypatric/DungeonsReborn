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
 * A simple navigation button that closes the current inventory.
 */
public final class CloseButton extends Button {
  public CloseButton() {
    this(p -> GuiButtons.item(GuiButtons.Type.CLOSE, Locales.component(p, "gui.button.close")), true);
  }

  public CloseButton(Component title) {
    this(p -> GuiItems.head("CANCEL", Objects.requireNonNull(title, "title"), List.of()), true);
  }

  public CloseButton(Function<Player, ItemStack> item) {
    this(item, true);
  }

  public CloseButton(Function<Player, ItemStack> item, boolean autoDescribeInLore) {
    super(item, ctx -> ctx.close());
    autoDescribeInLore(autoDescribeInLore);
  }

  public static CloseButton withLore(Component title, List<Component> lore) {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(lore, "lore");
    return new CloseButton(p -> GuiItems.head("CANCEL", title, lore), true);
  }
}
