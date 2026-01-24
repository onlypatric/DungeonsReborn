package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.Objects;

public record UpgradeFusionSpec(
    UpgradePriceSpec price,
    double successChance,
    boolean consumeOnFail,
    boolean destroyTargetOnFail,
    String downgradeTo
) {
  public UpgradeFusionSpec {
    Objects.requireNonNull(price, "price");
    if (!Double.isFinite(successChance) || successChance < 0.0 || successChance > 1.0) {
      throw new IllegalArgumentException("successChance must be between 0 and 1");
    }
  }

  public static UpgradeFusionSpec none() {
    return new UpgradeFusionSpec(UpgradePriceSpec.none(), 1.0, false, false, null);
  }

  public boolean isEmpty() {
    return price.isEmpty()
        && successChance >= 1.0
        && !consumeOnFail
        && !destroyTargetOnFail
        && (downgradeTo == null || downgradeTo.isBlank());
  }
}
