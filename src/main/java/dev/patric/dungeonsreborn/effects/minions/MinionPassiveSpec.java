package dev.patric.dungeonsreborn.effects.minions;

import java.util.Objects;

public record MinionPassiveSpec(String abilityId, long periodTicks) {
  public MinionPassiveSpec {
    Objects.requireNonNull(abilityId, "abilityId");
    if (abilityId.isBlank()) {
      throw new IllegalArgumentException("abilityId is blank");
    }
    if (periodTicks <= 0) {
      throw new IllegalArgumentException("periodTicks must be > 0");
    }
  }
}
