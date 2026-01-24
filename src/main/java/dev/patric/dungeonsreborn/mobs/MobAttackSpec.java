package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

public final class MobAttackSpec {
  private final String abilityId;
  private final long cooldownTicks;
  private final MobAttackTrigger trigger;
  private final MobTargetMode targetMode;
  private final double range;
  private final double chance;
  private final boolean requireLineOfSight;
  private final boolean requireTarget;
  private final double priorityWeight;
  private final long internalCooldownTicks;
  private final MobAttackAoESpec aoeSpec;
  private final java.util.function.Consumer<MobCastContext> beforeCast;
  private final java.util.function.Consumer<MobCastContext> afterCast;

  private MobAttackSpec(Builder builder) {
    this.abilityId = Objects.requireNonNull(builder.abilityId, "abilityId");
    this.cooldownTicks = builder.cooldownTicks;
    this.trigger = Objects.requireNonNull(builder.trigger, "trigger");
    this.targetMode = Objects.requireNonNull(builder.targetMode, "targetMode");
    this.range = builder.range;
    this.chance = builder.chance;
    this.requireLineOfSight = builder.requireLineOfSight;
    this.requireTarget = builder.requireTarget;
    this.priorityWeight = builder.priorityWeight;
    this.internalCooldownTicks = builder.internalCooldownTicks;
    this.aoeSpec = builder.aoeSpec;
    this.beforeCast = builder.beforeCast;
    this.afterCast = builder.afterCast;
  }

  public String abilityId() {
    return abilityId;
  }

  public long cooldownTicks() {
    return cooldownTicks;
  }

  public MobAttackTrigger trigger() {
    return trigger;
  }

  public MobTargetMode targetMode() {
    return targetMode;
  }

  public double range() {
    return range;
  }

  public double chance() {
    return chance;
  }

  public boolean requireLineOfSight() {
    return requireLineOfSight;
  }

  public boolean requireTarget() {
    return requireTarget;
  }

  public double priorityWeight() {
    return priorityWeight;
  }

  public long internalCooldownTicks() {
    return internalCooldownTicks;
  }

  public MobAttackAoESpec aoeSpec() {
    return aoeSpec;
  }

  public java.util.function.Consumer<MobCastContext> beforeCast() {
    return beforeCast;
  }

  public java.util.function.Consumer<MobCastContext> afterCast() {
    return afterCast;
  }

  public static Builder builder(String abilityId) {
    return new Builder(abilityId);
  }

  public static final class Builder {
    private final String abilityId;
    private long cooldownTicks = 40L;
    private MobAttackTrigger trigger = MobAttackTrigger.MELEE;
    private MobTargetMode targetMode = MobTargetMode.NEAREST_PLAYER;
    private double range = 10.0;
    private double chance = 1.0;
    private boolean requireLineOfSight = true;
    private boolean requireTarget = true;
    private double priorityWeight = 1.0;
    private long internalCooldownTicks;
    private MobAttackAoESpec aoeSpec;
    private java.util.function.Consumer<MobCastContext> beforeCast = ctx -> {
    };
    private java.util.function.Consumer<MobCastContext> afterCast = ctx -> {
    };

    private Builder(String abilityId) {
      this.abilityId = Objects.requireNonNull(abilityId, "abilityId");
    }

    public Builder cooldownTicks(long cooldownTicks) {
      if (cooldownTicks < 0) {
        throw new IllegalArgumentException("cooldownTicks must be >= 0");
      }
      this.cooldownTicks = cooldownTicks;
      return this;
    }

    public Builder trigger(MobAttackTrigger trigger) {
      this.trigger = Objects.requireNonNull(trigger, "trigger");
      return this;
    }

    public Builder targetMode(MobTargetMode targetMode) {
      this.targetMode = Objects.requireNonNull(targetMode, "targetMode");
      return this;
    }

    public Builder range(double range) {
      if (range < 0.0) {
        throw new IllegalArgumentException("range must be >= 0");
      }
      this.range = range;
      return this;
    }

    public Builder chance(double chance) {
      if (chance < 0.0 || chance > 1.0) {
        throw new IllegalArgumentException("chance must be in [0,1]");
      }
      this.chance = chance;
      return this;
    }

    public Builder requireLineOfSight(boolean requireLineOfSight) {
      this.requireLineOfSight = requireLineOfSight;
      return this;
    }

    public Builder requireTarget(boolean requireTarget) {
      this.requireTarget = requireTarget;
      return this;
    }

    public Builder priorityWeight(double priorityWeight) {
      if (!Double.isFinite(priorityWeight) || priorityWeight <= 0.0) {
        throw new IllegalArgumentException("priorityWeight must be > 0");
      }
      this.priorityWeight = priorityWeight;
      return this;
    }

    public Builder internalCooldownTicks(long internalCooldownTicks) {
      if (internalCooldownTicks < 0L) {
        throw new IllegalArgumentException("internalCooldownTicks must be >= 0");
      }
      this.internalCooldownTicks = internalCooldownTicks;
      return this;
    }

    public Builder aoe(MobAttackAoESpec aoeSpec) {
      this.aoeSpec = aoeSpec;
      return this;
    }

    public Builder beforeCast(java.util.function.Consumer<MobCastContext> beforeCast) {
      this.beforeCast = Objects.requireNonNull(beforeCast, "beforeCast");
      return this;
    }

    public Builder afterCast(java.util.function.Consumer<MobCastContext> afterCast) {
      this.afterCast = Objects.requireNonNull(afterCast, "afterCast");
      return this;
    }

    public MobAttackSpec build() {
      return new MobAttackSpec(this);
    }
  }
}
