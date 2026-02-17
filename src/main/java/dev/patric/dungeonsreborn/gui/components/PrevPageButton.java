package dev.patric.dungeonsreborn.gui.components;

import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.PaginatedWindow;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;

/**
 * A navigation button that goes to the previous page in a {@link PaginatedWindow}.
 */
public final class PrevPageButton extends Button {
  public PrevPageButton(PaginatedWindow window) {
    this(window, null, null);
  }

  public PrevPageButton(PaginatedWindow window, Component title, Component disabledLore) {
    super(p -> itemFor(Objects.requireNonNull(window, "window"),
        p,
        title,
        disabledLore),
        ctx -> window.previous(ctx.player()));
    autoDescribeInLore(false);
  }

  private static ItemStack itemFor(PaginatedWindow window, Player player, Component title, Component disabledLore) {
    Component titleLabel = title == null ? GuiI18n.tr(player, "gui.list.prev.title") : title;
    Component disabledLabel = disabledLore == null
        ? GuiI18n.tr(player, "gui.list.prev.disabled")
        : disabledLore;
    Component pageText = Locales.component(player, "gui.list.page",
        Locales.placeholders("current", window.page() + 1, "total", window.pageCount()));
    if (window.hasPrevious()) {
      return GuiButtons.item(GuiButtons.Type.PREV, titleLabel, List.of(pageText));
    }
    return GuiButtons.item(GuiButtons.Type.PREV, titleLabel, List.of(pageText, disabledLabel));
  }
}
