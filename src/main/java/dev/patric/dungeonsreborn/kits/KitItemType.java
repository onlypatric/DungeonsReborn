package dev.patric.dungeonsreborn.kits;

import java.util.Locale;

public enum KitItemType {
  ITEM_ID,
  MATERIAL,
  ITEMSTACK;

  public static KitItemType parse(String raw, String path) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException(path + ": type is required");
    }
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "itemid", "item_id", "item" -> ITEM_ID;
      case "material", "mat" -> MATERIAL;
      case "itemstack", "stack" -> ITEMSTACK;
      default -> throw new IllegalArgumentException(path + ": unknown item type " + raw);
    };
  }
}
