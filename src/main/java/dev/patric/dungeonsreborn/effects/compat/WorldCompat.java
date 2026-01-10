package dev.patric.dungeonsreborn.effects.compat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/**
 * Version-compatibility helpers for Bukkit/Paper APIs that differ across versions.
 */
public final class WorldCompat {
  private WorldCompat() {
  }

  private static final Method NEARBY_LIVING_ENTITIES_METHOD = resolveNearbyLivingEntitiesMethod();

  private static Method resolveNearbyLivingEntitiesMethod() {
    try {
      return World.class.getMethod("getNearbyLivingEntities", Location.class, double.class, double.class, double.class, Predicate.class);
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  public static List<LivingEntity> nearbyLivingEntities(World world, Location center, double xRadius, double yRadius, double zRadius,
      Predicate<LivingEntity> filter) {
    Objects.requireNonNull(world, "world");
    Objects.requireNonNull(center, "center");
    Objects.requireNonNull(filter, "filter");
    if (xRadius < 0 || yRadius < 0 || zRadius < 0) {
      throw new IllegalArgumentException("radii must be >= 0");
    }

    if (NEARBY_LIVING_ENTITIES_METHOD != null) {
      try {
        Object result = NEARBY_LIVING_ENTITIES_METHOD.invoke(world, center, xRadius, yRadius, zRadius, filter);
        if (result instanceof Iterable<?> it) {
          ArrayList<LivingEntity> list = new ArrayList<>();
          for (Object o : it) {
            if (o instanceof LivingEntity living) {
              list.add(living);
            }
          }
          return list;
        }
      } catch (ReflectiveOperationException ignored) {
      }
    }

    ArrayList<LivingEntity> list = new ArrayList<>();
    for (Entity e : world.getNearbyEntities(center, xRadius, yRadius, zRadius)) {
      if (e instanceof LivingEntity living && filter.test(living)) {
        list.add(living);
      }
    }
    return list;
  }
}

