package dev.patric.dungeonsreborn.effects.minions;

public record MinionOwnerScalingSpec(double levelMultiplier, double strengthMultiplier, double dexterityMultiplier,
                                     double intelligenceMultiplier, double vitalityMultiplier,
                                     double maxManaMultiplier, double maxHealthMultiplier) {
  public static final MinionOwnerScalingSpec NONE =
      new MinionOwnerScalingSpec(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

  public boolean isEnabled() {
    return Math.abs(levelMultiplier) > 1e-9
        || Math.abs(strengthMultiplier) > 1e-9
        || Math.abs(dexterityMultiplier) > 1e-9
        || Math.abs(intelligenceMultiplier) > 1e-9
        || Math.abs(vitalityMultiplier) > 1e-9
        || Math.abs(maxManaMultiplier) > 1e-9
        || Math.abs(maxHealthMultiplier) > 1e-9;
  }
}
