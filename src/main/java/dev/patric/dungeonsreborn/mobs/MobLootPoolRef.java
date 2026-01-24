package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

public record MobLootPoolRef(String poolId, Integer rolls, Integer bonusRolls, Double luckMultiplier,
    double chance, Boolean deterministic, Long seedSalt, MobDropConditions conditions) {
  public MobLootPoolRef {
    if (poolId == null || poolId.isBlank()) {
      throw new IllegalArgumentException("poolId must be set");
    }
    if (rolls != null && rolls < 0) {
      throw new IllegalArgumentException("rolls must be >= 0");
    }
    if (bonusRolls != null && bonusRolls < 0) {
      throw new IllegalArgumentException("bonusRolls must be >= 0");
    }
    if (luckMultiplier != null && (!Double.isFinite(luckMultiplier) || luckMultiplier < 0.0)) {
      throw new IllegalArgumentException("luckMultiplier must be >= 0");
    }
    if (!(chance >= 0.0 && chance <= 1.0)) {
      throw new IllegalArgumentException("chance must be in [0,1]");
    }
    if (seedSalt != null && seedSalt < 0L) {
      throw new IllegalArgumentException("seedSalt must be >= 0");
    }
    if (conditions == null) {
      conditions = MobDropConditions.none();
    }
    poolId = Objects.requireNonNull(poolId, "poolId").trim();
  }
}
