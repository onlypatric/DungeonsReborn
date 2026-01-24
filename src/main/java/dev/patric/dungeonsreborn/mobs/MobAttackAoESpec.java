package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

public final class MobAttackAoESpec {
  private final MobAttackAoEShape shape;
  private final double radius;
  private final double height;
  private final double angleDegrees;
  private final int maxTargets;
  private final MobTargetFilter filter;

  public MobAttackAoESpec(MobAttackAoEShape shape, double radius, double height, double angleDegrees, int maxTargets,
      MobTargetFilter filter) {
    this.shape = Objects.requireNonNull(shape, "shape");
    this.radius = Math.max(0.0, radius);
    this.height = Math.max(0.0, height);
    this.angleDegrees = angleDegrees;
    this.maxTargets = Math.max(0, maxTargets);
    this.filter = Objects.requireNonNull(filter, "filter");
  }

  public MobAttackAoEShape shape() {
    return shape;
  }

  public double radius() {
    return radius;
  }

  public double height() {
    return height;
  }

  public double angleDegrees() {
    return angleDegrees;
  }

  public int maxTargets() {
    return maxTargets;
  }

  public MobTargetFilter filter() {
    return filter;
  }
}
