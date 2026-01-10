package dev.patric.dungeonsreborn.effects.projectile;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;

import dev.patric.dungeonsreborn.effects.particles.Frame;
import dev.patric.dungeonsreborn.effects.particles.Frames;

public record ProjectileSpec(
    double speedPerTick,
    double maxDistance,
    double hitRadius,
    BlockCollision blockCollision,
    int maxBounces,
    double bounceRestitution,
    boolean ignoreCaster,
    Particle trailParticle,
    int trailCount,
    double trailOffset,
    double trailExtra,
    Predicate<LivingEntity> entityFilter,
    Consumer<ProjectileInstance> onStart,
    Consumer<ProjectileHit> onHit) {

  public ProjectileSpec {
    if (speedPerTick <= 0) {
      throw new IllegalArgumentException("speedPerTick must be > 0");
    }
    if (maxDistance <= 0) {
      throw new IllegalArgumentException("maxDistance must be > 0");
    }
    if (hitRadius < 0) {
      throw new IllegalArgumentException("hitRadius must be >= 0");
    }
    Objects.requireNonNull(blockCollision, "blockCollision");
    if (maxBounces < 0) {
      throw new IllegalArgumentException("maxBounces must be >= 0");
    }
    if (!Double.isFinite(bounceRestitution) || bounceRestitution < 0.0) {
      throw new IllegalArgumentException("bounceRestitution must be finite and >= 0");
    }
    Objects.requireNonNull(trailParticle, "trailParticle");
    if (trailCount < 0) {
      throw new IllegalArgumentException("trailCount must be >= 0");
    }
    if (trailOffset < 0) {
      throw new IllegalArgumentException("trailOffset must be >= 0");
    }
    Objects.requireNonNull(entityFilter, "entityFilter");
    Objects.requireNonNull(onStart, "onStart");
    Objects.requireNonNull(onHit, "onHit");
  }

  public static Builder builder() {
    return new Builder();
  }

  public enum BlockCollision {
    STOP,
    PASS_THROUGH,
    BOUNCE
  }

  public static final class Builder {
    private double speedPerTick = 1.3;
    private double maxDistance = 24.0;
    private double hitRadius = 0.25;
    private BlockCollision blockCollision = BlockCollision.STOP;
    private int maxBounces = 0;
    private double bounceRestitution = 0.9;
    private boolean ignoreCaster = true;
    private Particle trailParticle = Particle.END_ROD;
    private int trailCount = 1;
    private double trailOffset = 0.0;
    private double trailExtra = 0.0;
    private Predicate<LivingEntity> entityFilter = e -> true;
    private Consumer<ProjectileInstance> onStart = p -> {
    };
    private Consumer<ProjectileHit> onHit = hit -> {
    };

    private Builder() {
    }

    public Builder speedPerTick(double speedPerTick) {
      this.speedPerTick = speedPerTick;
      return this;
    }

    public Builder maxDistance(double maxDistance) {
      this.maxDistance = maxDistance;
      return this;
    }

    public Builder hitRadius(double hitRadius) {
      this.hitRadius = hitRadius;
      return this;
    }

    public Builder stopOnBlock(boolean stopOnBlock) {
      this.blockCollision = stopOnBlock ? BlockCollision.STOP : BlockCollision.PASS_THROUGH;
      return this;
    }

    public Builder blockCollision(BlockCollision mode) {
      this.blockCollision = Objects.requireNonNull(mode, "mode");
      return this;
    }

    public Builder bounces(int maxBounces, double restitution) {
      if (maxBounces < 0) {
        throw new IllegalArgumentException("maxBounces must be >= 0");
      }
      if (!Double.isFinite(restitution) || restitution < 0.0) {
        throw new IllegalArgumentException("restitution must be finite and >= 0");
      }
      this.blockCollision = BlockCollision.BOUNCE;
      this.maxBounces = maxBounces;
      this.bounceRestitution = restitution;
      return this;
    }

    public Builder ignoreCaster(boolean ignoreCaster) {
      this.ignoreCaster = ignoreCaster;
      return this;
    }

    public Builder trail(Particle particle, int count, double offset, double extra) {
      this.trailParticle = Objects.requireNonNull(particle, "particle");
      this.trailCount = count;
      this.trailOffset = offset;
      this.trailExtra = extra;
      return this;
    }

    public Builder filter(Predicate<LivingEntity> filter) {
      this.entityFilter = Objects.requireNonNull(filter, "filter");
      return this;
    }

    public Builder onHit(Consumer<ProjectileHit> onHit) {
      this.onHit = Objects.requireNonNull(onHit, "onHit");
      return this;
    }

    /**
     * Provides a live {@link Frame} attached to this projectile instance for VFX attachment.
     */
    public Builder frameOut(Consumer<Frame> consumer) {
      Objects.requireNonNull(consumer, "consumer");
      this.onStart = instance -> consumer.accept(Frames.dynamic(instance::location, instance::direction));
      return this;
    }

    public ProjectileSpec build() {
      return new ProjectileSpec(
          speedPerTick,
          maxDistance,
          hitRadius,
          blockCollision,
          maxBounces,
          bounceRestitution,
          ignoreCaster,
          trailParticle,
          trailCount,
          trailOffset,
          trailExtra,
          entityFilter,
          onStart,
          onHit);
    }
  }
}
