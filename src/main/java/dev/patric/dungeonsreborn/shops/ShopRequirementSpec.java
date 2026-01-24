package dev.patric.dungeonsreborn.shops;

import java.util.List;
import java.util.Objects;

import dev.patric.dungeonsreborn.quests.QuestRegion;

public final class ShopRequirementSpec {
  public enum Type {
    PERMISSION,
    LEVEL,
    CUSTOM_XP,
    QUEST,
    CLASS,
    REGION,
    FACTION
  }

  public enum QuestStatus {
    ACTIVE,
    AVAILABLE,
    COMPLETED,
    COOLDOWN,
    LOCKED
  }

  private final Type type;
  private final String permission;
  private final int minLevel;
  private final int minCustomLevel;
  private final long minCustomPoints;
  private final String questId;
  private final QuestStatus questStatus;
  private final List<String> classIds;
  private final List<QuestRegion> regions;
  private final String factionId;
  private final int minFactionRank;
  private final String message;

  private ShopRequirementSpec(Type type,
                              String permission,
                              int minLevel,
                              int minCustomLevel,
                              long minCustomPoints,
                              String questId,
                              QuestStatus questStatus,
                              List<String> classIds,
                              List<QuestRegion> regions,
                              String factionId,
                              int minFactionRank,
                              String message) {
    this.type = Objects.requireNonNull(type, "type");
    this.permission = permission;
    this.minLevel = minLevel;
    this.minCustomLevel = minCustomLevel;
    this.minCustomPoints = minCustomPoints;
    this.questId = questId;
    this.questStatus = questStatus;
    this.classIds = classIds == null ? List.of() : List.copyOf(classIds);
    this.regions = regions == null ? List.of() : List.copyOf(regions);
    this.factionId = factionId;
    this.minFactionRank = Math.max(0, minFactionRank);
    this.message = message;
  }

  public static ShopRequirementSpec permission(String permission, String message) {
    return new ShopRequirementSpec(Type.PERMISSION, permission, 0, 0, 0L, null, null, null, null, null, 0, message);
  }

  public static ShopRequirementSpec level(int minLevel, String message) {
    return new ShopRequirementSpec(Type.LEVEL, null, Math.max(0, minLevel), 0, 0L, null, null, null, null, null, 0, message);
  }

  public static ShopRequirementSpec customXp(int minLevel, long minPoints, String message) {
    return new ShopRequirementSpec(Type.CUSTOM_XP, null, 0, Math.max(0, minLevel), Math.max(0L, minPoints), null,
        null, null, null, null, 0, message);
  }

  public static ShopRequirementSpec quest(String questId, QuestStatus status, String message) {
    return new ShopRequirementSpec(Type.QUEST, null, 0, 0, 0L, questId, status, null, null, null, 0, message);
  }

  public static ShopRequirementSpec classes(List<String> classIds, String message) {
    return new ShopRequirementSpec(Type.CLASS, null, 0, 0, 0L, null, null, classIds, null, null, 0, message);
  }

  public static ShopRequirementSpec region(List<QuestRegion> regions, String message) {
    return new ShopRequirementSpec(Type.REGION, null, 0, 0, 0L, null, null, null, regions, null, 0, message);
  }

  public static ShopRequirementSpec faction(String factionId, int minRank, String message) {
    return new ShopRequirementSpec(Type.FACTION, null, 0, 0, 0L, null, null, null, null, factionId, minRank, message);
  }

  public Type type() {
    return type;
  }

  public String permission() {
    return permission;
  }

  public int minLevel() {
    return minLevel;
  }

  public int minCustomLevel() {
    return minCustomLevel;
  }

  public long minCustomPoints() {
    return minCustomPoints;
  }

  public String questId() {
    return questId;
  }

  public QuestStatus questStatus() {
    return questStatus;
  }

  public List<String> classIds() {
    return classIds;
  }

  public List<QuestRegion> regions() {
    return regions;
  }

  public String factionId() {
    return factionId;
  }

  public int minFactionRank() {
    return minFactionRank;
  }

  public String message() {
    return message;
  }
}
