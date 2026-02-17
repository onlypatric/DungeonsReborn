package dev.patric.dungeonsreborn.gui.components;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.PaginatedWindow;
import dev.patric.dungeonsreborn.gui.GuiComponent;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;

/**
 * A small label that renders the current page for a {@link PaginatedWindow}.
 */
public final class PageIndicator implements GuiComponent {
  private final PaginatedWindow window;

  public PageIndicator(PaginatedWindow window) {
    this.window = Objects.requireNonNull(window, "window");
  }

  @Override
  public ItemStack render(Player player) {
    Component label = Locales.component(player, "gui.list.page",
        Locales.placeholders("current", window.page() + 1, "total", window.pageCount()));
    return GuiButtons.item(GuiButtons.Type.PAGE, label);
  }
}
