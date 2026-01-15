package dev.patric.dungeonsreborn.effects.upgrades;

public record UpgradePriceSpec(int normal, int compressed, int pallet) {
  public UpgradePriceSpec {
    if (normal < 0) {
      throw new IllegalArgumentException("normal must be >= 0");
    }
    if (compressed < 0) {
      throw new IllegalArgumentException("compressed must be >= 0");
    }
    if (pallet < 0) {
      throw new IllegalArgumentException("pallet must be >= 0");
    }
  }

  public static UpgradePriceSpec none() {
    return new UpgradePriceSpec(0, 0, 0);
  }

  public boolean isEmpty() {
    return normal <= 0 && compressed <= 0 && pallet <= 0;
  }
}
