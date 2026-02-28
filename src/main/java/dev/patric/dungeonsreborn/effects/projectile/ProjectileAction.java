package dev.patric.dungeonsreborn.effects.projectile;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.HashSet;

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
import dev.patric.dungeonsreborn.effects.combat.CombatEventContext;
import dev.patric.dungeonsreborn.effects.combat.CombatEventSource;
import dev.patric.dungeonsreborn.effects.combat.CombatEventType;
import dev.patric.dungeonsreborn.effects.combat.ProjectileFamily;
import dev.patric.dungeonsreborn.effects.damage.DamageCause;

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
    UUID projectileId = UUID.randomUUID();

    ProjectileInstance instance = new ProjectileInstance(position.clone(), dir.clone());
    try {
      spec.onStart().accept(instance);
    } catch (Exception ex) {
      ctx.engine().warn("projectile onStart threw: " + ex.getMessage(), ex);
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
    final int[] pierces = new int[] { 0 };
    final long[] lastTravelDispatchTick = new long[] { -1L };
    final Set<UUID> alreadyHit = new HashSet<>();
    final boolean projectileEventsEnabled = ctx.engine().plugin().getConfig().getBoolean("effects.combat.projectiles.enabled", true)
        && ctx.engine().plugin().getConfig().getBoolean("effects.combat.projectiles.custom.enabled", true);
    AtomicBoolean done = new AtomicBoolean(false);
    final EffectsEngine.ScheduledHandle[] handle = new EffectsEngine.ScheduledHandle[1];
    final Runnable cancel = () -> {
      if (handle[0] != null && !handle[0].isCancelled()) {
        handle[0].cancel();
      }
      done.set(true);
    };
    ProjectileTelemetry launchTelemetry = telemetry(
        projectileId,
        spec.kind(),
        ctx,
        null,
        null,
        null,
        position,
        dir,
        0.0,
        velocity.length(),
        bounces[0],
        pierces[0],
        false);
    if (projectileEventsEnabled
        && ctx.engine().combatDispatcher().dispatchPre(combatContext(ctx, launchTelemetry, CombatEventType.ON_PROJECTILE_LAUNCH_PRE, null, null, false))) {
      ctx.engine().combatDispatcher().dispatch(combatContext(ctx, launchTelemetry, CombatEventType.ON_PROJECTILE_CANCELLED, null, null, false));
      return ActionHandle.completed();
    }
    if (projectileEventsEnabled) {
      ctx.engine().combatDispatcher().dispatch(combatContext(ctx, launchTelemetry, CombatEventType.ON_PROJECTILE_LAUNCH, null, null, false));
    }
    try {
      spec.onLaunch().accept(instance);
    } catch (Exception ex) {
      ctx.engine().warn("projectile onLaunch threw: " + ex.getMessage(), ex);
      ex.printStackTrace();
    }

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

      if (projectileEventsEnabled
          && spec.travelStepEnabled()
          && ctx.engine().combatDispatcher().hasBindings(CombatEventType.ON_PROJECTILE_TRAVEL_STEP)) {
        long now = ctx.engine().tickNow();
        if (lastTravelDispatchTick[0] < 0 || now - lastTravelDispatchTick[0] >= spec.travelStepIntervalTicks()) {
          ProjectileTelemetry stepTelemetry = telemetry(
              projectileId,
              spec.kind(),
              ctx,
              null,
              null,
              null,
              position,
              segDir(velocity),
              traveled[0],
              velocity.length(),
              bounces[0],
              pierces[0],
              false);
          ctx.engine().combatDispatcher().dispatch(
              combatContext(ctx, stepTelemetry, CombatEventType.ON_PROJECTILE_TRAVEL_STEP, null, null, false));
          try {
            spec.onStep().accept(instance);
          } catch (Exception ex) {
            ctx.engine().warn("projectile onStep threw: " + ex.getMessage(), ex);
          }
          lastTravelDispatchTick[0] = now;
        }
      }

      double remaining = maxDistance - traveled[0];
      if (remaining <= 0) {
        ProjectileTelemetry expireTelemetry = telemetry(
            projectileId,
            spec.kind(),
            ctx,
            null,
            null,
            null,
            position,
            segDir(velocity),
            traveled[0],
            velocity.length(),
            bounces[0],
            pierces[0],
            false);
        if (projectileEventsEnabled) {
          ctx.engine().combatDispatcher().dispatch(combatContext(ctx, expireTelemetry, CombatEventType.ON_PROJECTILE_EXPIRE, null, null, false));
        }
        try {
          spec.onExpire().accept(instance);
        } catch (Exception ex) {
          ctx.engine().warn("projectile onExpire threw: " + ex.getMessage(), ex);
        }
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
        ProjectileTelemetry hitTelemetry = telemetry(
            projectileId,
            spec.kind(),
            ctx,
            hitEntity,
            hitBlock,
            chosen.getHitBlockFace(),
            hitLocation,
            segDir,
            traveled[0],
            velocity.length(),
            bounces[0],
            pierces[0],
            hitEntity != null && alreadyHit.contains(hitEntity.getUniqueId()));
        CombatEventType preType = hitEntity != null
            ? CombatEventType.ON_PROJECTILE_COLLIDE_ENTITY_PRE
            : CombatEventType.ON_PROJECTILE_COLLIDE_BLOCK_PRE;
        if (projectileEventsEnabled
            && ctx.engine().combatDispatcher().dispatchPre(combatContext(ctx, hitTelemetry, preType, hitEntity, chosenIsEntity ? chosen.getHitEntity() : null, false))) {
          ctx.engine().combatDispatcher().dispatch(combatContext(ctx, hitTelemetry, CombatEventType.ON_PROJECTILE_CANCELLED, hitEntity, chosenIsEntity ? chosen.getHitEntity() : null, false));
          cancel.run();
          return;
        }
        if (projectileEventsEnabled) {
          ctx.engine().combatDispatcher().dispatch(combatContext(
              ctx,
              hitTelemetry,
              hitEntity != null ? CombatEventType.ON_PROJECTILE_HIT_ENTITY : CombatEventType.ON_PROJECTILE_HIT_BLOCK,
              hitEntity,
              chosenIsEntity ? chosen.getHitEntity() : null,
              false));
        }
        try {
          spec.onHit().accept(hit);
        } catch (Exception ex) {
          ctx.engine().warn("projectile onHit threw: " + ex.getMessage(), ex);
          ex.printStackTrace();
        }

        // Entity hits always terminate the projectile.
        if (hitEntity != null) {
          if (spec.maxPierces() > 0 && pierces[0] < spec.maxPierces() && alreadyHit.add(hitEntity.getUniqueId())) {
            pierces[0]++;
            if (projectileEventsEnabled) {
              ctx.engine().combatDispatcher().dispatch(combatContext(
                  ctx,
                  hitTelemetry,
                  CombatEventType.ON_PROJECTILE_PIERCE,
                  hitEntity,
                  chosenIsEntity ? chosen.getHitEntity() : null,
                  true));
            }
            try {
              spec.onPierce().accept(hit);
            } catch (Exception ex) {
              ctx.engine().warn("projectile onPierce threw: " + ex.getMessage(), ex);
            }
            double epsilon = 0.08;
            position.set(hitLocation.getX() + segDir.getX() * epsilon, hitLocation.getY() + segDir.getY() * epsilon,
                hitLocation.getZ() + segDir.getZ() * epsilon);
            return;
          }
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
          if (projectileEventsEnabled) {
            ctx.engine().combatDispatcher().dispatch(combatContext(
                ctx,
                hitTelemetry,
                CombatEventType.ON_PROJECTILE_BOUNCE,
                null,
                null,
                false));
            ctx.engine().combatDispatcher().dispatch(combatContext(
                ctx,
                hitTelemetry,
                CombatEventType.ON_PROJECTILE_DEFLECT,
                null,
                null,
                false));
          }
          try {
            spec.onBounce().accept(hit);
          } catch (Exception ex) {
            ctx.engine().warn("projectile onBounce threw: " + ex.getMessage(), ex);
          }
          return;
        }
        if (projectileEventsEnabled && hitBlock != null) {
          ctx.engine().combatDispatcher().dispatch(combatContext(
              ctx,
              hitTelemetry,
              CombatEventType.ON_PROJECTILE_STUCK,
              null,
              null,
              false));
        }
        cancel.run();
        return;
      }

      // No hit: advance to end of segment.
      position.add(segDir.getX() * segLen, segDir.getY() * segLen, segDir.getZ() * segLen);
      traveled[0] += segLen;
      instance.update(position.clone(), segDir.clone(), traveled[0]);

      if (traveled[0] >= maxDistance) {
        ProjectileTelemetry expireTelemetry = telemetry(
            projectileId,
            spec.kind(),
            ctx,
            null,
            null,
            null,
            position,
            segDir,
            traveled[0],
            velocity.length(),
            bounces[0],
            pierces[0],
            false);
        if (projectileEventsEnabled) {
          ctx.engine().combatDispatcher().dispatch(combatContext(
              ctx,
              expireTelemetry,
              CombatEventType.ON_PROJECTILE_EXPIRE,
              null,
              null,
              false));
        }
        try {
          spec.onExpire().accept(instance);
        } catch (Exception ex) {
          ctx.engine().warn("projectile onExpire threw: " + ex.getMessage(), ex);
        }
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

  private static Vector segDir(Vector velocity) {
    if (velocity == null || velocity.lengthSquared() < 1.0e-9) {
      return new Vector(0, 0, 1);
    }
    return velocity.clone().normalize();
  }

  private static ProjectileTelemetry telemetry(
      UUID projectileId,
      String kind,
      CastContext ctx,
      LivingEntity hitEntity,
      Block hitBlock,
      BlockFace hitBlockFace,
      Location location,
      Vector direction,
      double distance,
      double speed,
      int bounces,
      int pierces,
      boolean alreadyHit) {
    ProjectileTelemetry.Builder builder = ProjectileTelemetry.builder(projectileId, ProjectileFamily.CUSTOM, "CUSTOM")
        .kind(kind)
        .shooter(ctx.caster())
        .victim(hitEntity)
        .impact(location, direction)
        .movement(distance, speed)
        .drawForce(0.0)
        .pierce(pierces, pierces > 0)
        .inGroundTicks(0)
        .critical(false)
        .charged(false)
        .shotFromCrossbow(false);
    if (hitBlock != null) {
      builder.hitBlock(hitBlock, hitBlockFace);
    }
    return builder.build();
  }

  private static CombatEventContext combatContext(
      CastContext ctx,
      ProjectileTelemetry telemetry,
      CombatEventType eventType,
      LivingEntity victim,
      Entity rawDamager,
      boolean piercing) {
    String blockMaterial = telemetry.hitBlockMaterial() == null ? null : telemetry.hitBlockMaterial().name();
    String blockTag = telemetry.hitBlockMaterial() == null ? null : telemetry.hitBlockMaterial().getKey().getKey().toLowerCase();
    return new CombatEventContext(
        ctx.engine().tickNow(),
        eventType,
        ctx.caster(),
        victim,
        victim,
        rawDamager,
        CombatEventSource.PROJECTILE,
        0.0,
        telemetry.critical(),
        false,
        false,
        null,
        DamageCause.PROJECTILE,
        null,
        null,
        telemetry.projectileId(),
        telemetry.family(),
        telemetry.projectileType(),
        telemetry.projectileKind(),
        telemetry.distance(),
        telemetry.speed(),
        telemetry.drawForce(),
        telemetry.pierceLevel(),
        telemetry.inGroundTicks(),
        telemetry.critical(),
        telemetry.charged(),
        piercing || telemetry.piercing(),
        telemetry.shotFromCrossbow(),
        telemetry.shooterIsPlayer(),
        blockMaterial,
        blockTag,
        telemetry.hitBlockFace() == null ? null : telemetry.hitBlockFace().name(),
        telemetry.impactLocation(),
        telemetry.impactDirection(),
        telemetry);
  }
}
