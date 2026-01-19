package dev.patric.dungeonsreborn.effects.particles;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import dev.patric.dungeonsreborn.effects.CastContext;
import dev.patric.dungeonsreborn.effects.EffectsEngine;

public final class ParticlePhysics {
  private ParticlePhysics() {
  }

  public enum CollisionMode {
    STOP,
    BOUNCE,
    SLIDE
  }

  private record ParticleState(Location location, Vector velocity, boolean alive) {
  }

  public static EffectsEngine.ScheduledHandle simulate(
      CastContext ctx,
      Particle particle,
      int count,
      Vector baseVelocity,
      double spread,
      double gravity,
      double drag,
      int steps,
      long periodTicks,
      double offset,
      double extra,
      Object data,
      boolean collide,
      CollisionMode collisionMode,
      double restitution) {
    Objects.requireNonNull(ctx, "ctx");
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(baseVelocity, "baseVelocity");
    if (steps <= 0 || periodTicks <= 0 || count <= 0) {
      throw new IllegalArgumentException("steps, periodTicks, count must be > 0");
    }

    return simulate(ctx, particle, List.of(ctx.origin().clone()), count, baseVelocity, spread, gravity, drag, steps,
        periodTicks, offset, extra, data, collide, collisionMode, restitution);
  }

  public static EffectsEngine.ScheduledHandle simulate(
      CastContext ctx,
      Particle particle,
      List<Location> origins,
      int count,
      Vector baseVelocity,
      double spread,
      double gravity,
      double drag,
      int steps,
      long periodTicks,
      double offset,
      double extra,
      Object data,
      boolean collide,
      CollisionMode collisionMode,
      double restitution) {
    Objects.requireNonNull(ctx, "ctx");
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(origins, "origins");
    Objects.requireNonNull(baseVelocity, "baseVelocity");
    if (steps <= 0 || periodTicks <= 0 || count <= 0) {
      throw new IllegalArgumentException("steps, periodTicks, count must be > 0");
    }

    World world = ctx.world();
    if (world == null || origins.isEmpty()) {
      return ctx.engine().runLater(1L, () -> {
      });
    }

    List<ParticleState> particles = new ArrayList<>(origins.size() * count);
    for (Location origin : origins) {
      if (origin == null) {
        continue;
      }
      Location base = origin.clone();
      if (base.getWorld() == null) {
        base.setWorld(world);
      }
      if (base.getWorld() == null || !base.getWorld().equals(world)) {
        continue;
      }
      for (int i = 0; i < count; i++) {
        Vector jitter = baseVelocity.clone();
        if (spread > 1e-9) {
          double sx = (ctx.rng().nextDouble() * 2.0 - 1.0) * spread;
          double sy = (ctx.rng().nextDouble() * 2.0 - 1.0) * spread;
          double sz = (ctx.rng().nextDouble() * 2.0 - 1.0) * spread;
          jitter.add(new Vector(sx, sy, sz));
        }
        particles.add(new ParticleState(base.clone(), jitter, true));
      }
    }

    if (particles.isEmpty()) {
      return ctx.engine().runLater(1L, () -> {
      });
    }

    final int[] remaining = new int[] { steps };
    final EffectsEngine.ScheduledHandle[] handle = new EffectsEngine.ScheduledHandle[1];
    handle[0] = ctx.engine().runRepeating(0L, periodTicks, () -> {
      if (remaining[0]-- <= 0) {
        if (handle[0] != null) {
          handle[0].cancel();
        }
        return;
      }
      var engine = ctx.engine().particles();
      for (int i = 0; i < particles.size(); i++) {
        ParticleState state = particles.get(i);
        if (!state.alive()) {
          continue;
        }
        Vector vel = state.velocity();
        vel.setX(vel.getX() * (1.0 - drag));
        vel.setY(vel.getY() * (1.0 - drag) - gravity);
        vel.setZ(vel.getZ() * (1.0 - drag));

        Location loc = state.location();
        loc.add(vel);

        if (collide) {
          Block block = world.getBlockAt(loc);
          if (block.getType().isSolid()) {
            switch (collisionMode) {
              case BOUNCE -> {
                Vector bounced = vel.clone().multiply(-Math.max(0.0, Math.min(1.0, restitution)));
                particles.set(i, new ParticleState(loc, bounced, true));
              }
              case SLIDE -> {
                Vector slid = vel.clone();
                slid.setY(0);
                particles.set(i, new ParticleState(loc, slid, true));
              }
              case STOP -> {
                particles.set(i, new ParticleState(loc, vel, false));
              }
            }
            continue;
          }
        }

        particles.set(i, new ParticleState(loc, vel, true));
        Object resolved = data;
        if (resolved instanceof java.util.function.BiFunction<?, ?, ?> fn) {
          @SuppressWarnings("unchecked")
          java.util.function.BiFunction<CastContext, Location, Object> resolver =
              (java.util.function.BiFunction<CastContext, Location, Object>) fn;
          resolved = resolver.apply(ctx, loc);
        }
        engine.emit(world, loc, particle, 1, offset, offset, offset, extra, resolved);
      }
    });
    return handle[0];
  }
}
