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
 * A navigation button that goes to the next page in a {@link PaginatedWindow}.
 */
public final class NextPageButton extends Button {
  public NextPageButton(PaginatedWindow window) {
    this(window, null, null);
  }

  public NextPageButton(PaginatedWindow window, Component title, Component disabledLore) {
    super(p -> itemFor(Objects.requireNonNull(window, "window"),
        p,
        title,
        disabledLore),
        ctx -> window.next(ctx.player()));
    autoDescribeInLore(false);
  }

  private static ItemStack itemFor(PaginatedWindow window, Player player, Component title, Component disabledLore) {
    Component titleLabel = title == null ? GuiI18n.tr(player, "gui.list.next.title") : title;
    Component disabledLabel = disabledLore == null
        ? GuiI18n.tr(player, "gui.list.next.disabled")
        : disabledLore;
    Component pageText = Locales.component(player, "gui.list.page",
        Locales.placeholders("current", window.page() + 1, "total", window.pageCount()));
    if (window.hasNext()) {
      return GuiButtons.item(GuiButtons.Type.NEXT, titleLabel, List.of(pageText));
    }
    return GuiButtons.item(GuiButtons.Type.NEXT, titleLabel, List.of(pageText, disabledLabel));
  }
}
