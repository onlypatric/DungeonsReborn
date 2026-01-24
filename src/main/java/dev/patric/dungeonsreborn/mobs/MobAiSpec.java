package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

public final class MobAiSpec {
  private final boolean enabled;
  private final boolean overrideDefault;
  private final double aggroRadius;
  private final double leashRadius;
  private final double leashTeleportRadius;
  private final MobTargetMode aggroTargetMode;
  private final boolean preferLastAttacker;
  private final long targetSwitchCooldownTicks;
  private final double fleeHealthRatio;
  private final double fleeSpeed;
  private final double idleWanderRadius;
  private final long idleWanderIntervalTicks;
  private final double roamRadius;
  private final double kiteMinRange;
  private final double kiteSpeed;
  private final double chaseSpeed;
  private final MobLocomotionMode locomotionMode;
  private final boolean avoidWater;
  private final boolean avoidLava;
  private final boolean preferGround;
  private final java.util.List<org.bukkit.util.Vector> guardPoints;
  private final double rageHealthRatio;
  private final double rageSpeed;
  private final MobAiController controller;
  private final MobPartyRule partyRule;
  private final java.util.List<MobAiGoalSpec> goals;

  private MobAiSpec(Builder builder) {
    this.enabled = builder.enabled;
    this.overrideDefault = builder.overrideDefault;
    this.aggroRadius = builder.aggroRadius;
    this.leashRadius = builder.leashRadius;
    this.leashTeleportRadius = builder.leashTeleportRadius;
    this.aggroTargetMode = builder.aggroTargetMode;
    this.preferLastAttacker = builder.preferLastAttacker;
    this.targetSwitchCooldownTicks = builder.targetSwitchCooldownTicks;
    this.fleeHealthRatio = builder.fleeHealthRatio;
    this.fleeSpeed = builder.fleeSpeed;
    this.idleWanderRadius = builder.idleWanderRadius;
    this.idleWanderIntervalTicks = builder.idleWanderIntervalTicks;
    this.roamRadius = builder.roamRadius;
    this.kiteMinRange = builder.kiteMinRange;
    this.kiteSpeed = builder.kiteSpeed;
    this.chaseSpeed = builder.chaseSpeed;
    this.locomotionMode = builder.locomotionMode;
    this.avoidWater = builder.avoidWater;
    this.avoidLava = builder.avoidLava;
    this.preferGround = builder.preferGround;
    this.guardPoints = java.util.List.copyOf(builder.guardPoints);
    this.rageHealthRatio = builder.rageHealthRatio;
    this.rageSpeed = builder.rageSpeed;
    this.controller = builder.controller;
    this.partyRule = builder.partyRule;
    this.goals = java.util.List.copyOf(builder.goals);
  }

  public boolean enabled() {
    return enabled;
  }

  public boolean overrideDefault() {
    return overrideDefault;
  }

  public double aggroRadius() {
    return aggroRadius;
  }

  public double leashRadius() {
    return leashRadius;
  }

  public double leashTeleportRadius() {
    return leashTeleportRadius;
  }

  public MobTargetMode aggroTargetMode() {
    return aggroTargetMode;
  }

  public boolean preferLastAttacker() {
    return preferLastAttacker;
  }

  public long targetSwitchCooldownTicks() {
    return targetSwitchCooldownTicks;
  }

  public double fleeHealthRatio() {
    return fleeHealthRatio;
  }

  public double fleeSpeed() {
    return fleeSpeed;
  }

  public double idleWanderRadius() {
    return idleWanderRadius;
  }

  public long idleWanderIntervalTicks() {
    return idleWanderIntervalTicks;
  }

  public double roamRadius() {
    return roamRadius;
  }

  public double kiteMinRange() {
    return kiteMinRange;
  }

  public double kiteSpeed() {
    return kiteSpeed;
  }

  public double chaseSpeed() {
    return chaseSpeed;
  }

  public MobLocomotionMode locomotionMode() {
    return locomotionMode;
  }

  public boolean avoidWater() {
    return avoidWater;
  }

  public boolean avoidLava() {
    return avoidLava;
  }

  public boolean preferGround() {
    return preferGround;
  }

  public java.util.List<org.bukkit.util.Vector> guardPoints() {
    return guardPoints;
  }

  public double rageHealthRatio() {
    return rageHealthRatio;
  }

  public double rageSpeed() {
    return rageSpeed;
  }

  public MobAiController controller() {
    return controller;
  }

  public MobPartyRule partyRule() {
    return partyRule;
  }

  public java.util.List<MobAiGoalSpec> goals() {
    return goals;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private boolean enabled = true;
    private boolean overrideDefault;
    private double aggroRadius = 12.0;
    private double leashRadius = 24.0;
    private double leashTeleportRadius = 36.0;
    private MobTargetMode aggroTargetMode = MobTargetMode.NEAREST_PLAYER;
    private boolean preferLastAttacker = true;
    private long targetSwitchCooldownTicks = 40L;
    private double fleeHealthRatio;
    private double fleeSpeed = 0.35;
    private double idleWanderRadius = 6.0;
    private long idleWanderIntervalTicks = 80L;
    private double roamRadius;
    private double kiteMinRange;
    private double kiteSpeed;
    private double chaseSpeed = 0.25;
    private MobLocomotionMode locomotionMode = MobLocomotionMode.GROUND;
    private boolean avoidWater;
    private boolean avoidLava;
    private boolean preferGround = true;
    private final java.util.List<org.bukkit.util.Vector> guardPoints = new java.util.ArrayList<>();
    private double rageHealthRatio;
    private double rageSpeed = 0.35;
    private MobAiController controller;
    private MobPartyRule partyRule = MobPartyRule.NONE;
    private final java.util.List<MobAiGoalSpec> goals = new java.util.ArrayList<>();

    private Builder() {
    }

    public Builder enabled(boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    public Builder overrideDefault(boolean overrideDefault) {
      this.overrideDefault = overrideDefault;
      return this;
    }

    public Builder aggroRadius(double aggroRadius) {
      if (aggroRadius < 0.0) {
        throw new IllegalArgumentException("aggroRadius must be >= 0");
      }
      this.aggroRadius = aggroRadius;
      return this;
    }

    public Builder leashRadius(double leashRadius) {
      if (leashRadius < 0.0) {
        throw new IllegalArgumentException("leashRadius must be >= 0");
      }
      this.leashRadius = leashRadius;
      return this;
    }

    public Builder leashTeleportRadius(double leashTeleportRadius) {
      if (leashTeleportRadius < 0.0) {
        throw new IllegalArgumentException("leashTeleportRadius must be >= 0");
      }
      this.leashTeleportRadius = leashTeleportRadius;
      return this;
    }

    public Builder aggroTargetMode(MobTargetMode aggroTargetMode) {
      this.aggroTargetMode = Objects.requireNonNull(aggroTargetMode, "aggroTargetMode");
      return this;
    }

    public Builder preferLastAttacker(boolean preferLastAttacker) {
      this.preferLastAttacker = preferLastAttacker;
      return this;
    }

    public Builder targetSwitchCooldownTicks(long targetSwitchCooldownTicks) {
      if (targetSwitchCooldownTicks < 0) {
        throw new IllegalArgumentException("targetSwitchCooldownTicks must be >= 0");
      }
      this.targetSwitchCooldownTicks = targetSwitchCooldownTicks;
      return this;
    }

    public Builder fleeHealthRatio(double fleeHealthRatio) {
      if (fleeHealthRatio < 0.0 || fleeHealthRatio > 1.0) {
        throw new IllegalArgumentException("fleeHealthRatio must be in [0,1]");
      }
      this.fleeHealthRatio = fleeHealthRatio;
      return this;
    }

    public Builder fleeSpeed(double fleeSpeed) {
      if (fleeSpeed < 0.0) {
        throw new IllegalArgumentException("fleeSpeed must be >= 0");
      }
      this.fleeSpeed = fleeSpeed;
      return this;
    }

    public Builder idleWanderRadius(double idleWanderRadius) {
      if (idleWanderRadius < 0.0) {
        throw new IllegalArgumentException("idleWanderRadius must be >= 0");
      }
      this.idleWanderRadius = idleWanderRadius;
      return this;
    }

    public Builder idleWanderIntervalTicks(long idleWanderIntervalTicks) {
      if (idleWanderIntervalTicks <= 0) {
        throw new IllegalArgumentException("idleWanderIntervalTicks must be > 0");
      }
      this.idleWanderIntervalTicks = idleWanderIntervalTicks;
      return this;
    }

    public Builder roamRadius(double roamRadius) {
      if (roamRadius < 0.0) {
        throw new IllegalArgumentException("roamRadius must be >= 0");
      }
      this.roamRadius = roamRadius;
      return this;
    }

    public Builder kiteMinRange(double kiteMinRange) {
      if (kiteMinRange < 0.0) {
        throw new IllegalArgumentException("kiteMinRange must be >= 0");
      }
      this.kiteMinRange = kiteMinRange;
      return this;
    }

    public Builder kiteSpeed(double kiteSpeed) {
      if (kiteSpeed < 0.0) {
        throw new IllegalArgumentException("kiteSpeed must be >= 0");
      }
      this.kiteSpeed = kiteSpeed;
      return this;
    }

    public Builder chaseSpeed(double chaseSpeed) {
      if (chaseSpeed < 0.0) {
        throw new IllegalArgumentException("chaseSpeed must be >= 0");
      }
      this.chaseSpeed = chaseSpeed;
      return this;
    }

    public Builder locomotionMode(MobLocomotionMode locomotionMode) {
      this.locomotionMode = Objects.requireNonNull(locomotionMode, "locomotionMode");
      return this;
    }

    public Builder avoidWater(boolean avoidWater) {
      this.avoidWater = avoidWater;
      return this;
    }

    public Builder avoidLava(boolean avoidLava) {
      this.avoidLava = avoidLava;
      return this;
    }

    public Builder preferGround(boolean preferGround) {
      this.preferGround = preferGround;
      return this;
    }

    public Builder addGuardPoint(org.bukkit.util.Vector point) {
      if (point != null) {
        this.guardPoints.add(point);
      }
      return this;
    }

    public Builder rageHealthRatio(double rageHealthRatio) {
      if (rageHealthRatio < 0.0 || rageHealthRatio > 1.0) {
        throw new IllegalArgumentException("rageHealthRatio must be in [0,1]");
      }
      this.rageHealthRatio = rageHealthRatio;
      return this;
    }

    public Builder rageSpeed(double rageSpeed) {
      if (rageSpeed < 0.0) {
        throw new IllegalArgumentException("rageSpeed must be >= 0");
      }
      this.rageSpeed = rageSpeed;
      return this;
    }

    public Builder controller(MobAiController controller) {
      this.controller = controller;
      return this;
    }

    public Builder partyRule(MobPartyRule partyRule) {
      this.partyRule = Objects.requireNonNull(partyRule, "partyRule");
      return this;
    }

    public Builder addGoal(MobAiGoalSpec goal) {
      if (goal != null) {
        this.goals.add(goal);
      }
      return this;
    }

    public MobAiSpec build() {
      return new MobAiSpec(this);
    }
  }
}
