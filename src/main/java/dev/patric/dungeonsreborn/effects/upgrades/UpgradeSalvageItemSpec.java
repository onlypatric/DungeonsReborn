package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.Objects;

public record UpgradeSalvageItemSpec(String itemId, int amount) {
  public UpgradeSalvageItemSpec {
    Objects.requireNonNull(itemId, "itemId");
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
  }
}
