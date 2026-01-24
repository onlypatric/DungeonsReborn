package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.List;
import java.util.Objects;

public record UpgradeSalvageSpec(
    UpgradePriceSpec tokenRefund,
    List<UpgradeSalvageItemSpec> items
) {
  public UpgradeSalvageSpec {
    Objects.requireNonNull(tokenRefund, "tokenRefund");
    Objects.requireNonNull(items, "items");
  }

  public static UpgradeSalvageSpec none() {
    return new UpgradeSalvageSpec(UpgradePriceSpec.none(), List.of());
  }

  public boolean isEmpty() {
    return tokenRefund.isEmpty() && items.isEmpty();
  }
}
