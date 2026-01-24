package dev.patric.dungeonsreborn.crafting;

import java.util.List;
import java.util.Objects;

import dev.patric.dungeonsreborn.quests.QuestRegion;

public final class CraftingRequirementSpec {
  public enum Type {
    PERMISSION,
    LEVEL,
    CUSTOM_XP,
    QUEST,
    CLASS,
    REGION
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
  private final String message;

  private CraftingRequirementSpec(Type type,
                                  String permission,
                                  int minLevel,
                                  int minCustomLevel,
                                  long minCustomPoints,
                                  String questId,
                                  QuestStatus questStatus,
                                  List<String> classIds,
                                  List<QuestRegion> regions,
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
    this.message = message;
  }

  public static CraftingRequirementSpec permission(String permission, String message) {
    return new CraftingRequirementSpec(Type.PERMISSION, permission, 0, 0, 0L, null, null, null, null, message);
  }

  public static CraftingRequirementSpec level(int minLevel, String message) {
    return new CraftingRequirementSpec(Type.LEVEL, null, Math.max(0, minLevel), 0, 0L, null, null, null, null, message);
  }

  public static CraftingRequirementSpec customXp(int minLevel, long minPoints, String message) {
    return new CraftingRequirementSpec(Type.CUSTOM_XP, null, 0, Math.max(0, minLevel), Math.max(0L, minPoints), null,
        null, null, null, message);
  }

  public static CraftingRequirementSpec quest(String questId, QuestStatus status, String message) {
    return new CraftingRequirementSpec(Type.QUEST, null, 0, 0, 0L, questId, status, null, null, message);
  }

  public static CraftingRequirementSpec classes(List<String> classIds, String message) {
    return new CraftingRequirementSpec(Type.CLASS, null, 0, 0, 0L, null, null, classIds, null, message);
  }

  public static CraftingRequirementSpec region(List<QuestRegion> regions, String message) {
    return new CraftingRequirementSpec(Type.REGION, null, 0, 0, 0L, null, null, null, regions, message);
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

  public String message() {
    return message;
  }
}
