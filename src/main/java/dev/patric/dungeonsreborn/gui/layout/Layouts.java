package dev.patric.dungeonsreborn.gui.layout;

import dev.patric.dungeonsreborn.gui.Window;

public final class Layouts {
  private Layouts() {
  }

  public static GridLayout grid(int topRow, int leftCol, int rows, int cols) {
    return new GridLayout(topRow, leftCol, rows, cols);
  }

  public static GridLayout row(int row, int leftCol, int cols) {
    return grid(row, leftCol, 1, cols);
  }

  public static GridLayout column(int topRow, int col, int rows) {
    return grid(topRow, col, rows, 1);
  }

  public static BorderLayout border(int topRow, int leftCol, int rows, int cols) {
    return new BorderLayout(topRow, leftCol, rows, cols);
  }

  /**
   * Convenience: creates a border layout around the full window (all rows, 9 columns).
   */
  public static BorderLayout border(Window window) {
    return border(0, 0, window.rows(), 9);
  }
}
