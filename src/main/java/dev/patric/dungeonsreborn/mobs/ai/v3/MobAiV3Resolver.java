package dev.patric.dungeonsreborn.mobs.ai.v3;

import dev.patric.dungeonsreborn.mobs.MobAiSpec;

public final class MobAiV3Resolver {
  private MobAiV3Resolver() {
  }

  public static MobAiV3Spec resolve(MobAiSpec aiSpec) {
    return MobAiV3Spec.from(aiSpec);
  }
}

