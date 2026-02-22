package dev.patric.dungeonsreborn.mobs.ai.v3;

import dev.patric.dungeonsreborn.mobs.MobBehaviorState;

public final class MobAiModuleCombat {
  public MobBehaviorState resolveState(MobAiSnapshot snapshot) {
    if (snapshot == null || snapshot.spec() == null) {
      return MobBehaviorState.IDLE;
    }
    double ratio = snapshot.healthRatio();
    if (snapshot.spec().rageHealthRatio() > 0.0 && ratio <= snapshot.spec().rageHealthRatio()) {
      return MobBehaviorState.RAGE;
    }
    if (snapshot.spec().fleeHealthRatio() > 0.0 && ratio <= snapshot.spec().fleeHealthRatio()) {
      return MobBehaviorState.RETREAT;
    }
    return snapshot.currentTargetId() == null ? MobBehaviorState.IDLE : MobBehaviorState.ENGAGE;
  }
}

