package dev.patric.dungeonsreborn.gui.layout;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import dev.patric.dungeonsreborn.gui.GuiComponent;
import dev.patric.dungeonsreborn.gui.Window;

public class GridLayout implements Layout {
  public record Pos(int row, int col) {
    public Pos {
      if (row < 0 || col < 0) {
        throw new IllegalArgumentException("row/col must be >= 0");
      }
    }
  }

  private final int topRow;
  private final int leftCol;
  private final int rows;
  private final int cols;

  private final Map<Integer, GuiComponent> cells = new HashMap<>();
  private GuiComponent fill;
  private Function<Pos, GuiComponent> fillFactory;

  public GridLayout(int topRow, int leftCol, int rows, int cols) {
    if (rows <= 0 || cols <= 0) {
      throw new IllegalArgumentException("rows/cols must be > 0");
    }
    this.topRow = topRow;
    this.leftCol = leftCol;
    this.rows = rows;
    this.cols = cols;
  }

  public final int rows() {
    return rows;
  }

  public final int cols() {
    return cols;
  }

  public GridLayout set(int row, int col, GuiComponent component) {
    Objects.requireNonNull(component, "component");
    int idx = localIndex(row, col);
    cells.put(idx, component);
    return this;
  }

  public GridLayout set(int row, int col, Function<Pos, GuiComponent> factory) {
    Objects.requireNonNull(factory, "factory");
    Pos pos = new Pos(row, col);
    return set(row, col, Objects.requireNonNull(factory.apply(pos), "factory result"));
  }

  public GridLayout fill(GuiComponent component) {
    this.fill = Objects.requireNonNull(component, "component");
    this.fillFactory = null;
    return this;
  }

  public GridLayout fill(Function<Pos, GuiComponent> factory) {
    this.fillFactory = Objects.requireNonNull(factory, "factory");
    this.fill = null;
    return this;
  }

  public GridLayout clearFill() {
    this.fill = null;
    this.fillFactory = null;
    return this;
  }

  @Override
  public void apply(Window window, Placement placement) {
    Objects.requireNonNull(window, "window");
    Objects.requireNonNull(placement, "placement");
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        int idx = r * cols + c;
        GuiComponent component = cells.get(idx);
        if (component == null) {
          if (fillFactory != null) {
            component = fillFactory.apply(new Pos(r, c));
          } else {
            component = fill;
          }
        }
        if (component == null) {
          continue;
        }
        int slot = window.slotAt(topRow + r, leftCol + c);
        place(window, placement, slot, component);
      }
    }
  }

  private static void place(Window window, Placement placement, int slot, GuiComponent component) {
    if (placement == Placement.FIXED) {
      window.setFixed(slot, component);
    } else {
      window.setDynamic(slot, component);
    }
  }

  private int localIndex(int row, int col) {
    if (row < 0 || row >= rows || col < 0 || col >= cols) {
      throw new IllegalArgumentException("grid cell out of bounds: (" + row + "," + col + "), size=" + rows + "x" + cols);
    }
    return row * cols + col;
  }
}

