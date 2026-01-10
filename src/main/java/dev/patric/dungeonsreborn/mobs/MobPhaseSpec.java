package dev.patric.dungeonsreborn.mobs;

import java.util.List;
import java.util.Objects;

public record MobPhaseSpec(String id, double healthBelow, MobAttackSpec mainAttack, MobAttackSpec secondaryAttack,
                           List<MobPassiveSpec> passives) {
  public MobPhaseSpec {
    Objects.requireNonNull(id, "id");
    if (!Double.isFinite(healthBelow) || healthBelow <= 0.0 || healthBelow > 1.0) {
      throw new IllegalArgumentException("healthBelow must be in (0, 1]");
    }
    if (passives != null) {
      for (MobPassiveSpec passive : passives) {
        if (passive == null) {
          throw new IllegalArgumentException("passives contains null");
        }
      }
      passives = List.copyOf(passives);
    }
  }
}
