package dev.patric.dungeonsreborn.mobs;

import java.util.List;

public record TrialSpawnerProfile(
    List<TrialSpawnerMobEntry> mobPool,
    Integer waves,
    Integer simultaneous,
    Integer cooldownTicks,
    String keyLootPool) {
  public TrialSpawnerProfile {
    mobPool = mobPool == null ? List.of() : List.copyOf(mobPool);
    if (waves != null && waves < 1) {
      throw new IllegalArgumentException("waves must be >= 1");
    }
    if (simultaneous != null && simultaneous < 1) {
      throw new IllegalArgumentException("simultaneous must be >= 1");
    }
    if (cooldownTicks != null && cooldownTicks < 0) {
      throw new IllegalArgumentException("cooldownTicks must be >= 0");
    }
    if (keyLootPool != null) {
      keyLootPool = keyLootPool.trim();
      if (keyLootPool.isBlank()) {
        throw new IllegalArgumentException("keyLootPool must not be blank");
      }
    }
  }
}
