package dev.patric.dungeonsreborn.gui.components;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.PaginatedWindow;
import net.kyori.adventure.text.Component;

/**
 * A navigation button that goes to the next page in a {@link PaginatedWindow}.
 */
public final class NextPageButton extends Button {
  private static final Component DEFAULT_TITLE = Component.text("Next");
  private static final Component DEFAULT_DISABLED_LORE = Component.text("No next page");

  public NextPageButton(PaginatedWindow window) {
    this(window, DEFAULT_TITLE, DEFAULT_DISABLED_LORE);
  }

  public NextPageButton(PaginatedWindow window, Component title, Component disabledLore) {
    super(p -> itemFor(Objects.requireNonNull(window, "window"),
        Objects.requireNonNull(title, "title"),
        Objects.requireNonNull(disabledLore, "disabledLore")),
        ctx -> window.next(ctx.player()));
    autoDescribeInLore(false);
  }

  private static ItemStack itemFor(PaginatedWindow window, Component title, Component disabledLore) {
    Component pageText = Component.text("Page " + (window.page() + 1) + "/" + window.pageCount());
    if (window.hasNext()) {
      return GuiItems.named(Material.ARROW, title, List.of(pageText));
    }
    return GuiItems.named(Material.GRAY_DYE, title, List.of(pageText, disabledLore));
  }
}
