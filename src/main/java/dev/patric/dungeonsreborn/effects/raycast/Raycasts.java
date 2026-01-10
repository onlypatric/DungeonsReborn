package dev.patric.dungeonsreborn.effects.raycast;

import java.util.Objects;
import java.util.function.Predicate;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class Raycasts {
  private Raycasts() {
  }

  public static RayTraceResult rayTraceEntitiesStopOnBlock(World world, Location start, Vector direction, double maxDistance, double raySize,
      Predicate<Entity> filter) {
    Objects.requireNonNull(world, "world");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(direction, "direction");
    Objects.requireNonNull(filter, "filter");

    if (maxDistance <= 0) {
      return null;
    }
    if (raySize < 0) {
      throw new IllegalArgumentException("raySize must be >= 0");
    }

    RayTraceResult blockHit = world.rayTraceBlocks(start, direction, maxDistance, FluidCollisionMode.NEVER, true);
    if (blockHit != null && blockHit.getHitPosition() != null) {
      double blockDist = start.toVector().distance(blockHit.getHitPosition());
      maxDistance = Math.min(maxDistance, blockDist);
    }

    return world.rayTraceEntities(start, direction, maxDistance, raySize, filter);
  }
}

