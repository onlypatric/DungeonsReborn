package dev.patric.dungeonsreborn.effects.targeting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import dev.patric.dungeonsreborn.effects.compat.WorldCompat;
import dev.patric.dungeonsreborn.effects.math.Geometry;
import dev.patric.dungeonsreborn.effects.raycast.Raycasts;
import dev.patric.dungeonsreborn.effects.Vars;
import dev.patric.dungeonsreborn.effects.projectile.ProjectileHit;
import dev.patric.dungeonsreborn.effects.projectile.ProjectileTelemetry;

public final class Targeters {
  private Targeters() {
  }

  public enum SampleMode {
    RANDOM,
    NEAREST,
    WEIGHT_DISTANCE,
    WEIGHT_THREAT
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

      return sphereAt(world, origin, r, f);
    };
  }

  public static Targeter<LivingEntity> lineOfSight(double radius, boolean ignoreCaster, Predicate<LivingEntity> filter) {
    Objects.requireNonNull(filter, "filter");
    Targeter<LivingEntity> sphere = sphere(radius, ignoreCaster, filter);
    return ctx -> {
      World world = ctx.world();
      if (world == null) {
        return List.of();
      }
      Location origin = ctx.origin();
      List<LivingEntity> results = new ArrayList<>();
      for (LivingEntity living : sphere.select(ctx)) {
        if (hasLineOfSight(world, origin, living)) {
          results.add(living);
        }
      }
      return results;
    };
  }

  public static Targeter<LivingEntity> groundSphere(double radius, double maxDrop, boolean ignoreCaster, Predicate<LivingEntity> filter) {
    Objects.requireNonNull(filter, "filter");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (maxDrop < 0) {
      throw new IllegalArgumentException("maxDrop must be >= 0");
    }
    return ctx -> {
      World world = ctx.world();
      if (world == null) {
        return List.of();
      }
      Location origin = ctx.origin();
      Location snapped = snapToGround(world, origin, maxDrop);
      Predicate<LivingEntity> f = living -> {
        if (ignoreCaster && living.getUniqueId().equals(ctx.caster().getUniqueId())) {
          return false;
        }
        return filter.test(living);
      };
      return sphereAt(world, snapped, radius, f);
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

  public static Targeter<LivingEntity> nearestWithinAngle(double radius, double angleDegrees, boolean ignoreCaster,
      Predicate<LivingEntity> filter) {
    Targeter<LivingEntity> cone = cone(radius, angleDegrees, ignoreCaster, filter);
    return ctx -> {
      List<LivingEntity> results = cone.select(ctx);
      return results.isEmpty() ? List.of() : List.of(results.getFirst());
    };
  }

  public static Targeter<LivingEntity> chain(double radius, int maxTargets, boolean ignoreCaster, Predicate<LivingEntity> filter) {
    Objects.requireNonNull(filter, "filter");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (maxTargets < 0) {
      throw new IllegalArgumentException("maxTargets must be >= 0");
    }
    return ctx -> {
      World world = ctx.world();
      if (world == null) {
        return List.of();
      }
      List<LivingEntity> selected = new ArrayList<>();
      Set<java.util.UUID> seen = new HashSet<>();
      Location current = ctx.origin().clone();
      for (int i = 0; i < maxTargets; i++) {
        LivingEntity next = nearestFrom(world, current, radius, ctx.caster().getUniqueId(), ignoreCaster, filter, seen);
        if (next == null) {
          break;
        }
        selected.add(next);
        seen.add(next.getUniqueId());
        current = next.getLocation();
      }
      return selected;
    };
  }

  public static Targeter<LivingEntity> sample(Targeter<LivingEntity> base, int count, SampleMode mode) {
    Objects.requireNonNull(base, "base");
    Objects.requireNonNull(mode, "mode");
    return ctx -> sampleTargets(base.select(ctx), count, mode, ctx.origin());
  }

  public static Targeter<LivingEntity> union(Targeter<LivingEntity> left, Targeter<LivingEntity> right) {
    Objects.requireNonNull(left, "left");
    Objects.requireNonNull(right, "right");
    return ctx -> {
      List<LivingEntity> results = new ArrayList<>();
      Set<java.util.UUID> seen = new HashSet<>();
      for (LivingEntity living : left.select(ctx)) {
        if (seen.add(living.getUniqueId())) {
          results.add(living);
        }
      }
      for (LivingEntity living : right.select(ctx)) {
        if (seen.add(living.getUniqueId())) {
          results.add(living);
        }
      }
      return results;
    };
  }

  public static Targeter<LivingEntity> intersection(Targeter<LivingEntity> left, Targeter<LivingEntity> right) {
    Objects.requireNonNull(left, "left");
    Objects.requireNonNull(right, "right");
    return ctx -> {
      Set<java.util.UUID> rightIds = new HashSet<>();
      for (LivingEntity living : right.select(ctx)) {
        rightIds.add(living.getUniqueId());
      }
      List<LivingEntity> results = new ArrayList<>();
      for (LivingEntity living : left.select(ctx)) {
        if (rightIds.contains(living.getUniqueId())) {
          results.add(living);
        }
      }
      return results;
    };
  }

  public static Targeter<LivingEntity> difference(Targeter<LivingEntity> left, Targeter<LivingEntity> right) {
    Objects.requireNonNull(left, "left");
    Objects.requireNonNull(right, "right");
    return ctx -> {
      Set<java.util.UUID> rightIds = new HashSet<>();
      for (LivingEntity living : right.select(ctx)) {
        rightIds.add(living.getUniqueId());
      }
      List<LivingEntity> results = new ArrayList<>();
      for (LivingEntity living : left.select(ctx)) {
        if (!rightIds.contains(living.getUniqueId())) {
          results.add(living);
        }
      }
      return results;
    };
  }

  public static Targeter<LivingEntity> projectileHit() {
    return ctx -> {
      Object v = ctx.state().get(Vars.PROJECTILE_LAST_HIT);
      if (v instanceof ProjectileHit hit) {
        if (hit.hitEntity() == null) {
          return List.of();
        }
        return List.of(hit.hitEntity());
      }
      if (v instanceof ProjectileTelemetry telemetry) {
        if (telemetry.victim() == null) {
          return List.of();
        }
        return List.of(telemetry.victim());
      }
      return List.of();
    };
  }

  private static List<LivingEntity> sphereAt(World world, Location origin, double radius, Predicate<LivingEntity> filter) {
    List<LivingEntity> results = WorldCompat.nearbyLivingEntities(world, origin, radius, radius, radius, filter);
    results.sort(Comparator.comparingDouble(le -> le.getLocation().distanceSquared(origin)));
    return results;
  }

  private static boolean hasLineOfSight(World world, Location origin, LivingEntity target) {
    Location targetEye = target.getEyeLocation();
    Vector dir = targetEye.toVector().subtract(origin.toVector());
    double dist = dir.length();
    if (dist < 1e-6) {
      return true;
    }
    dir.normalize();
    RayTraceResult hit = world.rayTraceBlocks(origin, dir, dist, FluidCollisionMode.NEVER, true);
    return hit == null;
  }

  private static Location snapToGround(World world, Location origin, double maxDrop) {
    if (maxDrop <= 0) {
      return origin.clone();
    }
    RayTraceResult hit = world.rayTraceBlocks(origin, new Vector(0, -1, 0), maxDrop, FluidCollisionMode.NEVER, true);
    if (hit == null || hit.getHitPosition() == null) {
      return origin.clone();
    }
    return new Location(world, origin.getX(), hit.getHitPosition().getY() + 1.0, origin.getZ());
  }

  private static LivingEntity nearestFrom(World world, Location origin, double radius, java.util.UUID casterId,
      boolean ignoreCaster, Predicate<LivingEntity> filter, Set<java.util.UUID> seen) {
    double best = Double.MAX_VALUE;
    LivingEntity bestEntity = null;
    for (Entity entity : world.getNearbyEntities(origin, radius, radius, radius)) {
      if (!(entity instanceof LivingEntity living)) {
        continue;
      }
      if (ignoreCaster && entity.getUniqueId().equals(casterId)) {
        continue;
      }
      if (seen.contains(entity.getUniqueId())) {
        continue;
      }
      if (!filter.test(living)) {
        continue;
      }
      double d = entity.getLocation().distanceSquared(origin);
      if (d < best) {
        best = d;
        bestEntity = living;
      }
    }
    return bestEntity;
  }

  private static List<LivingEntity> sampleTargets(List<LivingEntity> candidates, int count, SampleMode mode, Location origin) {
    if (count <= 0 || candidates.isEmpty()) {
      return List.of();
    }
    if (count >= candidates.size()) {
      return List.copyOf(candidates);
    }
    return switch (mode) {
      case RANDOM -> sampleRandom(candidates, count);
      case NEAREST -> sampleNearest(candidates, count, origin);
      case WEIGHT_DISTANCE -> sampleWeighted(candidates, count, e -> weightByDistance(e, origin));
      case WEIGHT_THREAT -> sampleWeighted(candidates, count, Targeters::weightByThreat);
    };
  }

  private static List<LivingEntity> sampleRandom(List<LivingEntity> candidates, int count) {
    List<LivingEntity> pool = new ArrayList<>(candidates);
    java.util.Collections.shuffle(pool, ThreadLocalRandom.current());
    return List.copyOf(pool.subList(0, Math.min(count, pool.size())));
  }

  private static List<LivingEntity> sampleNearest(List<LivingEntity> candidates, int count, Location origin) {
    List<LivingEntity> pool = new ArrayList<>(candidates);
    if (origin != null) {
      pool.sort(Comparator.comparingDouble(le -> le.getLocation().distanceSquared(origin)));
    }
    return List.copyOf(pool.subList(0, Math.min(count, pool.size())));
  }

  private static List<LivingEntity> sampleWeighted(List<LivingEntity> candidates, int count,
      ToDoubleFunction<LivingEntity> weight) {
    List<LivingEntity> pool = new ArrayList<>(candidates);
    List<LivingEntity> out = new ArrayList<>();
    for (int i = 0; i < count && !pool.isEmpty(); i++) {
      double total = 0.0;
      for (LivingEntity living : pool) {
        total += Math.max(0.0, weight.applyAsDouble(living));
      }
      if (total <= 0.0) {
        out.addAll(sampleRandom(pool, Math.min(count - out.size(), pool.size())));
        break;
      }
      double roll = ThreadLocalRandom.current().nextDouble(total);
      double acc = 0.0;
      LivingEntity picked = null;
      for (LivingEntity living : pool) {
        acc += Math.max(0.0, weight.applyAsDouble(living));
        if (roll <= acc) {
          picked = living;
          break;
        }
      }
      if (picked == null) {
        picked = pool.getFirst();
      }
      out.add(picked);
      pool.remove(picked);
    }
    return List.copyOf(out);
  }

  private static double weightByDistance(LivingEntity living, Location origin) {
    if (origin == null) {
      return 1.0;
    }
    double dist = origin.distance(living.getLocation());
    return 1.0 / Math.max(0.25, dist);
  }

  private static double weightByThreat(LivingEntity living) {
    var attr = living.getAttribute(Attribute.MAX_HEALTH);
    double maxHealth = attr != null ? attr.getValue() : 20.0;
    double attack = 0.0;
    if (living.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
      attack = living.getAttribute(Attribute.ATTACK_DAMAGE).getValue();
    }
    return Math.max(0.1, 1.0 + maxHealth + attack);
  }
}
