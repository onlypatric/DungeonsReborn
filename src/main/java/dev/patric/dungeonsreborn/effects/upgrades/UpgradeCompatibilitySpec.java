package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.Objects;
import java.util.Set;

import org.bukkit.Material;

public record UpgradeCompatibilitySpec(
    Set<String> allowItemIds,
    Set<String> denyItemIds,
    Set<Material> allowMaterials,
    Set<Material> denyMaterials,
    Set<String> incompatibilityGroups,
    int priority
) {
  public UpgradeCompatibilitySpec {
    Objects.requireNonNull(allowItemIds, "allowItemIds");
    Objects.requireNonNull(denyItemIds, "denyItemIds");
    Objects.requireNonNull(allowMaterials, "allowMaterials");
    Objects.requireNonNull(denyMaterials, "denyMaterials");
    Objects.requireNonNull(incompatibilityGroups, "incompatibilityGroups");
  }

  public static UpgradeCompatibilitySpec none() {
    return new UpgradeCompatibilitySpec(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), 0);
  }

  public boolean isEmpty() {
    return allowItemIds.isEmpty() && denyItemIds.isEmpty()
        && allowMaterials.isEmpty() && denyMaterials.isEmpty()
        && incompatibilityGroups.isEmpty() && priority == 0;
  }
}
