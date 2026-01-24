package dev.patric.dungeonsreborn.crafting;

import java.util.Locale;

public enum CraftingMatchType {
  ITEM_ID,
  UPGRADE_ID,
  TAG,
  MATERIAL,
  CATEGORY,
  ANY;

  public static CraftingMatchType parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return ANY;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    return switch (normalized) {
      case "item_id", "item", "id", "custom_item" -> ITEM_ID;
      case "upgrade_id", "upgrade" -> UPGRADE_ID;
      case "tag", "pdc", "marker" -> TAG;
      case "material", "mat" -> MATERIAL;
      case "category", "cat" -> CATEGORY;
      case "any", "any_item" -> ANY;
      default -> ANY;
    };
  }
}
