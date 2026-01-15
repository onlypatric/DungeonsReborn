package dev.patric.dungeonsreborn.progression;

import java.util.UUID;

public final class PlayerProgression {
  private final UUID uuid;
  private long points;
  private int level;
  private int skillPoints;
  private int skillTreePoints;
  private int maxMana;
  private int strength;
  private int dexterity;
  private int intelligence;
  private int vitality;
  private long lastUpdateMillis;
  private boolean dirty;

  public PlayerProgression(UUID uuid, long points, int level, int skillPoints, int maxMana, long lastUpdateMillis,
      int strength, int dexterity, int intelligence, int vitality, int skillTreePoints) {
    if (uuid == null) {
      throw new IllegalArgumentException("uuid is required");
    }
    this.uuid = uuid;
    this.points = points;
    this.level = level;
    this.skillPoints = skillPoints;
    this.maxMana = maxMana;
    this.strength = Math.max(0, strength);
    this.dexterity = Math.max(0, dexterity);
    this.intelligence = Math.max(0, intelligence);
    this.vitality = Math.max(0, vitality);
    this.skillTreePoints = Math.max(0, skillTreePoints);
    this.lastUpdateMillis = lastUpdateMillis;
    this.dirty = false;
  }

  public static PlayerProgression createDefault(UUID uuid) {
    return new PlayerProgression(uuid, 0L, 1, 0, 100, System.currentTimeMillis(), 0, 0, 0, 0, 0);
  }

  public UUID uuid() {
    return uuid;
  }

  public long points() {
    return points;
  }

  public void points(long points) {
    this.points = Math.max(0L, points);
    touch();
  }

  public int level() {
    return level;
  }

  public void level(int level) {
    this.level = Math.max(1, level);
    touch();
  }

  public int skillPoints() {
    return skillPoints;
  }

  public void skillPoints(int skillPoints) {
    this.skillPoints = Math.max(0, skillPoints);
    touch();
  }

  public int skillTreePoints() {
    return skillTreePoints;
  }

  public void skillTreePoints(int skillTreePoints) {
    this.skillTreePoints = Math.max(0, skillTreePoints);
    touch();
  }

  public int maxMana() {
    return maxMana;
  }

  public void maxMana(int maxMana) {
    this.maxMana = Math.max(0, maxMana);
    touch();
  }

  public int strength() {
    return strength;
  }

  public void strength(int strength) {
    this.strength = Math.max(0, strength);
    touch();
  }

  public int dexterity() {
    return dexterity;
  }

  public void dexterity(int dexterity) {
    this.dexterity = Math.max(0, dexterity);
    touch();
  }

  public int intelligence() {
    return intelligence;
  }

  public void intelligence(int intelligence) {
    this.intelligence = Math.max(0, intelligence);
    touch();
  }

  public int vitality() {
    return vitality;
  }

  public void vitality(int vitality) {
    this.vitality = Math.max(0, vitality);
    touch();
  }

  public int allocatedSkillPoints() {
    return strength + dexterity + intelligence + vitality;
  }

  public long lastUpdateMillis() {
    return lastUpdateMillis;
  }

  public boolean dirty() {
    return dirty;
  }

  public void markClean() {
    this.dirty = false;
  }

  private void touch() {
    this.lastUpdateMillis = System.currentTimeMillis();
    this.dirty = true;
  }
}
