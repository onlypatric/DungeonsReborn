package dev.patric.dungeonsreborn.effects.minions;

public record MinionSummonSpec(int waves, long waveIntervalTicks, MinionFormation formation,
                               double formationRadius, boolean safeSpawn, int maxSpawnAttempts) {
  public static final MinionSummonSpec DEFAULT =
      new MinionSummonSpec(1, 0L, MinionFormation.RANDOM, 0.0, false, 6);

  public MinionSummonSpec {
    if (waves <= 0) {
      throw new IllegalArgumentException("waves must be > 0");
    }
    if (waveIntervalTicks < 0L) {
      throw new IllegalArgumentException("waveIntervalTicks must be >= 0");
    }
    if (formation == null) {
      formation = MinionFormation.RANDOM;
    }
    if (!Double.isFinite(formationRadius) || formationRadius < 0.0) {
      throw new IllegalArgumentException("formationRadius must be >= 0");
    }
    if (maxSpawnAttempts <= 0) {
      throw new IllegalArgumentException("maxSpawnAttempts must be > 0");
    }
  }
}
