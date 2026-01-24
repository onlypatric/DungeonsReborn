package dev.patric.dungeonsreborn.effects.minions;

public record MinionScalingLimits(double maxBonusHealth, double maxBonusDamage, double decayExponent) {
  public static final MinionScalingLimits NONE = new MinionScalingLimits(0.0, 0.0, 0.0);

  public boolean isEnabled() {
    return maxBonusHealth > 0.0 || maxBonusDamage > 0.0;
  }
}
