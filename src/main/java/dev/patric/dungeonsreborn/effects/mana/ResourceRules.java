package dev.patric.dungeonsreborn.effects.mana;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record ResourceRules(
    double baseMax,
    double hardCap,
    double softCap,
    double overflowDecay,
    RegenMode regenMode,
    double regenFlat,
    double regenPercent,
    double regenMultiplier,
    double costMultiplier,
    Map<String, Double> conversions
) {
  public static final double NO_CAP = 0.0;
  public static final double DEFAULT_REGEN_MULTIPLIER = 1.0;
  public static final double DEFAULT_COST_MULTIPLIER = 1.0;

  public ResourceRules {
    if (!Double.isFinite(baseMax) || !Double.isFinite(hardCap) || !Double.isFinite(softCap)
        || !Double.isFinite(overflowDecay) || !Double.isFinite(regenFlat) || !Double.isFinite(regenPercent)
        || !Double.isFinite(regenMultiplier) || !Double.isFinite(costMultiplier)) {
      throw new IllegalArgumentException("Resource rule values must be finite.");
    }
    if (regenMode == null) {
      regenMode = RegenMode.FLAT;
    }
    if (overflowDecay < 0.0 || overflowDecay > 1.0) {
      throw new IllegalArgumentException("overflowDecay must be in [0,1].");
    }
    conversions = conversions == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(conversions));
  }

  public static ResourceRules defaults(double baseMax) {
    return new ResourceRules(
        baseMax,
        NO_CAP,
        NO_CAP,
        0.0,
        RegenMode.FLAT,
        0.0,
        0.0,
        DEFAULT_REGEN_MULTIPLIER,
        DEFAULT_COST_MULTIPLIER,
        Map.of());
  }

  public ResourceRules merge(ResourceRules override) {
    Objects.requireNonNull(override, "override");
    return new ResourceRules(
        override.baseMax != 0.0 ? override.baseMax : baseMax,
        override.hardCap != 0.0 ? override.hardCap : hardCap,
        override.softCap != 0.0 ? override.softCap : softCap,
        override.overflowDecay != 0.0 ? override.overflowDecay : overflowDecay,
        override.regenMode != RegenMode.FLAT || regenMode == null ? override.regenMode : regenMode,
        override.regenFlat != 0.0 ? override.regenFlat : regenFlat,
        override.regenPercent != 0.0 ? override.regenPercent : regenPercent,
        override.regenMultiplier != 0.0 ? override.regenMultiplier : regenMultiplier,
        override.costMultiplier != 0.0 ? override.costMultiplier : costMultiplier,
        override.conversions.isEmpty() ? conversions : override.conversions);
  }

  public double resolveRegenBase(double globalFlat, double max, long periodTicks) {
    double flat = regenFlat > 0.0 ? regenFlat : globalFlat;
    double percentPerSecond = regenPercent > 0.0 ? regenPercent : 0.0;
    double percentAmount = max * percentPerSecond * (periodTicks / 20.0);
    return switch (regenMode) {
      case FLAT -> flat;
      case PERCENT -> percentAmount;
      case HYBRID -> flat + percentAmount;
    };
  }

  public enum RegenMode {
    FLAT,
    PERCENT,
    HYBRID
  }
}
