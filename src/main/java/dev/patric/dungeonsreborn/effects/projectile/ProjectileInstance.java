package dev.patric.dungeonsreborn.effects.projectile;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * Mutable projectile state for attaching VFX (trail, helix, etc.) to a running projectile.
 */
public final class ProjectileInstance {
  private Location location;
  private Vector direction;
  private double traveled;

  ProjectileInstance(Location location, Vector direction) {
    this.location = Objects.requireNonNull(location, "location");
    this.direction = Objects.requireNonNull(direction, "direction");
  }

  public Location location() {
    return location.clone();
  }

  public Vector direction() {
    return direction.clone();
  }

  public double traveled() {
    return traveled;
  }

  void update(Location location, Vector direction, double traveled) {
    this.location = Objects.requireNonNull(location, "location");
    this.direction = Objects.requireNonNull(direction, "direction");
    this.traveled = traveled;
  }
}

