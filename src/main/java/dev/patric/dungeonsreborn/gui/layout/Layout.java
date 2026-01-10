package dev.patric.dungeonsreborn.gui.layout;

import dev.patric.dungeonsreborn.gui.Window;

public interface Layout {
  void apply(Window window, Placement placement);

  default void applyFixed(Window window) {
    apply(window, Placement.FIXED);
  }

  default void applyDynamic(Window window) {
    apply(window, Placement.DYNAMIC);
  }
}

