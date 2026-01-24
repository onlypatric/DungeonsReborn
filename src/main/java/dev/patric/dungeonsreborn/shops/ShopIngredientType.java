package dev.patric.dungeonsreborn.shops;

public enum ShopIngredientType {
  TOKEN,
  ITEM_ID,
  MATERIAL,
  ITEMSTACK,
  TAG,
  CATEGORY,
  MATCHER,
  CURRENCY,
  XP,
  CUSTOM_XP;

  public static ShopIngredientType parse(String raw, String path) {
    if (raw == null) {
      throw new IllegalArgumentException(path + ": missing type");
    }
    String key = raw.trim().toLowerCase();
    return switch (key) {
      case "token" -> TOKEN;
      case "item", "itemstack", "stack" -> ITEMSTACK;
      case "itemid", "item_id", "id" -> ITEM_ID;
      case "material", "mat" -> MATERIAL;
      case "tag", "tags" -> TAG;
      case "category", "cat" -> CATEGORY;
      case "matcher", "match" -> MATCHER;
      case "currency", "currencies", "money" -> CURRENCY;
      case "xp", "exp", "experience" -> XP;
      case "custom_xp", "custom-xp", "customxp", "cxp" -> CUSTOM_XP;
      default -> throw new IllegalArgumentException(path + ": invalid ingredient type " + raw);
    };
  }
}
