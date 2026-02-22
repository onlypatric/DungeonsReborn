package dev.patric.dungeonsreborn.effects.combat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.LivingEntity;

import dev.patric.dungeonsreborn.effects.damage.DamageSpec;
import dev.patric.dungeonsreborn.effects.damage.DamageType;

public final class DamagePacket {
  private final LivingEntity attacker;
  private final LivingEntity victim;
  private final DamageSpec spec;
  private final Map<String, Double> stageValues = new LinkedHashMap<>();
  private double amount;
  private boolean cancelled;
  private boolean critical;
  private DamageType resolvedType;

  public DamagePacket(LivingEntity attacker, LivingEntity victim, DamageSpec spec, double amount) {
    this.attacker = attacker;
    this.victim = Objects.requireNonNull(victim, "victim");
    this.spec = Objects.requireNonNull(spec, "spec");
    this.amount = Math.max(0.0, amount);
    this.resolvedType = spec.type();
  }

  public LivingEntity attacker() {
    return attacker;
  }

  public LivingEntity victim() {
    return victim;
  }

  public DamageSpec spec() {
    return spec;
  }

  public double amount() {
    return amount;
  }

  public void setAmount(double amount) {
    this.amount = Double.isFinite(amount) ? Math.max(0.0, amount) : 0.0;
  }

  public boolean cancelled() {
    return cancelled;
  }

  public void cancel() {
    this.cancelled = true;
    this.amount = 0.0;
  }

  public void markCritical() {
    this.critical = true;
  }

  public boolean critical() {
    return critical;
  }

  public DamageType resolvedType() {
    return resolvedType;
  }

  public void setResolvedType(DamageType resolvedType) {
    this.resolvedType = resolvedType;
  }

  public void stage(String stage, double value) {
    if (stage != null) {
      stageValues.put(stage, value);
    }
  }

  public Map<String, Double> stageValues() {
    return Map.copyOf(stageValues);
  }
}

