package dev.patric.dungeonsreborn.effects.actions;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WitherSkull;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import dev.patric.dungeonsreborn.effects.CastContext;
import dev.patric.dungeonsreborn.effects.CinematicSettings;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.conditions.Condition;
import dev.patric.dungeonsreborn.effects.costs.Cost;
import dev.patric.dungeonsreborn.effects.raycast.Raycasts;
import dev.patric.dungeonsreborn.effects.projectile.ProjectileAction;
import dev.patric.dungeonsreborn.effects.projectile.ProjectileSpec;
import dev.patric.dungeonsreborn.effects.particles.Frame;
import dev.patric.dungeonsreborn.effects.particles.Frames;
import dev.patric.dungeonsreborn.effects.particles.ParticleShapes;
import dev.patric.dungeonsreborn.effects.particles.ParticlePhysics;
import dev.patric.dungeonsreborn.effects.particles.ParticleTransforms;
import dev.patric.dungeonsreborn.effects.math.Curves;
import dev.patric.dungeonsreborn.effects.targeting.TargetAction;
import dev.patric.dungeonsreborn.effects.targeting.TargetCondition;
import dev.patric.dungeonsreborn.effects.targeting.Targeter;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

public final class Actions {
  private Actions() {
  }

  public record TimelineEntry(long delayTicks, Action action) {
  }

  private record Aim(LivingEntity hitEntity, Location point, Vector direction) {
  }

  private static Aim resolveAim(CastContext ctx, double maxDistance, double raySize, boolean stopOnBlock, boolean requireTarget) {
    World world = ctx.world();
    if (world == null) {
      return null;
    }
    Vector dir = ctx.direction().clone();
    if (dir.lengthSquared() < 1e-9) {
      dir.setX(0).setY(0).setZ(1);
    }
    dir.normalize();

    RayTraceResult blockHit = stopOnBlock ? world.rayTraceBlocks(ctx.origin(), dir, maxDistance, FluidCollisionMode.NEVER, true) : null;
    Vector end = ctx.origin().toVector().add(dir.clone().multiply(maxDistance));
    if (blockHit != null && blockHit.getHitPosition() != null) {
      end = blockHit.getHitPosition();
    }

    Predicate<Entity> entityFilter = e -> e instanceof LivingEntity && !e.getUniqueId().equals(ctx.caster().getUniqueId());
    RayTraceResult entityHit = stopOnBlock
        ? Raycasts.rayTraceEntitiesStopOnBlock(world, ctx.origin(), dir, maxDistance, raySize, entityFilter)
        : world.rayTraceEntities(ctx.origin(), dir, maxDistance, raySize, entityFilter);

    LivingEntity hitEntity = null;
    if (entityHit != null && entityHit.getHitEntity() instanceof LivingEntity living) {
      hitEntity = living;
    }
    if (requireTarget && hitEntity == null) {
      return null;
    }

    Location point;
    Vector aimDir;
    if (hitEntity != null) {
      point = hitEntity.getLocation().clone();
      aimDir = hitEntity.getLocation().add(0, 1.0, 0).toVector().subtract(ctx.origin().toVector());
      if (aimDir.lengthSquared() < 1e-9) {
        aimDir = dir.clone();
      }
    } else {
      point = ctx.origin().clone();
      point.set(end.getX(), end.getY(), end.getZ());
      aimDir = dir.clone();
    }
    if (aimDir.lengthSquared() < 1e-9) {
      aimDir = new Vector(0, 0, 1);
    }
    aimDir.normalize();
    return new Aim(hitEntity, point, aimDir);
  }

  private static Vector jitterDirection(Vector base, double spreadRad, java.util.Random rng) {
    if (spreadRad <= 1e-9) {
      return base.clone();
    }
    Vector b = base.clone();
    if (b.lengthSquared() < 1e-9) {
      b = new Vector(0, 0, 1);
    }
    b.normalize();

    double yaw = (rng.nextDouble() * 2.0 - 1.0) * spreadRad;
    double pitch = (rng.nextDouble() * 2.0 - 1.0) * spreadRad;

    double cosY = Math.cos(yaw);
    double sinY = Math.sin(yaw);
    double x1 = b.getX() * cosY - b.getZ() * sinY;
    double z1 = b.getX() * sinY + b.getZ() * cosY;
    double y1 = b.getY();

    double cosX = Math.cos(pitch);
    double sinX = Math.sin(pitch);
    double y2 = y1 * cosX - z1 * sinX;
    double z2 = y1 * sinX + z1 * cosX;

    Vector out = new Vector(x1, y2, z2);
    if (out.lengthSquared() < 1e-9) {
      return b;
    }
    return out.normalize();
  }

  private static Object resolveParticleData(Object data, CastContext ctx, Location loc) {
    if (data instanceof java.util.function.BiFunction<?, ?, ?> fn) {
      @SuppressWarnings("unchecked")
      java.util.function.BiFunction<CastContext, Location, Object> resolver =
          (java.util.function.BiFunction<CastContext, Location, Object>) fn;
      return resolver.apply(ctx, loc);
    }
    return data;
  }

  public static Action particlesPoint(Particle particle, int count, double offset, double extra) {
    return particlesPoint(particle, count, offset, extra, null);
  }

  public static Action particlesPoint(Particle particle, int count, double offset, double extra, Object data) {
    Objects.requireNonNull(particle, "particle");
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      Object resolved = resolveParticleData(data, ctx, ctx.origin());
      ctx.engine().particles().emit(ctx.world(), ctx.origin(), particle, count, offset, offset, offset, extra, resolved);
    };
  }

  public static Action noop() {
    return ctx -> {
    };
  }

  private static ActionHandle scheduledHandle(EffectsEngine.ScheduledHandle handle, AtomicBoolean done) {
    return new ActionHandle() {
      @Override
      public boolean cancel() {
        boolean cancelled = handle.cancel();
        done.set(true);
        return cancelled;
      }

      @Override
      public boolean isDone() {
        return done.get() || handle.isCancelled();
      }
    };
  }

  public static Action timed(String name, Action action) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(action, "action");
    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext ctx) {
        long start = System.nanoTime();
        try {
          return action.executeWithHandle(ctx);
        } finally {
          ctx.state().recordTiming(name, System.nanoTime() - start);
        }
      }
    };
  }

  /**
   * Runs a pure-math computation off-thread, then applies the result on the main thread.
   * <p>
   * The compute function MUST NOT touch Bukkit APIs.
   */
  public static <T> Action asyncCompute(String name, java.util.function.Supplier<T> compute, BiConsumer<CastContext, T> onMainThread) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(compute, "compute");
    Objects.requireNonNull(onMainThread, "onMainThread");
    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext ctx) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean done = new AtomicBoolean(false);
        var handle = new EffectsEngine.ScheduledHandle() {
          @Override
          public boolean cancel() {
            boolean was = cancelled.compareAndSet(false, true);
            done.set(true);
            return was;
          }

          @Override
          public boolean isCancelled() {
            return cancelled.get();
          }
        };
        ctx.state().track(handle);

        ctx.plugin().getServer().getScheduler().runTaskAsynchronously(ctx.plugin(), () -> {
          if (cancelled.get() || ctx.state().isCancelled()) {
            done.set(true);
            return;
          }
          final T value;
          try {
            value = compute.get();
          } catch (Exception ex) {
            done.set(true);
            if (ctx.engine().isDebugEnabled()) {
              ctx.engine().debug("asyncCompute(" + name + ") threw: " + ex.getMessage());
            }
            ex.printStackTrace();
            return;
          }
          if (cancelled.get() || ctx.state().isCancelled()) {
            done.set(true);
            return;
          }
          ctx.plugin().getServer().getScheduler().runTask(ctx.plugin(), () -> {
            if (cancelled.get() || ctx.state().isCancelled()) {
              done.set(true);
              return;
            }
            try {
              onMainThread.accept(ctx, value);
            } catch (Exception ex) {
              if (ctx.engine().isDebugEnabled()) {
                ctx.engine().debug("asyncCompute(" + name + ") onMainThread threw: " + ex.getMessage());
              }
              ex.printStackTrace();
            } finally {
              done.set(true);
            }
          });
        });
        return scheduledHandle(handle, done);
      }
    };
  }

  public static Action sequence(Action... actions) {
    Objects.requireNonNull(actions, "actions");
    List<Action> list = Arrays.asList(actions);
    list.forEach(a -> Objects.requireNonNull(a, "action"));
    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext ctx) {
        List<ActionHandle> handles = new java.util.ArrayList<>(list.size());
        for (Action action : list) {
          handles.add(action.executeWithHandle(ctx));
        }
        return ActionHandle.composite(handles);
      }
    };
  }

  public static Action timeline(java.util.List<TimelineEntry> entries) {
    Objects.requireNonNull(entries, "entries");
    if (entries.isEmpty()) {
      return noop();
    }
    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext ctx) {
        AtomicBoolean done = new AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(entries.size());
        java.util.List<EffectsEngine.ScheduledHandle> handles = new java.util.ArrayList<>(entries.size());
        for (TimelineEntry entry : entries) {
          long delayTicks = Math.max(0L, entry.delayTicks());
          Action action = entry.action();
          var handle = ctx.engine().runLater(delayTicks, () -> {
            try {
              action.execute(ctx);
            } finally {
              if (remaining.decrementAndGet() == 0) {
                done.set(true);
              }
            }
          });
          ctx.state().track(handle);
          handles.add(handle);
        }
        return new ActionHandle() {
          @Override
          public boolean cancel() {
            boolean cancelled = false;
            for (EffectsEngine.ScheduledHandle handle : handles) {
              if (handle != null) {
                cancelled |= handle.cancel();
              }
            }
            done.set(true);
            return cancelled;
          }

          @Override
          public boolean isDone() {
            return done.get();
          }
        };
      }
    };
  }

  public static Action stateMachine(Action charge, Action sustain, Action release,
      long chargeTicks, long sustainTicks, long releaseTicks, long sustainPeriodTicks) {
    Objects.requireNonNull(charge, "charge");
    Objects.requireNonNull(sustain, "sustain");
    Objects.requireNonNull(release, "release");
    if (chargeTicks < 0 || sustainTicks < 0 || releaseTicks < 0) {
      throw new IllegalArgumentException("ticks must be >= 0");
    }
    if (sustainPeriodTicks <= 0) {
      throw new IllegalArgumentException("sustainPeriodTicks must be > 0");
    }
    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext ctx) {
        java.util.List<ActionHandle> handles = new java.util.ArrayList<>();
        handles.add(Actions.delayTicks(chargeTicks, charge).executeWithHandle(ctx));
        if (sustainTicks > 0) {
          int times = (int) Math.max(1, Math.round(sustainTicks / (double) sustainPeriodTicks));
          handles.add(Actions.repeatTicks(chargeTicks, sustainPeriodTicks, times, sustain).executeWithHandle(ctx));
        }
        if (releaseTicks > 0) {
          handles.add(Actions.delayTicks(chargeTicks + sustainTicks, release).executeWithHandle(ctx));
        }
        return new ActionHandle() {
          @Override
          public boolean cancel() {
            boolean cancelled = false;
            for (ActionHandle handle : handles) {
              if (handle != null) {
                cancelled |= handle.cancel();
              }
            }
            return cancelled;
          }

          @Override
          public boolean isDone() {
            for (ActionHandle handle : handles) {
              if (handle != null && !handle.isDone()) {
                return false;
              }
            }
            return true;
          }
        };
      }
    };
  }

  public static Action delayTicks(long delayTicks, Action then) {
    Objects.requireNonNull(then, "then");
    if (delayTicks < 0) {
      throw new IllegalArgumentException("delayTicks must be >= 0");
    }
    if (delayTicks == 0) {
      return then;
    }
    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext ctx) {
        AtomicBoolean done = new AtomicBoolean(false);
        var handle = ctx.engine().runLater(delayTicks, () -> {
          try {
            then.execute(ctx);
          } finally {
            done.set(true);
          }
        });
        ctx.state().track(handle);
        return scheduledHandle(handle, done);
      }
    };
  }

  public static Action delay(Duration delay, Action then) {
    Objects.requireNonNull(delay, "delay");
    Objects.requireNonNull(then, "then");
    if (delay.isZero() || delay.isNegative()) {
      return then;
    }
    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext ctx) {
        AtomicBoolean done = new AtomicBoolean(false);
        var handle = ctx.engine().runLater(delay, () -> {
          try {
            then.execute(ctx);
          } finally {
            done.set(true);
          }
        });
        ctx.state().track(handle);
        return scheduledHandle(handle, done);
      }
    };
  }

  public static Action repeatTicks(long delayTicks, long periodTicks, int times, Action action) {
    Objects.requireNonNull(action, "action");
    if (delayTicks < 0) {
      throw new IllegalArgumentException("delayTicks must be >= 0");
    }
    if (periodTicks <= 0) {
      throw new IllegalArgumentException("periodTicks must be > 0");
    }
    if (times <= 0) {
      return noop();
    }
    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext ctx) {
        AtomicBoolean done = new AtomicBoolean(false);
        final int[] remaining = new int[] { times };
        final EffectsEngine.ScheduledHandle[] handle = new EffectsEngine.ScheduledHandle[1];
        handle[0] = ctx.engine().runRepeating(delayTicks, periodTicks, () -> {
          if (remaining[0]-- <= 0) {
            if (handle[0] != null) {
              handle[0].cancel();
            }
            done.set(true);
            return;
          }
          action.execute(ctx);
        });
        ctx.state().track(handle[0]);
        return scheduledHandle(handle[0], done);
      }
    };
  }

  /**
   * Runs {@code action} up to {@code times} at a real-time {@code period}.
   * <p>
   * Note: execution still happens on the main thread and is limited by server ticks; drift is handled by re-syncing time.
   */
  public static Action repeat(Duration delay, Duration period, int times, Action action) {
    Objects.requireNonNull(delay, "delay");
    Objects.requireNonNull(period, "period");
    Objects.requireNonNull(action, "action");
    if (times <= 0) {
      return noop();
    }
    if (period.isZero() || period.isNegative()) {
      throw new IllegalArgumentException("period must be > 0");
    }
    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext ctx) {
        AtomicBoolean done = new AtomicBoolean(false);
        final int[] remaining = new int[] { times };
        final EffectsEngine.ScheduledHandle[] handle = new EffectsEngine.ScheduledHandle[1];
        handle[0] = ctx.engine().runRepeating(delay, period, () -> {
          if (remaining[0]-- <= 0) {
            if (handle[0] != null) {
              handle[0].cancel();
            }
            done.set(true);
            return;
          }
          action.execute(ctx);
        });
        ctx.state().track(handle[0]);
        return scheduledHandle(handle[0], done);
      }
    };
  }

  /**
   * Runs {@code onTick} over {@code durationTicks} with normalized time {@code t} in [0..1].
   * <p>
   * The scheduled task is tracked in {@link dev.patric.dungeonsreborn.effects.CastState} for cancellation.
   */
  public static Action animate(long durationTicks, long periodTicks, DoubleUnaryOperator easing, BiConsumer<CastContext, Double> onTick) {
    Objects.requireNonNull(easing, "easing");
    Objects.requireNonNull(onTick, "onTick");
    if (durationTicks <= 0) {
      throw new IllegalArgumentException("durationTicks must be > 0");
    }
    if (periodTicks <= 0) {
      throw new IllegalArgumentException("periodTicks must be > 0");
    }

    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext ctx) {
        AtomicBoolean done = new AtomicBoolean(false);
        final long start = ctx.engine().tickNow();
        final EffectsEngine.ScheduledHandle[] handle = new EffectsEngine.ScheduledHandle[1];
        handle[0] = ctx.engine().runRepeating(0L, periodTicks, () -> {
          if (handle[0] == null || handle[0].isCancelled()) {
            return;
          }
          long elapsed = ctx.engine().tickNow() - start;
          if (elapsed >= durationTicks) {
            handle[0].cancel();
            done.set(true);
            return;
          }
          double t = elapsed / (double) durationTicks;
          double eased = easing.applyAsDouble(t);
          onTick.accept(ctx, eased);
        });
        ctx.state().track(handle[0]);
        return scheduledHandle(handle[0], done);
      }
    };
  }

  /**
   * Runs {@code onTick} over {@code duration} with normalized time {@code t} in [0..1] based on wall-clock time.
   * <p>
   * Execution is still on the main thread; if the server lags, {@code t} advances faster per tick to reduce drift.
   */
  public static Action animateRealTime(Duration duration, Duration period, DoubleUnaryOperator easing, BiConsumer<CastContext, Double> onTick) {
    Objects.requireNonNull(duration, "duration");
    Objects.requireNonNull(period, "period");
    Objects.requireNonNull(easing, "easing");
    Objects.requireNonNull(onTick, "onTick");
    if (duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException("duration must be > 0");
    }
    if (period.isZero() || period.isNegative()) {
      throw new IllegalArgumentException("period must be > 0");
    }

    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext ctx) {
        AtomicBoolean done = new AtomicBoolean(false);
        final long start = ctx.engine().nanoTime();
        final long durationNanos = duration.toNanos();
        final EffectsEngine.ScheduledHandle[] handle = new EffectsEngine.ScheduledHandle[1];
        handle[0] = ctx.engine().runRepeating(Duration.ZERO, period, () -> {
          if (handle[0] == null || handle[0].isCancelled()) {
            return;
          }
          long elapsed = ctx.engine().nanoTime() - start;
          if (elapsed >= durationNanos) {
            handle[0].cancel();
            done.set(true);
            return;
          }
          double t = Math.max(0.0, Math.min(1.0, elapsed / (double) durationNanos));
          double eased = easing.applyAsDouble(t);
          onTick.accept(ctx, eased);
        });
        ctx.state().track(handle[0]);
        return scheduledHandle(handle[0], done);
      }
    };
  }

  public enum MotionMode {
    TRANSLATE,
    FOLLOW,
    ORBIT,
    DRIFT
  }

  public record MotionSpec(MotionMode mode, Function<CastContext, Location> base, Vector velocity,
      double radius, double turns, double vertical, double drift, double driftVertical, double driftSpeed) {
    public MotionSpec {
      Objects.requireNonNull(mode, "mode");
      Objects.requireNonNull(base, "base");
      velocity = velocity == null ? new Vector() : velocity.clone();
    }
  }

  public static Action motion(long durationTicks, long periodTicks, DoubleUnaryOperator easing, MotionSpec motion,
      BiConsumer<CastContext, Double> onTick) {
    Objects.requireNonNull(easing, "easing");
    Objects.requireNonNull(motion, "motion");
    Objects.requireNonNull(onTick, "onTick");
    if (durationTicks <= 0 || periodTicks <= 0) {
      throw new IllegalArgumentException("durationTicks and periodTicks must be > 0");
    }
    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext ctx) {
        AtomicLong stepIndex = new AtomicLong(0L);
        return Actions.animate(durationTicks, periodTicks, easing, (tickCtx, t) -> {
          Location base = motion.base().apply(tickCtx);
          if (base == null || base.getWorld() == null) {
            return;
          }
          Location moved = applyMotion(tickCtx, base, motion, t, stepIndex.getAndIncrement());
          if (moved == null || moved.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              tickCtx.engine(),
              tickCtx.plugin(),
              tickCtx.castId(),
              tickCtx.abilityId(),
              tickCtx.tick(),
              tickCtx.state(),
              tickCtx.caster(),
              moved.clone(),
              tickCtx.direction().clone(),
              tickCtx.itemInHand());
          onTick.accept(exec, t);
        }).executeWithHandle(ctx);
      }
    };
  }

  public static Action motion(Action action, MotionSpec motion, long durationTicks, long periodTicks, DoubleUnaryOperator easing) {
    Objects.requireNonNull(action, "action");
    return motion(durationTicks, periodTicks, easing, motion, (exec, t) -> action.execute(exec));
  }

  private static Location applyMotion(CastContext ctx, Location base, MotionSpec motion, double t, long step) {
    Location out = base.clone();
    switch (motion.mode()) {
      case TRANSLATE -> {
        Vector vel = motion.velocity();
        out.add(vel.getX() * step, vel.getY() * step, vel.getZ() * step);
      }
      case FOLLOW -> {
        // No extra offset; base already follows the selected frame.
      }
      case ORBIT -> {
        double angle = (Math.PI * 2.0) * motion.turns() * t;
        Vector offset = new Vector(motion.radius(), 0.0, 0.0);
        Vector rotated = ParticleTransforms.rotateAroundY(offset, angle);
        out.add(rotated);
        if (motion.vertical() != 0.0) {
          out.add(0.0, motion.vertical() * t, 0.0);
        }
      }
      case DRIFT -> {
        double speed = motion.driftSpeed() == 0.0 ? 0.35 : motion.driftSpeed();
        double ang = step * speed;
        double dx = Math.cos(ang) * motion.drift();
        double dz = Math.sin(ang) * motion.drift();
        double dy = Math.sin(ang * 0.7) * motion.driftVertical();
        out.add(dx, dy, dz);
      }
      default -> {
      }
    }
    return out;
  }

  public static Action particlesLine(Particle particle, double length, double step, int count, double offset, double extra) {
    return particlesLine(particle, length, step, count, offset, extra, null);
  }

  public static Action particlesLine(Particle particle, double length, double step, int count, double offset, double extra, Object data) {
    Objects.requireNonNull(particle, "particle");
    if (length < 0) {
      throw new IllegalArgumentException("length must be >= 0");
    }
    if (step <= 0) {
      throw new IllegalArgumentException("step must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      var pe = ctx.engine().particles();
      var dir = ctx.direction().clone();
      if (dir.lengthSquared() < 1e-9) {
        dir.setX(0).setY(0).setZ(1);
      }
      dir.normalize();

      var pos = ctx.origin().clone();
      for (double d = 0.0; d <= length + 1e-9; d += step) {
        Object resolved = resolveParticleData(data, ctx, pos);
        pe.emit(ctx.world(), pos, particle, count, offset, offset, offset, extra, resolved);
        pos.add(dir.getX() * step, dir.getY() * step, dir.getZ() * step);
      }
    };
  }

  public static Action particlesPhysics(Particle particle, int count, Vector velocity, double spread,
      double gravity, double drag, int steps, long periodTicks, double offset, double extra, Object data,
      boolean collide, dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode collisionMode,
      double restitution) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(velocity, "velocity");
    if (count <= 0) {
      throw new IllegalArgumentException("count must be > 0");
    }
    if (steps <= 0) {
      throw new IllegalArgumentException("steps must be > 0");
    }
    if (periodTicks <= 0) {
      throw new IllegalArgumentException("periodTicks must be > 0");
    }
    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext ctx) {
        AtomicBoolean done = new AtomicBoolean(false);
        var handle = ParticlePhysics.simulate(
            ctx,
            particle,
            count,
            velocity,
            spread,
            gravity,
            drag,
            steps,
            periodTicks,
            offset,
            extra,
            data,
            collide,
            collisionMode,
            restitution);
        ctx.state().track(handle);
        return scheduledHandle(handle, done);
      }
    };
  }

  public static Action particlesPhysics(Particle particle, int count, Vector velocity, double spread,
      double gravity, double drag, int steps, long periodTicks, double offset, double extra, Object data,
      boolean collide) {
    return particlesPhysics(
        particle,
        count,
        velocity,
        spread,
        gravity,
        drag,
        steps,
        periodTicks,
        offset,
        extra,
        data,
        collide,
        dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode.STOP,
        0.0);
  }

  /**
   * Visualizes the caster's look ray by drawing a particle line and marking the end/impact point.
   */
  public static Action visualizeLookRay(double maxDistance, double step, Particle lineParticle, Particle hitParticle) {
    Objects.requireNonNull(lineParticle, "lineParticle");
    Objects.requireNonNull(hitParticle, "hitParticle");
    if (maxDistance <= 0) {
      throw new IllegalArgumentException("maxDistance must be > 0");
    }
    if (step <= 0) {
      throw new IllegalArgumentException("step must be > 0");
    }

    return ctx -> {
      World world = ctx.world();
      if (world == null) {
        return;
      }
      Vector dir = ctx.direction().clone();
      if (dir.lengthSquared() < 1e-9) {
        dir.setX(0).setY(0).setZ(1);
      }
      dir.normalize();

      RayTraceResult blockHit = world.rayTraceBlocks(ctx.origin(), dir, maxDistance, FluidCollisionMode.NEVER, true);
      org.bukkit.util.Vector end = ctx.origin().toVector().add(dir.clone().multiply(maxDistance));
      if (blockHit != null && blockHit.getHitPosition() != null) {
        end = blockHit.getHitPosition();
      }

      double length = ctx.origin().toVector().distance(end);
      var pe = ctx.engine().particles();

      var pos = ctx.origin().clone();
      for (double d = 0.0; d <= length + 1e-9; d += step) {
        pe.emit(world, pos, lineParticle, 1, 0.0, 0.0, 0.0, 0.0);
        pos.add(dir.getX() * step, dir.getY() * step, dir.getZ() * step);
      }

      var hitLoc = ctx.origin().clone();
      hitLoc.set(end.getX(), end.getY(), end.getZ());
      pe.emit(world, hitLoc, hitParticle, 8, 0.08, 0.08, 0.08, 0.01);
    };
  }

  /**
   * Visualizes a capsule ray: a "thick ray" from origin along direction.
   */
  public static Action visualizeCapsuleRay(double maxDistance, double radius, double step, Particle ringParticle, Particle capParticle) {
    Objects.requireNonNull(ringParticle, "ringParticle");
    Objects.requireNonNull(capParticle, "capParticle");
    if (maxDistance <= 0) {
      throw new IllegalArgumentException("maxDistance must be > 0");
    }
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (step <= 0) {
      throw new IllegalArgumentException("step must be > 0");
    }

    return ctx -> {
      World world = ctx.world();
      if (world == null) {
        return;
      }

      Vector dir = ctx.direction().clone();
      if (dir.lengthSquared() < 1e-9) {
        dir.setX(0).setY(0).setZ(1);
      }
      dir.normalize();

      RayTraceResult blockHit = world.rayTraceBlocks(ctx.origin(), dir, maxDistance, FluidCollisionMode.NEVER, true);
      org.bukkit.util.Vector end = ctx.origin().toVector().add(dir.clone().multiply(maxDistance));
      if (blockHit != null && blockHit.getHitPosition() != null) {
        end = blockHit.getHitPosition();
      }
      double length = ctx.origin().toVector().distance(end);

      var pe = ctx.engine().particles();
      var pos = ctx.origin().clone();
      int points = Math.max(10, (int) Math.round(2 * Math.PI * radius * 6));
      points = Math.min(48, points);

      for (double d = 0.0; d <= length + 1e-9; d += step) {
        ParticleShapes.ring(pos, dir, radius, points,
            loc -> pe.emit(world, loc, ringParticle, 1, 0.0, 0.0, 0.0, 0.0));
        pos.add(dir.getX() * step, dir.getY() * step, dir.getZ() * step);
      }

      var endLoc = ctx.origin().clone();
      endLoc.set(end.getX(), end.getY(), end.getZ());
      pe.emit(world, ctx.origin(), capParticle, 10, 0.12, 0.12, 0.12, 0.02);
      pe.emit(world, endLoc, capParticle, 10, 0.12, 0.12, 0.12, 0.02);
    };
  }

  public static Action launchWitherSkull(double maxDistance, double raySize, boolean stopOnBlock, boolean requireTarget,
      double speed, boolean charged, float yield, boolean incendiary) {
    if (maxDistance <= 0) {
      throw new IllegalArgumentException("maxDistance must be > 0");
    }
    if (raySize < 0) {
      throw new IllegalArgumentException("raySize must be >= 0");
    }
    if (speed <= 0) {
      throw new IllegalArgumentException("speed must be > 0");
    }
    if (yield < 0) {
      throw new IllegalArgumentException("yield must be >= 0");
    }
    return ctx -> {
      Aim aim = resolveAim(ctx, maxDistance, raySize, stopOnBlock, requireTarget);
      if (aim == null || ctx.world() == null) {
        return;
      }
      WitherSkull skull = ctx.world().spawn(ctx.origin(), WitherSkull.class);
      skull.setShooter(ctx.caster());
      skull.setCharged(charged);
      skull.setYield(yield);
      skull.setIsIncendiary(incendiary);
      skull.setVelocity(aim.direction().clone().multiply(speed));
    };
  }

  public static Action launchFireball(double maxDistance, double raySize, boolean stopOnBlock, boolean requireTarget,
      double speed, float yield, boolean incendiary) {
    if (maxDistance <= 0) {
      throw new IllegalArgumentException("maxDistance must be > 0");
    }
    if (raySize < 0) {
      throw new IllegalArgumentException("raySize must be >= 0");
    }
    if (speed <= 0) {
      throw new IllegalArgumentException("speed must be > 0");
    }
    if (yield < 0) {
      throw new IllegalArgumentException("yield must be >= 0");
    }
    return ctx -> {
      Aim aim = resolveAim(ctx, maxDistance, raySize, stopOnBlock, requireTarget);
      if (aim == null || ctx.world() == null) {
        return;
      }
      Fireball fb = ctx.world().spawn(ctx.origin(), Fireball.class);
      fb.setShooter(ctx.caster());
      fb.setYield(yield);
      fb.setIsIncendiary(incendiary);
      fb.setVelocity(aim.direction().clone().multiply(speed));
    };
  }

  public static Action launchDragonFireball(double maxDistance, double raySize, boolean stopOnBlock, boolean requireTarget, double speed) {
    if (maxDistance <= 0) {
      throw new IllegalArgumentException("maxDistance must be > 0");
    }
    if (raySize < 0) {
      throw new IllegalArgumentException("raySize must be >= 0");
    }
    if (speed <= 0) {
      throw new IllegalArgumentException("speed must be > 0");
    }
    return ctx -> {
      Aim aim = resolveAim(ctx, maxDistance, raySize, stopOnBlock, requireTarget);
      if (aim == null || ctx.world() == null) {
        return;
      }
      DragonFireball fb = ctx.world().spawn(ctx.origin(), DragonFireball.class);
      fb.setShooter(ctx.caster());
      fb.setVelocity(aim.direction().clone().multiply(speed));
    };
  }

  public static Action arrowVolley(double maxDistance, double raySize, boolean stopOnBlock, boolean requireTarget,
      int count, double spreadDegrees, double speed, boolean spectral) {
    if (maxDistance <= 0) {
      throw new IllegalArgumentException("maxDistance must be > 0");
    }
    if (raySize < 0) {
      throw new IllegalArgumentException("raySize must be >= 0");
    }
    if (count <= 0) {
      throw new IllegalArgumentException("count must be > 0");
    }
    if (spreadDegrees < 0) {
      throw new IllegalArgumentException("spreadDegrees must be >= 0");
    }
    if (speed <= 0) {
      throw new IllegalArgumentException("speed must be > 0");
    }
    return ctx -> {
      Aim aim = resolveAim(ctx, maxDistance, raySize, stopOnBlock, requireTarget);
      if (aim == null || ctx.world() == null) {
        return;
      }
      double spreadRad = Math.toRadians(spreadDegrees);
      java.util.Random rng = ctx.rng();

      for (int i = 0; i < count; i++) {
        Vector dir = jitterDirection(aim.direction(), spreadRad, rng);
        AbstractArrow arrow;
        if (spectral) {
          arrow = ctx.world().spawn(ctx.origin(), SpectralArrow.class);
        } else {
          arrow = ctx.world().spawn(ctx.origin(), Arrow.class);
        }
        arrow.setShooter(ctx.caster());
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setVelocity(dir.multiply(speed));
      }
    };
  }

  public static Action throwTrident(double maxDistance, double raySize, boolean stopOnBlock, boolean requireTarget, double speed) {
    if (maxDistance <= 0) {
      throw new IllegalArgumentException("maxDistance must be > 0");
    }
    if (raySize < 0) {
      throw new IllegalArgumentException("raySize must be >= 0");
    }
    if (speed <= 0) {
      throw new IllegalArgumentException("speed must be > 0");
    }
    return ctx -> {
      Aim aim = resolveAim(ctx, maxDistance, raySize, stopOnBlock, requireTarget);
      if (aim == null || ctx.world() == null) {
        return;
      }
      Trident trident = ctx.world().spawn(ctx.origin(), Trident.class);
      trident.setShooter(ctx.caster());
      trident.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
      trident.setVelocity(aim.direction().clone().multiply(speed));
    };
  }

  public static Action strikeLightning(double maxDistance, boolean stopOnBlock, boolean requireTarget, boolean effectOnly) {
    if (maxDistance <= 0) {
      throw new IllegalArgumentException("maxDistance must be > 0");
    }
    return ctx -> {
      Aim aim = resolveAim(ctx, maxDistance, 0.5, stopOnBlock, requireTarget);
      if (aim == null || ctx.world() == null) {
        return;
      }
      Location loc = aim.hitEntity() != null ? aim.hitEntity().getLocation() : aim.point();
      if (effectOnly) {
        loc.getWorld().strikeLightningEffect(loc);
      } else {
        loc.getWorld().strikeLightning(loc);
      }
    };
  }

  public static Action explodeAtLook(double maxDistance, boolean stopOnBlock, boolean requireTarget, float power, boolean setFire, boolean breakBlocks) {
    if (maxDistance <= 0) {
      throw new IllegalArgumentException("maxDistance must be > 0");
    }
    if (power <= 0) {
      throw new IllegalArgumentException("power must be > 0");
    }
    return ctx -> {
      Aim aim = resolveAim(ctx, maxDistance, 0.6, stopOnBlock, requireTarget);
      if (aim == null || ctx.world() == null) {
        return;
      }
      Location loc = aim.hitEntity() != null ? aim.hitEntity().getLocation() : aim.point();
      loc.getWorld().createExplosion(loc, power, setFire, breakBlocks, ctx.caster());
    };
  }

  public static Action evokerFangsLine(double maxDistance, boolean stopOnBlock, boolean requireTarget, int count, double spacing, long periodTicks) {
    if (maxDistance <= 0) {
      throw new IllegalArgumentException("maxDistance must be > 0");
    }
    if (count <= 0) {
      throw new IllegalArgumentException("count must be > 0");
    }
    if (spacing <= 0) {
      throw new IllegalArgumentException("spacing must be > 0");
    }
    if (periodTicks <= 0) {
      throw new IllegalArgumentException("periodTicks must be > 0");
    }
    return ctx -> {
      Aim aim = resolveAim(ctx, maxDistance, 0.75, stopOnBlock, requireTarget);
      if (aim == null || ctx.world() == null) {
        return;
      }
      Vector flat = aim.direction().clone();
      flat.setY(0);
      if (flat.lengthSquared() < 1e-9) {
        flat = new Vector(0, 0, 1);
      }
      flat.normalize();
      final Vector flatDir = flat;
      final double spacingBlocks = spacing;

      for (int i = 0; i < count; i++) {
        int idx = i;
        var handle = ctx.engine().runLater(idx * periodTicks, () -> {
          if (ctx.state().isCancelled() || ctx.world() == null) {
            return;
          }
          var base = ctx.origin().clone().add(flatDir.getX() * spacingBlocks * (idx + 1), 0.0, flatDir.getZ() * spacingBlocks * (idx + 1));
          base = base.toHighestLocation().add(0, 0.1, 0);
          EvokerFangs fangs = ctx.world().spawn(base, EvokerFangs.class);
          fangs.setOwner(ctx.caster());
        });
        ctx.state().track(handle);
      }
    };
  }

  public static Action throwSplashPotion(double maxDistance, double raySize, boolean stopOnBlock, boolean requireTarget,
      PotionType basePotion, double speed) {
    Objects.requireNonNull(basePotion, "basePotion");
    if (maxDistance <= 0) {
      throw new IllegalArgumentException("maxDistance must be > 0");
    }
    if (raySize < 0) {
      throw new IllegalArgumentException("raySize must be >= 0");
    }
    if (speed <= 0) {
      throw new IllegalArgumentException("speed must be > 0");
    }
    return ctx -> {
      Aim aim = resolveAim(ctx, maxDistance, raySize, stopOnBlock, requireTarget);
      if (aim == null || ctx.world() == null) {
        return;
      }

      ItemStack stack = new ItemStack(org.bukkit.Material.SPLASH_POTION);
      var meta = stack.getItemMeta();
      if (!(meta instanceof PotionMeta pm)) {
        return;
      }
      pm.setBasePotionType(basePotion);
      stack.setItemMeta(pm);

      ThrownPotion potion = ctx.world().spawn(ctx.origin(), ThrownPotion.class);
      potion.setShooter(ctx.caster());
      potion.setItem(stack);
      potion.setVelocity(aim.direction().clone().multiply(speed));
    };
  }

  public static Action areaEffectCloud(double maxDistance, double raySize, boolean stopOnBlock, boolean requireTarget,
      PotionEffectType type, Duration effectDuration, int amplifier,
      long cloudDurationTicks, float radius, float radiusPerTick, int waitTicks, int reapplyDelayTicks) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(effectDuration, "effectDuration");
    if (maxDistance <= 0) {
      throw new IllegalArgumentException("maxDistance must be > 0");
    }
    if (raySize < 0) {
      throw new IllegalArgumentException("raySize must be >= 0");
    }
    if (amplifier < 0) {
      throw new IllegalArgumentException("amplifier must be >= 0");
    }
    if (cloudDurationTicks <= 0) {
      throw new IllegalArgumentException("cloudDurationTicks must be > 0");
    }
    if (radius <= 0) {
      throw new IllegalArgumentException("radius must be > 0");
    }
    if (waitTicks < 0 || reapplyDelayTicks < 0) {
      throw new IllegalArgumentException("waitTicks and reapplyDelayTicks must be >= 0");
    }

    int effectTicks = Math.max(1, (int) Math.min(Integer.MAX_VALUE, (effectDuration.toMillis() + 49L) / 50L));
    return ctx -> {
      Aim aim = resolveAim(ctx, maxDistance, raySize, stopOnBlock, requireTarget);
      if (aim == null || ctx.world() == null) {
        return;
      }
      Location loc = aim.hitEntity() != null ? aim.hitEntity().getLocation() : aim.point();
      AreaEffectCloud cloud = ctx.world().spawn(loc, AreaEffectCloud.class);
      cloud.setSource(ctx.caster());
      cloud.setDuration((int) Math.min(Integer.MAX_VALUE, cloudDurationTicks));
      cloud.setRadius(radius);
      cloud.setRadiusPerTick(radiusPerTick);
      cloud.setWaitTime(waitTicks);
      cloud.setReapplicationDelay(reapplyDelayTicks);
      cloud.addCustomEffect(new PotionEffect(type, effectTicks, amplifier, false, true, true), true);
    };
  }

  public static Action particlesRing(Particle particle, double radius, int points, int count, double offset, double extra) {
    return particlesRing(particle, radius, points, count, offset, extra, null);
  }

  public static Action particlesRing(Particle particle, double radius, int points, int count, double offset, double extra, Object data) {
    Objects.requireNonNull(particle, "particle");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (points <= 0) {
      throw new IllegalArgumentException("points must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      var pe = ctx.engine().particles();
      Vector n = ctx.direction();
      ParticleShapes.ring(ctx.origin(), n, radius, points,
          loc -> pe.emit(ctx.world(), loc, particle, count, offset, offset, offset, extra, resolveParticleData(data, ctx, loc)));
    };
  }

  public static Action particlesArc(Particle particle, double radius, double angleDegrees, int points, int count, double offset, double extra) {
    return particlesArc(particle, radius, angleDegrees, points, count, offset, extra, null);
  }

  public static Action particlesArc(Particle particle, double radius, double angleDegrees, int points, int count, double offset, double extra, Object data) {
    Objects.requireNonNull(particle, "particle");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (points <= 0) {
      throw new IllegalArgumentException("points must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      var pe = ctx.engine().particles();
      ParticleShapes.arc(ctx.origin(), ctx.direction(), radius, angleDegrees, points,
          loc -> pe.emit(ctx.world(), loc, particle, count, offset, offset, offset, extra, resolveParticleData(data, ctx, loc)));
    };
  }

  public static Action particlesDisk(Particle particle, double radius, int rings, int pointsPerRing, int count, double offset, double extra) {
    return particlesDisk(particle, radius, rings, pointsPerRing, count, offset, extra, null);
  }

  public static Action particlesDisk(Particle particle, double radius, int rings, int pointsPerRing, int count, double offset, double extra, Object data) {
    Objects.requireNonNull(particle, "particle");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      var pe = ctx.engine().particles();
      ParticleShapes.disk(ctx.origin(), ctx.direction(), radius, rings, pointsPerRing,
          loc -> pe.emit(ctx.world(), loc, particle, count, offset, offset, offset, extra, resolveParticleData(data, ctx, loc)));
    };
  }

  public static Action particlesSphereShell(Particle particle, double radius, int points, int count, double offset, double extra) {
    return particlesSphereShell(particle, radius, points, count, offset, extra, null);
  }

  public static Action particlesSphereShell(Particle particle, double radius, int points, int count, double offset, double extra, Object data) {
    Objects.requireNonNull(particle, "particle");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (points <= 0) {
      throw new IllegalArgumentException("points must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      var pe = ctx.engine().particles();
      ParticleShapes.sphereShell(ctx.origin(), radius, points,
          loc -> pe.emit(ctx.world(), loc, particle, count, offset, offset, offset, extra, resolveParticleData(data, ctx, loc)));
    };
  }

  public static Action particlesSphereFilled(Particle particle, double radius, int points, int count, double offset, double extra) {
    return particlesSphereFilled(particle, radius, points, count, offset, extra, null);
  }

  public static Action particlesSphereFilled(Particle particle, double radius, int points, int count, double offset, double extra, Object data) {
    Objects.requireNonNull(particle, "particle");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (points <= 0) {
      throw new IllegalArgumentException("points must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      var pe = ctx.engine().particles();
      ParticleShapes.sphereFilled(ctx.origin(), radius, points,
          loc -> pe.emit(ctx.world(), loc, particle, count, offset, offset, offset, extra, resolveParticleData(data, ctx, loc)));
    };
  }

  public static Action particlesHelix(Particle particle, double radius, double length, int turns, int points, int count, double offset, double extra) {
    return particlesHelix(particle, radius, length, turns, points, count, offset, extra, null);
  }

  public static Action particlesHelix(Particle particle, double radius, double length, int turns, int points, int count, double offset, double extra, Object data) {
    Objects.requireNonNull(particle, "particle");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (length < 0) {
      throw new IllegalArgumentException("length must be >= 0");
    }
    if (points <= 0) {
      throw new IllegalArgumentException("points must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      var pe = ctx.engine().particles();
      ParticleShapes.helix(ctx.origin(), ctx.direction(), radius, length, turns, points,
          loc -> pe.emit(ctx.world(), loc, particle, count, offset, offset, offset, extra, resolveParticleData(data, ctx, loc)));
    };
  }

  public static Action particlesBezier(java.util.function.Function<CastContext, org.bukkit.Location> p0,
      java.util.function.Function<CastContext, org.bukkit.Location> p1,
      java.util.function.Function<CastContext, org.bukkit.Location> p2,
      java.util.function.Function<CastContext, org.bukkit.Location> p3,
      double pointsPerMeter, int maxPoints, Particle particle, int count, double offset, double extra) {
    return particlesBezier(p0, p1, p2, p3, pointsPerMeter, maxPoints, particle, count, offset, extra, null);
  }

  public static Action particlesBezier(java.util.function.Function<CastContext, org.bukkit.Location> p0,
      java.util.function.Function<CastContext, org.bukkit.Location> p1,
      java.util.function.Function<CastContext, org.bukkit.Location> p2,
      java.util.function.Function<CastContext, org.bukkit.Location> p3,
      double pointsPerMeter, int maxPoints, Particle particle, int count, double offset, double extra, Object data) {
    Objects.requireNonNull(p0, "p0");
    Objects.requireNonNull(p1, "p1");
    Objects.requireNonNull(p2, "p2");
    Objects.requireNonNull(p3, "p3");
    Objects.requireNonNull(particle, "particle");
    if (pointsPerMeter <= 0) {
      throw new IllegalArgumentException("pointsPerMeter must be > 0");
    }
    if (maxPoints <= 0) {
      throw new IllegalArgumentException("maxPoints must be > 0");
    }

    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      var a = p0.apply(ctx);
      var b = p1.apply(ctx);
      var c = p2.apply(ctx);
      var d = p3.apply(ctx);
      if (a == null || b == null || c == null || d == null) {
        return;
      }
      if (a.getWorld() == null) {
        return;
      }
      var pe = ctx.engine().particles();
      ParticleShapes.cubicBezier(a, b, c, d, pointsPerMeter, maxPoints,
          loc -> pe.emit(a.getWorld(), loc, particle, count, offset, offset, offset, extra, resolveParticleData(data, ctx, loc)));
    };
  }

  /**
   * Emits a trail following {@code frame} for {@code durationTicks}.
   */
  public static Action particlesTrail(Frame frame, long durationTicks, long periodTicks,
      Particle particle, int count, double offset, double extra) {
    Objects.requireNonNull(frame, "frame");
    Objects.requireNonNull(particle, "particle");
    if (durationTicks <= 0) {
      throw new IllegalArgumentException("durationTicks must be > 0");
    }
    if (periodTicks <= 0) {
      throw new IllegalArgumentException("periodTicks must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      final long start = ctx.tick();
      final EffectsEngine.ScheduledHandle[] handle = new EffectsEngine.ScheduledHandle[1];
      handle[0] = ctx.engine().runRepeating(0L, periodTicks, () -> {
        if (handle[0] == null || handle[0].isCancelled()) {
          return;
        }
        long elapsed = ctx.engine().tickNow() - start;
        if (elapsed >= durationTicks) {
          handle[0].cancel();
          return;
        }
        var loc = frame.location(ctx);
        if (loc == null || loc.getWorld() == null) {
          handle[0].cancel();
          return;
        }
        ctx.engine().particles().emit(loc.getWorld(), loc, particle, count, offset, offset, offset, extra);
      });
      ctx.state().track(handle[0]);
    };
  }

  public static Action particlesTrailCasterEyes(long durationTicks, Particle particle, int count, double offset, double extra) {
    return particlesTrail(Frames.casterEyes(), durationTicks, 1L, particle, count, offset, extra);
  }

  public static Action sound(Sound sound, float volume, float pitch) {
    Objects.requireNonNull(sound, "sound");
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      ctx.world().playSound(ctx.origin(), sound, volume, pitch);
    };
  }

  public static Action soundAtFrame(Frame frame, Sound sound, float volume, float pitch) {
    Objects.requireNonNull(frame, "frame");
    Objects.requireNonNull(sound, "sound");
    return ctx -> {
      Location loc = frame.location(ctx);
      if (loc == null || loc.getWorld() == null) {
        return;
      }
      loc.getWorld().playSound(loc, sound, volume, pitch);
    };
  }

  public static Frames.FrameSpec frameWithOffsets(Frame frame, double forward, double right, double up) {
    return Frames.withOffsets(frame, forward, right, up);
  }

  public static Action message(Component message) {
    Objects.requireNonNull(message, "message");
    return ctx -> {
      if (ctx.caster() instanceof Player player) {
        player.sendMessage(message);
      }
    };
  }

  public static Action actionBar(Component message) {
    Objects.requireNonNull(message, "message");
    return ctx -> {
      if (ctx.caster() instanceof Player player) {
        player.sendActionBar(message);
      }
    };
  }

  public static Action title(Component title, Component subtitle, Duration fadeIn, Duration stay, Duration fadeOut) {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(subtitle, "subtitle");
    Objects.requireNonNull(fadeIn, "fadeIn");
    Objects.requireNonNull(stay, "stay");
    Objects.requireNonNull(fadeOut, "fadeOut");
    return ctx -> {
      if (ctx.caster() instanceof Player player) {
        player.showTitle(Title.title(title, subtitle, Title.Times.times(fadeIn, stay, fadeOut)));
      }
    };
  }

  public static Action overlay(Component title, Component subtitle, Duration fadeIn, Duration stay, Duration fadeOut) {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(subtitle, "subtitle");
    Objects.requireNonNull(fadeIn, "fadeIn");
    Objects.requireNonNull(stay, "stay");
    Objects.requireNonNull(fadeOut, "fadeOut");
    return ctx -> {
      if (ctx.caster() instanceof Player player) {
        if (!ctx.engine().cinematicSettings().enabled(player, CinematicSettings.Flag.OVERLAY)) {
          return;
        }
        player.showTitle(Title.title(title, subtitle, Title.Times.times(fadeIn, stay, fadeOut)));
      }
    };
  }

  public static Action screenShake(int durationTicks, int amplifier, boolean ambient, boolean particles, boolean icon) {
    return ctx -> {
      if (ctx.caster() instanceof Player player) {
        screenShake(player, ctx.engine().cinematicSettings(), durationTicks, amplifier, ambient, particles, icon);
      }
    };
  }

  public static void screenShake(Player player, CinematicSettings settings, int durationTicks, int amplifier,
      boolean ambient, boolean particles, boolean icon) {
    if (player == null || durationTicks <= 0) {
      return;
    }
    if (!settings.enabled(player, CinematicSettings.Flag.SHAKE)) {
      return;
    }
    int amp = Math.max(0, amplifier);
    player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, durationTicks, amp, ambient, particles, icon));
  }

  public static Action screenFlash(Particle particle, int count, double offset, double extra) {
    Objects.requireNonNull(particle, "particle");
    return ctx -> {
      if (ctx.caster() instanceof Player player) {
        screenFlash(player, ctx.engine().cinematicSettings(), particle, count, offset, extra);
      }
    };
  }

  public static void screenFlash(Player player, CinematicSettings settings, Particle particle, int count, double offset, double extra) {
    if (player == null || count <= 0) {
      return;
    }
    if (!settings.enabled(player, CinematicSettings.Flag.FLASH)) {
      return;
    }
    Location loc = player.getEyeLocation();
    if (loc.getWorld() == null) {
      return;
    }
    loc.getWorld().spawnParticle(particle, loc, count, offset, offset, offset, extra);
  }

  public static Action teleportCasterToLook(double maxDistance, boolean stopOnBlock, double safetyBackoff) {
    if (maxDistance <= 0) {
      throw new IllegalArgumentException("maxDistance must be > 0");
    }
    if (!Double.isFinite(safetyBackoff) || safetyBackoff < 0) {
      throw new IllegalArgumentException("safetyBackoff must be finite and >= 0");
    }

    return ctx -> {
      World world = ctx.world();
      if (world == null) {
        return;
      }
      Vector dir = ctx.direction().clone();
      if (dir.lengthSquared() < 1e-9) {
        dir.setX(0).setY(0).setZ(1);
      }
      dir.normalize();

      RayTraceResult blockHit = stopOnBlock ? world.rayTraceBlocks(ctx.origin(), dir, maxDistance, FluidCollisionMode.NEVER, true) : null;
      Vector end = ctx.origin().toVector().add(dir.clone().multiply(maxDistance));
      if (blockHit != null && blockHit.getHitPosition() != null) {
        end = blockHit.getHitPosition().clone();
        if (safetyBackoff > 0) {
          end.subtract(dir.clone().multiply(safetyBackoff));
        }
      }

      Location dest = ctx.origin().clone();
      dest.set(end.getX(), end.getY(), end.getZ());
      Location casterLoc = ctx.caster().getLocation();
      dest.setYaw(casterLoc.getYaw());
      dest.setPitch(casterLoc.getPitch());
      ctx.caster().teleport(dest);
    };
  }

  public static Action dashCaster(double maxDistance, boolean stopOnBlock,
      double horizontal, double vertical,
      double maxHorizontal, double maxVertical,
      boolean addToExisting) {
    if (maxDistance <= 0) {
      throw new IllegalArgumentException("maxDistance must be > 0");
    }
    if (!Double.isFinite(horizontal) || !Double.isFinite(vertical)) {
      throw new IllegalArgumentException("horizontal/vertical must be finite");
    }
    if (maxHorizontal < 0 || maxVertical < 0) {
      throw new IllegalArgumentException("maxHorizontal/maxVertical must be >= 0");
    }

    return ctx -> {
      World world = ctx.world();
      if (world == null) {
        return;
      }
      Vector dir = ctx.direction().clone();
      dir.setY(0);
      if (dir.lengthSquared() < 1e-9) {
        dir.setX(0).setY(0).setZ(1);
      }
      dir.normalize();

      double scale = 1.0;
      if (stopOnBlock) {
        RayTraceResult hit = world.rayTraceBlocks(ctx.caster().getLocation(), dir, maxDistance, FluidCollisionMode.NEVER, true);
        if (hit != null && hit.getHitPosition() != null) {
          double dist = hit.getHitPosition().distance(ctx.caster().getLocation().toVector());
          scale = Math.max(0.0, Math.min(1.0, dist / maxDistance));
        }
      }

      Vector impulse = dir.multiply(horizontal * scale);
      impulse.setY(vertical);
      EntityActions.setVelocity(impulse, addToExisting, maxHorizontal, maxVertical).execute(ctx, ctx.caster());
    };
  }

  public static Action setVelocityCaster(Vector velocity, boolean addToExisting, double maxHorizontal, double maxVertical) {
    Objects.requireNonNull(velocity, "velocity");
    return ctx -> EntityActions.setVelocity(velocity, addToExisting, maxHorizontal, maxVertical).execute(ctx, ctx.caster());
  }

  public static Action setVar(String key, Object value) {
    Objects.requireNonNull(key, "key");
    return ctx -> ctx.state().put(key, value);
  }

  public static <T> Action setVar(dev.patric.dungeonsreborn.effects.VarKey<T> key, T value) {
    Objects.requireNonNull(key, "key");
    return ctx -> ctx.state().put(key, value);
  }

  public static Action debugLog(String message) {
    Objects.requireNonNull(message, "message");
    return ctx -> ctx.engine().debug("castId=" + ctx.castId() + " tick=" + ctx.tick() + " ability=" + ctx.abilityId() + " " + message);
  }

  public static Action chance(double probability, Action action) {
    Objects.requireNonNull(action, "action");
    if (!Double.isFinite(probability)) {
      throw new IllegalArgumentException("probability must be finite");
    }
    if (probability <= 0.0) {
      return noop();
    }
    if (probability >= 1.0) {
      return action;
    }
    return ctx -> {
      if (ctx.rng().nextDouble() < probability) {
        action.execute(ctx);
      }
    };
  }

  public static Action chanceElse(double probability, Action then, Action otherwise) {
    Objects.requireNonNull(then, "then");
    Objects.requireNonNull(otherwise, "otherwise");
    if (!Double.isFinite(probability)) {
      throw new IllegalArgumentException("probability must be finite");
    }
    if (probability <= 0.0) {
      return otherwise;
    }
    if (probability >= 1.0) {
      return then;
    }
    return ctx -> {
      if (ctx.rng().nextDouble() < probability) {
        then.execute(ctx);
      } else {
        otherwise.execute(ctx);
      }
    };
  }

  public static Action randomChoice(Action... actions) {
    Objects.requireNonNull(actions, "actions");
    if (actions.length == 0) {
      return noop();
    }
    for (Action action : actions) {
      Objects.requireNonNull(action, "action");
    }
    return ctx -> actions[ctx.rng().nextInt(actions.length)].execute(ctx);
  }

  public static Action randomChoiceWeighted(double[] weights, Action[] actions) {
    Objects.requireNonNull(weights, "weights");
    Objects.requireNonNull(actions, "actions");
    if (weights.length != actions.length) {
      throw new IllegalArgumentException("weights and actions must have same length");
    }
    if (actions.length == 0) {
      return noop();
    }
    double total = 0.0;
    for (int i = 0; i < actions.length; i++) {
      Objects.requireNonNull(actions[i], "action");
      double w = weights[i];
      if (!Double.isFinite(w) || w < 0) {
        throw new IllegalArgumentException("weights must be finite and >= 0");
      }
      total += w;
    }
    if (total <= 0) {
      return noop();
    }
    final double sum = total;
    return ctx -> {
      double roll = ctx.rng().nextDouble() * sum;
      double acc = 0.0;
      for (int i = 0; i < actions.length; i++) {
        acc += weights[i];
        if (roll <= acc) {
          actions[i].execute(ctx);
          return;
        }
      }
      actions[actions.length - 1].execute(ctx);
    };
  }

  public static <T> Action withVar(String key, Class<T> type, T defaultValue, BiConsumer<CastContext, T> consumer) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(consumer, "consumer");
    return ctx -> {
      Object v = ctx.state().get(key);
      T typed = (v != null && type.isInstance(v)) ? type.cast(v) : defaultValue;
      consumer.accept(ctx, typed);
    };
  }

  public static Action incrementIntVar(String key, int delta, int defaultValue) {
    Objects.requireNonNull(key, "key");
    return ctx -> {
      Object v = ctx.state().get(key);
      int current = v instanceof Number n ? n.intValue() : defaultValue;
      ctx.state().put(key, current + delta);
    };
  }

  public static Action particlesCone(Particle particle, double length, double angleDegrees, int rings, int pointsPerRing,
      int count, double offset, double extra) {
    return particlesCone(particle, length, angleDegrees, rings, pointsPerRing, count, offset, extra, null);
  }

  public static Action particlesCone(Particle particle, double length, double angleDegrees, int rings, int pointsPerRing,
      int count, double offset, double extra, Object data) {
    Objects.requireNonNull(particle, "particle");
    if (length < 0) {
      throw new IllegalArgumentException("length must be >= 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      var pe = ctx.engine().particles();
      ParticleShapes.coneShell(ctx.origin(), ctx.direction(), length, angleDegrees, rings, pointsPerRing,
          loc -> pe.emit(ctx.world(), loc, particle, count, offset, offset, offset, extra, resolveParticleData(data, ctx, loc)));
    };
  }

  public static Action particlesCylinder(Particle particle, double radius, double height, int rings, int pointsPerRing,
      int count, double offset, double extra) {
    return particlesCylinder(particle, radius, height, rings, pointsPerRing, count, offset, extra, null);
  }

  public static Action particlesCylinder(Particle particle, double radius, double height, int rings, int pointsPerRing,
      int count, double offset, double extra, Object data) {
    Objects.requireNonNull(particle, "particle");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (height < 0) {
      throw new IllegalArgumentException("height must be >= 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      var pe = ctx.engine().particles();
      ParticleShapes.cylinderShell(ctx.origin(), new Vector(0, 1, 0), radius, height, rings, pointsPerRing,
          loc -> pe.emit(ctx.world(), loc, particle, count, offset, offset, offset, extra, resolveParticleData(data, ctx, loc)));
    };
  }

  public static Action particlesBox(Particle particle, double xRadius, double yRadius, double zRadius, double step,
      int count, double offset, double extra) {
    return particlesBox(particle, xRadius, yRadius, zRadius, step, count, offset, extra, null);
  }

  public static Action particlesBox(Particle particle, double xRadius, double yRadius, double zRadius, double step,
      int count, double offset, double extra, Object data) {
    Objects.requireNonNull(particle, "particle");
    if (xRadius < 0 || yRadius < 0 || zRadius < 0) {
      throw new IllegalArgumentException("radii must be >= 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      var pe = ctx.engine().particles();
      ParticleShapes.boxOutline(ctx.origin(), xRadius, yRadius, zRadius, step,
          loc -> pe.emit(ctx.world(), loc, particle, count, offset, offset, offset, extra, resolveParticleData(data, ctx, loc)));
    };
  }

  public static Action particlesPolygon(Particle particle, Vector normal, double radius, int sides, int pointsPerEdge,
      int count, double offset, double extra) {
    return particlesPolygon(particle, normal, radius, sides, pointsPerEdge, count, offset, extra, null);
  }

  public static Action particlesPolygon(Particle particle, Vector normal, double radius, int sides, int pointsPerEdge,
      int count, double offset, double extra, Object data) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(normal, "normal");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      var pe = ctx.engine().particles();
      ParticleShapes.polygonOutline(ctx.origin(), normal, radius, sides, pointsPerEdge,
          loc -> pe.emit(ctx.world(), loc, particle, count, offset, offset, offset, extra, resolveParticleData(data, ctx, loc)));
    };
  }

  public static Action particlesSpline(java.util.List<java.util.function.Function<CastContext, Location>> controlPoints,
      double pointsPerMeter, int maxPoints, Particle particle, int count, double offset, double extra) {
    return particlesSpline(controlPoints, pointsPerMeter, maxPoints, particle, count, offset, extra, null);
  }

  public static Action particlesSpline(java.util.List<java.util.function.Function<CastContext, Location>> controlPoints,
      double pointsPerMeter, int maxPoints, Particle particle, int count, double offset, double extra, Object data) {
    Objects.requireNonNull(controlPoints, "controlPoints");
    Objects.requireNonNull(particle, "particle");
    if (controlPoints.size() < 2) {
      throw new IllegalArgumentException("controlPoints must have at least 2 points");
    }
    if (pointsPerMeter <= 0) {
      throw new IllegalArgumentException("pointsPerMeter must be > 0");
    }
    if (maxPoints <= 0) {
      throw new IllegalArgumentException("maxPoints must be > 0");
    }

    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      java.util.ArrayList<Location> pts = new java.util.ArrayList<>(controlPoints.size());
      for (var fn : controlPoints) {
        if (fn == null) {
          return;
        }
        Location loc = fn.apply(ctx);
        if (loc == null || loc.getWorld() == null) {
          return;
        }
        pts.add(loc);
      }
      if (pts.size() < 2) {
        return;
      }
      var pe = ctx.engine().particles();
      ParticleShapes.catmullRomSpline(pts, pointsPerMeter, maxPoints,
          loc -> pe.emit(loc.getWorld(), loc, particle, count, offset, offset, offset, extra, resolveParticleData(data, ctx, loc)));
    };
  }

  private static java.util.List<Location> buildSplinePoints(
      java.util.List<java.util.function.Function<CastContext, Location>> controlPoints,
      double pointsPerMeter,
      int maxPoints,
      CastContext ctx) {
    java.util.ArrayList<Location> pts = new java.util.ArrayList<>(controlPoints.size());
    for (var fn : controlPoints) {
      if (fn == null) {
        return java.util.List.of();
      }
      Location loc = fn.apply(ctx);
      if (loc == null || loc.getWorld() == null) {
        return java.util.List.of();
      }
      pts.add(loc);
    }
    if (pts.size() < 2) {
      return java.util.List.of();
    }
    java.util.ArrayList<Location> out = new java.util.ArrayList<>();
    ParticleShapes.catmullRomSpline(pts, pointsPerMeter, maxPoints, loc -> out.add(loc.clone()));
    return out;
  }

  public static Action presetSplineMotion(
      java.util.List<java.util.function.Function<CastContext, Location>> controlPoints,
      double pointsPerMeter,
      int maxPoints,
      long durationTicks,
      long periodTicks,
      DoubleUnaryOperator easing,
      Particle particle,
      int count,
      double offset,
      double extra,
      Object data) {
    Objects.requireNonNull(controlPoints, "controlPoints");
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(easing, "easing");
    if (pointsPerMeter <= 0) {
      throw new IllegalArgumentException("pointsPerMeter must be > 0");
    }
    if (maxPoints <= 0) {
      throw new IllegalArgumentException("maxPoints must be > 0");
    }
    if (durationTicks <= 0 || periodTicks <= 0) {
      throw new IllegalArgumentException("durationTicks and periodTicks must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      java.util.List<Location> points = buildSplinePoints(controlPoints, pointsPerMeter, maxPoints, ctx);
      if (points.size() < 2) {
        return;
      }
      Actions.animate(durationTicks, periodTicks, easing, (tickCtx, t) -> {
        int idx = (int) Math.round((points.size() - 1) * t);
        idx = Math.max(0, Math.min(points.size() - 1, idx));
        Location loc = points.get(idx);
        Object resolved = resolveParticleData(data, tickCtx, loc);
        tickCtx.engine().particles().emit(loc.getWorld(), loc, particle, count, offset, offset, offset, extra, resolved);
      }).execute(ctx);
    };
  }

  public static Action presetShockwave(Particle particle, double startRadius, double endRadius,
      long durationTicks, long periodTicks, DoubleUnaryOperator easing,
      int points, int count, double offset, double extra) {
    return presetShockwave(particle, startRadius, endRadius, durationTicks, periodTicks, easing, points, count, offset, extra, null);
  }

  public static Action presetShockwave(Particle particle, double startRadius, double endRadius,
      long durationTicks, long periodTicks, DoubleUnaryOperator easing,
      int points, int count, double offset, double extra, Object data) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(easing, "easing");
    if (startRadius < 0 || endRadius < 0) {
      throw new IllegalArgumentException("radii must be >= 0");
    }
    if (points <= 0) {
      throw new IllegalArgumentException("points must be > 0");
    }
    return animate(durationTicks, periodTicks, easing, (ctx, t) -> {
      if (ctx.world() == null) {
        return;
      }
      double r = startRadius + (endRadius - startRadius) * t;
      var center = ctx.origin().clone();
      var pe = ctx.engine().particles();
        ParticleShapes.ring(center, new Vector(0, 1, 0), r, points,
            loc -> pe.emit(ctx.world(), loc, particle, count, offset, offset, offset, extra, resolveParticleData(data, ctx, loc)));
    });
  }

  private static Color lerpColor(Color start, Color end, double t) {
    double clamped = Math.max(0.0, Math.min(1.0, t));
    int r = (int) Math.round(start.getRed() + (end.getRed() - start.getRed()) * clamped);
    int g = (int) Math.round(start.getGreen() + (end.getGreen() - start.getGreen()) * clamped);
    int b = (int) Math.round(start.getBlue() + (end.getBlue() - start.getBlue()) * clamped);
    return Color.fromRGB(
        Math.max(0, Math.min(255, r)),
        Math.max(0, Math.min(255, g)),
        Math.max(0, Math.min(255, b)));
  }

  private static Object resolveGradientData(Particle particle, Object data, CastContext ctx, Location loc,
      Color from, Color to, float size, double t) {
    Object resolved = resolveParticleData(data, ctx, loc);
    if (from == null || to == null) {
      return resolved;
    }
    if (particle == Particle.DUST) {
      return new Particle.DustOptions(lerpColor(from, to, t), size);
    }
    if (particle == Particle.DUST_COLOR_TRANSITION) {
      return new Particle.DustTransition(from, to, size);
    }
    return resolved;
  }

  private static void emitGradient(CastContext ctx, Location loc, Particle particle, int count, double offset, double extra, Object data,
      Color from, Color to, float size, double t) {
    Object resolved = resolveGradientData(particle, data, ctx, loc, from, to, size, t);
    ctx.engine().particles().emit(loc.getWorld(), loc, particle, count, offset, offset, offset, extra, resolved);
  }

  private static java.util.function.Consumer<Location> gradientConsumer(CastContext ctx, Particle particle, int count,
      double offset, double extra, Object data, Color from, Color to, float size, int totalPoints) {
    AtomicInteger index = new AtomicInteger(0);
    int denom = Math.max(1, totalPoints - 1);
    return loc -> {
      int i = index.getAndIncrement();
      double t = denom == 0 ? 0.0 : Math.min(1.0, Math.max(0.0, i / (double) denom));
      emitGradient(ctx, loc, particle, count, offset, extra, data, from, to, size, t);
    };
  }

  private static java.util.List<Location> samplePolyline(java.util.List<Location> points, double step) {
    var out = new java.util.ArrayList<Location>();
    ParticleShapes.polyline(points, step, loc -> out.add(loc.clone()));
    return out;
  }

  private static java.util.List<Location> sampleMesh(java.util.List<Location[]> triangles, double step) {
    var out = new java.util.ArrayList<Location>();
    ParticleShapes.mesh(triangles, step, loc -> out.add(loc.clone()));
    return out;
  }

  public static Action presetMorphRing(Particle particle, double startRadius, double endRadius,
      long durationTicks, long periodTicks, DoubleUnaryOperator easing,
      int points, int count, double offset, double extra, Object data,
      Color startColor, Color endColor, float size) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(easing, "easing");
    if (startRadius < 0 || endRadius < 0) {
      throw new IllegalArgumentException("radii must be >= 0");
    }
    if (points <= 0) {
      throw new IllegalArgumentException("points must be > 0");
    }
    return animate(durationTicks, periodTicks, easing, (ctx, t) -> {
      if (ctx.world() == null) {
        return;
      }
      double r = startRadius + (endRadius - startRadius) * t;
      var center = ctx.origin().clone();
      var pe = ctx.engine().particles();
      ParticleShapes.ring(center, new Vector(0, 1, 0), r, points, loc -> {
        Object resolved = resolveParticleData(data, ctx, loc);
        if (startColor != null && endColor != null) {
          if (particle == Particle.DUST) {
            resolved = new Particle.DustOptions(lerpColor(startColor, endColor, t), size);
          } else if (particle == Particle.DUST_COLOR_TRANSITION) {
            resolved = new Particle.DustTransition(startColor, endColor, size);
          }
        }
        pe.emit(ctx.world(), loc, particle, count, offset, offset, offset, extra, resolved);
      });
    });
  }

  public static Action presetGradientRing(Particle particle, double radius, int points, int count, double offset, double extra,
      Object data, Color from, Color to, float size) {
    Objects.requireNonNull(particle, "particle");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (points <= 0) {
      throw new IllegalArgumentException("points must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      @SuppressWarnings("unused")
      var pe = ctx.engine().particles();
      var consumer = gradientConsumer(ctx, particle, count, offset, extra, data, from, to, size, points);
      ParticleShapes.ring(ctx.origin(), new Vector(0, 1, 0), radius, points, consumer);
    };
  }

  public static Action presetMorphLine(Particle particle, double startLength, double endLength,
      long durationTicks, long periodTicks, DoubleUnaryOperator easing,
      double step, int count, double offset, double extra, Object data,
      Color startColor, Color endColor, float size) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(easing, "easing");
    if (step <= 0) {
      throw new IllegalArgumentException("step must be > 0");
    }
    return animate(durationTicks, periodTicks, easing, (ctx, t) -> {
      if (ctx.world() == null) {
        return;
      }
      double length = startLength + (endLength - startLength) * t;
      if (length < 0) {
        return;
      }
      Vector dir = ctx.direction().clone();
      if (dir.lengthSquared() < 1e-9) {
        dir.setX(0).setY(0).setZ(1);
      }
      dir.normalize();
      Location pos = ctx.origin().clone();
      for (double d = 0.0; d <= length + 1e-9; d += step) {
        emitGradient(ctx, pos, particle, count, offset, extra, data, startColor, endColor, size, t);
        pos.add(dir.getX() * step, dir.getY() * step, dir.getZ() * step);
      }
    });
  }

  public static Action presetGradientLine(Particle particle, double length, double step, int count, double offset, double extra,
      Object data, Color from, Color to, float size) {
    Objects.requireNonNull(particle, "particle");
    if (length < 0) {
      throw new IllegalArgumentException("length must be >= 0");
    }
    if (step <= 0) {
      throw new IllegalArgumentException("step must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      Vector dir = ctx.direction().clone();
      if (dir.lengthSquared() < 1e-9) {
        dir.setX(0).setY(0).setZ(1);
      }
      dir.normalize();
      int totalPoints = (int) Math.floor(length / step) + 1;
      var consumer = gradientConsumer(ctx, particle, count, offset, extra, data, from, to, size, totalPoints);
      Location pos = ctx.origin().clone();
      for (double d = 0.0; d <= length + 1e-9; d += step) {
        consumer.accept(pos);
        pos.add(dir.getX() * step, dir.getY() * step, dir.getZ() * step);
      }
    };
  }

  public static Action presetMorphArc(Particle particle, double startRadius, double endRadius, double angleDegrees,
      long durationTicks, long periodTicks, DoubleUnaryOperator easing,
      int points, int count, double offset, double extra, Object data,
      Color startColor, Color endColor, float size) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(easing, "easing");
    if (angleDegrees <= 0 || angleDegrees > 360) {
      throw new IllegalArgumentException("angleDegrees must be in (0, 360]");
    }
    if (points <= 0) {
      throw new IllegalArgumentException("points must be > 0");
    }
    return animate(durationTicks, periodTicks, easing, (ctx, t) -> {
      if (ctx.world() == null) {
        return;
      }
      double r = startRadius + (endRadius - startRadius) * t;
      if (r < 0) {
        return;
      }
      ParticleShapes.arc(ctx.origin(), new Vector(0, 1, 0), r, angleDegrees, points,
          loc -> emitGradient(ctx, loc, particle, count, offset, extra, data, startColor, endColor, size, t));
    });
  }

  public static Action presetGradientArc(Particle particle, double radius, double angleDegrees, int points, int count, double offset, double extra,
      Object data, Color from, Color to, float size) {
    Objects.requireNonNull(particle, "particle");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (angleDegrees <= 0 || angleDegrees > 360) {
      throw new IllegalArgumentException("angleDegrees must be in (0, 360]");
    }
    if (points <= 0) {
      throw new IllegalArgumentException("points must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      var consumer = gradientConsumer(ctx, particle, count, offset, extra, data, from, to, size, points);
      ParticleShapes.arc(ctx.origin(), new Vector(0, 1, 0), radius, angleDegrees, points, consumer);
    };
  }

  public static Action presetMorphDisk(Particle particle, double startRadius, double endRadius, int rings, int pointsPerRing,
      long durationTicks, long periodTicks, DoubleUnaryOperator easing,
      int count, double offset, double extra, Object data, Color startColor, Color endColor, float size) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(easing, "easing");
    if (rings <= 0 || pointsPerRing <= 0) {
      throw new IllegalArgumentException("rings and pointsPerRing must be > 0");
    }
    return animate(durationTicks, periodTicks, easing, (ctx, t) -> {
      if (ctx.world() == null) {
        return;
      }
      double r = startRadius + (endRadius - startRadius) * t;
      if (r < 0) {
        return;
      }
      ParticleShapes.disk(ctx.origin(), new Vector(0, 1, 0), r, rings, pointsPerRing,
          loc -> emitGradient(ctx, loc, particle, count, offset, extra, data, startColor, endColor, size, t));
    });
  }

  public static Action presetGradientDisk(Particle particle, double radius, int rings, int pointsPerRing, int count, double offset, double extra,
      Object data, Color from, Color to, float size) {
    Objects.requireNonNull(particle, "particle");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (rings <= 0 || pointsPerRing <= 0) {
      throw new IllegalArgumentException("rings and pointsPerRing must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      int totalPoints = 1;
      for (int i = 1; i <= rings; i++) {
        int pts = Math.max(6, (int) Math.round(pointsPerRing * (i / (double) rings)));
        totalPoints += pts;
      }
      var consumer = gradientConsumer(ctx, particle, count, offset, extra, data, from, to, size, totalPoints);
      ParticleShapes.disk(ctx.origin(), new Vector(0, 1, 0), radius, rings, pointsPerRing, consumer);
    };
  }

  public static Action presetMorphSphereShell(Particle particle, double startRadius, double endRadius,
      long durationTicks, long periodTicks, DoubleUnaryOperator easing,
      int points, int count, double offset, double extra, Object data, Color startColor, Color endColor, float size) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(easing, "easing");
    if (points <= 0) {
      throw new IllegalArgumentException("points must be > 0");
    }
    return animate(durationTicks, periodTicks, easing, (ctx, t) -> {
      if (ctx.world() == null) {
        return;
      }
      double r = startRadius + (endRadius - startRadius) * t;
      if (r < 0) {
        return;
      }
      ParticleShapes.sphereShell(ctx.origin(), r, points,
          loc -> emitGradient(ctx, loc, particle, count, offset, extra, data, startColor, endColor, size, t));
    });
  }

  public static Action presetGradientSphereShell(Particle particle, double radius, int points, int count, double offset, double extra,
      Object data, Color from, Color to, float size) {
    Objects.requireNonNull(particle, "particle");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (points <= 0) {
      throw new IllegalArgumentException("points must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      var consumer = gradientConsumer(ctx, particle, count, offset, extra, data, from, to, size, points);
      ParticleShapes.sphereShell(ctx.origin(), radius, points, consumer);
    };
  }

  public static Action presetMorphSphereFilled(Particle particle, double startRadius, double endRadius,
      long durationTicks, long periodTicks, DoubleUnaryOperator easing,
      int points, int count, double offset, double extra, Object data, Color startColor, Color endColor, float size) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(easing, "easing");
    if (points <= 0) {
      throw new IllegalArgumentException("points must be > 0");
    }
    return animate(durationTicks, periodTicks, easing, (ctx, t) -> {
      if (ctx.world() == null) {
        return;
      }
      double r = startRadius + (endRadius - startRadius) * t;
      if (r < 0) {
        return;
      }
      ParticleShapes.sphereFilled(ctx.origin(), r, points,
          loc -> emitGradient(ctx, loc, particle, count, offset, extra, data, startColor, endColor, size, t));
    });
  }

  public static Action presetGradientSphereFilled(Particle particle, double radius, int points, int count, double offset, double extra,
      Object data, Color from, Color to, float size) {
    Objects.requireNonNull(particle, "particle");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (points <= 0) {
      throw new IllegalArgumentException("points must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      var consumer = gradientConsumer(ctx, particle, count, offset, extra, data, from, to, size, points);
      ParticleShapes.sphereFilled(ctx.origin(), radius, points, consumer);
    };
  }

  public static Action presetMorphHelix(Particle particle, double startRadius, double endRadius, double length, int turns, int points,
      long durationTicks, long periodTicks, DoubleUnaryOperator easing,
      int count, double offset, double extra, Object data, Color startColor, Color endColor, float size) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(easing, "easing");
    if (points <= 0 || turns <= 0) {
      throw new IllegalArgumentException("points and turns must be > 0");
    }
    return animate(durationTicks, periodTicks, easing, (ctx, t) -> {
      if (ctx.world() == null) {
        return;
      }
      double r = startRadius + (endRadius - startRadius) * t;
      if (r < 0) {
        return;
      }
      ParticleShapes.helix(ctx.origin(), ctx.direction(), r, length, turns, points,
          loc -> emitGradient(ctx, loc, particle, count, offset, extra, data, startColor, endColor, size, t));
    });
  }

  public static Action presetGradientHelix(Particle particle, double radius, double length, int turns, int points,
      int count, double offset, double extra, Object data, Color from, Color to, float size) {
    Objects.requireNonNull(particle, "particle");
    if (points <= 0 || turns <= 0) {
      throw new IllegalArgumentException("points and turns must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      var consumer = gradientConsumer(ctx, particle, count, offset, extra, data, from, to, size, points);
      ParticleShapes.helix(ctx.origin(), ctx.direction(), radius, length, turns, points, consumer);
    };
  }

  public static Action presetMorphCone(Particle particle, double startLength, double endLength, double angleDegrees, int rings, int pointsPerRing,
      long durationTicks, long periodTicks, DoubleUnaryOperator easing,
      int count, double offset, double extra, Object data, Color startColor, Color endColor, float size) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(easing, "easing");
    if (rings <= 0 || pointsPerRing <= 0) {
      throw new IllegalArgumentException("rings and pointsPerRing must be > 0");
    }
    return animate(durationTicks, periodTicks, easing, (ctx, t) -> {
      if (ctx.world() == null) {
        return;
      }
      double len = startLength + (endLength - startLength) * t;
      if (len < 0) {
        return;
      }
      ParticleShapes.coneShell(ctx.origin(), ctx.direction(), len, angleDegrees, rings, pointsPerRing,
          loc -> emitGradient(ctx, loc, particle, count, offset, extra, data, startColor, endColor, size, t));
    });
  }

  public static Action presetGradientCone(Particle particle, double length, double angleDegrees, int rings, int pointsPerRing,
      int count, double offset, double extra, Object data, Color from, Color to, float size) {
    Objects.requireNonNull(particle, "particle");
    if (rings <= 0 || pointsPerRing <= 0) {
      throw new IllegalArgumentException("rings and pointsPerRing must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      int totalPoints = 1 + (rings * pointsPerRing);
      var consumer = gradientConsumer(ctx, particle, count, offset, extra, data, from, to, size, totalPoints);
      ParticleShapes.coneShell(ctx.origin(), ctx.direction(), length, angleDegrees, rings, pointsPerRing, consumer);
    };
  }

  public static Action presetMorphCylinder(Particle particle, double startRadius, double endRadius, double height, int rings, int pointsPerRing,
      long durationTicks, long periodTicks, DoubleUnaryOperator easing,
      int count, double offset, double extra, Object data, Color startColor, Color endColor, float size) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(easing, "easing");
    if (rings <= 0 || pointsPerRing <= 0) {
      throw new IllegalArgumentException("rings and pointsPerRing must be > 0");
    }
    return animate(durationTicks, periodTicks, easing, (ctx, t) -> {
      if (ctx.world() == null) {
        return;
      }
      double r = startRadius + (endRadius - startRadius) * t;
      if (r < 0) {
        return;
      }
      ParticleShapes.cylinderShell(ctx.origin(), new Vector(0, 1, 0), r, height, rings, pointsPerRing,
          loc -> emitGradient(ctx, loc, particle, count, offset, extra, data, startColor, endColor, size, t));
    });
  }

  public static Action presetGradientCylinder(Particle particle, double radius, double height, int rings, int pointsPerRing,
      int count, double offset, double extra, Object data, Color from, Color to, float size) {
    Objects.requireNonNull(particle, "particle");
    if (rings <= 0 || pointsPerRing <= 0) {
      throw new IllegalArgumentException("rings and pointsPerRing must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      int totalPoints = (rings + 1) * pointsPerRing;
      var consumer = gradientConsumer(ctx, particle, count, offset, extra, data, from, to, size, totalPoints);
      ParticleShapes.cylinderShell(ctx.origin(), new Vector(0, 1, 0), radius, height, rings, pointsPerRing, consumer);
    };
  }

  public static Action presetMorphBox(Particle particle, double startX, double startY, double startZ, double endX, double endY, double endZ, double step,
      long durationTicks, long periodTicks, DoubleUnaryOperator easing,
      int count, double offset, double extra, Object data, Color startColor, Color endColor, float size) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(easing, "easing");
    if (step <= 0) {
      throw new IllegalArgumentException("step must be > 0");
    }
    return animate(durationTicks, periodTicks, easing, (ctx, t) -> {
      if (ctx.world() == null) {
        return;
      }
      double xr = startX + (endX - startX) * t;
      double yr = startY + (endY - startY) * t;
      double zr = startZ + (endZ - startZ) * t;
      if (xr < 0 || yr < 0 || zr < 0) {
        return;
      }
      ParticleShapes.boxOutline(ctx.origin(), xr, yr, zr, step,
          loc -> emitGradient(ctx, loc, particle, count, offset, extra, data, startColor, endColor, size, t));
    });
  }

  public static Action presetGradientBox(Particle particle, double xRadius, double yRadius, double zRadius, double step,
      int count, double offset, double extra, Object data, Color from, Color to, float size) {
    Objects.requireNonNull(particle, "particle");
    if (xRadius < 0 || yRadius < 0 || zRadius < 0) {
      throw new IllegalArgumentException("radii must be >= 0");
    }
    if (step <= 0) {
      throw new IllegalArgumentException("step must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      long px = (long) Math.ceil((2.0 * xRadius) / step) + 1L;
      long py = (long) Math.ceil((2.0 * yRadius) / step) + 1L;
      long pz = (long) Math.ceil((2.0 * zRadius) / step) + 1L;
      int totalPoints = (int) Math.max(1L, 4L * (px + py + pz));
      var consumer = gradientConsumer(ctx, particle, count, offset, extra, data, from, to, size, totalPoints);
      ParticleShapes.boxOutline(ctx.origin(), xRadius, yRadius, zRadius, step, consumer);
    };
  }

  public static Action presetMorphPolygon(Particle particle, double startRadius, double endRadius, int sides, int pointsPerEdge,
      long durationTicks, long periodTicks, DoubleUnaryOperator easing,
      int count, double offset, double extra, Object data, Color startColor, Color endColor, float size) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(easing, "easing");
    if (sides <= 0 || pointsPerEdge <= 0) {
      throw new IllegalArgumentException("sides and pointsPerEdge must be > 0");
    }
    return animate(durationTicks, periodTicks, easing, (ctx, t) -> {
      if (ctx.world() == null) {
        return;
      }
      double r = startRadius + (endRadius - startRadius) * t;
      if (r < 0) {
        return;
      }
      ParticleShapes.polygonOutline(ctx.origin(), new Vector(0, 1, 0), r, sides, pointsPerEdge,
          loc -> emitGradient(ctx, loc, particle, count, offset, extra, data, startColor, endColor, size, t));
    });
  }

  public static Action presetGradientPolygon(Particle particle, double radius, int sides, int pointsPerEdge,
      int count, double offset, double extra, Object data, Color from, Color to, float size) {
    Objects.requireNonNull(particle, "particle");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (sides <= 0 || pointsPerEdge <= 0) {
      throw new IllegalArgumentException("sides and pointsPerEdge must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      int totalPoints = sides * (pointsPerEdge + 1);
      var consumer = gradientConsumer(ctx, particle, count, offset, extra, data, from, to, size, totalPoints);
      ParticleShapes.polygonOutline(ctx.origin(), new Vector(0, 1, 0), radius, sides, pointsPerEdge, consumer);
    };
  }

  public static Action presetGradientBezier(java.util.function.Function<CastContext, Location> p0,
      java.util.function.Function<CastContext, Location> p1,
      java.util.function.Function<CastContext, Location> p2,
      java.util.function.Function<CastContext, Location> p3,
      double pointsPerMeter, int maxPoints, Particle particle, int count, double offset, double extra, Object data,
      Color from, Color to, float size) {
    Objects.requireNonNull(p0, "p0");
    Objects.requireNonNull(p1, "p1");
    Objects.requireNonNull(p2, "p2");
    Objects.requireNonNull(p3, "p3");
    Objects.requireNonNull(particle, "particle");
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      Location a = p0.apply(ctx);
      Location b = p1.apply(ctx);
      Location c = p2.apply(ctx);
      Location d = p3.apply(ctx);
      if (a == null || b == null || c == null || d == null || a.getWorld() == null) {
        return;
      }
      Vector v0 = a.toVector();
      Vector v1 = b.toVector();
      Vector v2 = c.toVector();
      Vector v3 = d.toVector();
      double approxLen = Curves.approximateLengthCubicBezier(v0, v1, v2, v3, 24);
      int points = (int) Math.ceil(approxLen * pointsPerMeter) + 1;
      points = Math.max(2, Math.min(maxPoints, points));
      var consumer = gradientConsumer(ctx, particle, count, offset, extra, data, from, to, size, points);
      Location tmp = a.clone();
      for (int i = 0; i < points; i++) {
        double t = points == 1 ? 0.0 : (i / (double) (points - 1));
        Vector pos = Curves.cubicBezier(v0, v1, v2, v3, t);
        tmp.set(pos.getX(), pos.getY(), pos.getZ());
        consumer.accept(tmp);
      }
    };
  }

  public static Action presetGradientSpline(java.util.List<java.util.function.Function<CastContext, Location>> points,
      double pointsPerMeter, int maxPoints, Particle particle, int count, double offset, double extra, Object data,
      Color from, Color to, float size) {
    Objects.requireNonNull(points, "points");
    Objects.requireNonNull(particle, "particle");
    return ctx -> {
      if (ctx.world() == null) {
        return;
      }
      if (points.size() < 2) {
        return;
      }
      int n = points.size();
      Vector[] p = new Vector[n];
      Location first = null;
      for (int i = 0; i < n; i++) {
        Location loc = points.get(i).apply(ctx);
        if (loc == null) {
          return;
        }
        if (first == null) {
          first = loc;
        }
        p[i] = loc.toVector();
      }
      if (first == null || first.getWorld() == null) {
        return;
      }
      int segments = n - 1;
      double[] segLen = new double[segments];
      double totalLen = 0.0;
      for (int i = 0; i < segments; i++) {
        Vector p0 = p[Math.max(0, i - 1)];
        Vector p1 = p[i];
        Vector p2 = p[i + 1];
        Vector p3 = p[Math.min(n - 1, i + 2)];
        double len = Curves.approximateLengthCatmullRom(p0, p1, p2, p3, 12);
        segLen[i] = len;
        totalLen += len;
      }
      if (totalLen <= 1e-9) {
        return;
      }
      int idealTotal = (int) Math.ceil(totalLen * pointsPerMeter) + 1;
      idealTotal = Math.max(2, idealTotal);
      int totalPoints = Math.min(maxPoints, idealTotal);
      int[] segPoints = new int[segments];
      int allocated = 0;
      for (int i = 0; i < segments; i++) {
        int pts = (int) Math.round((segLen[i] / totalLen) * totalPoints);
        pts = Math.max(2, pts);
        segPoints[i] = pts;
        allocated += pts;
      }
      while (allocated > totalPoints) {
        for (int i = 0; i < segments && allocated > totalPoints; i++) {
          if (segPoints[i] > 2) {
            segPoints[i]--;
            allocated--;
          }
        }
      }
      while (allocated < totalPoints) {
        for (int i = 0; i < segments && allocated < totalPoints; i++) {
          segPoints[i]++;
          allocated++;
        }
      }
      var consumer = gradientConsumer(ctx, particle, count, offset, extra, data, from, to, size, totalPoints);
      Location tmp = first.clone();
      for (int i = 0; i < segments; i++) {
        Vector p0 = p[Math.max(0, i - 1)];
        Vector p1 = p[i];
        Vector p2 = p[i + 1];
        Vector p3 = p[Math.min(n - 1, i + 2)];
        int pts = segPoints[i];
        for (int j = 0; j < pts; j++) {
          double t = pts == 1 ? 0.0 : (j / (double) (pts - 1));
          Vector pos = Curves.catmullRom(p0, p1, p2, p3, t);
          tmp.set(pos.getX(), pos.getY(), pos.getZ());
          consumer.accept(tmp);
        }
      }
    };
  }

  public static Action particlesPoints(Particle particle, java.util.List<java.util.function.Function<CastContext, Location>> points,
      int count, double offset, double extra, Object data) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(points, "points");
    return ctx -> {
      if (ctx.world() == null || points.isEmpty()) {
        return;
      }
      var pe = ctx.engine().particles();
      for (var fn : points) {
        Location loc = fn.apply(ctx);
        if (loc == null) {
          continue;
        }
        if (loc.getWorld() == null) {
          loc = loc.clone();
          loc.setWorld(ctx.world());
        }
        Object resolved = resolveParticleData(data, ctx, loc);
        pe.emit(loc.getWorld(), loc, particle, count, offset, offset, offset, extra, resolved);
      }
    };
  }

  public static Action particlesPointsGradient(Particle particle, java.util.List<java.util.function.Function<CastContext, Location>> points,
      int count, double offset, double extra, Object data, Color from, Color to, float size) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(points, "points");
    return ctx -> {
      if (ctx.world() == null || points.isEmpty()) {
        return;
      }
      var locations = new java.util.ArrayList<Location>(points.size());
      for (var fn : points) {
        Location loc = fn.apply(ctx);
        if (loc == null) {
          continue;
        }
        if (loc.getWorld() == null) {
          loc = loc.clone();
          loc.setWorld(ctx.world());
        }
        locations.add(loc);
      }
      if (locations.isEmpty()) {
        return;
      }
      var consumer = gradientConsumer(ctx, particle, count, offset, extra, data, from, to, size, locations.size());
      for (Location loc : locations) {
        consumer.accept(loc);
      }
    };
  }

  public static Action particlesPhysicsPoints(Particle particle, java.util.List<java.util.function.Function<CastContext, Location>> points,
      int count, Vector velocity, double spread, double gravity, double drag, int steps, long periodTicks,
      double offset, double extra, Object data, boolean collide,
      dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode collisionMode, double restitution) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(points, "points");
    Objects.requireNonNull(velocity, "velocity");
    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext ctx) {
        if (ctx.world() == null || points.isEmpty()) {
          return ActionHandle.completed();
        }
        var origins = new java.util.ArrayList<Location>(points.size());
        for (var fn : points) {
          Location loc = fn.apply(ctx);
          if (loc == null) {
            continue;
          }
          if (loc.getWorld() == null) {
            loc = loc.clone();
            loc.setWorld(ctx.world());
          }
          origins.add(loc);
        }
        if (origins.isEmpty()) {
          return ActionHandle.completed();
        }
        var handle = ParticlePhysics.simulate(
            ctx,
            particle,
            origins,
            count,
            velocity,
            spread,
            gravity,
            drag,
            steps,
            periodTicks,
            offset,
            extra,
            data,
            collide,
            collisionMode,
            restitution);
        ctx.state().track(handle);
        return scheduledHandle(handle, new AtomicBoolean(false));
      }
    };
  }

  public static Action particlesPhysicsPoints(Particle particle, java.util.List<java.util.function.Function<CastContext, Location>> points,
      int count, Vector velocity, double spread, double gravity, double drag, int steps, long periodTicks,
      double offset, double extra, Object data, boolean collide) {
    return particlesPhysicsPoints(
        particle,
        points,
        count,
        velocity,
        spread,
        gravity,
        drag,
        steps,
        periodTicks,
        offset,
        extra,
        data,
        collide,
        dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode.STOP,
        0.0);
  }

  public static Action particlesPolyline(Particle particle, java.util.List<java.util.function.Function<CastContext, Location>> points,
      double step, int count, double offset, double extra, Object data) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(points, "points");
    if (step <= 0) {
      throw new IllegalArgumentException("step must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null || points.size() < 2) {
        return;
      }
      var list = new java.util.ArrayList<Location>(points.size());
      for (var fn : points) {
        Location loc = fn.apply(ctx);
        if (loc == null) {
          continue;
        }
        if (loc.getWorld() == null) {
          loc = loc.clone();
          loc.setWorld(ctx.world());
        }
        list.add(loc);
      }
      if (list.size() < 2) {
        return;
      }
      var pe = ctx.engine().particles();
      ParticleShapes.polyline(list, step, loc -> {
        Object resolved = resolveParticleData(data, ctx, loc);
        pe.emit(loc.getWorld(), loc, particle, count, offset, offset, offset, extra, resolved);
      });
    };
  }

  public static Action particlesPhysicsPolyline(Particle particle, java.util.List<java.util.function.Function<CastContext, Location>> points,
      double step, int count, Vector velocity, double spread, double gravity, double drag, int steps, long periodTicks,
      double offset, double extra, Object data, boolean collide,
      dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode collisionMode, double restitution) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(points, "points");
    Objects.requireNonNull(velocity, "velocity");
    if (step <= 0) {
      throw new IllegalArgumentException("step must be > 0");
    }
    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext ctx) {
        if (ctx.world() == null || points.size() < 2) {
          return ActionHandle.completed();
        }
        var list = new java.util.ArrayList<Location>(points.size());
        for (var fn : points) {
          Location loc = fn.apply(ctx);
          if (loc == null) {
            continue;
          }
          if (loc.getWorld() == null) {
            loc = loc.clone();
            loc.setWorld(ctx.world());
          }
          list.add(loc);
        }
        if (list.size() < 2) {
          return ActionHandle.completed();
        }
        var sampled = samplePolyline(list, step);
        if (sampled.isEmpty()) {
          return ActionHandle.completed();
        }
        var handle = ParticlePhysics.simulate(
            ctx,
            particle,
            sampled,
            count,
            velocity,
            spread,
            gravity,
            drag,
            steps,
            periodTicks,
            offset,
            extra,
            data,
            collide,
            collisionMode,
            restitution);
        ctx.state().track(handle);
        return scheduledHandle(handle, new AtomicBoolean(false));
      }
    };
  }

  public static Action particlesPhysicsPolyline(Particle particle, java.util.List<java.util.function.Function<CastContext, Location>> points,
      double step, int count, Vector velocity, double spread, double gravity, double drag, int steps, long periodTicks,
      double offset, double extra, Object data, boolean collide) {
    return particlesPhysicsPolyline(
        particle,
        points,
        step,
        count,
        velocity,
        spread,
        gravity,
        drag,
        steps,
        periodTicks,
        offset,
        extra,
        data,
        collide,
        dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode.STOP,
        0.0);
  }

  public static Action particlesPolylineGradient(Particle particle, java.util.List<java.util.function.Function<CastContext, Location>> points,
      double step, int count, double offset, double extra, Object data, Color from, Color to, float size) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(points, "points");
    if (step <= 0) {
      throw new IllegalArgumentException("step must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null || points.size() < 2) {
        return;
      }
      var list = new java.util.ArrayList<Location>(points.size());
      for (var fn : points) {
        Location loc = fn.apply(ctx);
        if (loc == null) {
          continue;
        }
        if (loc.getWorld() == null) {
          loc = loc.clone();
          loc.setWorld(ctx.world());
        }
        list.add(loc);
      }
      if (list.size() < 2) {
        return;
      }
      var sampled = samplePolyline(list, step);
      if (sampled.isEmpty()) {
        return;
      }
      var consumer = gradientConsumer(ctx, particle, count, offset, extra, data, from, to, size, sampled.size());
      for (Location loc : sampled) {
        consumer.accept(loc);
      }
    };
  }

  public static Action particlesMesh(Particle particle, java.util.List<java.util.function.Function<CastContext, Location[]>> triangles,
      double step, int count, double offset, double extra, Object data) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(triangles, "triangles");
    if (step <= 0) {
      throw new IllegalArgumentException("step must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null || triangles.isEmpty()) {
        return;
      }
      var tris = new java.util.ArrayList<Location[]>(triangles.size());
      for (var fn : triangles) {
        Location[] tri = fn.apply(ctx);
        if (tri == null || tri.length < 3 || tri[0] == null || tri[1] == null || tri[2] == null) {
          continue;
        }
        for (int i = 0; i < 3; i++) {
          if (tri[i].getWorld() == null) {
            tri[i] = tri[i].clone();
            tri[i].setWorld(ctx.world());
          }
        }
        tris.add(tri);
      }
      if (tris.isEmpty()) {
        return;
      }
      var pe = ctx.engine().particles();
      ParticleShapes.mesh(tris, step, loc -> {
        Object resolved = resolveParticleData(data, ctx, loc);
        pe.emit(loc.getWorld(), loc, particle, count, offset, offset, offset, extra, resolved);
      });
    };
  }

  public static Action particlesPhysicsMesh(Particle particle, java.util.List<java.util.function.Function<CastContext, Location[]>> triangles,
      double step, int count, Vector velocity, double spread, double gravity, double drag, int steps, long periodTicks,
      double offset, double extra, Object data, boolean collide,
      dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode collisionMode, double restitution) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(triangles, "triangles");
    Objects.requireNonNull(velocity, "velocity");
    if (step <= 0) {
      throw new IllegalArgumentException("step must be > 0");
    }
    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext ctx) {
        if (ctx.world() == null || triangles.isEmpty()) {
          return ActionHandle.completed();
        }
        var tris = new java.util.ArrayList<Location[]>(triangles.size());
        for (var fn : triangles) {
          Location[] tri = fn.apply(ctx);
          if (tri == null || tri.length < 3 || tri[0] == null || tri[1] == null || tri[2] == null) {
            continue;
          }
          for (int i = 0; i < 3; i++) {
            if (tri[i].getWorld() == null) {
              tri[i] = tri[i].clone();
              tri[i].setWorld(ctx.world());
            }
          }
          tris.add(tri);
        }
        if (tris.isEmpty()) {
          return ActionHandle.completed();
        }
        var sampled = sampleMesh(tris, step);
        if (sampled.isEmpty()) {
          return ActionHandle.completed();
        }
        var handle = ParticlePhysics.simulate(
            ctx,
            particle,
            sampled,
            count,
            velocity,
            spread,
            gravity,
            drag,
            steps,
            periodTicks,
            offset,
            extra,
            data,
            collide,
            collisionMode,
            restitution);
        ctx.state().track(handle);
        return scheduledHandle(handle, new AtomicBoolean(false));
      }
    };
  }

  public static Action particlesPhysicsMesh(Particle particle, java.util.List<java.util.function.Function<CastContext, Location[]>> triangles,
      double step, int count, Vector velocity, double spread, double gravity, double drag, int steps, long periodTicks,
      double offset, double extra, Object data, boolean collide) {
    return particlesPhysicsMesh(
        particle,
        triangles,
        step,
        count,
        velocity,
        spread,
        gravity,
        drag,
        steps,
        periodTicks,
        offset,
        extra,
        data,
        collide,
        dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode.STOP,
        0.0);
  }

  public static Action particlesMeshGradient(Particle particle, java.util.List<java.util.function.Function<CastContext, Location[]>> triangles,
      double step, int count, double offset, double extra, Object data, Color from, Color to, float size) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(triangles, "triangles");
    if (step <= 0) {
      throw new IllegalArgumentException("step must be > 0");
    }
    return ctx -> {
      if (ctx.world() == null || triangles.isEmpty()) {
        return;
      }
      var tris = new java.util.ArrayList<Location[]>(triangles.size());
      for (var fn : triangles) {
        Location[] tri = fn.apply(ctx);
        if (tri == null || tri.length < 3 || tri[0] == null || tri[1] == null || tri[2] == null) {
          continue;
        }
        for (int i = 0; i < 3; i++) {
          if (tri[i].getWorld() == null) {
            tri[i] = tri[i].clone();
            tri[i].setWorld(ctx.world());
          }
        }
        tris.add(tri);
      }
      if (tris.isEmpty()) {
        return;
      }
      var sampled = sampleMesh(tris, step);
      if (sampled.isEmpty()) {
        return;
      }
      var consumer = gradientConsumer(ctx, particle, count, offset, extra, data, from, to, size, sampled.size());
      for (Location loc : sampled) {
        consumer.accept(loc);
      }
    };
  }

  public static Action presetOrbit(Particle particle, double radius, long durationTicks, long periodTicks, DoubleUnaryOperator easing,
      int copies, int count, double offset, double extra) {
    return presetOrbit(particle, radius, durationTicks, periodTicks, easing, copies, count, offset, extra, null);
  }

  public static Action presetOrbit(Particle particle, double radius, long durationTicks, long periodTicks, DoubleUnaryOperator easing,
      int copies, int count, double offset, double extra, Object data) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(easing, "easing");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (copies <= 0) {
      throw new IllegalArgumentException("copies must be > 0");
    }
    return animate(durationTicks, periodTicks, easing, (ctx, t) -> {
      if (ctx.world() == null) {
        return;
      }
      double ang = (Math.PI * 2.0) * t;
      var pe = ctx.engine().particles();
      var center = ctx.origin().clone().add(0, 0.75, 0);
      for (int i = 0; i < copies; i++) {
        double a = ang + (Math.PI * 2.0) * (i / (double) copies);
        Vector base = new Vector(radius, 0.0, 0.0);
        Vector rotated = ParticleTransforms.rotateAroundY(base, a);
        Location loc = center.clone().add(rotated);
        pe.emit(ctx.world(), loc, particle, count, offset, offset, offset, extra, resolveParticleData(data, ctx, loc));
      }
    });
  }

  public static Action presetSwirl(Particle particle, double radius, double height, long durationTicks, long periodTicks, DoubleUnaryOperator easing,
      int points, int count, double offset, double extra) {
    return presetSwirl(particle, radius, height, durationTicks, periodTicks, easing, points, count, offset, extra, null);
  }

  public static Action presetSwirl(Particle particle, double radius, double height, long durationTicks, long periodTicks, DoubleUnaryOperator easing,
      int points, int count, double offset, double extra, Object data) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(easing, "easing");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (!Double.isFinite(height) || height < 0) {
      throw new IllegalArgumentException("height must be finite and >= 0");
    }
    if (points <= 0) {
      throw new IllegalArgumentException("points must be > 0");
    }
    return animate(durationTicks, periodTicks, easing, (ctx, t) -> {
      if (ctx.world() == null) {
        return;
      }
      var pe = ctx.engine().particles();
      var center = ctx.origin().clone().add(0, 0.25, 0);
      double ang = (Math.PI * 2.0) * (t * 2.0);
      for (int i = 0; i < points; i++) {
        double u = i / (double) points;
        double a = ang + (Math.PI * 2.0) * u;
        double y = u * height;
        Vector base = new Vector(radius, 0.0, 0.0);
        Vector rotated = ParticleTransforms.rotateAroundY(base, a);
        Location loc = center.clone().add(rotated).add(0, y, 0);
        pe.emit(ctx.world(), loc, particle, count, offset, offset, offset, extra, resolveParticleData(data, ctx, loc));
      }
    });
  }

  public static Action presetBeamChargeup(Particle particle, double startLength, double endLength,
      long durationTicks, long periodTicks, DoubleUnaryOperator easing,
      double step, int count, double offset, double extra) {
    return presetBeamChargeup(particle, startLength, endLength, durationTicks, periodTicks, easing, step, count, offset, extra, null);
  }

  public static Action presetBeamChargeup(Particle particle, double startLength, double endLength,
      long durationTicks, long periodTicks, DoubleUnaryOperator easing,
      double step, int count, double offset, double extra, Object data) {
    Objects.requireNonNull(particle, "particle");
    Objects.requireNonNull(easing, "easing");
    if (!Double.isFinite(startLength) || startLength < 0) {
      throw new IllegalArgumentException("startLength must be finite and >= 0");
    }
    if (!Double.isFinite(endLength) || endLength < 0) {
      throw new IllegalArgumentException("endLength must be finite and >= 0");
    }
    if (step <= 0) {
      throw new IllegalArgumentException("step must be > 0");
    }
    return animate(durationTicks, periodTicks, easing, (ctx, t) -> {
      if (ctx.world() == null) {
        return;
      }
      double length = startLength + (endLength - startLength) * t;
      var pe = ctx.engine().particles();
      var dir = ctx.direction().clone();
      if (dir.lengthSquared() < 1e-9) {
        dir.setX(0).setY(0).setZ(1);
      }
      dir.normalize();

      var pos = ctx.origin().clone();
      for (double d = 0.0; d <= length + 1e-9; d += step) {
        Object resolved = resolveParticleData(data, ctx, pos);
        pe.emit(ctx.world(), pos, particle, count, offset, offset, offset, extra, resolved);
        pos.add(dir.getX() * step, dir.getY() * step, dir.getZ() * step);
      }
    });
  }

  /**
   * Performs an entity raycast from {@link CastContext#origin()} along {@link CastContext#direction()}.
   * <p>
   * If {@code stopOnBlock} is true, entities behind the first solid block won't be hit.
   */
  public static Action raycastHitEntity(double maxDistance, double raySize, boolean stopOnBlock, boolean ignoreCaster,
      Predicate<LivingEntity> filter, BiConsumer<CastContext, LivingEntity> onHit) {
    Objects.requireNonNull(filter, "filter");
    Objects.requireNonNull(onHit, "onHit");
    if (maxDistance <= 0) {
      throw new IllegalArgumentException("maxDistance must be > 0");
    }
    if (raySize < 0) {
      throw new IllegalArgumentException("raySize must be >= 0");
    }

    return ctx -> {
      World world = ctx.world();
      if (world == null) {
        return;
      }

      Predicate<Entity> entityFilter = entity -> {
        if (!(entity instanceof LivingEntity living)) {
          return false;
        }
        if (ignoreCaster && entity.getUniqueId().equals(ctx.caster().getUniqueId())) {
          return false;
        }
        return filter.test(living);
      };

      RayTraceResult hit;
      if (stopOnBlock) {
        hit = Raycasts.rayTraceEntitiesStopOnBlock(world, ctx.origin(), ctx.direction(), maxDistance, raySize, entityFilter);
      } else {
        hit = world.rayTraceEntities(ctx.origin(), ctx.direction(), maxDistance, raySize, entityFilter);
      }

      if (hit == null) {
        return;
      }
      if (!(hit.getHitEntity() instanceof LivingEntity living)) {
        return;
      }
      onHit.accept(ctx, living);
    };
  }

  public static Action raycastHitEntity(double maxDistance, double raySize, BiConsumer<CastContext, LivingEntity> onHit) {
    return raycastHitEntity(maxDistance, raySize, true, true, e -> true, onHit);
  }

  public static Action projectile(ProjectileSpec spec) {
    return new ProjectileAction(spec);
  }

  public static Action projectile(java.util.function.Consumer<ProjectileSpec.Builder> specBuilder) {
    Objects.requireNonNull(specBuilder, "specBuilder");
    ProjectileSpec.Builder builder = ProjectileSpec.builder();
    specBuilder.accept(builder);
    return projectile(builder.build());
  }

  public static <T> Action forEach(Targeter<T> targeter, TargetAction<T> action) {
    Objects.requireNonNull(targeter, "targeter");
    Objects.requireNonNull(action, "action");
    return ctx -> {
      for (T target : targeter.select(ctx)) {
        action.execute(ctx, target);
      }
    };
  }

  public static <T> Action forEachWhere(Targeter<T> targeter, TargetCondition<T> condition, TargetAction<T> action) {
    Objects.requireNonNull(targeter, "targeter");
    Objects.requireNonNull(condition, "condition");
    Objects.requireNonNull(action, "action");
    return ctx -> {
      for (T target : targeter.select(ctx)) {
        if (condition.test(ctx, target)) {
          action.execute(ctx, target);
        }
      }
    };
  }

  /**
   * Runs {@code action} only if the caster is not on cooldown for {@code key}.
   * <p>
   * Cooldowns are stored per-player. Non-player casters bypass the cooldown gate.
   */
  public static Action withCooldown(String key, long cooldownTicks, Action action) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(action, "action");
    if (cooldownTicks <= 0) {
      throw new IllegalArgumentException("cooldownTicks must be > 0");
    }
    return ctx -> {
      if (!(ctx.caster() instanceof Player player)) {
        action.execute(ctx);
        return;
      }
      if (!ctx.engine().tryStartCooldown(player.getUniqueId(), key, cooldownTicks)) {
        long remaining = ctx.engine().cooldownRemainingTicks(player.getUniqueId(), key);
        player.sendMessage("§cOn cooldown (" + remaining + "t)");
        return;
      }
      action.execute(ctx);
    };
  }

  public static Action when(Condition condition, Action then) {
    Objects.requireNonNull(condition, "condition");
    Objects.requireNonNull(then, "then");
    return ctx -> {
      if (condition.test(ctx)) {
        then.execute(ctx);
      }
    };
  }

  public static Action whenElse(Condition condition, Action then, Action otherwise) {
    Objects.requireNonNull(condition, "condition");
    Objects.requireNonNull(then, "then");
    Objects.requireNonNull(otherwise, "otherwise");
    return ctx -> {
      if (condition.test(ctx)) {
        then.execute(ctx);
      } else {
        otherwise.execute(ctx);
      }
    };
  }

  public static Action withCost(Cost cost, Action action) {
    Objects.requireNonNull(cost, "cost");
    Objects.requireNonNull(action, "action");
    return ctx -> {
      var fail = cost.tryApply(ctx);
      if (fail != null) {
        if (ctx.caster() instanceof Player player) {
          player.sendMessage(fail);
        }
        return;
      }
      action.execute(ctx);
    };
  }
}
