package dev.patric.dungeonsreborn.quests;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public record QuestObjectiveSpec(
    QuestObjectiveType type,
    String mobId,
    EntityType entityType,
    String mobTier,
    String mobPhase,
    String mobVariant,
    String mobTrait,
    List<String> mobTags,
    String itemId,
    Material material,
    List<String> itemTags,
    Map<String, String> itemPdc,
    List<String> loreContains,
    Integer customModelData,
    QuestRegion region,
    List<String> worlds,
    List<String> biomes,
    List<String> structures,
    String recipeId,
    int count,
    QuestPartyRole partyRole,
    String groupId,
    QuestCompositeMode groupMode,
    int order,
    boolean optional,
    int stage,
    long timeLimitSeconds,
    QuestObjectiveShareSpec share
) {
  public QuestObjectiveSpec {
    if (groupMode == null) {
      groupMode = QuestCompositeMode.NONE;
    }
    if (count < 1) {
      count = 1;
    }
    if (stage < 0) {
      stage = 0;
    }
    if (timeLimitSeconds < 0) {
      timeLimitSeconds = 0L;
    }
    if (mobId != null && mobId.isBlank()) {
      mobId = null;
    }
    if (mobTier != null && mobTier.isBlank()) {
      mobTier = null;
    }
    if (mobPhase != null && mobPhase.isBlank()) {
      mobPhase = null;
    }
    if (mobVariant != null && mobVariant.isBlank()) {
      mobVariant = null;
    }
    if (mobTrait != null && mobTrait.isBlank()) {
      mobTrait = null;
    }
    mobTags = normalizeList(mobTags);
    if (itemId != null && itemId.isBlank()) {
      itemId = null;
    }
    itemTags = normalizeList(itemTags);
    loreContains = normalizeList(loreContains);
    worlds = normalizeList(worlds);
    biomes = normalizeList(biomes);
    structures = normalizeList(structures);
    if (itemPdc == null) {
      itemPdc = Map.of();
    } else if (itemPdc.isEmpty()) {
      itemPdc = Map.of();
    } else {
      Map<String, String> out = new LinkedHashMap<>();
      for (var entry : itemPdc.entrySet()) {
        if (entry.getKey() == null) {
          continue;
        }
        String key = entry.getKey().trim();
        if (key.isEmpty()) {
          continue;
        }
        String value = entry.getValue();
        out.put(key, value == null ? "" : value);
      }
      itemPdc = Collections.unmodifiableMap(out);
    }
    if (partyRole == null) {
      partyRole = QuestPartyRole.ANY;
    }
    if (share == null) {
      share = QuestObjectiveShareSpec.none();
    }
  }

  public static QuestObjectiveSpec killMob(String mobId, EntityType entityType, int count) {
    return new QuestObjectiveSpec(QuestObjectiveType.KILL_MOB, mobId, entityType, null, null, null, null, List.of(),
        null, null, List.of(), Map.of(), List.of(), null, null, List.of(), List.of(), List.of(), null, count,
        QuestPartyRole.ANY, null, QuestCompositeMode.NONE, 0, false, 0, 0L, QuestObjectiveShareSpec.none());
  }

  public static QuestObjectiveSpec useItem(String itemId, Material material, int count) {
    return new QuestObjectiveSpec(QuestObjectiveType.USE_ITEM, null, null, null, null, null, null, List.of(),
        itemId, material, List.of(), Map.of(), List.of(), null, null, List.of(), List.of(), List.of(), null, count,
        QuestPartyRole.ANY, null, QuestCompositeMode.NONE, 0, false, 0, 0L, QuestObjectiveShareSpec.none());
  }

  public static QuestObjectiveSpec visitRegion(QuestRegion region) {
    return new QuestObjectiveSpec(QuestObjectiveType.VISIT_REGION, null, null, null, null, null, null, List.of(),
        null, null, List.of(), Map.of(), List.of(), null, region, List.of(), List.of(), List.of(), null, 1,
        QuestPartyRole.ANY, null, QuestCompositeMode.NONE, 0, false, 0, 0L, QuestObjectiveShareSpec.none());
  }

  public static QuestObjectiveSpec craftItem(String recipeId, String itemId, Material material, int count) {
    return new QuestObjectiveSpec(QuestObjectiveType.CRAFT_ITEM, null, null, null, null, null, null, List.of(),
        itemId, material, List.of(), Map.of(), List.of(), null, null, List.of(), List.of(), List.of(), recipeId, count,
        QuestPartyRole.ANY, null, QuestCompositeMode.NONE, 0, false, 0, 0L, QuestObjectiveShareSpec.none());
  }

  public static QuestObjectiveSpec breakBlock(Material material, int count) {
    return new QuestObjectiveSpec(QuestObjectiveType.BREAK_BLOCK, null, null, null, null, null, null, List.of(),
        null, material, List.of(), Map.of(), List.of(), null, null, List.of(), List.of(), List.of(), null, count,
        QuestPartyRole.ANY, null, QuestCompositeMode.NONE, 0, false, 0, 0L, QuestObjectiveShareSpec.none());
  }

  public static QuestObjectiveSpec placeBlock(Material material, int count) {
    return new QuestObjectiveSpec(QuestObjectiveType.PLACE_BLOCK, null, null, null, null, null, null, List.of(),
        null, material, List.of(), Map.of(), List.of(), null, null, List.of(), List.of(), List.of(), null, count,
        QuestPartyRole.ANY, null, QuestCompositeMode.NONE, 0, false, 0, 0L, QuestObjectiveShareSpec.none());
  }

  public QuestObjectiveSpec withMeta(String groupId, QuestCompositeMode mode, int order, boolean optional,
      int stage, long timeLimitSeconds) {
    return new QuestObjectiveSpec(type, mobId, entityType, mobTier, mobPhase, mobVariant, mobTrait, mobTags,
        itemId, material, itemTags, itemPdc, loreContains, customModelData, region, worlds, biomes, structures,
        recipeId, count,
        partyRole, groupId, mode, order, optional, stage, timeLimitSeconds, share);
  }

  public QuestObjectiveSpec withPartyRole(QuestPartyRole role) {
    return new QuestObjectiveSpec(type, mobId, entityType, mobTier, mobPhase, mobVariant, mobTrait, mobTags,
        itemId, material, itemTags, itemPdc, loreContains, customModelData, region, worlds, biomes, structures,
        recipeId, count,
        role, groupId, groupMode, order, optional, stage, timeLimitSeconds, share);
  }

  public QuestObjectiveSpec withShare(QuestObjectiveShareSpec override) {
    return new QuestObjectiveSpec(type, mobId, entityType, mobTier, mobPhase, mobVariant, mobTrait, mobTags,
        itemId, material, itemTags, itemPdc, loreContains, customModelData, region, worlds, biomes, structures,
        recipeId, count,
        partyRole, groupId, groupMode, order, optional, stage, timeLimitSeconds, override);
  }

  private static List<String> normalizeList(List<String> list) {
    if (list == null || list.isEmpty()) {
      return List.of();
    }
    java.util.ArrayList<String> out = new java.util.ArrayList<>();
    for (String entry : list) {
      if (entry == null) {
        continue;
      }
      String value = entry.trim();
      if (value.isEmpty()) {
        continue;
      }
      out.add(value.toLowerCase(Locale.ROOT));
    }
    return out.isEmpty() ? List.of() : Collections.unmodifiableList(out);
  }
}
