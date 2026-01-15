package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.Objects;
import java.util.Set;

import org.bukkit.Material;

public record UpgradeCompatibilitySpec(
    Set<String> allowItemIds,
    Set<String> denyItemIds,
    Set<Material> allowMaterials,
    Set<Material> denyMaterials
) {
  public UpgradeCompatibilitySpec {
    Objects.requireNonNull(allowItemIds, "allowItemIds");
    Objects.requireNonNull(denyItemIds, "denyItemIds");
    Objects.requireNonNull(allowMaterials, "allowMaterials");
    Objects.requireNonNull(denyMaterials, "denyMaterials");
  }

  public static UpgradeCompatibilitySpec none() {
    return new UpgradeCompatibilitySpec(Set.of(), Set.of(), Set.of(), Set.of());
  }

  public boolean isEmpty() {
    return allowItemIds.isEmpty() && denyItemIds.isEmpty()
        && allowMaterials.isEmpty() && denyMaterials.isEmpty();
  }
}
