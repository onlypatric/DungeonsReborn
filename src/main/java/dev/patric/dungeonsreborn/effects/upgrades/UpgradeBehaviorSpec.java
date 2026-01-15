package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.List;
import java.util.Objects;

public record UpgradeBehaviorSpec(
    List<String> secondaryAbilities,
    List<String> particlePresets,
    List<UpgradeStatusEffectSpec> statusEffects,
    List<UpgradeStatusEffectSpec> inventoryEffects,
    boolean inventoryActive,
    List<UpgradeOnDamagedSpec> onDamagedEffects
) {
  public UpgradeBehaviorSpec {
    Objects.requireNonNull(secondaryAbilities, "secondaryAbilities");
    Objects.requireNonNull(particlePresets, "particlePresets");
    Objects.requireNonNull(statusEffects, "statusEffects");
    Objects.requireNonNull(inventoryEffects, "inventoryEffects");
    Objects.requireNonNull(onDamagedEffects, "onDamagedEffects");
  }

  public static UpgradeBehaviorSpec none() {
    return new UpgradeBehaviorSpec(List.of(), List.of(), List.of(), List.of(), false, List.of());
  }

  public boolean isEmpty() {
    return secondaryAbilities.isEmpty() && particlePresets.isEmpty() && statusEffects.isEmpty()
        && inventoryEffects.isEmpty() && !inventoryActive && onDamagedEffects.isEmpty();
  }
}
