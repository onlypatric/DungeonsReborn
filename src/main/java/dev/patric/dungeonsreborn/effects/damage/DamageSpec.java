package dev.patric.dungeonsreborn.effects.damage;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import dev.patric.dungeonsreborn.effects.actions.EntityActions;

public record DamageSpec(
    double amount,
    DamageAmountMode mode,
    DamageType type,
    DamageCause cause,
    boolean ignoreResistance,
    EntityActions.DamagePolicy policy,
    String source,
    Set<String> tags,
    double cap,
    double maxPercent,
    boolean applyStatusEffects) {

  public DamageSpec {
    if (!Double.isFinite(amount) || amount < 0.0) {
      throw new IllegalArgumentException("amount must be finite and >= 0");
    }
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(policy, "policy");
    if (!Double.isFinite(cap) || cap < 0.0) {
      throw new IllegalArgumentException("cap must be finite and >= 0");
    }
    if (!Double.isFinite(maxPercent) || maxPercent < 0.0) {
      throw new IllegalArgumentException("maxPercent must be finite and >= 0");
    }
    if (tags == null) {
      tags = Collections.emptySet();
    }
  }

  public static DamageSpec flat(double amount, DamageType type, DamageCause cause,
      boolean ignoreResistance, EntityActions.DamagePolicy policy) {
    return new DamageSpec(amount, DamageAmountMode.FLAT, type, cause, ignoreResistance, policy, null, Collections.emptySet(), 0.0, 0.0, true);
  }

  public static DamageSpec percent(double percent, DamageType type, DamageCause cause,
      boolean ignoreResistance, EntityActions.DamagePolicy policy) {
    return new DamageSpec(percent, DamageAmountMode.PERCENT_MAX_HEALTH, type, cause, ignoreResistance, policy, null, Collections.emptySet(), 0.0, 0.0, true);
  }

  public static DamageSpec trueDamage(double amount, DamageCause cause, EntityActions.DamagePolicy policy) {
    return new DamageSpec(amount, DamageAmountMode.TRUE, null, cause, true, policy, null, Collections.emptySet(), 0.0, 0.0, false);
  }

  public DamageSpec withSource(String source) {
    return new DamageSpec(amount, mode, type, cause, ignoreResistance, policy, source, tags, cap, maxPercent, applyStatusEffects);
  }

  public DamageSpec withTags(Set<String> tags) {
    return new DamageSpec(amount, mode, type, cause, ignoreResistance, policy, source, tags, cap, maxPercent, applyStatusEffects);
  }

  public DamageSpec withCaps(double cap, double maxPercent) {
    return new DamageSpec(amount, mode, type, cause, ignoreResistance, policy, source, tags, cap, maxPercent, applyStatusEffects);
  }

  public DamageSpec withStatusEffects(boolean apply) {
    return new DamageSpec(amount, mode, type, cause, ignoreResistance, policy, source, tags, cap, maxPercent, apply);
  }
}
