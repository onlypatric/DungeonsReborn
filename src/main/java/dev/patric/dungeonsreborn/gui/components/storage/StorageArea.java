package dev.patric.dungeonsreborn.gui.components.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.layout.Layout;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.Window;

/**
 * Multi-slot storage area for GUIs.
 * <p>
 * This is a small helper that places multiple {@link StorageSlot}s into a window and stores their contents per-player.
 */
public final class StorageArea implements Layout {
  @FunctionalInterface
  public interface ChangeListener {
    void onChange(Player player, int index, ItemStack stack);
  }

  private final int topRow;
  private final int leftCol;
  private final int rows;
  private final int cols;

  private final StorageSlot[] slots;
  private final Map<UUID, ItemStack[]> contentsByPlayer = new HashMap<>();
  private ChangeListener onChange;

  public StorageArea(int topRow, int leftCol, int rows, int cols) {
    if (rows <= 0 || cols <= 0) {
      throw new IllegalArgumentException("rows/cols must be > 0");
    }
    this.topRow = topRow;
    this.leftCol = leftCol;
    this.rows = rows;
    this.cols = cols;
    this.slots = new StorageSlot[rows * cols];
    for (int i = 0; i < slots.length; i++) {
      slots[i] = new StorageSlot(this, i);
    }
  }

  public int rows() {
    return rows;
  }

  public int cols() {
    return cols;
  }

  public int size() {
    return slots.length;
  }

  public StorageArea onChange(ChangeListener listener) {
    this.onChange = Objects.requireNonNull(listener, "listener");
    return this;
  }

  public StorageSlot slot(int index) {
    if (index < 0 || index >= slots.length) {
      throw new IllegalArgumentException("slot index out of bounds: " + index);
    }
    return slots[index];
  }

  public StorageSlot slot(int row, int col) {
    return slot(localIndex(row, col));
  }

  public ItemStack get(Player player, int index) {
    Objects.requireNonNull(player, "player");
    ItemStack[] contents = contentsByPlayer.get(player.getUniqueId());
    if (contents == null) {
      return null;
    }
    ItemStack stack = contents[index];
    return stack == null ? null : stack.clone();
  }

  public void set(Player player, int index, ItemStack stack) {
    Objects.requireNonNull(player, "player");
    ItemStack[] contents = contentsByPlayer.computeIfAbsent(player.getUniqueId(), id -> new ItemStack[slots.length]);
    contents[index] = stack == null ? null : stack.clone();
  }

  public void clear(Player player) {
    Objects.requireNonNull(player, "player");
    contentsByPlayer.remove(player.getUniqueId());
  }

  public ItemStack[] contents(Player player) {
    Objects.requireNonNull(player, "player");
    ItemStack[] raw = contentsByPlayer.get(player.getUniqueId());
    if (raw == null) {
      return new ItemStack[slots.length];
    }
    ItemStack[] copy = new ItemStack[raw.length];
    for (int i = 0; i < raw.length; i++) {
      copy[i] = raw[i] == null ? null : raw[i].clone();
    }
    return copy;
  }

  @Override
  public void apply(Window window, Placement placement) {
    Objects.requireNonNull(window, "window");
    Objects.requireNonNull(placement, "placement");
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        int idx = r * cols + c;
        int slot = window.slotAt(topRow + r, leftCol + c);
        if (placement == Placement.FIXED) {
          window.setFixed(slot, slots[idx]);
        } else {
          window.setDynamic(slot, slots[idx]);
        }
      }
    }
  }

  void commit(Player player, int index, ItemStack stack) {
    if (onChange != null) {
      onChange.onChange(player, index, stack == null ? null : stack.clone());
    }
  }

  ItemStack[] mutableContents(UUID playerId) {
    return contentsByPlayer.computeIfAbsent(playerId, id -> new ItemStack[slots.length]);
  }

  private int localIndex(int row, int col) {
    if (row < 0 || row >= rows || col < 0 || col >= cols) {
      throw new IllegalArgumentException("grid cell out of bounds: (" + row + "," + col + "), size=" + rows + "x" + cols);
    }
    return row * cols + col;
  }

  @Override
  public String toString() {
    return "StorageArea[" + topRow + "," + leftCol + " " + rows + "x" + cols + ", slots=" + slots.length + "]";
  }
}
