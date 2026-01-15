package dev.patric.dungeonsreborn.gui.components;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiComponent;
import dev.patric.dungeonsreborn.gui.Window;

public final class StatusRow {
  private final int row;
  private final Map<Integer, GuiComponent> columns = new HashMap<>();
  private ItemStack filler;

  public StatusRow(int row) {
    if (row < 0) {
      throw new IllegalArgumentException("row must be >= 0");
    }
    this.row = row;
  }

  public StatusRow column(int col, GuiComponent component) {
    if (col < 0 || col >= 9) {
      throw new IllegalArgumentException("column out of bounds: " + col);
    }
    columns.put(col, Objects.requireNonNull(component, "component"));
    return this;
  }

  public StatusRow filler(ItemStack item) {
    this.filler = Objects.requireNonNull(item, "item");
    return this;
  }

  public void apply(Window window) {
    Objects.requireNonNull(window, "window");
    for (Map.Entry<Integer, GuiComponent> entry : columns.entrySet()) {
      window.setFixedAt(row, entry.getKey(), entry.getValue());
    }
    if (filler != null) {
      for (int col = 0; col < 9; col++) {
        if (columns.containsKey(col)) {
          continue;
        }
        window.setFixedAt(row, col, new Label(p -> filler));
      }
    }
  }
}
