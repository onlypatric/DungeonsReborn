package dev.patric.dungeonsreborn.effects.minions;

import java.util.Objects;

public record MinionSpecialAttackSpec(String abilityId, long cooldownTicks, double chance, boolean requireTarget) {
  public MinionSpecialAttackSpec {
    Objects.requireNonNull(abilityId, "abilityId");
    if (abilityId.isBlank()) {
      throw new IllegalArgumentException("abilityId is blank");
    }
    if (cooldownTicks <= 0) {
      throw new IllegalArgumentException("cooldownTicks must be > 0");
    }
    if (!Double.isFinite(chance) || chance < 0.0 || chance > 1.0) {
      throw new IllegalArgumentException("chance must be in [0,1]");
    }
  }
}
