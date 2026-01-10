package dev.patric.dungeonsreborn.effects.projectile;

import java.util.Objects;
import java.util.function.Predicate;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.concurrent.atomic.AtomicBoolean;

import dev.patric.dungeonsreborn.effects.CastContext;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.Vars;
import dev.patric.dungeonsreborn.effects.actions.ActionHandle;
import dev.patric.dungeonsreborn.effects.actions.ActionWithHandle;

public final class ProjectileAction extends ActionWithHandle {
  private final ProjectileSpec spec;

  public ProjectileAction(ProjectileSpec spec) {
    this.spec = Objects.requireNonNull(spec, "spec");
  }

  @Override
  public ActionHandle executeWithHandle(CastContext ctx) {
    World world = ctx.world();
    if (world == null) {
      return ActionHandle.completed();
    }

    Vector dir = ctx.direction().clone();
    if (dir.lengthSquared() < 1e-9) {
      dir.setX(0).setY(0).setZ(1);
    }
    dir.normalize();

    Location position = ctx.origin().clone();
    Vector velocity = dir.clone().multiply(spec.speedPerTick());
    double maxDistance = spec.maxDistance();

    ProjectileInstance instance = new ProjectileInstance(position.clone(), dir.clone());
    try {
      spec.onStart().accept(instance);
    } catch (Exception ex) {
      ctx.plugin().getLogger().warning("[Effects] projectile onStart threw: " + ex.getMessage());
      ex.printStackTrace();
    }

    Predicate<Entity> entityFilter = entity -> {
      if (!(entity instanceof LivingEntity living)) {
        return false;
      }
      if (spec.ignoreCaster() && entity.getUniqueId().equals(ctx.caster().getUniqueId())) {
        return false;
      }
      return spec.entityFilter().test(living);
    };

    final double[] traveled = new double[] { 0.0 };
    final int[] bounces = new int[] { 0 };
    AtomicBoolean done = new AtomicBoolean(false);
    final EffectsEngine.ScheduledHandle[] handle = new EffectsEngine.ScheduledHandle[1];
    final Runnable cancel = () -> {
      if (handle[0] != null && !handle[0].isCancelled()) {
        handle[0].cancel();
      }
      done.set(true);
    };
    handle[0] = ctx.engine().runRepeating(0L, 1L, () -> {
      if (handle[0] == null || handle[0].isCancelled()) {
        done.set(true);
        return;
      }
      if (position.getWorld() == null) {
        cancel.run();
        return;
      }

      double stepLen = velocity.length();
      if (stepLen < 1e-9) {
        cancel.run();
        return;
      }

      // Render trail at current position.
      if (spec.trailCount() > 0) {
        ctx.engine().particles().emit(world, position, spec.trailParticle(), spec.trailCount(),
            spec.trailOffset(), spec.trailOffset(), spec.trailOffset(), spec.trailExtra());
      }

      double remaining = maxDistance - traveled[0];
      if (remaining <= 0) {
        cancel.run();
        return;
      }

      double segLen = Math.min(stepLen, remaining);
      Vector segDir = velocity.clone().normalize();

      // Raycast blocks and entities along this tick segment and pick the nearest hit.
      RayTraceResult blockHit = spec.blockCollision() == ProjectileSpec.BlockCollision.PASS_THROUGH ? null : world.rayTraceBlocks(position, segDir, segLen);
      RayTraceResult entityHit = world.rayTraceEntities(position, segDir, segLen, spec.hitRadius(), entityFilter);

      RayTraceResult chosen = null;
      boolean chosenIsEntity = false;
      if (blockHit != null && blockHit.getHitPosition() != null) {
        chosen = blockHit;
        chosenIsEntity = false;
      }
      if (entityHit != null && entityHit.getHitPosition() != null) {
        if (chosen == null) {
          chosen = entityHit;
          chosenIsEntity = true;
        } else {
          double blockDist = position.toVector().distance(chosen.getHitPosition());
          double entityDist = position.toVector().distance(entityHit.getHitPosition());
          if (entityDist <= blockDist) {
            chosen = entityHit;
            chosenIsEntity = true;
          }
        }
      }

      if (chosen != null && chosen.getHitPosition() != null) {
        Vector hitPos = chosen.getHitPosition();
        Location hitLocation = new Location(world, hitPos.getX(), hitPos.getY(), hitPos.getZ());
        LivingEntity hitEntity = chosenIsEntity && chosen.getHitEntity() instanceof LivingEntity living ? living : null;
        Block hitBlock = !chosenIsEntity ? chosen.getHitBlock() : null;
        double hitDist = position.toVector().distance(hitPos);
        traveled[0] += hitDist;

        ProjectileHit hit = new ProjectileHit(ctx, hitLocation, segDir, traveled[0], hitEntity, hitBlock);
        instance.update(hitLocation.clone(), segDir.clone(), traveled[0]);
        ctx.state().put(Vars.PROJECTILE_LAST_HIT, hit);
        try {
          spec.onHit().accept(hit);
        } catch (Exception ex) {
          ctx.plugin().getLogger().warning("[Effects] projectile onHit threw: " + ex.getMessage());
          ex.printStackTrace();
        }

        // Entity hits always terminate the projectile.
        if (hitEntity != null) {
          cancel.run();
          return;
        }

        // Block hits: either terminate, pass-through (shouldn't happen), or bounce/reflect.
        if (hitBlock != null && spec.blockCollision() == ProjectileSpec.BlockCollision.BOUNCE && bounces[0] < spec.maxBounces()) {
          BlockFace face = chosen.getHitBlockFace();
          if (face == null) {
            cancel.run();
            return;
          }
          Vector n = new Vector(face.getModX(), face.getModY(), face.getModZ());
          if (n.lengthSquared() < 1e-9) {
            cancel.run();
            return;
          }
          n.normalize();

          Vector in = segDir.clone();
          double dot = in.dot(n);
          Vector reflected = in.subtract(n.clone().multiply(2.0 * dot));
          if (reflected.lengthSquared() < 1e-9) {
            cancel.run();
            return;
          }
          reflected.normalize();

          double speed = Math.max(0.0, stepLen * spec.bounceRestitution());
          if (speed < 1e-6) {
            cancel.run();
            return;
          }

          // Update velocity in-place (captured by lambda).
          velocity.setX(reflected.getX() * speed);
          velocity.setY(reflected.getY() * speed);
          velocity.setZ(reflected.getZ() * speed);

          // Nudge away from the hit surface to avoid immediate re-hit on the next tick.
          double epsilon = 0.05;
          position.set(
              hitLocation.getX() + reflected.getX() * epsilon,
              hitLocation.getY() + reflected.getY() * epsilon,
              hitLocation.getZ() + reflected.getZ() * epsilon);

          bounces[0]++;
          instance.update(position.clone(), reflected.clone(), traveled[0]);
          return;
        }

        cancel.run();
        return;
      }

      // No hit: advance to end of segment.
      position.add(segDir.getX() * segLen, segDir.getY() * segLen, segDir.getZ() * segLen);
      traveled[0] += segLen;
      instance.update(position.clone(), segDir.clone(), traveled[0]);

      if (traveled[0] >= maxDistance) {
        cancel.run();
      }
    });
    ctx.state().track(handle[0]);
    return new ActionHandle() {
      @Override
      public boolean cancel() {
        if (done.get()) {
          return false;
        }
        cancel.run();
        return true;
      }

      @Override
      public boolean isDone() {
        return done.get() || handle[0] == null || handle[0].isCancelled();
      }
    };
  }
}
