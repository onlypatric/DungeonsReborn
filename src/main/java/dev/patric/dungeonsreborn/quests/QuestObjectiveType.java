package dev.patric.dungeonsreborn.quests;

public enum QuestObjectiveType {
  KILL_MOB,
  USE_ITEM,
  VISIT_REGION,
  CRAFT_ITEM,
  BREAK_BLOCK,
  PLACE_BLOCK;

  public static QuestObjectiveType parse(String raw) {
    if (raw == null) {
      return null;
    }
    String normalized = raw.trim().toLowerCase();
    return switch (normalized) {
      case "kill_mob", "kill", "mob" -> KILL_MOB;
      case "use_item", "use", "item" -> USE_ITEM;
      case "visit_region", "visit", "region" -> VISIT_REGION;
      case "craft_item", "craft", "recipe" -> CRAFT_ITEM;
      case "break_block", "break", "block_break" -> BREAK_BLOCK;
      case "place_block", "place", "block_place" -> PLACE_BLOCK;
      default -> null;
    };
  }
}
