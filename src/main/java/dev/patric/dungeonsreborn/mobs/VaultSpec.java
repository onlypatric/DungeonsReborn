package dev.patric.dungeonsreborn.mobs;

import java.util.List;
import java.util.Objects;

public record VaultSpec(
    String id,
    String keyItem,
    String lootPoolNormal,
    String lootPoolOminous,
    double activationRange,
    double deactivationRange,
    List<VaultDisplayItemEntry> displayedItemPool) {
  public VaultSpec {
    id = Objects.requireNonNull(id, "id").trim();
    if (id.isBlank()) {
      throw new IllegalArgumentException("id must be set");
    }
    keyItem = Objects.requireNonNull(keyItem, "keyItem").trim();
    lootPoolNormal = Objects.requireNonNull(lootPoolNormal, "lootPoolNormal").trim();
    lootPoolOminous = Objects.requireNonNull(lootPoolOminous, "lootPoolOminous").trim();
    if (keyItem.isBlank()) {
      throw new IllegalArgumentException("keyItem must be set");
    }
    if (lootPoolNormal.isBlank()) {
      throw new IllegalArgumentException("lootPoolNormal must be set");
    }
    if (lootPoolOminous.isBlank()) {
      throw new IllegalArgumentException("lootPoolOminous must be set");
    }
    if (!Double.isFinite(activationRange) || activationRange <= 0.0) {
      throw new IllegalArgumentException("activationRange must be > 0");
    }
    if (!Double.isFinite(deactivationRange) || deactivationRange <= activationRange) {
      throw new IllegalArgumentException("deactivationRange must be > activationRange");
    }
    displayedItemPool = displayedItemPool == null ? List.of() : List.copyOf(displayedItemPool);
  }
}
