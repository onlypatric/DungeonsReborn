package dev.patric.dungeonsreborn.effects.heal;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import dev.patric.dungeonsreborn.effects.actions.EntityActions;

public record HealSpec(
    double amount,
    HealAmountMode mode,
    HealType type,
    EntityActions.DamagePolicy policy,
    String source,
    Set<String> tags,
    double cap,
    boolean overhealToShield,
    double shieldCap,
    long shieldDecayTicks) {

  public HealSpec {
    if (!Double.isFinite(amount) || amount < 0.0) {
      throw new IllegalArgumentException("amount must be finite and >= 0");
    }
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(policy, "policy");
    if (tags == null) {
      tags = Collections.emptySet();
    }
    if (cap < 0.0 || !Double.isFinite(cap)) {
      throw new IllegalArgumentException("cap must be finite and >= 0");
    }
    if (shieldCap < 0.0 || !Double.isFinite(shieldCap)) {
      throw new IllegalArgumentException("shieldCap must be finite and >= 0");
    }
    if (shieldDecayTicks < 0L) {
      throw new IllegalArgumentException("shieldDecayTicks must be >= 0");
    }
  }

  public static HealSpec flat(double amount, HealType type, EntityActions.DamagePolicy policy) {
    return new HealSpec(amount, HealAmountMode.FLAT, type, policy, null, Collections.emptySet(), 0.0, false, 0.0, 0L);
  }

  public static HealSpec percent(double percent, HealType type, EntityActions.DamagePolicy policy) {
    return new HealSpec(percent, HealAmountMode.PERCENT_MAX_HEALTH, type, policy, null, Collections.emptySet(), 0.0, false, 0.0, 0L);
  }

  public HealSpec withSource(String source) {
    return new HealSpec(amount, mode, type, policy, source, tags, cap, overhealToShield, shieldCap, shieldDecayTicks);
  }

  public HealSpec withTags(Set<String> tags) {
    return new HealSpec(amount, mode, type, policy, source, tags, cap, overhealToShield, shieldCap, shieldDecayTicks);
  }

  public HealSpec withCap(double cap) {
    return new HealSpec(amount, mode, type, policy, source, tags, cap, overhealToShield, shieldCap, shieldDecayTicks);
  }

  public HealSpec withOverhealToShield(boolean value, double shieldCap, long decayTicks) {
    return new HealSpec(amount, mode, type, policy, source, tags, cap, value, shieldCap, decayTicks);
  }
}
