package dev.patric.dungeonsreborn.effects.projectile;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import dev.patric.dungeonsreborn.effects.combat.ProjectileFamily;

public record ProjectileTelemetry(
    UUID projectileId,
    ProjectileFamily family,
    String projectileType,
    String projectileKind,
    LivingEntity shooter,
    LivingEntity victim,
    Location impactLocation,
    Vector impactDirection,
    double distance,
    double speed,
    double drawForce,
    int pierceLevel,
    int inGroundTicks,
    boolean critical,
    boolean charged,
    boolean piercing,
    boolean shotFromCrossbow,
    boolean shooterIsPlayer,
    Material hitBlockMaterial,
    BlockFace hitBlockFace) {

  public ProjectileTelemetry {
    Objects.requireNonNull(projectileId, "projectileId");
    family = family == null ? ProjectileFamily.CUSTOM : family;
    projectileType = sanitize(projectileType, family == ProjectileFamily.CUSTOM ? "CUSTOM" : "UNKNOWN");
    projectileKind = sanitize(projectileKind, "");
    if (impactLocation != null) {
      impactLocation = impactLocation.clone();
    }
    if (impactDirection != null) {
      impactDirection = impactDirection.clone();
      if (impactDirection.lengthSquared() > 1.0e-9) {
        impactDirection.normalize();
      }
    }
    if (!Double.isFinite(distance)) {
      distance = 0.0;
    }
    if (!Double.isFinite(speed)) {
      speed = 0.0;
    }
    if (!Double.isFinite(drawForce)) {
      drawForce = 0.0;
    }
    pierceLevel = Math.max(0, pierceLevel);
    inGroundTicks = Math.max(0, inGroundTicks);
  }

  public static Builder builder(UUID projectileId, ProjectileFamily family, String projectileType) {
    return new Builder(projectileId, family, projectileType);
  }

  public ProjectileTelemetry withImpact(Block block) {
    if (block == null) {
      return this;
    }
    return new ProjectileTelemetry(
        projectileId,
        family,
        projectileType,
        projectileKind,
        shooter,
        victim,
        impactLocation == null ? block.getLocation().add(0.5, 0.5, 0.5) : impactLocation,
        impactDirection,
        distance,
        speed,
        drawForce,
        pierceLevel,
        inGroundTicks,
        critical,
        charged,
        piercing,
        shotFromCrossbow,
        shooterIsPlayer,
        block.getType(),
        hitBlockFace);
  }

  public static final class Builder {
    private final UUID projectileId;
    private final ProjectileFamily family;
    private final String projectileType;
    private String projectileKind = "";
    private LivingEntity shooter;
    private LivingEntity victim;
    private Location impactLocation;
    private Vector impactDirection;
    private double distance;
    private double speed;
    private double drawForce;
    private int pierceLevel;
    private int inGroundTicks;
    private boolean critical;
    private boolean charged;
    private boolean piercing;
    private boolean shotFromCrossbow;
    private boolean shooterIsPlayer;
    private Material hitBlockMaterial;
    private BlockFace hitBlockFace;

    private Builder(UUID projectileId, ProjectileFamily family, String projectileType) {
      this.projectileId = projectileId;
      this.family = family;
      this.projectileType = projectileType;
    }

    public Builder kind(String projectileKind) {
      this.projectileKind = projectileKind;
      return this;
    }

    public Builder shooter(LivingEntity shooter) {
      this.shooter = shooter;
      this.shooterIsPlayer = shooter instanceof org.bukkit.entity.Player;
      return this;
    }

    public Builder victim(LivingEntity victim) {
      this.victim = victim;
      return this;
    }

    public Builder impact(Location impactLocation, Vector impactDirection) {
      this.impactLocation = impactLocation;
      this.impactDirection = impactDirection;
      return this;
    }

    public Builder movement(double distance, double speed) {
      this.distance = distance;
      this.speed = speed;
      return this;
    }

    public Builder drawForce(double drawForce) {
      this.drawForce = drawForce;
      return this;
    }

    public Builder pierce(int level, boolean piercing) {
      this.pierceLevel = level;
      this.piercing = piercing;
      return this;
    }

    public Builder inGroundTicks(int ticks) {
      this.inGroundTicks = ticks;
      return this;
    }

    public Builder critical(boolean critical) {
      this.critical = critical;
      return this;
    }

    public Builder charged(boolean charged) {
      this.charged = charged;
      return this;
    }

    public Builder shotFromCrossbow(boolean shotFromCrossbow) {
      this.shotFromCrossbow = shotFromCrossbow;
      return this;
    }

    public Builder hitBlock(Block block, BlockFace face) {
      this.hitBlockMaterial = block == null ? null : block.getType();
      this.hitBlockFace = face;
      return this;
    }

    public ProjectileTelemetry build() {
      return new ProjectileTelemetry(
          projectileId,
          family,
          projectileType,
          projectileKind,
          shooter,
          victim,
          impactLocation,
          impactDirection,
          distance,
          speed,
          drawForce,
          pierceLevel,
          inGroundTicks,
          critical,
          charged,
          piercing,
          shotFromCrossbow,
          shooterIsPlayer,
          hitBlockMaterial,
          hitBlockFace);
    }
  }

  private static String sanitize(String raw, String def) {
    if (raw == null) {
      return def;
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
      return def;
    }
    return trimmed;
  }
}
