package dev.patric.dungeonsreborn.mobs;

import java.util.List;
import java.util.Objects;

import org.bukkit.util.Vector;

public final class MobAiGoalSpec {
  private final MobAiGoalType type;
  private final int priority;
  private final double radius;
  private final double speed;
  private final long intervalTicks;
  private final List<Vector> points;

  public MobAiGoalSpec(MobAiGoalType type, int priority, double radius, double speed, long intervalTicks,
      List<Vector> points) {
    this.type = Objects.requireNonNull(type, "type");
    this.priority = priority;
    this.radius = Math.max(0.0, radius);
    this.speed = Math.max(0.0, speed);
    this.intervalTicks = Math.max(0L, intervalTicks);
    this.points = points == null ? List.of() : List.copyOf(points);
  }

  public MobAiGoalType type() {
    return type;
  }

  public int priority() {
    return priority;
  }

  public double radius() {
    return radius;
  }

  public double speed() {
    return speed;
  }

  public long intervalTicks() {
    return intervalTicks;
  }

  public List<Vector> points() {
    return points;
  }
}
