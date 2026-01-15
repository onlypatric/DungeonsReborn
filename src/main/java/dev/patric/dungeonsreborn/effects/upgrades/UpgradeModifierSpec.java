package dev.patric.dungeonsreborn.effects.upgrades;

public record UpgradeModifierSpec(UpgradeModifierType type, double value) {
  public UpgradeModifierSpec {
    if (type == null) {
      throw new IllegalArgumentException("type");
    }
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("value must be finite");
    }
  }
}
