package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.List;
import java.util.Objects;

public record UpgradeSpec(
    String id,
    String name,
    String description,
    UpgradeRequirements requirements,
    UpgradePriceSpec price,
    UpgradeTargetSpec target,
    UpgradeCompatibilitySpec compatibility,
    UpgradeLimitsSpec limits,
    UpgradeBehaviorSpec behaviors,
    boolean allowUnsafe,
    List<UpgradeModifierSpec> modifiers,
    List<UpgradeAttributeSpec> attributes,
    List<UpgradeEnchantSpec> enchants,
    List<UpgradeSpellSpec> spells
) {
  public UpgradeSpec {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(requirements, "requirements");
    Objects.requireNonNull(price, "price");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(compatibility, "compatibility");
    Objects.requireNonNull(limits, "limits");
    Objects.requireNonNull(behaviors, "behaviors");
    Objects.requireNonNull(modifiers, "modifiers");
    Objects.requireNonNull(attributes, "attributes");
    Objects.requireNonNull(enchants, "enchants");
    Objects.requireNonNull(spells, "spells");
  }

  public UpgradeSpellSpec primarySpell() {
    return spells.isEmpty() ? null : spells.get(0);
  }
}
