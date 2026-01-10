package dev.patric.dungeonsreborn.gui.layout;

import java.util.Objects;

import dev.patric.dungeonsreborn.gui.GuiComponent;
import dev.patric.dungeonsreborn.gui.Window;

public final class BorderLayout implements Layout {
  private final int topRow;
  private final int leftCol;
  private final int rows;
  private final int cols;

  private GuiComponent top;
  private GuiComponent bottom;
  private GuiComponent left;
  private GuiComponent right;
  private GuiComponent corners;
  private GuiComponent centerFill;

  private GridLayout center;

  public BorderLayout(int topRow, int leftCol, int rows, int cols) {
    if (rows < 2 || cols < 2) {
      throw new IllegalArgumentException("border layout needs at least 2x2");
    }
    this.topRow = topRow;
    this.leftCol = leftCol;
    this.rows = rows;
    this.cols = cols;
  }

  public BorderLayout all(GuiComponent component) {
    return edges(component).corners(component);
  }

  public BorderLayout edges(GuiComponent component) {
    Objects.requireNonNull(component, "component");
    this.top = component;
    this.bottom = component;
    this.left = component;
    this.right = component;
    return this;
  }

  public BorderLayout top(GuiComponent component) {
    this.top = Objects.requireNonNull(component, "component");
    return this;
  }

  public BorderLayout bottom(GuiComponent component) {
    this.bottom = Objects.requireNonNull(component, "component");
    return this;
  }

  public BorderLayout left(GuiComponent component) {
    this.left = Objects.requireNonNull(component, "component");
    return this;
  }

  public BorderLayout right(GuiComponent component) {
    this.right = Objects.requireNonNull(component, "component");
    return this;
  }

  public BorderLayout corners(GuiComponent component) {
    this.corners = Objects.requireNonNull(component, "component");
    return this;
  }

  public BorderLayout fillCenter(GuiComponent component) {
    this.centerFill = Objects.requireNonNull(component, "component");
    return this;
  }

  /**
   * Returns a grid for the center area (rows-2 x cols-2) using local coordinates.
   * <p>
   * It will be applied after the border.
   */
  public GridLayout center() {
    if (center == null) {
      center = new GridLayout(topRow + 1, leftCol + 1, rows - 2, cols - 2);
    }
    return center;
  }

  @Override
  public void apply(Window window, Placement placement) {
    Objects.requireNonNull(window, "window");
    Objects.requireNonNull(placement, "placement");

    for (int c = 0; c < cols; c++) {
      placeIfPresent(window, placement, topRow, leftCol + c, edgeOrCorner(c == 0 || c == cols - 1, top));
      placeIfPresent(window, placement, topRow + rows - 1, leftCol + c, edgeOrCorner(c == 0 || c == cols - 1, bottom));
    }

    for (int r = 1; r < rows - 1; r++) {
      placeIfPresent(window, placement, topRow + r, leftCol, edgeOrCorner(true, left));
      placeIfPresent(window, placement, topRow + r, leftCol + cols - 1, edgeOrCorner(true, right));
    }

    if (centerFill != null) {
      for (int r = 1; r < rows - 1; r++) {
        for (int c = 1; c < cols - 1; c++) {
          placeIfPresent(window, placement, topRow + r, leftCol + c, centerFill);
        }
      }
    }

    if (center != null) {
      center.apply(window, placement);
    }
  }

  private GuiComponent edgeOrCorner(boolean isCorner, GuiComponent edge) {
    if (!isCorner) {
      return edge;
    }
    return corners != null ? corners : edge;
  }

  private static void placeIfPresent(Window window, Placement placement, int row, int col, GuiComponent component) {
    if (component == null) {
      return;
    }
    int slot = window.slotAt(row, col);
    if (placement == Placement.FIXED) {
      window.setFixed(slot, component);
    } else {
      window.setDynamic(slot, component);
    }
  }
}

