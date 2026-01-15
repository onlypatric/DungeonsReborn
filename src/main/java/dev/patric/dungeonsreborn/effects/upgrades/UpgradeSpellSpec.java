package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.Objects;

public record UpgradeSpellSpec(String abilityId, UpgradeActivator activator) {
  public UpgradeSpellSpec {
    Objects.requireNonNull(abilityId, "abilityId");
    Objects.requireNonNull(activator, "activator");
  }
}
