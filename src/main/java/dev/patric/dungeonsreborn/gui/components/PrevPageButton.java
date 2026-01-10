package dev.patric.dungeonsreborn.gui.components;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.PaginatedWindow;
import net.kyori.adventure.text.Component;

/**
 * A navigation button that goes to the previous page in a {@link PaginatedWindow}.
 */
public final class PrevPageButton extends Button {
  private static final Component DEFAULT_TITLE = Component.text("Previous");
  private static final Component DEFAULT_DISABLED_LORE = Component.text("No previous page");

  public PrevPageButton(PaginatedWindow window) {
    this(window, DEFAULT_TITLE, DEFAULT_DISABLED_LORE);
  }

  public PrevPageButton(PaginatedWindow window, Component title, Component disabledLore) {
    super(p -> itemFor(Objects.requireNonNull(window, "window"),
        Objects.requireNonNull(title, "title"),
        Objects.requireNonNull(disabledLore, "disabledLore")),
        ctx -> window.previous(ctx.player()));
    autoDescribeInLore(false);
  }

  private static ItemStack itemFor(PaginatedWindow window, Component title, Component disabledLore) {
    Component pageText = Component.text("Page " + (window.page() + 1) + "/" + window.pageCount());
    if (window.hasPrevious()) {
      return GuiItems.named(Material.ARROW, title, List.of(pageText));
    }
    return GuiItems.named(Material.GRAY_DYE, title, List.of(pageText, disabledLore));
  }
}
