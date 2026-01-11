package dev.patric.dungeonsreborn.crafting;

import java.util.Locale;

import org.bukkit.Material;

public enum CraftingItemCategory {
  ANY,
  WEAPON,
  RANGED,
  TOOL,
  ARMOR,
  FOOD,
  BLOCK,
  POTION;

  public static CraftingItemCategory parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return ANY;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    return switch (normalized) {
      case "any" -> ANY;
      case "weapon", "weapons", "melee" -> WEAPON;
      case "ranged", "bow" -> RANGED;
      case "tool", "tools" -> TOOL;
      case "armor", "armour" -> ARMOR;
      case "food", "edible" -> FOOD;
      case "block", "blocks" -> BLOCK;
      case "potion", "potions" -> POTION;
      default -> ANY;
    };
  }

  public boolean matches(Material material) {
    if (material == null) {
      return false;
    }
    if (this == ANY) {
      return true;
    }
    String name = material.name();
    return switch (this) {
      case WEAPON -> name.endsWith("_SWORD")
          || name.endsWith("_AXE")
          || name.equals("TRIDENT")
          || name.equals("MACE");
      case RANGED -> name.equals("BOW")
          || name.equals("CROSSBOW")
          || name.equals("TRIDENT");
      case TOOL -> name.endsWith("_PICKAXE")
          || name.endsWith("_AXE")
          || name.endsWith("_SHOVEL")
          || name.endsWith("_HOE")
          || name.equals("SHEARS")
          || name.equals("FISHING_ROD");
      case ARMOR -> name.endsWith("_HELMET")
          || name.endsWith("_CHESTPLATE")
          || name.endsWith("_LEGGINGS")
          || name.endsWith("_BOOTS");
      case FOOD -> material.isEdible();
      case BLOCK -> material.isBlock();
      case POTION -> material == Material.POTION
          || material == Material.SPLASH_POTION
          || material == Material.LINGERING_POTION;
      case ANY -> true;
    };
  }
}
