package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

import dev.patric.dungeonsreborn.mobs.ai.MobAiEngineMode;
import dev.patric.dungeonsreborn.mobs.ai.MobAiHooksSpec;
import dev.patric.dungeonsreborn.mobs.ai.MobAiProfile;

public final class MobAiSpec {
  private final MobAiEngineMode engineMode;
  private final MobAiProfile profile;
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
  private final boolean openDoors;
  private final boolean breakDoors;
  private final boolean avoidSunlight;
  private final boolean avoidPowderSnow;
  private final boolean avoidCactus;
  private final double callForHelpRadius;
  private final double assistRadius;
  private final long stateTransitionCooldownTicks;
  private final boolean preferGround;
  private final java.util.List<org.bukkit.util.Vector> guardPoints;
  private final double rageHealthRatio;
  private final double rageSpeed;
  private final MobAiController controller;
  private final MobPartyRule partyRule;
  private final MobAiHooksSpec hooks;
  private final java.util.List<MobAiGoalSpec> goals;

  private MobAiSpec(Builder builder) {
    this.engineMode = builder.engineMode;
    this.profile = builder.profile;
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
    this.openDoors = builder.openDoors;
    this.breakDoors = builder.breakDoors;
    this.avoidSunlight = builder.avoidSunlight;
    this.avoidPowderSnow = builder.avoidPowderSnow;
    this.avoidCactus = builder.avoidCactus;
    this.callForHelpRadius = builder.callForHelpRadius;
    this.assistRadius = builder.assistRadius;
    this.stateTransitionCooldownTicks = builder.stateTransitionCooldownTicks;
    this.preferGround = builder.preferGround;
    this.guardPoints = java.util.List.copyOf(builder.guardPoints);
    this.rageHealthRatio = builder.rageHealthRatio;
    this.rageSpeed = builder.rageSpeed;
    this.controller = builder.controller;
    this.partyRule = builder.partyRule;
    this.hooks = builder.hooks == null ? MobAiHooksSpec.empty() : builder.hooks;
    this.goals = java.util.List.copyOf(builder.goals);
  }

  public MobAiEngineMode engineMode() {
    return engineMode;
  }

  public MobAiProfile profile() {
    return profile;
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

  public boolean openDoors() {
    return openDoors;
  }

  public boolean breakDoors() {
    return breakDoors;
  }

  public boolean avoidSunlight() {
    return avoidSunlight;
  }

  public boolean avoidPowderSnow() {
    return avoidPowderSnow;
  }

  public boolean avoidCactus() {
    return avoidCactus;
  }

  public double callForHelpRadius() {
    return callForHelpRadius;
  }

  public double assistRadius() {
    return assistRadius;
  }

  public long stateTransitionCooldownTicks() {
    return stateTransitionCooldownTicks;
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

  public MobAiHooksSpec hooks() {
    return hooks;
  }

  public java.util.List<MobAiGoalSpec> goals() {
    return goals;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(MobAiSpec template) {
    return template == null ? new Builder() : new Builder(template);
  }

  public static final class Builder {
    private MobAiEngineMode engineMode = MobAiEngineMode.LEGACY;
    private MobAiProfile profile = MobAiProfile.NEUTRAL;
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
    private boolean openDoors;
    private boolean breakDoors;
    private boolean avoidSunlight;
    private boolean avoidPowderSnow = true;
    private boolean avoidCactus = true;
    private double callForHelpRadius;
    private double assistRadius;
    private long stateTransitionCooldownTicks = 10L;
    private boolean preferGround = true;
    private final java.util.List<org.bukkit.util.Vector> guardPoints = new java.util.ArrayList<>();
    private double rageHealthRatio;
    private double rageSpeed = 0.35;
    private MobAiController controller;
    private MobPartyRule partyRule = MobPartyRule.NONE;
    private MobAiHooksSpec hooks = MobAiHooksSpec.empty();
    private final java.util.List<MobAiGoalSpec> goals = new java.util.ArrayList<>();

    private Builder() {
    }

    private Builder(MobAiSpec template) {
      this.engineMode = template.engineMode;
      this.profile = template.profile;
      this.enabled = template.enabled;
      this.overrideDefault = template.overrideDefault;
      this.aggroRadius = template.aggroRadius;
      this.leashRadius = template.leashRadius;
      this.leashTeleportRadius = template.leashTeleportRadius;
      this.aggroTargetMode = template.aggroTargetMode;
      this.preferLastAttacker = template.preferLastAttacker;
      this.targetSwitchCooldownTicks = template.targetSwitchCooldownTicks;
      this.fleeHealthRatio = template.fleeHealthRatio;
      this.fleeSpeed = template.fleeSpeed;
      this.idleWanderRadius = template.idleWanderRadius;
      this.idleWanderIntervalTicks = template.idleWanderIntervalTicks;
      this.roamRadius = template.roamRadius;
      this.kiteMinRange = template.kiteMinRange;
      this.kiteSpeed = template.kiteSpeed;
      this.chaseSpeed = template.chaseSpeed;
      this.locomotionMode = template.locomotionMode;
      this.avoidWater = template.avoidWater;
      this.avoidLava = template.avoidLava;
      this.openDoors = template.openDoors;
      this.breakDoors = template.breakDoors;
      this.avoidSunlight = template.avoidSunlight;
      this.avoidPowderSnow = template.avoidPowderSnow;
      this.avoidCactus = template.avoidCactus;
      this.callForHelpRadius = template.callForHelpRadius;
      this.assistRadius = template.assistRadius;
      this.stateTransitionCooldownTicks = template.stateTransitionCooldownTicks;
      this.preferGround = template.preferGround;
      this.guardPoints.addAll(template.guardPoints);
      this.rageHealthRatio = template.rageHealthRatio;
      this.rageSpeed = template.rageSpeed;
      this.controller = template.controller;
      this.partyRule = template.partyRule;
      this.hooks = template.hooks;
      this.goals.addAll(template.goals);
    }

    public Builder engineMode(MobAiEngineMode engineMode) {
      this.engineMode = Objects.requireNonNull(engineMode, "engineMode");
      return this;
    }

    public Builder profile(MobAiProfile profile) {
      this.profile = Objects.requireNonNull(profile, "profile");
      return this;
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

    public Builder openDoors(boolean openDoors) {
      this.openDoors = openDoors;
      return this;
    }

    public Builder breakDoors(boolean breakDoors) {
      this.breakDoors = breakDoors;
      return this;
    }

    public Builder avoidSunlight(boolean avoidSunlight) {
      this.avoidSunlight = avoidSunlight;
      return this;
    }

    public Builder avoidPowderSnow(boolean avoidPowderSnow) {
      this.avoidPowderSnow = avoidPowderSnow;
      return this;
    }

    public Builder avoidCactus(boolean avoidCactus) {
      this.avoidCactus = avoidCactus;
      return this;
    }

    public Builder callForHelpRadius(double callForHelpRadius) {
      if (callForHelpRadius < 0.0) {
        throw new IllegalArgumentException("callForHelpRadius must be >= 0");
      }
      this.callForHelpRadius = callForHelpRadius;
      return this;
    }

    public Builder assistRadius(double assistRadius) {
      if (assistRadius < 0.0) {
        throw new IllegalArgumentException("assistRadius must be >= 0");
      }
      this.assistRadius = assistRadius;
      return this;
    }

    public Builder stateTransitionCooldownTicks(long stateTransitionCooldownTicks) {
      if (stateTransitionCooldownTicks < 0L) {
        throw new IllegalArgumentException("stateTransitionCooldownTicks must be >= 0");
      }
      this.stateTransitionCooldownTicks = stateTransitionCooldownTicks;
      return this;
    }

    public Builder preferGround(boolean preferGround) {
      this.preferGround = preferGround;
      return this;
    }

    public Builder clearGuardPoints() {
      this.guardPoints.clear();
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

    public Builder hooks(MobAiHooksSpec hooks) {
      this.hooks = hooks == null ? MobAiHooksSpec.empty() : hooks;
      return this;
    }

    public Builder clearGoals() {
      this.goals.clear();
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
