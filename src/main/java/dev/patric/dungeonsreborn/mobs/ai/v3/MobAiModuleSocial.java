package dev.patric.dungeonsreborn.mobs.ai.v3;

public final class MobAiModuleSocial {
  public boolean enabled(MobAiSnapshot snapshot) {
    return snapshot != null && snapshot.spec() != null && snapshot.spec().assistRadius() > 0.0;
  }
}

