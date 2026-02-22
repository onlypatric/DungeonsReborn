package dev.patric.dungeonsreborn.mobs.ai.v3;

import dev.patric.dungeonsreborn.mobs.MobBehaviorState;

import java.util.UUID;

public record MobAiSnapshot(
    long tick,
    UUID entityId,
    String mobId,
    String phaseId,
    UUID ownerId,
    double x,
    double y,
    double z,
    double vx,
    double vy,
    double vz,
    double health,
    double maxHealth,
    UUID currentTargetId,
    double targetX,
    double targetY,
    double targetZ,
    double targetDistanceSq,
    MobBehaviorState behaviorState,
    MobAiV3Spec spec) {

  public double healthRatio() {
    return maxHealth <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, health / maxHealth));
  }
}

