package dev.patric.dungeonsreborn.mobs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record MobLootSpec(boolean clearVanilla, List<MobDropSpec> drops) {
  public MobLootSpec {
    Objects.requireNonNull(drops, "drops");
    drops = Collections.unmodifiableList(new ArrayList<>(drops));
  }
}
