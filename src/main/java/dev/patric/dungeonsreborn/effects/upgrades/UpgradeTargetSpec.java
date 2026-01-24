package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.List;
import java.util.Objects;

public record UpgradeTargetSpec(
    List<String> abilityIds,
    List<String> abilityTags,
    List<String> itemIds,
    List<String> itemTags,
    List<String> itemCategories
) {
  public UpgradeTargetSpec {
    Objects.requireNonNull(abilityIds, "abilityIds");
    Objects.requireNonNull(abilityTags, "abilityTags");
    Objects.requireNonNull(itemIds, "itemIds");
    Objects.requireNonNull(itemTags, "itemTags");
    Objects.requireNonNull(itemCategories, "itemCategories");
  }

  public static UpgradeTargetSpec none() {
    return new UpgradeTargetSpec(List.of(), List.of(), List.of(), List.of(), List.of());
  }

  public boolean isEmpty() {
    return abilityIds.isEmpty() && abilityTags.isEmpty()
        && itemIds.isEmpty() && itemTags.isEmpty() && itemCategories.isEmpty();
  }
}
