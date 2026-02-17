package dev.patric.dungeonsreborn.mobs;

import java.util.List;
import java.util.Objects;

public record TrialSpawnerSpec(
    String id,
    List<TrialSpawnerMobEntry> mobPool,
    int waves,
    int simultaneous,
    int cooldownTicks,
    int requiredPlayers,
    double activationRange,
    String keyLootPool,
    TrialSpawnerProfile ominousProfile) {
  public TrialSpawnerSpec {
    id = Objects.requireNonNull(id, "id").trim();
    if (id.isBlank()) {
      throw new IllegalArgumentException("id must be set");
    }
    mobPool = List.copyOf(Objects.requireNonNull(mobPool, "mobPool"));
    if (mobPool.isEmpty()) {
      throw new IllegalArgumentException("mobPool must have at least one entry");
    }
    if (waves < 1) {
      throw new IllegalArgumentException("waves must be >= 1");
    }
    if (simultaneous < 1) {
      throw new IllegalArgumentException("simultaneous must be >= 1");
    }
    if (cooldownTicks < 0) {
      throw new IllegalArgumentException("cooldownTicks must be >= 0");
    }
    if (requiredPlayers < 1) {
      throw new IllegalArgumentException("requiredPlayers must be >= 1");
    }
    if (!Double.isFinite(activationRange) || activationRange <= 0.0) {
      throw new IllegalArgumentException("activationRange must be > 0");
    }
    keyLootPool = Objects.requireNonNull(keyLootPool, "keyLootPool").trim();
    if (keyLootPool.isBlank()) {
      throw new IllegalArgumentException("keyLootPool must be set");
    }
  }
}
