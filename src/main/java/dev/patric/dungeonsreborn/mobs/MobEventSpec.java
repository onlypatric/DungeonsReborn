package dev.patric.dungeonsreborn.mobs;

public final class MobEventSpec {
  private final String onHit;
  private final String onHurt;
  private final String onTarget;
  private final String onKill;
  private final String onPhaseChange;
  private final String onNear;
  private final double onNearRadius;
  private final long onNearCooldownTicks;
  private final String onSpawnTick;
  private final long onSpawnTickIntervalTicks;
  private final String onDespawn;
  private final String onIdle;
  private final long onIdleIntervalTicks;
  private final String onStuck;
  private final long onStuckIntervalTicks;
  private final double onStuckDistance;

  private MobEventSpec(Builder builder) {
    this.onHit = builder.onHit;
    this.onHurt = builder.onHurt;
    this.onTarget = builder.onTarget;
    this.onKill = builder.onKill;
    this.onPhaseChange = builder.onPhaseChange;
    this.onNear = builder.onNear;
    this.onNearRadius = builder.onNearRadius;
    this.onNearCooldownTicks = builder.onNearCooldownTicks;
    this.onSpawnTick = builder.onSpawnTick;
    this.onSpawnTickIntervalTicks = builder.onSpawnTickIntervalTicks;
    this.onDespawn = builder.onDespawn;
    this.onIdle = builder.onIdle;
    this.onIdleIntervalTicks = builder.onIdleIntervalTicks;
    this.onStuck = builder.onStuck;
    this.onStuckIntervalTicks = builder.onStuckIntervalTicks;
    this.onStuckDistance = builder.onStuckDistance;
  }

  public String onHit() {
    return onHit;
  }

  public String onHurt() {
    return onHurt;
  }

  public String onTarget() {
    return onTarget;
  }

  public String onKill() {
    return onKill;
  }

  public String onPhaseChange() {
    return onPhaseChange;
  }

  public String onNear() {
    return onNear;
  }

  public double onNearRadius() {
    return onNearRadius;
  }

  public long onNearCooldownTicks() {
    return onNearCooldownTicks;
  }

  public String onSpawnTick() {
    return onSpawnTick;
  }

  public long onSpawnTickIntervalTicks() {
    return onSpawnTickIntervalTicks;
  }

  public String onDespawn() {
    return onDespawn;
  }

  public String onIdle() {
    return onIdle;
  }

  public long onIdleIntervalTicks() {
    return onIdleIntervalTicks;
  }

  public String onStuck() {
    return onStuck;
  }

  public long onStuckIntervalTicks() {
    return onStuckIntervalTicks;
  }

  public double onStuckDistance() {
    return onStuckDistance;
  }

  public boolean isEmpty() {
    return onHit == null
        && onHurt == null
        && onTarget == null
        && onKill == null
        && onPhaseChange == null
        && onNear == null
        && onSpawnTick == null
        && onDespawn == null
        && onIdle == null
        && onStuck == null;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String onHit;
    private String onHurt;
    private String onTarget;
    private String onKill;
    private String onPhaseChange;
    private String onNear;
    private double onNearRadius = 6.0;
    private long onNearCooldownTicks = 40L;
    private String onSpawnTick;
    private long onSpawnTickIntervalTicks = 20L;
    private String onDespawn;
    private String onIdle;
    private long onIdleIntervalTicks = 80L;
    private String onStuck;
    private long onStuckIntervalTicks = 60L;
    private double onStuckDistance = 0.35;

    private Builder() {
    }

    public Builder onHit(String abilityId) {
      this.onHit = abilityId;
      return this;
    }

    public Builder onHurt(String abilityId) {
      this.onHurt = abilityId;
      return this;
    }

    public Builder onTarget(String abilityId) {
      this.onTarget = abilityId;
      return this;
    }

    public Builder onKill(String abilityId) {
      this.onKill = abilityId;
      return this;
    }

    public Builder onPhaseChange(String abilityId) {
      this.onPhaseChange = abilityId;
      return this;
    }

    public Builder onNear(String abilityId, double radius, long cooldownTicks) {
      this.onNear = abilityId;
      this.onNearRadius = Math.max(0.0, radius);
      this.onNearCooldownTicks = Math.max(0L, cooldownTicks);
      return this;
    }

    public Builder onSpawnTick(String abilityId, long intervalTicks) {
      this.onSpawnTick = abilityId;
      this.onSpawnTickIntervalTicks = Math.max(1L, intervalTicks);
      return this;
    }

    public Builder onDespawn(String abilityId) {
      this.onDespawn = abilityId;
      return this;
    }

    public Builder onIdle(String abilityId, long intervalTicks) {
      this.onIdle = abilityId;
      this.onIdleIntervalTicks = Math.max(1L, intervalTicks);
      return this;
    }

    public Builder onStuck(String abilityId, long intervalTicks, double distance) {
      this.onStuck = abilityId;
      this.onStuckIntervalTicks = Math.max(1L, intervalTicks);
      this.onStuckDistance = Math.max(0.0, distance);
      return this;
    }

    public MobEventSpec build() {
      return new MobEventSpec(this);
    }
  }
}
