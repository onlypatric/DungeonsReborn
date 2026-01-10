package dev.patric.dungeonsreborn.gui.components.item;

import java.util.Objects;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiComponent;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.layout.Layout;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import net.kyori.adventure.text.Component;

/**
 * Small helper that shows a "before -> after" item comparison across 3 slots.
 */
public final class ItemCompare implements Layout {
  private final int row;
  private final int col;

  private Function<Player, ItemStack> before = p -> null;
  private Function<Player, ItemStack> after = p -> null;
  private GuiComponent arrow = new Label(GuiItems.named(Material.ARROW, Component.text("→")));

  private ItemStack beforePlaceholder = GuiItem.of(Material.GRAY_STAINED_GLASS_PANE).displayName(Component.text(" ")).build();
  private ItemStack afterPlaceholder = beforePlaceholder;

  public ItemCompare(int row, int col) {
    this.row = row;
    this.col = col;
  }

  public ItemCompare before(Function<Player, ItemStack> item) {
    this.before = Objects.requireNonNull(item, "item");
    return this;
  }

  public ItemCompare after(Function<Player, ItemStack> item) {
    this.after = Objects.requireNonNull(item, "item");
    return this;
  }

  public ItemCompare arrow(GuiComponent component) {
    this.arrow = Objects.requireNonNull(component, "component");
    return this;
  }

  public ItemCompare placeholders(ItemStack before, ItemStack after) {
    this.beforePlaceholder = Objects.requireNonNull(before, "before");
    this.afterPlaceholder = Objects.requireNonNull(after, "after");
    return this;
  }

  @Override
  public void apply(Window window, Placement placement) {
    Objects.requireNonNull(window, "window");
    Objects.requireNonNull(placement, "placement");

    ItemPreview beforePreview = new ItemPreview(before).placeholder(beforePlaceholder);
    ItemPreview afterPreview = new ItemPreview(after).placeholder(afterPlaceholder);

    place(window, placement, window.slotAt(row, col), beforePreview);
    place(window, placement, window.slotAt(row, col + 1), arrow);
    place(window, placement, window.slotAt(row, col + 2), afterPreview);
  }

  private static void place(Window window, Placement placement, int slot, GuiComponent component) {
    if (placement == Placement.FIXED) {
      window.setFixed(slot, component);
    } else {
      window.setDynamic(slot, component);
    }
  }
}

