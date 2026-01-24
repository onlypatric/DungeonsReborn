package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.Objects;

public record UpgradeSpellSpec(
    String abilityId,
    UpgradeActivator activator,
    Long cooldownTicks,
    Integer manaCost,
    Integer durabilityCost,
    Integer consumeAmount,
    UpgradeCooldownScope cooldownScope,
    boolean requireSneaking,
    boolean requireSprinting,
    boolean requireAirborne,
    boolean requireOnGround
) {
  public UpgradeSpellSpec {
    Objects.requireNonNull(abilityId, "abilityId");
    Objects.requireNonNull(activator, "activator");
    Objects.requireNonNull(cooldownScope, "cooldownScope");
  }
}
