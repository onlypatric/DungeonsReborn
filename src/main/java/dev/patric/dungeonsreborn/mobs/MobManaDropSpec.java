package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;
import java.util.Random;

public record MobManaDropSpec(MobManaRange killer, MobManaRange nearby, double nearbyRadius) {
  public MobManaDropSpec {
    if (nearbyRadius < 0.0 || !Double.isFinite(nearbyRadius)) {
      throw new IllegalArgumentException("nearbyRadius must be >= 0");
    }
  }

  public boolean isEmpty() {
    return (killer == null || killer.isEmpty()) && (nearby == null || nearby.isEmpty());
  }

  public record MobManaRange(double min, double max) {
    public MobManaRange {
      if (!Double.isFinite(min) || !Double.isFinite(max)) {
        throw new IllegalArgumentException("mana drop range must be finite");
      }
      if (min < 0.0 || max < 0.0) {
        throw new IllegalArgumentException("mana drop range must be >= 0");
      }
      if (max < min) {
        max = min;
      }
    }

    public boolean isEmpty() {
      return max <= 0.0;
    }

    public double roll(Random rng) {
      Objects.requireNonNull(rng, "rng");
      if (max <= 0.0) {
        return 0.0;
      }
      if (Math.abs(max - min) < 1e-9) {
        return max;
      }
      return min + (max - min) * rng.nextDouble();
    }
  }
}
