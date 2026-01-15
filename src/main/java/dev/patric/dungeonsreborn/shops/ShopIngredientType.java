package dev.patric.dungeonsreborn.shops;

public enum ShopIngredientType {
  TOKEN,
  ITEM_ID,
  MATERIAL,
  ITEMSTACK;

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
      default -> throw new IllegalArgumentException(path + ": invalid ingredient type " + raw);
    };
  }
}
