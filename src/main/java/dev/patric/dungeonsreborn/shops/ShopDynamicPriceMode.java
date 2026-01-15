package dev.patric.dungeonsreborn.shops;

public enum ShopDynamicPriceMode {
  STOCK,
  TIME;

  public static ShopDynamicPriceMode parse(String raw, String path) {
    if (raw == null || raw.isBlank()) {
      return STOCK;
    }
    String normalized = raw.trim().toLowerCase();
    return switch (normalized) {
      case "stock", "stocks" -> STOCK;
      case "time", "hourly", "daily" -> TIME;
      default -> throw new IllegalArgumentException(path + ": unknown dynamic price mode " + raw);
    };
  }
}
