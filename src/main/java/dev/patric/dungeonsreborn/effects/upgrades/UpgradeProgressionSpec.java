package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.List;
import java.util.Objects;

public record UpgradeProgressionSpec(
    String category,
    int tier,
    boolean requirePreviousTier,
    boolean consumePreviousTier,
    List<String> requires,
    List<String> consumes
) {
  public UpgradeProgressionSpec {
    if (tier < 1) {
      throw new IllegalArgumentException("tier must be >= 1");
    }
    Objects.requireNonNull(requires, "requires");
    Objects.requireNonNull(consumes, "consumes");
  }

  public static UpgradeProgressionSpec none() {
    return new UpgradeProgressionSpec(null, 1, false, false, List.of(), List.of());
  }

  public boolean isEmpty() {
    return (category == null || category.isBlank())
        && tier == 1
        && !requirePreviousTier
        && !consumePreviousTier
        && requires.isEmpty()
        && consumes.isEmpty();
  }
}
