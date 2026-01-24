package dev.patric.dungeonsreborn.mobs;

public record MobSpawnTimeWindow(int startTick, int endTick) {
  public boolean matches(long worldTime) {
    int time = (int) (worldTime % 24000L);
    if (startTick <= endTick) {
      return time >= startTick && time <= endTick;
    }
    return time >= startTick || time <= endTick;
  }
}
