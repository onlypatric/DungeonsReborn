package dev.patric.dungeonsreborn.effects.minions;

public record MinionScaling(double healthPerLevel, double damagePerLevel, double healthPerMaxHealth,
                            double damagePerMaxHealth, double healthPerManaMax, double damagePerManaMax) {
  public static final MinionScaling NONE = new MinionScaling(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

  public boolean isEnabled() {
    return Math.abs(healthPerLevel) > 1e-9
        || Math.abs(damagePerLevel) > 1e-9
        || Math.abs(healthPerMaxHealth) > 1e-9
        || Math.abs(damagePerMaxHealth) > 1e-9
        || Math.abs(healthPerManaMax) > 1e-9
        || Math.abs(damagePerManaMax) > 1e-9;
  }
}
