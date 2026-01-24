package dev.patric.dungeonsreborn.shops;

public record ShopStockSpec(int min, int max, long restockSeconds, ShopStockScope scope) {
  public ShopStockSpec {
    if (restockSeconds < 0) {
      throw new IllegalArgumentException("restockSeconds must be >= 0");
    }
    if (min < 0 || max < 0) {
      throw new IllegalArgumentException("stock min/max must be >= 0");
    }
    if (max > 0 && min > max) {
      throw new IllegalArgumentException("stock min cannot exceed max");
    }
    if (scope == null) {
      scope = ShopStockScope.GLOBAL;
    }
  }

  public ShopStockSpec(int min, int max, long restockSeconds) {
    this(min, max, restockSeconds, ShopStockScope.GLOBAL);
  }

  public boolean enabled() {
    return max > 0;
  }
}
