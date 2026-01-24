package dev.patric.dungeonsreborn.mobs;

import java.util.List;
import java.util.Objects;
import java.util.Random;

public record MobManaDropSpec(String resourceId, MobManaRange killer, MobManaRange nearby, double nearbyRadius,
                              double capPerKill, MobManaStreak streak, List<MobManaTier> tiers) {
  public MobManaDropSpec {
    if (resourceId == null || resourceId.isBlank()) {
      resourceId = "mana";
    }
    if (nearbyRadius < 0.0 || !Double.isFinite(nearbyRadius)) {
      throw new IllegalArgumentException("nearbyRadius must be >= 0");
    }
    if (!Double.isFinite(capPerKill) || capPerKill < 0.0) {
      throw new IllegalArgumentException("capPerKill must be >= 0");
    }
    if (tiers == null) {
      tiers = List.of();
    } else {
      tiers = List.copyOf(tiers);
    }
  }

  public boolean isEmpty() {
    return (killer == null || killer.isEmpty()) && (nearby == null || nearby.isEmpty());
  }

  public double rollTierMultiplier(Random rng) {
    Objects.requireNonNull(rng, "rng");
    if (tiers.isEmpty()) {
      return 1.0;
    }
    double total = 0.0;
    for (MobManaTier tier : tiers) {
      total += Math.max(0.0, tier.weight());
    }
    if (total <= 0.0) {
      return 1.0;
    }
    double roll = rng.nextDouble() * total;
    double acc = 0.0;
    for (MobManaTier tier : tiers) {
      acc += Math.max(0.0, tier.weight());
      if (roll <= acc) {
        return tier.rollMultiplier(rng);
      }
    }
    return tiers.get(tiers.size() - 1).rollMultiplier(rng);
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

  public record MobManaStreak(int maxStacks, double multiplier, long windowTicks) {
    public MobManaStreak {
      if (maxStacks < 0) {
        throw new IllegalArgumentException("maxStacks must be >= 0");
      }
      if (!Double.isFinite(multiplier) || multiplier < 0.0) {
        throw new IllegalArgumentException("multiplier must be >= 0");
      }
      if (windowTicks < 0L) {
        throw new IllegalArgumentException("windowTicks must be >= 0");
      }
    }
  }

  public record MobManaTier(double weight, double minMultiplier, double maxMultiplier) {
    public MobManaTier {
      if (!Double.isFinite(weight) || weight < 0.0) {
        throw new IllegalArgumentException("weight must be >= 0");
      }
      if (!Double.isFinite(minMultiplier) || !Double.isFinite(maxMultiplier)) {
        throw new IllegalArgumentException("multiplier must be finite");
      }
      if (maxMultiplier < minMultiplier) {
        maxMultiplier = minMultiplier;
      }
    }

    public double rollMultiplier(Random rng) {
      Objects.requireNonNull(rng, "rng");
      if (Math.abs(maxMultiplier - minMultiplier) < 1e-9) {
        return maxMultiplier;
      }
      return minMultiplier + (maxMultiplier - minMultiplier) * rng.nextDouble();
    }
  }
}
