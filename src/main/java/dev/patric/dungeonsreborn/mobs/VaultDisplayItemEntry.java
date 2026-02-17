package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

public record VaultDisplayItemEntry(String itemId, double weight) {
  public VaultDisplayItemEntry {
    itemId = Objects.requireNonNull(itemId, "itemId").trim();
    if (itemId.isBlank()) {
      throw new IllegalArgumentException("itemId must be set");
    }
    if (!Double.isFinite(weight) || weight <= 0.0) {
      throw new IllegalArgumentException("weight must be > 0");
    }
  }
}
