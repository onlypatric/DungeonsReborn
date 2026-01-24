package dev.patric.dungeonsreborn.crafting;

import java.util.Objects;

public record CraftingGridSpec(int width, int height, boolean allowMirror, boolean allowRotate) {
  public CraftingGridSpec {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("grid width/height must be > 0");
    }
    Objects.checkIndex(width - 1, Integer.MAX_VALUE);
    Objects.checkIndex(height - 1, Integer.MAX_VALUE);
  }
}
