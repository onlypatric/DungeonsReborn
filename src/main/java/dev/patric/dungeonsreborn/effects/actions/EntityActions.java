package dev.patric.dungeonsreborn.effects.actions;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Location;
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
import dev.patric.dungeonsreborn.effects.damage.DamageCause;
import dev.patric.dungeonsreborn.effects.damage.DamageSpec;
import dev.patric.dungeonsreborn.effects.damage.DamageType;
import dev.patric.dungeonsreborn.effects.heal.HealSpec;
import dev.patric.dungeonsreborn.effects.heal.HealType;
import dev.patric.dungeonsreborn.effects.projectile.ProjectileHit;
import dev.patric.dungeonsreborn.effects.projectile.ProjectileTelemetry;
import dev.patric.dungeonsreborn.effects.relations.Relation;
import dev.patric.dungeonsreborn.effects.targeting.TargetAction;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeStatusEffectSpec;

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
    return damage(amount, policy, DamageCause.DIRECT, null, Set.of());
  }

  public static TargetAction<LivingEntity> damage(double amount, DamagePolicy policy, DamageCause cause, String source, Set<String> tags) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      DamageCause resolved = cause == null ? DamageCause.DIRECT : cause;
      DamageSpec spec = DamageSpec.flat(amount, DamageType.PHYSICAL, resolved, false, policy);
      spec = applyDamageMetadata(spec, source, tags);
      ctx.engine().applyDamage(ctx, target, spec);
    };
  }

  public static TargetAction<LivingEntity> damageTyped(double amount, DamageType type, boolean ignoreResistance, DamagePolicy policy) {
    return damageTyped(amount, type, ignoreResistance, policy, DamageCause.DIRECT, null, Set.of());
  }

  public static TargetAction<LivingEntity> damageTyped(double amount, DamageType type, boolean ignoreResistance, DamagePolicy policy,
      DamageCause cause, String source, Set<String> tags) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      DamageCause resolved = cause == null ? DamageCause.DIRECT : cause;
      DamageSpec spec = DamageSpec.flat(amount, type, resolved, ignoreResistance, policy);
      spec = applyDamageMetadata(spec, source, tags);
      ctx.engine().applyDamage(ctx, target, spec);
    };
  }

  public static TargetAction<LivingEntity> damagePercent(double percent, DamagePolicy policy) {
    return damagePercent(percent, policy, DamageCause.PERCENT, null, Set.of());
  }

  public static TargetAction<LivingEntity> damagePercent(double percent, DamagePolicy policy, DamageCause cause, String source, Set<String> tags) {
    if (!Double.isFinite(percent) || percent <= 0) {
      throw new IllegalArgumentException("percent must be > 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      DamageCause resolved = cause == null ? DamageCause.PERCENT : cause;
      DamageSpec spec = DamageSpec.percent(percent, DamageType.PHYSICAL, resolved, false, policy);
      spec = applyDamageMetadata(spec, source, tags);
      ctx.engine().applyDamage(ctx, target, spec);
    };
  }

  public static TargetAction<LivingEntity> ignite(int ticks) {
    if (ticks <= 0) {
      throw new IllegalArgumentException("ticks must be > 0");
    }
    return (ctx, target) -> {
      int applied = Math.max(target.getFireTicks(), ticks);
      target.setFireTicks(applied);
    };
  }

  public static TargetAction<LivingEntity> damageTrue(double amount, DamagePolicy policy) {
    return damageTrue(amount, policy, DamageCause.TRUE, null, Set.of());
  }

  public static TargetAction<LivingEntity> damageTrue(double amount, DamagePolicy policy, DamageCause cause, String source, Set<String> tags) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      DamageCause resolved = cause == null ? DamageCause.TRUE : cause;
      DamageSpec spec = DamageSpec.trueDamage(amount, resolved, policy);
      spec = applyDamageMetadata(spec, source, tags);
      ctx.engine().applyDamage(ctx, target, spec);
    };
  }

  public static TargetAction<LivingEntity> damageWithFalloff(double amount, double maxDistance, double minMultiplier, DamagePolicy policy) {
    return damageWithFalloff(amount, maxDistance, minMultiplier, policy, DamageCause.FALLOFF, null, Set.of());
  }

  public static TargetAction<LivingEntity> damageWithFalloff(double amount, double maxDistance, double minMultiplier, DamagePolicy policy,
      DamageCause cause, String source, Set<String> tags) {
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
      double dist = target.getLocation().distance(ctx.origin());
      double t = Math.min(1.0, Math.max(0.0, dist / maxDistance));
      double mult = Math.max(minMultiplier, 1.0 - t);
      double dmg = amount * mult;
      if (dmg <= 0.0) {
        return;
      }
      DamageCause resolved = cause == null ? DamageCause.FALLOFF : cause;
      DamageSpec spec = DamageSpec.flat(dmg, DamageType.PHYSICAL, resolved, false, policy);
      spec = applyDamageMetadata(spec, source, tags);
      ctx.engine().applyDamage(ctx, target, spec);
    };
  }

  public static TargetAction<LivingEntity> damageCrit(double amount, double critChance, double critMultiplier,
      double headshotMultiplier, double headshotThreshold, DamagePolicy policy) {
    return damageCrit(amount, critChance, critMultiplier, headshotMultiplier, headshotThreshold, policy, DamageCause.CRIT, null, Set.of());
  }

  public static TargetAction<LivingEntity> damageCrit(double amount, double critChance, double critMultiplier,
      double headshotMultiplier, double headshotThreshold, DamagePolicy policy, DamageCause cause, String source, Set<String> tags) {
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
      double chance = critChance > 1.0 ? critChance / 100.0 : critChance;
      double multiplier = 1.0;
      boolean crit = false;
      boolean headshot = false;
      if (chance > 0.0 && ctx.rng().nextDouble() < chance) {
        multiplier *= critMultiplier;
        crit = true;
      }
      if (headshotMultiplier > 1.0) {
        Object hitObj = ctx.state().get(Vars.PROJECTILE_LAST_HIT);
        Location impact = null;
        if (hitObj instanceof ProjectileHit hit && target.equals(hit.hitEntity())) {
          impact = hit.location();
        } else if (hitObj instanceof ProjectileTelemetry telemetry && target.equals(telemetry.victim())) {
          impact = telemetry.impactLocation();
        }
        if (impact != null) {
          double eyeY = target.getEyeLocation().getY();
          if (impact.getY() >= (eyeY - headshotThreshold)) {
            multiplier *= headshotMultiplier;
            headshot = true;
          }
        }
      }
      double dmg = amount * multiplier;
      if (dmg <= 0.0) {
        return;
      }
      java.util.Set<String> localTags = new java.util.HashSet<>();
      if (crit) {
        localTags.add("crit");
      }
      if (headshot) {
        localTags.add("headshot");
      }
      DamageCause resolved = cause == null ? DamageCause.CRIT : cause;
      DamageSpec spec = DamageSpec.flat(dmg, DamageType.PHYSICAL, resolved, false, policy);
      spec = applyDamageMetadata(spec, source, mergeTags(localTags, tags));
      ctx.engine().applyDamage(ctx, target, spec);
    };
  }

  public static TargetAction<LivingEntity> damageLifesteal(double amount, double ratio, DamagePolicy policy) {
    return damageLifesteal(amount, ratio, policy, DamageCause.LIFESTEAL, null, Set.of());
  }

  public static TargetAction<LivingEntity> damageLifesteal(double amount, double ratio, DamagePolicy policy,
      DamageCause cause, String source, Set<String> tags) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    if (!Double.isFinite(ratio) || ratio < 0) {
      throw new IllegalArgumentException("ratio must be >= 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      DamageCause resolved = cause == null ? DamageCause.LIFESTEAL : cause;
      DamageSpec spec = DamageSpec.flat(amount, DamageType.PHYSICAL, resolved, false, policy);
      spec = applyDamageMetadata(spec, source, tags);
      double applied = ctx.engine().applyDamage(ctx, target, spec);
      double heal = applied * ratio;
      if (heal > 0.0) {
        LivingEntity caster = ctx.caster();
        double max = resolveMaxHealth(caster);
        double next = Math.min(max, caster.getHealth() + heal);
        caster.setHealth(next);
      }
    };
  }

  public static TargetAction<LivingEntity> damageOverTime(double amount, long periodTicks, int times, DamagePolicy policy) {
    return damageOverTime(amount, periodTicks, times, policy, DamageCause.DOT, null, Set.of(), (ctx, target) -> {
    });
  }

  public static TargetAction<LivingEntity> damageOverTime(double amount, long periodTicks, int times, DamagePolicy policy,
      DamageCause cause, String source, Set<String> tags, TargetAction<LivingEntity> onTick) {
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
    Objects.requireNonNull(onTick, "onTick");
    return (ctx, target) -> {
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
        DamageCause resolved = cause == null ? DamageCause.DOT : cause;
        DamageSpec spec = DamageSpec.flat(amount, DamageType.PHYSICAL, resolved, false, policy);
        spec = applyDamageMetadata(spec, source, tags);
        ctx.engine().applyDamage(ctx, captured, spec);
        onTick.execute(ctx, captured);
      });
      ctx.state().track(handle[0]);
    };
  }

  public static TargetAction<LivingEntity> chainDamage(double amount, double radius, int maxJumps, long delayTicks,
      double falloff, DamagePolicy policy, TargetAction<LivingEntity> onHit) {
    return chainDamage(amount, radius, maxJumps, delayTicks, falloff, policy, DamageCause.CHAIN, null, Set.of(), onHit);
  }

  public static TargetAction<LivingEntity> chainDamage(double amount, double radius, int maxJumps, long delayTicks,
      double falloff, DamagePolicy policy, DamageCause cause, String source, Set<String> tags, TargetAction<LivingEntity> onHit) {
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
          DamageCause resolved = cause == null ? DamageCause.CHAIN : cause;
          DamageSpec spec = DamageSpec.flat(dmg, DamageType.LIGHTNING, resolved, false, policy);
          spec = applyDamageMetadata(spec, source, tags);
          ctx.engine().applyDamage(ctx, now, spec);
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
    return damageIFramed(amount, group, iFrameTicks, policy, DamageCause.DIRECT, null, Set.of());
  }

  public static TargetAction<LivingEntity> damageIFramed(double amount, String group, long iFrameTicks, DamagePolicy policy,
      DamageCause cause, String source, Set<String> tags) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    if (iFrameTicks <= 0) {
      throw new IllegalArgumentException("iFrameTicks must be > 0");
    }
    Objects.requireNonNull(policy, "policy");
    return (ctx, target) -> {
      String g = group == null || group.isBlank() ? ("damage:" + ctx.abilityId()) : group;
      if (!ctx.engine().tryStartImmunity(target.getUniqueId(), g, iFrameTicks)) {
        return;
      }
      DamageCause resolved = cause == null ? DamageCause.DIRECT : cause;
      DamageSpec spec = DamageSpec.flat(amount, DamageType.PHYSICAL, resolved, false, policy);
      spec = applyDamageMetadata(spec, source, tags);
      ctx.engine().applyDamage(ctx, target, spec);
    };
  }

  private static DamageSpec applyDamageMetadata(DamageSpec spec, String source, Set<String> tags) {
    DamageSpec out = spec;
    if (source != null && !source.isBlank()) {
      out = out.withSource(source);
    }
    if (tags != null && !tags.isEmpty()) {
      Set<String> merged = mergeTags(out.tags(), tags);
      out = out.withTags(merged);
    }
    return out;
  }

  private static Set<String> mergeTags(Set<String> base, Set<String> extra) {
    if (extra == null || extra.isEmpty()) {
      return base == null ? Set.of() : base;
    }
    Set<String> merged = new HashSet<>();
    if (base != null) {
      merged.addAll(base);
    }
    merged.addAll(extra);
    return Set.copyOf(merged);
  }

  public static TargetAction<LivingEntity> heal(double amount) {
    return heal(amount, DamagePolicy.any());
  }

  public static TargetAction<LivingEntity> heal(double amount, DamagePolicy policy) {
    return heal(amount, policy, HealType.DIRECT, null, Set.of(), 0.0, false, 0.0, 0L);
  }

  public static TargetAction<LivingEntity> heal(double amount, DamagePolicy policy, HealType type, String source,
      Set<String> tags, double cap, boolean overhealToShield, double shieldCap, long shieldDecayTicks) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    Objects.requireNonNull(policy, "policy");
    Objects.requireNonNull(type, "type");
    return (ctx, target) -> {
      HealSpec spec = HealSpec.flat(amount, type, policy)
          .withSource(source == null ? null : source)
          .withTags(tags == null ? Set.of() : tags)
          .withCap(cap)
          .withOverhealToShield(overhealToShield, shieldCap, shieldDecayTicks);
      ctx.engine().applyHeal(ctx, target, spec);
    };
  }

  public static TargetAction<LivingEntity> healPercent(double percent, DamagePolicy policy) {
    return healPercent(percent, policy, HealType.DIRECT, null, Set.of(), 0.0, false, 0.0, 0L);
  }

  public static TargetAction<LivingEntity> healPercent(double percent, DamagePolicy policy, HealType type, String source,
      Set<String> tags, double cap, boolean overhealToShield, double shieldCap, long shieldDecayTicks) {
    if (!Double.isFinite(percent) || percent <= 0) {
      throw new IllegalArgumentException("percent must be > 0");
    }
    Objects.requireNonNull(policy, "policy");
    Objects.requireNonNull(type, "type");
    return (ctx, target) -> {
      HealSpec spec = HealSpec.percent(percent, type, policy)
          .withSource(source == null ? null : source)
          .withTags(tags == null ? Set.of() : tags)
          .withCap(cap)
          .withOverhealToShield(overhealToShield, shieldCap, shieldDecayTicks);
      ctx.engine().applyHeal(ctx, target, spec);
    };
  }

  public static TargetAction<LivingEntity> healOverTime(double amount, long periodTicks, int times, DamagePolicy policy) {
    return healOverTime(amount, periodTicks, times, policy, HealType.HOT, null, Set.of(), 0.0, false, 0.0, 0L,
        (ctx, target) -> {
        });
  }

  public static TargetAction<LivingEntity> healOverTime(double amount, long periodTicks, int times, DamagePolicy policy,
      HealType type, String source, Set<String> tags, double cap, boolean overhealToShield,
      double shieldCap, long shieldDecayTicks, TargetAction<LivingEntity> onTick) {
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
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(onTick, "onTick");
    return (ctx, target) -> {
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
        HealSpec spec = HealSpec.flat(amount, type, policy)
            .withSource(source == null ? null : source)
            .withTags(tags == null ? Set.of() : tags)
            .withCap(cap)
            .withOverhealToShield(overhealToShield, shieldCap, shieldDecayTicks);
        ctx.engine().applyHeal(ctx, captured, spec);
        onTick.execute(ctx, captured);
      });
      ctx.state().track(handle[0]);
    };
  }

  public static TargetAction<LivingEntity> shield(double amount, double cap, long decayTicks, DamagePolicy policy, HealType type) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    if (cap < 0) {
      throw new IllegalArgumentException("cap must be >= 0");
    }
    if (decayTicks < 0) {
      throw new IllegalArgumentException("decayTicks must be >= 0");
    }
    Objects.requireNonNull(policy, "policy");
    Objects.requireNonNull(type, "type");
    return (ctx, target) -> {
      if (!canAffect(ctx, target, policy)) {
        return;
      }
      ctx.engine().addShield(target.getUniqueId(), amount, cap, decayTicks);
    };
  }

  public static TargetAction<LivingEntity> potion(PotionEffectType type, Duration duration, int amplifier) {
    return potion(type, duration, amplifier, false, true, true);
  }

  public static TargetAction<LivingEntity> potion(PotionEffectType type, Duration duration, int amplifier,
      boolean ambient, boolean particles, boolean icon) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(duration, "duration");
    if (amplifier < 0) {
      throw new IllegalArgumentException("amplifier must be >= 0");
    }
    int ticks = Math.max(1, (int) Math.min(Integer.MAX_VALUE, (duration.toMillis() + 49L) / 50L));
    return (ctx, target) -> target.addPotionEffect(new PotionEffect(type, ticks, amplifier, ambient, particles, icon));
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

  public static boolean canAffect(CastContext ctx, LivingEntity target, DamagePolicy policy) {
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

  public static double resolveMaxHealth(LivingEntity entity) {
    AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
    if (attribute == null) {
      return 20.0;
    }
    return attribute.getValue();
  }

  public static void applyUpgradeStatusEffects(CastContext ctx, LivingEntity target) {
    if (target == null || target.isDead()) {
      return;
    }
    Object raw = ctx.variables().get(Vars.UPGRADE_STATUS_EFFECTS);
    if (!(raw instanceof List<?> list) || list.isEmpty()) {
      return;
    }
    for (Object entry : list) {
      if (!(entry instanceof UpgradeStatusEffectSpec spec)) {
        continue;
      }
      double chance = spec.chance() > 1.0 ? spec.chance() / 100.0 : spec.chance();
      if (chance <= 0.0) {
        continue;
      }
      if (chance < 1.0 && ctx.rng().nextDouble() > chance) {
        continue;
      }
      target.addPotionEffect(new PotionEffect(spec.type(), spec.durationTicks(), spec.amplifier(),
          spec.ambient(), spec.particles(), spec.icon()));
    }
  }
}
