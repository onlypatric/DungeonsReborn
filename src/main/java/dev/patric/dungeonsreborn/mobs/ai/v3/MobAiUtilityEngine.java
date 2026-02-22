package dev.patric.dungeonsreborn.mobs.ai.v3;

import dev.patric.dungeonsreborn.mobs.MobAiGoalSpec;
import dev.patric.dungeonsreborn.mobs.MobAiGoalType;
import dev.patric.dungeonsreborn.mobs.MobBehaviorState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MobAiUtilityEngine {
  private final MobAiModulePerception perception = new MobAiModulePerception();
  private final MobAiModuleCombat combat = new MobAiModuleCombat();
  private final MobAiModuleGroup group = new MobAiModuleGroup();

  public MobAiPlan plan(MobAiSnapshot snapshot) {
    if (snapshot == null || snapshot.spec() == null) {
      return new MobAiPlan(0L, null, MobAiPlan.Intent.NONE, null, 0.0, 0.0, 0.0, 0.0, MobBehaviorState.IDLE, "none");
    }
    MobBehaviorState desired = combat.resolveState(snapshot);
    if (!perception.hasTarget(snapshot)) {
      return new MobAiPlan(
          snapshot.tick(),
          snapshot.entityId(),
          MobAiPlan.Intent.NONE,
          null,
          snapshot.x(),
          snapshot.y(),
          snapshot.z(),
          0.0,
          desired,
          "idle");
    }
    if (desired == MobBehaviorState.RETREAT) {
      return new MobAiPlan(
          snapshot.tick(),
          snapshot.entityId(),
          MobAiPlan.Intent.FLEE,
          snapshot.currentTargetId(),
          snapshot.targetX(),
          snapshot.targetY(),
          snapshot.targetZ(),
          Math.max(0.1, snapshot.spec().fleeSpeed()),
          desired,
          "combat_retreat");
    }
    List<MobAiGoalSpec> goals = new ArrayList<>(snapshot.spec().goals());
    goals.sort(Comparator.comparingInt(MobAiGoalSpec::priority));
    for (MobAiGoalSpec goal : goals) {
      MobAiPlan plan = fromGoal(snapshot, goal, desired);
      if (plan != null) {
        return plan;
      }
    }
    if (group.shouldCallHelp(snapshot)) {
      return new MobAiPlan(
          snapshot.tick(),
          snapshot.entityId(),
          MobAiPlan.Intent.CALL_HELP,
          snapshot.currentTargetId(),
          snapshot.targetX(),
          snapshot.targetY(),
          snapshot.targetZ(),
          Math.max(0.1, snapshot.spec().chaseSpeed()),
          desired,
          "group_call_help");
    }
    return new MobAiPlan(
        snapshot.tick(),
        snapshot.entityId(),
        MobAiPlan.Intent.CHASE,
        snapshot.currentTargetId(),
        snapshot.targetX(),
        snapshot.targetY(),
        snapshot.targetZ(),
        Math.max(0.1, snapshot.spec().chaseSpeed()),
        desired,
        "combat_chase");
  }

  private MobAiPlan fromGoal(MobAiSnapshot snapshot, MobAiGoalSpec goal, MobBehaviorState desired) {
    if (goal == null) {
      return null;
    }
    MobAiGoalType type = goal.type();
    if (type == MobAiGoalType.HOLD_RANGE) {
      return new MobAiPlan(
          snapshot.tick(),
          snapshot.entityId(),
          MobAiPlan.Intent.HOLD_RANGE,
          snapshot.currentTargetId(),
          snapshot.targetX(),
          snapshot.targetY(),
          snapshot.targetZ(),
          goal.speed() > 0.0 ? goal.speed() : Math.max(0.1, snapshot.spec().kiteSpeed()),
          desired,
          "goal_hold_range");
    }
    if (type == MobAiGoalType.FLEE) {
      return new MobAiPlan(
          snapshot.tick(),
          snapshot.entityId(),
          MobAiPlan.Intent.FLEE,
          snapshot.currentTargetId(),
          snapshot.targetX(),
          snapshot.targetY(),
          snapshot.targetZ(),
          goal.speed() > 0.0 ? goal.speed() : Math.max(0.1, snapshot.spec().fleeSpeed()),
          desired,
          "goal_flee");
    }
    if (type == MobAiGoalType.HOLD_POSITION) {
      return new MobAiPlan(
          snapshot.tick(),
          snapshot.entityId(),
          MobAiPlan.Intent.HOLD_POSITION,
          snapshot.currentTargetId(),
          snapshot.x(),
          snapshot.y(),
          snapshot.z(),
          0.0,
          desired,
          "goal_hold_position");
    }
    if (type == MobAiGoalType.CALL_HELP) {
      return new MobAiPlan(
          snapshot.tick(),
          snapshot.entityId(),
          MobAiPlan.Intent.CALL_HELP,
          snapshot.currentTargetId(),
          snapshot.targetX(),
          snapshot.targetY(),
          snapshot.targetZ(),
          Math.max(0.1, snapshot.spec().chaseSpeed()),
          desired,
          "goal_call_help");
    }
    if (type == MobAiGoalType.ASSIST) {
      return new MobAiPlan(
          snapshot.tick(),
          snapshot.entityId(),
          MobAiPlan.Intent.ASSIST,
          snapshot.currentTargetId(),
          snapshot.targetX(),
          snapshot.targetY(),
          snapshot.targetZ(),
          goal.speed() > 0.0 ? goal.speed() : Math.max(0.1, snapshot.spec().chaseSpeed()),
          desired,
          "goal_assist");
    }
    if (type == MobAiGoalType.CHASE || type == MobAiGoalType.GUARD || type == MobAiGoalType.RETURN || type == MobAiGoalType.PATROL) {
      return new MobAiPlan(
          snapshot.tick(),
          snapshot.entityId(),
          MobAiPlan.Intent.CHASE,
          snapshot.currentTargetId(),
          snapshot.targetX(),
          snapshot.targetY(),
          snapshot.targetZ(),
          goal.speed() > 0.0 ? goal.speed() : Math.max(0.1, snapshot.spec().chaseSpeed()),
          desired,
          "goal_chase");
    }
    return null;
  }
}

