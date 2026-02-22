package dev.patric.dungeonsreborn.mobs.ai.v3;

public final class MobAiModulePerception {
  public boolean hasTarget(MobAiSnapshot snapshot) {
    return snapshot != null && snapshot.currentTargetId() != null;
  }
}

