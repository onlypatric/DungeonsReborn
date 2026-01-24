package dev.patric.dungeonsreborn.quests;

import java.util.Locale;

public enum QuestRewardTag {
  ANY,
  XP,
  TOKENS,
  COMPRESSED,
  PALLET,
  MANA,
  RESOURCES,
  ITEMS,
  ENTRIES,
  POOLS;

  public static QuestRewardTag parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return ANY;
    }
    String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    return switch (key) {
      case "XP", "EXPERIENCE" -> XP;
      case "TOKENS", "TOKEN" -> TOKENS;
      case "COMPRESSED" -> COMPRESSED;
      case "PALLET", "PALLETS" -> PALLET;
      case "MANA" -> MANA;
      case "RESOURCES", "RESOURCE" -> RESOURCES;
      case "ITEMS", "ITEM" -> ITEMS;
      case "ENTRIES", "ENTRY" -> ENTRIES;
      case "POOLS", "POOL" -> POOLS;
      default -> ANY;
    };
  }
}
