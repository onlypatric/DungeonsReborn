package dev.patric.dungeonsreborn.mobs.ai.v3;

public final class MobAiModuleGroup {
  public boolean shouldCallHelp(MobAiSnapshot snapshot) {
    return snapshot != null && snapshot.spec() != null && snapshot.spec().callForHelpRadius() > 0.0
        && snapshot.currentTargetId() != null;
  }
}

