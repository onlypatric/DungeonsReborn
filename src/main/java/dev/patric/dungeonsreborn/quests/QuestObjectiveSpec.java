package dev.patric.dungeonsreborn.quests;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public record QuestObjectiveSpec(
    QuestObjectiveType type,
    String mobId,
    EntityType entityType,
    String itemId,
    Material material,
    QuestRegion region,
    String recipeId,
    int count
) {
  public static QuestObjectiveSpec killMob(String mobId, EntityType entityType, int count) {
    return new QuestObjectiveSpec(QuestObjectiveType.KILL_MOB, mobId, entityType, null, null, null, null, count);
  }

  public static QuestObjectiveSpec useItem(String itemId, Material material, int count) {
    return new QuestObjectiveSpec(QuestObjectiveType.USE_ITEM, null, null, itemId, material, null, null, count);
  }

  public static QuestObjectiveSpec visitRegion(QuestRegion region) {
    return new QuestObjectiveSpec(QuestObjectiveType.VISIT_REGION, null, null, null, null, region, null, 1);
  }

  public static QuestObjectiveSpec craftItem(String recipeId, String itemId, Material material, int count) {
    return new QuestObjectiveSpec(QuestObjectiveType.CRAFT_ITEM, null, null, itemId, material, null, recipeId, count);
  }
}
