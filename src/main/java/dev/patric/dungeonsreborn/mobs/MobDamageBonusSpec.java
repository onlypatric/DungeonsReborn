package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

import org.bukkit.entity.LivingEntity;

import dev.patric.dungeonsreborn.effects.damage.DamageType;

public final class MobDamageBonusSpec {
  private final DamageType damageType;
  private final MobTargetFilter targetFilter;
  private final double multiplier;

  public MobDamageBonusSpec(DamageType damageType, MobTargetFilter targetFilter, double multiplier) {
    this.damageType = Objects.requireNonNull(damageType, "damageType");
    this.targetFilter = Objects.requireNonNull(targetFilter, "targetFilter");
    if (!Double.isFinite(multiplier) || multiplier <= 0.0) {
      throw new IllegalArgumentException("multiplier must be > 0");
    }
    this.multiplier = multiplier;
  }

  public DamageType damageType() {
    return damageType;
  }

  public MobTargetFilter targetFilter() {
    return targetFilter;
  }

  public double multiplier() {
    return multiplier;
  }

  public boolean matches(LivingEntity target) {
    return target == null || targetFilter.matches(target);
  }
}
