package dev.patric.dungeonsreborn.mobs.ai.v3;

public final class MobAiModuleEnvironment {
  public boolean prefersSafePath(MobAiSnapshot snapshot) {
    return snapshot != null && snapshot.spec() != null
        && (snapshot.spec().avoidLava() || snapshot.spec().avoidPowderSnow() || snapshot.spec().avoidCactus());
  }
}

