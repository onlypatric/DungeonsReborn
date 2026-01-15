package dev.patric.dungeonsreborn.mobs;

public record MobProgressionSpec(int minXp, int maxXp, int maxPlayerXp) {
  public int minAward() {
    return Math.max(0, minXp);
  }

  public int maxAward() {
    return Math.max(minAward(), maxXp);
  }

  public int maxPlayerCap() {
    return Math.max(0, maxPlayerXp);
  }
}
