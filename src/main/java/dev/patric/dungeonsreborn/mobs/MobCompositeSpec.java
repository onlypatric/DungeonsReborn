package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

public record MobCompositeSpec(String mountMobId, String riderMobId, MobCompositeRole role,
                               boolean keepAliveTogether) {
  public MobCompositeSpec {
    Objects.requireNonNull(mountMobId, "mountMobId");
    Objects.requireNonNull(riderMobId, "riderMobId");
    Objects.requireNonNull(role, "role");
  }
}
