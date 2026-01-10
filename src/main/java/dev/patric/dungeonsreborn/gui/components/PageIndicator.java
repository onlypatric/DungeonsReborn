package dev.patric.dungeonsreborn.gui.components;

import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.PaginatedWindow;
import dev.patric.dungeonsreborn.gui.GuiComponent;
import net.kyori.adventure.text.Component;

/**
 * A small label that renders the current page for a {@link PaginatedWindow}.
 */
public final class PageIndicator implements GuiComponent {
  private final PaginatedWindow window;
  private final Material material;

  public PageIndicator(PaginatedWindow window) {
    this(window, Material.PAPER);
  }

  public PageIndicator(PaginatedWindow window, Material material) {
    this.window = Objects.requireNonNull(window, "window");
    this.material = Objects.requireNonNull(material, "material");
  }

  @Override
  public ItemStack render(Player player) {
    return GuiItems.named(material, Component.text("Page " + (window.page() + 1) + "/" + window.pageCount()));
  }
}

