package dev.patric.dungeonsreborn.mobs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record MobLootSpec(boolean clearVanilla, List<MobDropSpec> guaranteed, List<MobDropSpec> drops,
    List<MobLootPoolRef> pools, List<MobLootBundleSpec> bundles, MobLootRewardSpec rewards,
    int rolls, int bonusRolls, double luckMultiplier, Set<String> announceTiers, String announceTemplate,
    boolean deterministic, long seedSalt) {
  public MobLootSpec {
    Objects.requireNonNull(guaranteed, "guaranteed");
    Objects.requireNonNull(drops, "drops");
    Objects.requireNonNull(pools, "pools");
    Objects.requireNonNull(bundles, "bundles");
    Objects.requireNonNull(announceTiers, "announceTiers");
    if (rolls < 0) {
      throw new IllegalArgumentException("rolls must be >= 0");
    }
    if (bonusRolls < 0) {
      throw new IllegalArgumentException("bonusRolls must be >= 0");
    }
    if (!Double.isFinite(luckMultiplier) || luckMultiplier < 0.0) {
      throw new IllegalArgumentException("luckMultiplier must be >= 0");
    }
    if (seedSalt < 0L) {
      throw new IllegalArgumentException("seedSalt must be >= 0");
    }
    guaranteed = Collections.unmodifiableList(new ArrayList<>(guaranteed));
    drops = Collections.unmodifiableList(new ArrayList<>(drops));
    pools = Collections.unmodifiableList(new ArrayList<>(pools));
    bundles = Collections.unmodifiableList(new ArrayList<>(bundles));
    announceTiers = announceTiers.stream()
        .filter(Objects::nonNull)
        .map(tier -> tier.trim().toLowerCase(Locale.ROOT))
        .filter(tier -> !tier.isBlank())
        .collect(Collectors.toUnmodifiableSet());
  }
}
