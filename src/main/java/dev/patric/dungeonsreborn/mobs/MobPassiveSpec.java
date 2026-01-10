package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

public record MobPassiveSpec(String abilityId, long periodTicks) {
  public MobPassiveSpec {
    Objects.requireNonNull(abilityId, "abilityId");
    if (periodTicks <= 0) {
      throw new IllegalArgumentException("periodTicks must be > 0");
    }
  }
}
