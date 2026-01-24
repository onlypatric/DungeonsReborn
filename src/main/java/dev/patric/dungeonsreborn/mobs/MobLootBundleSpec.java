package dev.patric.dungeonsreborn.mobs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record MobLootBundleSpec(List<MobDropSpec> drops, int rolls, int bonusRolls, double chance,
    MobDropConditions conditions) {
  public MobLootBundleSpec {
    Objects.requireNonNull(drops, "drops");
    if (rolls < 0) {
      throw new IllegalArgumentException("rolls must be >= 0");
    }
    if (bonusRolls < 0) {
      throw new IllegalArgumentException("bonusRolls must be >= 0");
    }
    if (!(chance >= 0.0 && chance <= 1.0)) {
      throw new IllegalArgumentException("chance must be in [0,1]");
    }
    if (conditions == null) {
      conditions = MobDropConditions.none();
    }
    drops = Collections.unmodifiableList(new ArrayList<>(drops));
  }
}
