package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

public record TrialSpawnerMobEntry(String mobId, double weight) {
  public TrialSpawnerMobEntry {
    mobId = Objects.requireNonNull(mobId, "mobId").trim();
    if (mobId.isBlank()) {
      throw new IllegalArgumentException("mobId must be set");
    }
    if (!Double.isFinite(weight) || weight <= 0.0) {
      throw new IllegalArgumentException("weight must be > 0");
    }
  }
}
