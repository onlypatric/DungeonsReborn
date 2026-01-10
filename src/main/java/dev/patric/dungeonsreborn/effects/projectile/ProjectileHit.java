package dev.patric.dungeonsreborn.effects.projectile;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import dev.patric.dungeonsreborn.effects.CastContext;

public record ProjectileHit(
    CastContext cast,
    Location location,
    Vector direction,
    double traveled,
    LivingEntity hitEntity,
    Block hitBlock) {

  public ProjectileHit {
    Objects.requireNonNull(cast, "cast");
    Objects.requireNonNull(location, "location");
    Objects.requireNonNull(direction, "direction");
  }

  public boolean isEntityHit() {
    return hitEntity != null;
  }

  public boolean isBlockHit() {
    return hitBlock != null;
  }
}

