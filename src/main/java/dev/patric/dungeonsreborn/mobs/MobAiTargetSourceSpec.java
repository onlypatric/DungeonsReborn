package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

public record MobAiTargetSourceSpec(
    MobAiTargetSourceType type,
    double radius,
    long memoryTicks,
    long cooldownTicks,
    int priority) {

  public MobAiTargetSourceSpec {
    type = Objects.requireNonNull(type, "type");
    if (!Double.isFinite(radius) || radius < 0.0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (memoryTicks < 0L) {
      throw new IllegalArgumentException("memoryTicks must be >= 0");
    }
    if (cooldownTicks < 0L) {
      throw new IllegalArgumentException("cooldownTicks must be >= 0");
    }
    if (priority < 0) {
      throw new IllegalArgumentException("priority must be >= 0");
    }
  }
}
