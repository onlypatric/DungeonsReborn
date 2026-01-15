package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.List;
import java.util.Objects;

public record UpgradeTargetSpec(
    List<String> abilityIds,
    List<String> abilityTags
) {
  public UpgradeTargetSpec {
    Objects.requireNonNull(abilityIds, "abilityIds");
    Objects.requireNonNull(abilityTags, "abilityTags");
  }

  public static UpgradeTargetSpec none() {
    return new UpgradeTargetSpec(List.of(), List.of());
  }

  public boolean isEmpty() {
    return abilityIds.isEmpty() && abilityTags.isEmpty();
  }
}
