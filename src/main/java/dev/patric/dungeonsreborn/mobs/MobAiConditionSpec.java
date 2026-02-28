package dev.patric.dungeonsreborn.mobs;

import java.util.List;
import java.util.Objects;

public final class MobAiConditionSpec {
  public enum Kind {
    ALWAYS,
    ALL,
    ANY,
    NOT,
    HAS_TARGET,
    HEALTH_RATIO_LTE,
    HEALTH_RATIO_GTE,
    TARGET_DISTANCE_LTE,
    TARGET_DISTANCE_GTE,
    BEHAVIOR_STATE,
    RANDOM_CHANCE
  }

  private static final MobAiConditionSpec ALWAYS = new MobAiConditionSpec(
      Kind.ALWAYS,
      List.of(),
      null,
      null,
      null);

  private final Kind kind;
  private final List<MobAiConditionSpec> children;
  private final Double numberValue;
  private final MobBehaviorState behaviorState;
  private final Boolean booleanValue;

  private MobAiConditionSpec(
      Kind kind,
      List<MobAiConditionSpec> children,
      Double numberValue,
      MobBehaviorState behaviorState,
      Boolean booleanValue) {
    this.kind = Objects.requireNonNull(kind, "kind");
    this.children = children == null ? List.of() : List.copyOf(children);
    this.numberValue = numberValue;
    this.behaviorState = behaviorState;
    this.booleanValue = booleanValue;
  }

  public static MobAiConditionSpec always() {
    return ALWAYS;
  }

  public static MobAiConditionSpec all(List<MobAiConditionSpec> children) {
    return new MobAiConditionSpec(Kind.ALL, children, null, null, null);
  }

  public static MobAiConditionSpec any(List<MobAiConditionSpec> children) {
    return new MobAiConditionSpec(Kind.ANY, children, null, null, null);
  }

  public static MobAiConditionSpec not(MobAiConditionSpec child) {
    return new MobAiConditionSpec(Kind.NOT, child == null ? List.of() : List.of(child), null, null, null);
  }

  public static MobAiConditionSpec hasTarget(boolean value) {
    return new MobAiConditionSpec(Kind.HAS_TARGET, List.of(), null, null, value);
  }

  public static MobAiConditionSpec healthRatioLte(double value) {
    return new MobAiConditionSpec(Kind.HEALTH_RATIO_LTE, List.of(), value, null, null);
  }

  public static MobAiConditionSpec healthRatioGte(double value) {
    return new MobAiConditionSpec(Kind.HEALTH_RATIO_GTE, List.of(), value, null, null);
  }

  public static MobAiConditionSpec targetDistanceLte(double value) {
    return new MobAiConditionSpec(Kind.TARGET_DISTANCE_LTE, List.of(), value, null, null);
  }

  public static MobAiConditionSpec targetDistanceGte(double value) {
    return new MobAiConditionSpec(Kind.TARGET_DISTANCE_GTE, List.of(), value, null, null);
  }

  public static MobAiConditionSpec behaviorState(MobBehaviorState value) {
    return new MobAiConditionSpec(Kind.BEHAVIOR_STATE, List.of(), null, Objects.requireNonNull(value, "value"), null);
  }

  public static MobAiConditionSpec randomChance(double value) {
    return new MobAiConditionSpec(Kind.RANDOM_CHANCE, List.of(), value, null, null);
  }

  public Kind kind() {
    return kind;
  }

  public List<MobAiConditionSpec> children() {
    return children;
  }

  public Double numberValue() {
    return numberValue;
  }

  public MobBehaviorState behaviorState() {
    return behaviorState;
  }

  public Boolean booleanValue() {
    return booleanValue;
  }
}
