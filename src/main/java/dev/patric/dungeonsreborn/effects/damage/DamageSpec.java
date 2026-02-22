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
    boolean applyStatusEffects,
    double armorPenFlat,
    double armorPenPct,
    double resistPenPct,
    String vulnerabilityTag,
    double critChance,
    double critMultiplier,
    double minDamageFloor,
    String mitigationProfile,
    Set<String> pipelineTags,
    boolean snapshotAtCast) {

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
    if (pipelineTags == null) {
      pipelineTags = Collections.emptySet();
    }
  }

  public static DamageSpec flat(double amount, DamageType type, DamageCause cause,
      boolean ignoreResistance, EntityActions.DamagePolicy policy) {
    return new DamageSpec(amount, DamageAmountMode.FLAT, type, cause, ignoreResistance, policy, null, Collections.emptySet(), 0.0, 0.0, true,
        0.0, 0.0, 0.0, null, 0.0, 1.5, 0.0, null, Collections.emptySet(), false);
  }

  public static DamageSpec percent(double percent, DamageType type, DamageCause cause,
      boolean ignoreResistance, EntityActions.DamagePolicy policy) {
    return new DamageSpec(percent, DamageAmountMode.PERCENT_MAX_HEALTH, type, cause, ignoreResistance, policy, null, Collections.emptySet(), 0.0, 0.0, true,
        0.0, 0.0, 0.0, null, 0.0, 1.5, 0.0, null, Collections.emptySet(), false);
  }

  public static DamageSpec trueDamage(double amount, DamageCause cause, EntityActions.DamagePolicy policy) {
    return new DamageSpec(amount, DamageAmountMode.TRUE, null, cause, true, policy, null, Collections.emptySet(), 0.0, 0.0, false,
        0.0, 0.0, 0.0, null, 0.0, 1.5, 0.0, null, Collections.emptySet(), false);
  }

  public DamageSpec withSource(String source) {
    return new DamageSpec(amount, mode, type, cause, ignoreResistance, policy, source, tags, cap, maxPercent, applyStatusEffects,
        armorPenFlat, armorPenPct, resistPenPct, vulnerabilityTag, critChance, critMultiplier, minDamageFloor, mitigationProfile,
        pipelineTags, snapshotAtCast);
  }

  public DamageSpec withTags(Set<String> tags) {
    return new DamageSpec(amount, mode, type, cause, ignoreResistance, policy, source, tags, cap, maxPercent, applyStatusEffects,
        armorPenFlat, armorPenPct, resistPenPct, vulnerabilityTag, critChance, critMultiplier, minDamageFloor, mitigationProfile,
        pipelineTags, snapshotAtCast);
  }

  public DamageSpec withCaps(double cap, double maxPercent) {
    return new DamageSpec(amount, mode, type, cause, ignoreResistance, policy, source, tags, cap, maxPercent, applyStatusEffects,
        armorPenFlat, armorPenPct, resistPenPct, vulnerabilityTag, critChance, critMultiplier, minDamageFloor, mitigationProfile,
        pipelineTags, snapshotAtCast);
  }

  public DamageSpec withStatusEffects(boolean apply) {
    return new DamageSpec(amount, mode, type, cause, ignoreResistance, policy, source, tags, cap, maxPercent, apply,
        armorPenFlat, armorPenPct, resistPenPct, vulnerabilityTag, critChance, critMultiplier, minDamageFloor, mitigationProfile,
        pipelineTags, snapshotAtCast);
  }

  public DamageSpec withPipeline(
      double armorPenFlat,
      double armorPenPct,
      double resistPenPct,
      String vulnerabilityTag,
      double critChance,
      double critMultiplier,
      double minDamageFloor,
      String mitigationProfile,
      Set<String> pipelineTags,
      boolean snapshotAtCast) {
    return new DamageSpec(amount, mode, type, cause, ignoreResistance, policy, source, tags, cap, maxPercent, applyStatusEffects,
        armorPenFlat, armorPenPct, resistPenPct, vulnerabilityTag, critChance, critMultiplier, minDamageFloor,
        mitigationProfile, pipelineTags == null ? Collections.emptySet() : pipelineTags, snapshotAtCast);
  }
}
