package dev.patric.dungeonsreborn.effects.actions;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WitherSkull;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import dev.patric.dungeonsreborn.effects.CastContext;
import dev.patric.dungeonsreborn.effects.Vars;
import dev.patric.dungeonsreborn.effects.compat.WorldCompat;
import dev.patric.dungeonsreborn.effects.damage.DamageType;
import dev.patric.dungeonsreborn.effects.projectile.ProjectileHit;
import dev.patric.dungeonsreborn.effects.relations.Relation;
import dev.patric.dungeonsreborn.effects.targeting.TargetAction;

public final class EntityActions {
  private EntityActions() {
  }

  public record DamagePolicy(boolean allowPlayers, boolean allowMobs, boolean allowAllies, boolean allowSelf) {
    public static DamagePolicy any() {
      return new DamagePolicy(true, true, true, true);
    }

    public static DamagePolicy hostileDefault() {
      return new DamagePolicy(true, true, false, false);
    }

    public static DamagePolicy pveOnly() {
      return new DamagePolicy(false, true, false, false);
    }

    public static DamagePolicy pvpOnly() {
      return new DamagePolicy(true, false, false, false);
    }
  }

  public static TargetAction<LivingEntity> damage(double amount) {
    return damage(amount, DamagePolicy.any());
  }

  public static TargetAction<LivingEntity> damage(double amount, DamagePolicy policy) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      if (!canAffect(ctx, target, policy)) {
        return;
      }
      ctx.engine().recordDamageAttribution(target.getUniqueId(), ctx.castId(), ctx.abilityId(), ctx.caster().getUniqueId());
      target.damage(amount, ctx.caster());
    };
  }

  public static TargetAction<LivingEntity> damageTyped(double amount, DamageType type, boolean ignoreResistance, DamagePolicy policy) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      if (!canAffect(ctx, target, policy)) {
        return;
      }
      double multiplier = ignoreResistance ? 1.0 : ctx.engine().resistanceMultiplier(target.getUniqueId(), type);
      double dmg = amount * multiplier;
      if (!(dmg > 0.0)) {
        return;
      }
      ctx.engine().recordDamageAttribution(target.getUniqueId(), ctx.castId(), ctx.abilityId(), ctx.caster().getUniqueId());
      target.damage(dmg, ctx.caster());
    };
  }

  public static TargetAction<LivingEntity> damagePercent(double percent, DamagePolicy policy) {
    if (!Double.isFinite(percent) || percent <= 0) {
      throw new IllegalArgumentException("percent must be > 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      if (!canAffect(ctx, target, policy)) {
        return;
      }
      double pct = percent > 1.0 ? percent / 100.0 : percent;
      if (pct <= 0.0) {
        return;
      }
      double max = resolveMaxHealth(target);
      double amount = max * pct;
      if (amount <= 0.0) {
        return;
      }
      ctx.engine().recordDamageAttribution(target.getUniqueId(), ctx.castId(), ctx.abilityId(), ctx.caster().getUniqueId());
      target.damage(amount, ctx.caster());
    };
  }

  public static TargetAction<LivingEntity> damageTrue(double amount, DamagePolicy policy) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      if (!canAffect(ctx, target, policy)) {
        return;
      }
      double next = Math.max(0.0, target.getHealth() - amount);
      ctx.engine().recordDamageAttribution(target.getUniqueId(), ctx.castId(), ctx.abilityId(), ctx.caster().getUniqueId());
      target.setHealth(next);
    };
  }

  public static TargetAction<LivingEntity> damageWithFalloff(double amount, double maxDistance, double minMultiplier, DamagePolicy policy) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    if (maxDistance <= 0) {
      throw new IllegalArgumentException("maxDistance must be > 0");
    }
    if (!Double.isFinite(minMultiplier) || minMultiplier < 0) {
      throw new IllegalArgumentException("minMultiplier must be >= 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      if (!canAffect(ctx, target, policy)) {
        return;
      }
      double dist = target.getLocation().distance(ctx.origin());
      double t = Math.min(1.0, Math.max(0.0, dist / maxDistance));
      double mult = Math.max(minMultiplier, 1.0 - t);
      double dmg = amount * mult;
      if (dmg <= 0.0) {
        return;
      }
      ctx.engine().recordDamageAttribution(target.getUniqueId(), ctx.castId(), ctx.abilityId(), ctx.caster().getUniqueId());
      target.damage(dmg, ctx.caster());
    };
  }

  public static TargetAction<LivingEntity> damageCrit(double amount, double critChance, double critMultiplier,
      double headshotMultiplier, double headshotThreshold, DamagePolicy policy) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    if (!Double.isFinite(critChance) || critChance < 0) {
      throw new IllegalArgumentException("critChance must be >= 0");
    }
    if (!Double.isFinite(critMultiplier) || critMultiplier <= 0) {
      throw new IllegalArgumentException("critMultiplier must be > 0");
    }
    if (!Double.isFinite(headshotMultiplier) || headshotMultiplier <= 0) {
      throw new IllegalArgumentException("headshotMultiplier must be > 0");
    }
    if (!Double.isFinite(headshotThreshold) || headshotThreshold < 0) {
      throw new IllegalArgumentException("headshotThreshold must be >= 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      if (!canAffect(ctx, target, policy)) {
        return;
      }
      double chance = critChance > 1.0 ? critChance / 100.0 : critChance;
      double multiplier = 1.0;
      if (chance > 0.0 && ctx.rng().nextDouble() < chance) {
        multiplier *= critMultiplier;
      }
      if (headshotMultiplier > 1.0) {
        Object hitObj = ctx.state().get(Vars.PROJECTILE_LAST_HIT);
        if (hitObj instanceof ProjectileHit hit && target.equals(hit.hitEntity())) {
          double hitY = hit.location().getY();
          double eyeY = target.getEyeLocation().getY();
          if (hitY >= (eyeY - headshotThreshold)) {
            multiplier *= headshotMultiplier;
          }
        }
      }
      double dmg = amount * multiplier;
      if (dmg <= 0.0) {
        return;
      }
      ctx.engine().recordDamageAttribution(target.getUniqueId(), ctx.castId(), ctx.abilityId(), ctx.caster().getUniqueId());
      target.damage(dmg, ctx.caster());
    };
  }

  public static TargetAction<LivingEntity> damageLifesteal(double amount, double ratio, DamagePolicy policy) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    if (!Double.isFinite(ratio) || ratio < 0) {
      throw new IllegalArgumentException("ratio must be >= 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      if (!canAffect(ctx, target, policy)) {
        return;
      }
      ctx.engine().recordDamageAttribution(target.getUniqueId(), ctx.castId(), ctx.abilityId(), ctx.caster().getUniqueId());
      target.damage(amount, ctx.caster());
      double heal = amount * ratio;
      if (heal > 0.0) {
        LivingEntity caster = ctx.caster();
        double max = resolveMaxHealth(caster);
        double next = Math.min(max, caster.getHealth() + heal);
        caster.setHealth(next);
      }
    };
  }

  public static TargetAction<LivingEntity> damageOverTime(double amount, long periodTicks, int times, DamagePolicy policy) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    if (periodTicks <= 0) {
      throw new IllegalArgumentException("periodTicks must be > 0");
    }
    if (times <= 0) {
      throw new IllegalArgumentException("times must be > 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      if (!canAffect(ctx, target, policy)) {
        return;
      }
      final LivingEntity captured = target;
      final int[] remaining = new int[] { times };
      final dev.patric.dungeonsreborn.effects.EffectsEngine.ScheduledHandle[] handle = new dev.patric.dungeonsreborn.effects.EffectsEngine.ScheduledHandle[1];
      handle[0] = ctx.engine().runRepeating(0L, periodTicks, () -> {
        if (handle[0] == null || handle[0].isCancelled()) {
          return;
        }
        if (remaining[0]-- <= 0) {
          handle[0].cancel();
          return;
        }
        if (!captured.isValid() || captured.isDead()) {
          handle[0].cancel();
          return;
        }
        if (!canAffect(ctx, captured, policy)) {
          handle[0].cancel();
          return;
        }
        ctx.engine().recordDamageAttribution(captured.getUniqueId(), ctx.castId(), ctx.abilityId(), ctx.caster().getUniqueId());
        captured.damage(amount, ctx.caster());
      });
      ctx.state().track(handle[0]);
    };
  }

  public static TargetAction<LivingEntity> chainDamage(double amount, double radius, int maxJumps, long delayTicks,
      double falloff, DamagePolicy policy, TargetAction<LivingEntity> onHit) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    if (radius <= 0) {
      throw new IllegalArgumentException("radius must be > 0");
    }
    if (maxJumps <= 0) {
      throw new IllegalArgumentException("maxJumps must be > 0");
    }
    if (delayTicks < 0) {
      throw new IllegalArgumentException("delayTicks must be >= 0");
    }
    if (!Double.isFinite(falloff) || falloff < 0) {
      throw new IllegalArgumentException("falloff must be >= 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      if (!canAffect(ctx, target, policy)) {
        return;
      }
      if (ctx.world() == null || target.getWorld() == null || !ctx.world().equals(target.getWorld())) {
        return;
      }
      Set<java.util.UUID> visited = new HashSet<>();
      final LivingEntity[] current = new LivingEntity[] { target };
      final int[] jumps = new int[] { 0 };
      Runnable step = () -> {
        LivingEntity now = current[0];
        if (now == null || !now.isValid() || now.isDead()) {
          current[0] = null;
          return;
        }
        if (!canAffect(ctx, now, policy)) {
          current[0] = null;
          return;
        }
        visited.add(now.getUniqueId());
        double dmg = amount * Math.pow(falloff, jumps[0]);
        if (dmg > 0.0) {
          ctx.engine().recordDamageAttribution(now.getUniqueId(), ctx.castId(), ctx.abilityId(), ctx.caster().getUniqueId());
          now.damage(dmg, ctx.caster());
        }
        if (onHit != null) {
          onHit.execute(ctx, now);
        }
        jumps[0]++;
        if (jumps[0] >= maxJumps) {
          current[0] = null;
          return;
        }
        LivingEntity next = nextChainTarget(ctx, now.getLocation(), radius, visited, policy);
        current[0] = next;
      };

      if (delayTicks <= 0) {
        while (current[0] != null && jumps[0] < maxJumps) {
          step.run();
        }
        return;
      }

      final dev.patric.dungeonsreborn.effects.EffectsEngine.ScheduledHandle[] handle = new dev.patric.dungeonsreborn.effects.EffectsEngine.ScheduledHandle[1];
      handle[0] = ctx.engine().runRepeating(0L, delayTicks, () -> {
        if (handle[0] == null || handle[0].isCancelled()) {
          return;
        }
        step.run();
        if (current[0] == null) {
          handle[0].cancel();
        }
      });
      ctx.state().track(handle[0]);
    };
  }

  /**
   * Damage with a per-target i-frame window (anti multi-hit).
   */
  public static TargetAction<LivingEntity> damageIFramed(double amount, long iFrameTicks) {
    return damageIFramed(amount, null, iFrameTicks, DamagePolicy.hostileDefault());
  }

  public static TargetAction<LivingEntity> damageIFramed(double amount, String group, long iFrameTicks, DamagePolicy policy) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    if (iFrameTicks <= 0) {
      throw new IllegalArgumentException("iFrameTicks must be > 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      if (!canAffect(ctx, target, policy)) {
        return;
      }
      String g = group == null || group.isBlank() ? ("damage:" + ctx.abilityId()) : group;
      if (!ctx.engine().tryStartImmunity(target.getUniqueId(), g, iFrameTicks)) {
        return;
      }
      ctx.engine().recordDamageAttribution(target.getUniqueId(), ctx.castId(), ctx.abilityId(), ctx.caster().getUniqueId());
      target.damage(amount, ctx.caster());
    };
  }

  public static TargetAction<LivingEntity> heal(double amount) {
    return heal(amount, DamagePolicy.any());
  }

  public static TargetAction<LivingEntity> heal(double amount, DamagePolicy policy) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      if (!canAffect(ctx, target, policy)) {
        return;
      }
      double max = resolveMaxHealth(target);
      double next = Math.min(max, target.getHealth() + amount);
      target.setHealth(next);
    };
  }

  public static TargetAction<LivingEntity> potion(PotionEffectType type, Duration duration, int amplifier) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(duration, "duration");
    if (amplifier < 0) {
      throw new IllegalArgumentException("amplifier must be >= 0");
    }
    int ticks = Math.max(1, (int) Math.min(Integer.MAX_VALUE, (duration.toMillis() + 49L) / 50L));
    return (ctx, target) -> target.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, true, true));
  }

  /**
   * Potion effect gated by an immunity group stored per target.
   * <p>
   * This is useful for preventing rapid reapplication / spam from repeating actions.
   */
  public static TargetAction<LivingEntity> potionWithImmunity(PotionEffectType type, Duration duration, int amplifier,
      String immunityGroup, long immunityTicks) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(duration, "duration");
    Objects.requireNonNull(immunityGroup, "immunityGroup");
    if (amplifier < 0) {
      throw new IllegalArgumentException("amplifier must be >= 0");
    }
    if (immunityTicks <= 0) {
      throw new IllegalArgumentException("immunityTicks must be > 0");
    }
    int ticks = Math.max(1, (int) Math.min(Integer.MAX_VALUE, (duration.toMillis() + 49L) / 50L));
    return (ctx, target) -> {
      String g = "potion:" + immunityGroup;
      if (!ctx.engine().tryStartImmunity(target.getUniqueId(), g, immunityTicks)) {
        return;
      }
      target.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, true, true));
    };
  }

  public static TargetAction<LivingEntity> addTag(String tag) {
    Objects.requireNonNull(tag, "tag");
    if (tag.isBlank()) {
      throw new IllegalArgumentException("tag is blank");
    }
    return (ctx, target) -> target.addScoreboardTag(tag);
  }

  public static TargetAction<LivingEntity> removeTag(String tag) {
    Objects.requireNonNull(tag, "tag");
    if (tag.isBlank()) {
      throw new IllegalArgumentException("tag is blank");
    }
    return (ctx, target) -> target.removeScoreboardTag(tag);
  }

  /**
   * Adds a scoreboard tag for a duration (removed afterwards).
   */
  public static TargetAction<LivingEntity> tagForDuration(String tag, long durationTicks) {
    Objects.requireNonNull(tag, "tag");
    if (tag.isBlank()) {
      throw new IllegalArgumentException("tag is blank");
    }
    if (durationTicks <= 0) {
      throw new IllegalArgumentException("durationTicks must be > 0");
    }
    return (ctx, target) -> {
      target.addScoreboardTag(tag);
      var handle = ctx.engine().runLater(durationTicks, () -> target.removeScoreboardTag(tag));
      ctx.state().track(handle);
    };
  }

  public static TargetAction<LivingEntity> launchWitherSkull(double speed, boolean charged, float yield, boolean incendiary, DamagePolicy policy) {
    if (speed <= 0) {
      throw new IllegalArgumentException("speed must be > 0");
    }
    if (yield < 0) {
      throw new IllegalArgumentException("yield must be >= 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      if (!canAffect(ctx, target, policy)) {
        return;
      }
      if (ctx.world() == null || target.getWorld() == null || !ctx.world().equals(target.getWorld())) {
        return;
      }
      Vector dir = directionToTarget(ctx, target);
      var skull = ctx.world().spawn(ctx.origin(), WitherSkull.class);
      skull.setShooter(ctx.caster());
      skull.setCharged(charged);
      skull.setYield(yield);
      skull.setIsIncendiary(incendiary);
      skull.setVelocity(dir.multiply(speed));
    };
  }

  public static TargetAction<LivingEntity> launchFireball(double speed, float yield, boolean incendiary, DamagePolicy policy) {
    if (speed <= 0) {
      throw new IllegalArgumentException("speed must be > 0");
    }
    if (yield < 0) {
      throw new IllegalArgumentException("yield must be >= 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      if (!canAffect(ctx, target, policy)) {
        return;
      }
      if (ctx.world() == null || target.getWorld() == null || !ctx.world().equals(target.getWorld())) {
        return;
      }
      Vector dir = directionToTarget(ctx, target);
      var fb = ctx.world().spawn(ctx.origin(), Fireball.class);
      fb.setShooter(ctx.caster());
      fb.setYield(yield);
      fb.setIsIncendiary(incendiary);
      fb.setVelocity(dir.multiply(speed));
    };
  }

  public static TargetAction<LivingEntity> launchDragonFireball(double speed, DamagePolicy policy) {
    if (speed <= 0) {
      throw new IllegalArgumentException("speed must be > 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      if (!canAffect(ctx, target, policy)) {
        return;
      }
      if (ctx.world() == null || target.getWorld() == null || !ctx.world().equals(target.getWorld())) {
        return;
      }
      Vector dir = directionToTarget(ctx, target);
      var fb = ctx.world().spawn(ctx.origin(), DragonFireball.class);
      fb.setShooter(ctx.caster());
      fb.setVelocity(dir.multiply(speed));
    };
  }

  public static TargetAction<LivingEntity> arrowVolley(int count, double spreadDegrees, double speed, boolean spectral, DamagePolicy policy) {
    if (count <= 0) {
      throw new IllegalArgumentException("count must be > 0");
    }
    if (spreadDegrees < 0) {
      throw new IllegalArgumentException("spreadDegrees must be >= 0");
    }
    if (speed <= 0) {
      throw new IllegalArgumentException("speed must be > 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      if (!canAffect(ctx, target, policy)) {
        return;
      }
      if (ctx.world() == null || target.getWorld() == null || !ctx.world().equals(target.getWorld())) {
        return;
      }
      Vector base = directionToTarget(ctx, target);
      java.util.Random rng = ctx.rng();
      double spreadRad = Math.toRadians(spreadDegrees);

      for (int i = 0; i < count; i++) {
        Vector dir = jitterDirection(base, spreadRad, rng);
        Projectile arrow;
        if (spectral) {
          arrow = ctx.world().spawn(ctx.origin(), org.bukkit.entity.SpectralArrow.class);
        } else {
          arrow = ctx.world().spawn(ctx.origin(), Arrow.class);
        }
        if (arrow instanceof AbstractArrow aa) {
          aa.setShooter(ctx.caster());
          aa.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        } else {
          arrow.setShooter(ctx.caster());
        }
        arrow.setVelocity(dir.multiply(speed));
      }
    };
  }

  public static TargetAction<LivingEntity> throwTrident(double speed, DamagePolicy policy) {
    if (speed <= 0) {
      throw new IllegalArgumentException("speed must be > 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      if (!canAffect(ctx, target, policy)) {
        return;
      }
      if (ctx.world() == null || target.getWorld() == null || !ctx.world().equals(target.getWorld())) {
        return;
      }
      Vector dir = directionToTarget(ctx, target);
      Trident trident = ctx.world().spawn(ctx.origin(), Trident.class);
      trident.setShooter(ctx.caster());
      trident.setVelocity(dir.multiply(speed));
      trident.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
    };
  }

  public static TargetAction<LivingEntity> strikeLightning(boolean effectOnly, DamagePolicy policy) {
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      if (!canAffect(ctx, target, policy)) {
        return;
      }
      if (target.getWorld() == null) {
        return;
      }
      if (effectOnly) {
        target.getWorld().strikeLightningEffect(target.getLocation());
      } else {
        target.getWorld().strikeLightning(target.getLocation());
      }
    };
  }

  public static TargetAction<LivingEntity> explodeAt(float power, boolean setFire, boolean breakBlocks, DamagePolicy policy) {
    if (power <= 0) {
      throw new IllegalArgumentException("power must be > 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      if (!canAffect(ctx, target, policy)) {
        return;
      }
      if (target.getWorld() == null) {
        return;
      }
      target.getWorld().createExplosion(target.getLocation(), power, setFire, breakBlocks, ctx.caster());
    };
  }

  /**
   * Spawns evoker fangs in a line from the caster towards the target.
   */
  public static TargetAction<LivingEntity> evokerFangsLine(int count, double spacing, long periodTicks, DamagePolicy policy) {
    if (count <= 0) {
      throw new IllegalArgumentException("count must be > 0");
    }
    if (spacing <= 0) {
      throw new IllegalArgumentException("spacing must be > 0");
    }
    if (periodTicks <= 0) {
      throw new IllegalArgumentException("periodTicks must be > 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      if (!canAffect(ctx, target, policy)) {
        return;
      }
      if (ctx.world() == null || target.getWorld() == null || !ctx.world().equals(target.getWorld())) {
        return;
      }
      Vector dir = directionToTarget(ctx, target);
      Vector flat = dir.clone();
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

  public static TargetAction<LivingEntity> knockbackFromOrigin(double horizontal, double vertical) {
    if (horizontal < 0) {
      throw new IllegalArgumentException("horizontal must be >= 0");
    }
    return (ctx, target) -> {
      Vector dir = target.getLocation().toVector().subtract(ctx.origin().toVector());
      dir.setY(0);
      if (dir.lengthSquared() < 1e-9) {
        dir = ctx.direction().clone();
        dir.setY(0);
      }
      if (dir.lengthSquared() < 1e-9) {
        dir = new Vector(0, 0, 1);
      }
      dir.normalize().multiply(horizontal);
      dir.setY(vertical);
      target.setVelocity(target.getVelocity().add(dir));
    };
  }

  public static TargetAction<LivingEntity> knockbackFromOriginCapped(double horizontal, double vertical,
      double maxHorizontalVelocity, double maxVerticalVelocity, double maxTotalVelocity) {
    if (horizontal < 0) {
      throw new IllegalArgumentException("horizontal must be >= 0");
    }
    if (maxHorizontalVelocity < 0 || maxVerticalVelocity < 0 || maxTotalVelocity < 0) {
      throw new IllegalArgumentException("velocity caps must be >= 0");
    }
    return (ctx, target) -> {
      Vector dir = target.getLocation().toVector().subtract(ctx.origin().toVector());
      dir.setY(0);
      if (dir.lengthSquared() < 1e-9) {
        dir = ctx.direction().clone();
        dir.setY(0);
      }
      if (dir.lengthSquared() < 1e-9) {
        dir = new Vector(0, 0, 1);
      }
      dir.normalize().multiply(horizontal);
      dir.setY(vertical);
      Vector next = target.getVelocity().add(dir);
      target.setVelocity(clampVelocity(next, maxHorizontalVelocity, maxVerticalVelocity, maxTotalVelocity));
    };
  }

  public static TargetAction<LivingEntity> pullToOrigin(double horizontal, double vertical) {
    if (horizontal < 0) {
      throw new IllegalArgumentException("horizontal must be >= 0");
    }
    return (ctx, target) -> {
      Vector dir = ctx.origin().toVector().subtract(target.getLocation().toVector());
      dir.setY(0);
      if (dir.lengthSquared() < 1e-9) {
        dir = ctx.direction().clone().multiply(-1);
        dir.setY(0);
      }
      if (dir.lengthSquared() < 1e-9) {
        dir = new Vector(0, 0, -1);
      }
      dir.normalize().multiply(horizontal);
      dir.setY(vertical);
      target.setVelocity(target.getVelocity().add(dir));
    };
  }

  public static TargetAction<LivingEntity> pullToOriginCapped(double horizontal, double vertical,
      double maxHorizontalVelocity, double maxVerticalVelocity, double maxTotalVelocity) {
    if (horizontal < 0) {
      throw new IllegalArgumentException("horizontal must be >= 0");
    }
    if (maxHorizontalVelocity < 0 || maxVerticalVelocity < 0 || maxTotalVelocity < 0) {
      throw new IllegalArgumentException("velocity caps must be >= 0");
    }
    return (ctx, target) -> {
      Vector dir = ctx.origin().toVector().subtract(target.getLocation().toVector());
      dir.setY(0);
      if (dir.lengthSquared() < 1e-9) {
        dir = ctx.direction().clone().multiply(-1);
        dir.setY(0);
      }
      if (dir.lengthSquared() < 1e-9) {
        dir = new Vector(0, 0, -1);
      }
      dir.normalize().multiply(horizontal);
      dir.setY(vertical);
      Vector next = target.getVelocity().add(dir);
      target.setVelocity(clampVelocity(next, maxHorizontalVelocity, maxVerticalVelocity, maxTotalVelocity));
    };
  }

  /**
   * Pulls entities towards {@link CastContext#origin()} with distance-scaled horizontal strength.
   * <p>
   * Strength scales from {@code minHorizontal} (at distance 0) to {@code maxHorizontal} (at distance {@code maxRadius} and beyond).
   */
  public static TargetAction<LivingEntity> pullToOriginScaled(double minHorizontal, double maxHorizontal, double vertical, double maxRadius) {
    if (minHorizontal < 0) {
      throw new IllegalArgumentException("minHorizontal must be >= 0");
    }
    if (maxHorizontal < 0) {
      throw new IllegalArgumentException("maxHorizontal must be >= 0");
    }
    if (maxHorizontal < minHorizontal) {
      throw new IllegalArgumentException("maxHorizontal must be >= minHorizontal");
    }
    if (maxRadius <= 0) {
      throw new IllegalArgumentException("maxRadius must be > 0");
    }

    return (ctx, target) -> {
      double dist = target.getLocation().distance(ctx.origin());
      double t = Math.min(1.0, Math.max(0.0, dist / maxRadius));
      // Slightly emphasize far targets so the pull feels more "catch-up" at the edges.
      t = Math.pow(t, 1.35);
      double horizontal = minHorizontal + (maxHorizontal - minHorizontal) * t;
      pullToOrigin(horizontal, vertical).execute(ctx, target);
    };
  }

  public static TargetAction<LivingEntity> pullToOriginScaledCapped(double minHorizontal, double maxHorizontal, double vertical, double maxRadius,
      double maxHorizontalVelocity, double maxVerticalVelocity, double maxTotalVelocity) {
    if (minHorizontal < 0) {
      throw new IllegalArgumentException("minHorizontal must be >= 0");
    }
    if (maxHorizontal < 0) {
      throw new IllegalArgumentException("maxHorizontal must be >= 0");
    }
    if (maxHorizontal < minHorizontal) {
      throw new IllegalArgumentException("maxHorizontal must be >= minHorizontal");
    }
    if (maxRadius <= 0) {
      throw new IllegalArgumentException("maxRadius must be > 0");
    }
    if (maxHorizontalVelocity < 0 || maxVerticalVelocity < 0 || maxTotalVelocity < 0) {
      throw new IllegalArgumentException("velocity caps must be >= 0");
    }

    return (ctx, target) -> {
      double dist = target.getLocation().distance(ctx.origin());
      double t = Math.min(1.0, Math.max(0.0, dist / maxRadius));
      t = Math.pow(t, 1.35);
      double horizontal = minHorizontal + (maxHorizontal - minHorizontal) * t;
      pullToOriginCapped(horizontal, vertical, maxHorizontalVelocity, maxVerticalVelocity, maxTotalVelocity).execute(ctx, target);
    };
  }

  public static TargetAction<LivingEntity> setVelocity(Vector velocity, boolean addToExisting, double maxHorizontal, double maxVertical) {
    Objects.requireNonNull(velocity, "velocity");
    if (maxHorizontal < 0 || maxVertical < 0) {
      throw new IllegalArgumentException("maxHorizontal/maxVertical must be >= 0");
    }
    return (ctx, target) -> {
      Vector next = velocity.clone();
      if (addToExisting) {
        Vector existing = target.getVelocity();
        if (existing != null) {
          next.add(existing);
        }
      }
      if (!Double.isFinite(next.getX()) || !Double.isFinite(next.getY()) || !Double.isFinite(next.getZ())) {
        return;
      }
      next = clampVelocity(next, maxHorizontal, maxVertical, Double.MAX_VALUE);
      target.setVelocity(next);
    };
  }

  private static boolean canAffect(CastContext ctx, LivingEntity target, DamagePolicy policy) {
    if (!policy.allowSelf() && target.getUniqueId().equals(ctx.caster().getUniqueId())) {
      return false;
    }
    if (target instanceof Player) {
      if (!policy.allowPlayers()) {
        return false;
      }
    } else {
      if (!policy.allowMobs()) {
        return false;
      }
    }
    Relation rel = ctx.engine().relation(ctx.caster(), target);
    if (!policy.allowAllies() && rel == Relation.ALLY) {
      return false;
    }
    return true;
  }

  private static LivingEntity nextChainTarget(CastContext ctx, org.bukkit.Location origin, double radius, Set<java.util.UUID> visited,
      DamagePolicy policy) {
    if (ctx.world() == null) {
      return null;
    }
    List<LivingEntity> results = WorldCompat.nearbyLivingEntities(ctx.world(), origin, radius, radius, radius,
        living -> !visited.contains(living.getUniqueId()) && canAffect(ctx, living, policy));
    if (results.isEmpty()) {
      return null;
    }
    results.sort(Comparator.comparingDouble(le -> le.getLocation().distanceSquared(origin)));
    return results.get(0);
  }

  private static Vector directionToTarget(CastContext ctx, LivingEntity target) {
    Vector to = target.getLocation().add(0, 1.0, 0).toVector().subtract(ctx.origin().toVector());
    if (to.lengthSquared() < 1e-9) {
      to = ctx.direction().clone();
    }
    if (to.lengthSquared() < 1e-9) {
      to = new Vector(0, 0, 1);
    }
    return to.normalize();
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

    // Rotate around Y (yaw)
    double cosY = Math.cos(yaw);
    double sinY = Math.sin(yaw);
    double x1 = b.getX() * cosY - b.getZ() * sinY;
    double z1 = b.getX() * sinY + b.getZ() * cosY;
    double y1 = b.getY();

    // Rotate around X (pitch)
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

  private static Vector clampVelocity(Vector v, double maxHorizontal, double maxVertical, double maxTotal) {
    double x = v.getX();
    double y = v.getY();
    double z = v.getZ();

    double horiz = Math.sqrt(x * x + z * z);
    if (horiz > 1e-9 && horiz > maxHorizontal) {
      double s = maxHorizontal / horiz;
      x *= s;
      z *= s;
    }
    if (Math.abs(y) > maxVertical) {
      y = Math.copySign(maxVertical, y);
    }

    Vector out = new Vector(x, y, z);
    double total = out.length();
    if (total > 1e-9 && total > maxTotal) {
      out.normalize().multiply(maxTotal);
    }
    return out;
  }

  private static double resolveMaxHealth(LivingEntity entity) {
    AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
    if (attribute == null) {
      return 20.0;
    }
    return attribute.getValue();
  }
}
