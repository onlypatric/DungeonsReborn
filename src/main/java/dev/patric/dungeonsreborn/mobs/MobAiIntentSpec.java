package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

public final class MobAiIntentSpec {
  private final MobAiIntentType type;
  private final double speed;
  private final double radius;
  private final double minRange;
  private final double maxRange;
  private final long intervalTicks;
  private final String abilityId;
  private final long castCooldownTicks;
  private final boolean requireTarget;

  public MobAiIntentSpec(
      MobAiIntentType type,
      double speed,
      double radius,
      double minRange,
      double maxRange,
      long intervalTicks,
      String abilityId,
      long castCooldownTicks,
      boolean requireTarget) {
    this.type = Objects.requireNonNull(type, "type");
    this.speed = Math.max(0.0, speed);
    this.radius = Math.max(0.0, radius);
    this.minRange = Math.max(0.0, minRange);
    this.maxRange = Math.max(0.0, maxRange);
    this.intervalTicks = Math.max(0L, intervalTicks);
    this.abilityId = abilityId == null || abilityId.isBlank() ? null : abilityId.trim();
    this.castCooldownTicks = Math.max(0L, castCooldownTicks);
    this.requireTarget = requireTarget;
  }

  public MobAiIntentType type() {
    return type;
  }

  public double speed() {
    return speed;
  }

  public double radius() {
    return radius;
  }

  public double minRange() {
    return minRange;
  }

  public double maxRange() {
    return maxRange;
  }

  public long intervalTicks() {
    return intervalTicks;
  }

  public String abilityId() {
    return abilityId;
  }

  public long castCooldownTicks() {
    return castCooldownTicks;
  }

  public boolean requireTarget() {
    return requireTarget;
  }

  public boolean hasCastAbility() {
    return abilityId != null && !abilityId.isBlank();
  }
}
