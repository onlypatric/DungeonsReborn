package dev.patric.dungeonsreborn.mobs.ai.v3;

public final class MobAiGuardrailController {
  public enum DegradeTier {
    NONE,
    DROP_LOW_PRIORITY,
    SLOW_RECALC,
    DISABLE_SOCIAL_ECONOMY,
    LIGHTWEIGHT_FALLBACK
  }

  public DegradeTier tierForOverload(double ratio) {
    if (ratio <= 1.0) {
      return DegradeTier.NONE;
    }
    if (ratio <= 1.25) {
      return DegradeTier.DROP_LOW_PRIORITY;
    }
    if (ratio <= 1.5) {
      return DegradeTier.SLOW_RECALC;
    }
    if (ratio <= 2.0) {
      return DegradeTier.DISABLE_SOCIAL_ECONOMY;
    }
    return DegradeTier.LIGHTWEIGHT_FALLBACK;
  }
}

