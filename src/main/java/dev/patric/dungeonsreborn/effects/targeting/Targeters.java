package dev.patric.dungeonsreborn.effects.targeting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import dev.patric.dungeonsreborn.effects.compat.WorldCompat;
import dev.patric.dungeonsreborn.effects.math.Geometry;
import dev.patric.dungeonsreborn.effects.raycast.Raycasts;
import dev.patric.dungeonsreborn.effects.Vars;
import dev.patric.dungeonsreborn.effects.projectile.ProjectileHit;

public final class Targeters {
  private Targeters() {
  }

  private record CacheEntry(long tick, List<?> values) {
  }

  /**
   * Caches targeter results for the current engine tick, avoiding repeated nearby-entity queries within the same tick.
   */
  public static <T> Targeter<T> cachedPerTick(String key, Targeter<T> delegate) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(delegate, "delegate");
    final String cacheKey = "targeter_cache:" + key;
    return ctx -> {
      long nowTick = ctx.engine().tickNow();
      Object existing = ctx.state().get(cacheKey);
      if (existing instanceof CacheEntry entry && entry.tick == nowTick) {
        @SuppressWarnings("unchecked")
        List<T> cached = (List<T>) entry.values;
        return cached;
      }
      List<T> selected = delegate.select(ctx);
      List<T> stable = List.copyOf(selected);
      ctx.state().put(cacheKey, new CacheEntry(nowTick, stable));
      return stable;
    };
  }

  public static Targeter<LivingEntity> self() {
    return ctx -> List.of(ctx.caster());
  }

  public static Targeter<LivingEntity> contextTarget(String key) {
    Objects.requireNonNull(key, "key");
    return ctx -> {
      Object raw = ctx.state().get(key);
      if (raw instanceof LivingEntity living && living.isValid() && !living.isDead()) {
        return List.of(living);
      }
      return List.of();
    };
  }

  public static Targeter<LivingEntity> lookRay(double maxDistance, double raySize, boolean stopOnBlock, boolean ignoreCaster,
      Predicate<LivingEntity> filter) {
    Objects.requireNonNull(filter, "filter");
    if (maxDistance <= 0) {
      throw new IllegalArgumentException("maxDistance must be > 0");
    }
    if (raySize < 0) {
      throw new IllegalArgumentException("raySize must be >= 0");
    }
    return ctx -> {
      World world = ctx.world();
      if (world == null) {
        return List.of();
      }
      Predicate<Entity> entityFilter = e -> e instanceof LivingEntity living
          && (!ignoreCaster || !e.getUniqueId().equals(ctx.caster().getUniqueId()))
          && filter.test(living);

      RayTraceResult hit = stopOnBlock
          ? Raycasts.rayTraceEntitiesStopOnBlock(world, ctx.origin(), ctx.direction(), maxDistance, raySize, entityFilter)
          : world.rayTraceEntities(ctx.origin(), ctx.direction(), maxDistance, raySize, entityFilter);
      if (hit == null || !(hit.getHitEntity() instanceof LivingEntity living)) {
        return List.of();
      }
      return List.of(living);
    };
  }

  public static Targeter<LivingEntity> lookRay(double maxDistance, double raySize) {
    return lookRay(maxDistance, raySize, true, true, e -> true);
  }

  public static Targeter<LivingEntity> sphere(double radius, boolean ignoreCaster, Predicate<LivingEntity> filter) {
    Objects.requireNonNull(filter, "filter");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    return ctx -> {
      World world = ctx.world();
      if (world == null) {
        return List.of();
      }
      Location origin = ctx.origin();
      double r = radius;

      Predicate<LivingEntity> f = living -> {
        if (ignoreCaster && living.getUniqueId().equals(ctx.caster().getUniqueId())) {
          return false;
        }
        return filter.test(living);
      };

      List<LivingEntity> results = WorldCompat.nearbyLivingEntities(world, origin, r, r, r, f);
      results.sort(Comparator.comparingDouble(le -> le.getLocation().distanceSquared(origin)));
      return results;
    };
  }

  public static Targeter<LivingEntity> cone(double radius, double angleDegrees, boolean ignoreCaster, Predicate<LivingEntity> filter) {
    Objects.requireNonNull(filter, "filter");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (angleDegrees <= 0 || angleDegrees > 180) {
      throw new IllegalArgumentException("angleDegrees must be in (0, 180]");
    }

    Targeter<LivingEntity> sphere = sphere(radius, ignoreCaster, filter);
    return ctx -> {
      Vector forward = ctx.direction().clone();
      forward.setY(0);
      if (forward.lengthSquared() < 1e-9) {
        forward = new Vector(0, 0, 1);
      }
      forward.normalize();

      double cos = Math.cos(Math.toRadians(angleDegrees) / 2.0);
      Location origin = ctx.origin();

      List<LivingEntity> results = new ArrayList<>();
      for (LivingEntity e : sphere.select(ctx)) {
        Vector to = e.getLocation().toVector().subtract(origin.toVector());
        to.setY(0);
        if (to.lengthSquared() < 1e-9) {
          continue;
        }
        to.normalize();
        if (to.dot(forward) >= cos) {
          results.add(e);
        }
      }
      return results;
    };
  }

  /**
   * Axis-aligned box in world coordinates centered on {@link dev.patric.dungeonsreborn.effects.CastContext#origin()}.
   */
  public static Targeter<LivingEntity> box(double xRadius, double yRadius, double zRadius, boolean ignoreCaster, Predicate<LivingEntity> filter) {
    Objects.requireNonNull(filter, "filter");
    if (xRadius < 0 || yRadius < 0 || zRadius < 0) {
      throw new IllegalArgumentException("radii must be >= 0");
    }
    return ctx -> {
      World world = ctx.world();
      if (world == null) {
        return List.of();
      }
      Location origin = ctx.origin();
      Predicate<LivingEntity> f = living -> {
        if (ignoreCaster && living.getUniqueId().equals(ctx.caster().getUniqueId())) {
          return false;
        }
        return filter.test(living);
      };

      List<LivingEntity> results = WorldCompat.nearbyLivingEntities(world, origin, xRadius, yRadius, zRadius, f);
      results.sort(Comparator.comparingDouble(le -> le.getLocation().distanceSquared(origin)));
      return results;
    };
  }

  /**
   * Vertical cylinder centered at {@link dev.patric.dungeonsreborn.effects.CastContext#origin()}.
   */
  public static Targeter<LivingEntity> cylinder(double radius, double halfHeight, boolean ignoreCaster, Predicate<LivingEntity> filter) {
    Objects.requireNonNull(filter, "filter");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (halfHeight < 0) {
      throw new IllegalArgumentException("halfHeight must be >= 0");
    }
    return ctx -> {
      World world = ctx.world();
      if (world == null) {
        return List.of();
      }
      Location origin = ctx.origin();
      double r = radius;
      double y = halfHeight;

      Predicate<LivingEntity> f = living -> {
        if (ignoreCaster && living.getUniqueId().equals(ctx.caster().getUniqueId())) {
          return false;
        }
        if (!filter.test(living)) {
          return false;
        }
        Location loc = living.getLocation();
        double dy = loc.getY() - origin.getY();
        if (Math.abs(dy) > y) {
          return false;
        }
        double dx = loc.getX() - origin.getX();
        double dz = loc.getZ() - origin.getZ();
        return (dx * dx + dz * dz) <= (r * r);
      };

      List<LivingEntity> results = WorldCompat.nearbyLivingEntities(world, origin, r, y, r, f);
      results.sort(Comparator.comparingDouble(le -> le.getLocation().distanceSquared(origin)));
      return results;
    };
  }

  /**
   * Capsule-shaped selection along the caster direction, from origin to {@code maxDistance}.
   * <p>
   * If {@code stopOnBlock} is true, the capsule ends at the first solid block hit.
   */
  public static Targeter<LivingEntity> capsuleRay(double maxDistance, double radius, boolean stopOnBlock, boolean ignoreCaster,
      Predicate<LivingEntity> filter) {
    Objects.requireNonNull(filter, "filter");
    if (maxDistance <= 0) {
      throw new IllegalArgumentException("maxDistance must be > 0");
    }
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }

    return ctx -> {
      World world = ctx.world();
      if (world == null) {
        return List.of();
      }

      Vector dir = ctx.direction().clone();
      if (dir.lengthSquared() < 1e-9) {
        dir.setX(0).setY(0).setZ(1);
      }
      dir.normalize();

      Vector start = ctx.origin().toVector();
      double len = maxDistance;
      if (stopOnBlock) {
        RayTraceResult blockHit = world.rayTraceBlocks(ctx.origin(), dir, maxDistance, org.bukkit.FluidCollisionMode.NEVER, true);
        if (blockHit != null && blockHit.getHitPosition() != null) {
          len = Math.min(len, start.distance(blockHit.getHitPosition()));
        }
      }
      Vector end = start.clone().add(dir.clone().multiply(len));

      Vector mid = start.clone().add(end).multiply(0.5);
      double halfLen = start.distance(end) / 2.0;
      double query = halfLen + radius;
      Location center = new Location(world, mid.getX(), mid.getY(), mid.getZ());

      double r2 = radius * radius;
      List<LivingEntity> results = new ArrayList<>();
      for (Entity e : world.getNearbyEntities(center, query, query, query)) {
        if (!(e instanceof LivingEntity living)) {
          continue;
        }
        if (ignoreCaster && e.getUniqueId().equals(ctx.caster().getUniqueId())) {
          continue;
        }
        if (!filter.test(living)) {
          continue;
        }
        double d2 = Geometry.distanceSquaredPointToSegment(living.getLocation().toVector(), start, end);
        if (d2 <= r2) {
          results.add(living);
        }
      }
      results.sort(Comparator.comparingDouble(le -> le.getLocation().distanceSquared(ctx.origin())));
      return results;
    };
  }

  public static Targeter<LivingEntity> nearest(double radius, boolean ignoreCaster, Predicate<LivingEntity> filter) {
    Targeter<LivingEntity> sphere = sphere(radius, ignoreCaster, filter);
    return ctx -> {
      List<LivingEntity> results = sphere.select(ctx);
      return results.isEmpty() ? List.of() : List.of(results.getFirst());
    };
  }

  public static Targeter<LivingEntity> projectileHit() {
    return ctx -> {
      Object v = ctx.state().get(Vars.PROJECTILE_LAST_HIT);
      if (!(v instanceof ProjectileHit hit)) {
        return List.of();
      }
      if (hit.hitEntity() == null) {
        return List.of();
      }
      return List.of(hit.hitEntity());
    };
  }
}
