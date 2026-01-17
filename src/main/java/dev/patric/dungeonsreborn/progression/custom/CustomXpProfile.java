package dev.patric.dungeonsreborn.progression.custom;

import java.util.UUID;

public final class CustomXpProfile {
  private final UUID uuid;
  private long points;
  private int level;
  private long lastUpdateMillis;
  private boolean dirty;

  public CustomXpProfile(UUID uuid, long points, int level, long lastUpdateMillis) {
    if (uuid == null) {
      throw new IllegalArgumentException("uuid is required");
    }
    this.uuid = uuid;
    this.points = Math.max(0L, points);
    this.level = Math.max(1, level);
    this.lastUpdateMillis = lastUpdateMillis;
    this.dirty = false;
  }

  public static CustomXpProfile createDefault(UUID uuid) {
    return new CustomXpProfile(uuid, 0L, 1, System.currentTimeMillis());
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
