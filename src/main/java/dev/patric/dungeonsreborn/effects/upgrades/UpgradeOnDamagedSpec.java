package dev.patric.dungeonsreborn.effects.upgrades;

public record UpgradeOnDamagedSpec(UpgradeStatusEffectSpec effect, long cooldownTicks) {
  public UpgradeOnDamagedSpec {
    if (effect == null) {
      throw new IllegalArgumentException("effect must not be null");
    }
    if (cooldownTicks < 0) {
      throw new IllegalArgumentException("cooldownTicks must be >= 0");
    }
  }
}
