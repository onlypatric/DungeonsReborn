package dev.patric.dungeonsreborn.mobs.ai.v3;

import dev.patric.dungeonsreborn.mobs.MobBehaviorState;

import java.util.UUID;

public record MobAiPlan(
    long tick,
    UUID entityId,
    Intent intent,
    UUID targetId,
    double moveX,
    double moveY,
    double moveZ,
    double speed,
    MobBehaviorState desiredState,
    String debugSelector) {

  public enum Intent {
    NONE,
    CHASE,
    FLEE,
    HOLD_RANGE,
    HOLD_POSITION,
    WANDER,
    CALL_HELP,
    ASSIST
  }
}

