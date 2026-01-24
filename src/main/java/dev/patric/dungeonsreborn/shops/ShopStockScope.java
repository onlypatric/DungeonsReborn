package dev.patric.dungeonsreborn.shops;

public enum ShopStockScope {
  GLOBAL,
  TRADE,
  PLAYER;

  public static ShopStockScope parse(String raw, String path) {
    if (raw == null || raw.isBlank()) {
      return GLOBAL;
    }
    String normalized = raw.trim().toLowerCase();
    return switch (normalized) {
      case "global", "shop", "shared" -> GLOBAL;
      case "trade", "per_trade", "per-trade" -> TRADE;
      case "player", "per_player", "per-player" -> PLAYER;
      default -> throw new IllegalArgumentException(path + ": unknown stock scope " + raw);
    };
  }
}
