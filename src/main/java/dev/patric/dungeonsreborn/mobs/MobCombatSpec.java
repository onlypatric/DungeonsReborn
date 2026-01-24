package dev.patric.dungeonsreborn.mobs;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.potion.PotionEffectType;

public final class MobCombatSpec {
  private final double armorMultiplier;
  private final double blockChance;
  private final double blockMultiplier;
  private final long blockCooldownTicks;
  private final Set<PotionEffectType> immuneEffects;
  private final Set<PotionEffectType> cleanseEffects;

  private MobCombatSpec(Builder builder) {
    this.armorMultiplier = builder.armorMultiplier;
    this.blockChance = builder.blockChance;
    this.blockMultiplier = builder.blockMultiplier;
    this.blockCooldownTicks = builder.blockCooldownTicks;
    this.immuneEffects = Set.copyOf(builder.immuneEffects);
    this.cleanseEffects = Set.copyOf(builder.cleanseEffects);
  }

  public double armorMultiplier() {
    return armorMultiplier;
  }

  public double blockChance() {
    return blockChance;
  }

  public double blockMultiplier() {
    return blockMultiplier;
  }

  public long blockCooldownTicks() {
    return blockCooldownTicks;
  }

  public Set<PotionEffectType> immuneEffects() {
    return immuneEffects;
  }

  public Set<PotionEffectType> cleanseEffects() {
    return cleanseEffects;
  }

  public boolean isEmpty() {
    return armorMultiplier == 1.0
        && blockChance <= 0.0
        && immuneEffects.isEmpty()
        && cleanseEffects.isEmpty();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private double armorMultiplier = 1.0;
    private double blockChance;
    private double blockMultiplier = 0.5;
    private long blockCooldownTicks = 40L;
    private final Set<PotionEffectType> immuneEffects = new HashSet<>();
    private final Set<PotionEffectType> cleanseEffects = new HashSet<>();

    private Builder() {
    }

    public Builder armorMultiplier(double armorMultiplier) {
      if (!Double.isFinite(armorMultiplier) || armorMultiplier <= 0.0) {
        throw new IllegalArgumentException("armorMultiplier must be > 0");
      }
      this.armorMultiplier = armorMultiplier;
      return this;
    }

    public Builder blockChance(double blockChance) {
      if (blockChance < 0.0 || blockChance > 1.0) {
        throw new IllegalArgumentException("blockChance must be in [0,1]");
      }
      this.blockChance = blockChance;
      return this;
    }

    public Builder blockMultiplier(double blockMultiplier) {
      if (!Double.isFinite(blockMultiplier) || blockMultiplier <= 0.0) {
        throw new IllegalArgumentException("blockMultiplier must be > 0");
      }
      this.blockMultiplier = blockMultiplier;
      return this;
    }

    public Builder blockCooldownTicks(long blockCooldownTicks) {
      if (blockCooldownTicks < 0L) {
        throw new IllegalArgumentException("blockCooldownTicks must be >= 0");
      }
      this.blockCooldownTicks = blockCooldownTicks;
      return this;
    }

    public Builder addImmuneEffect(PotionEffectType type) {
      if (type != null) {
        immuneEffects.add(type);
      }
      return this;
    }

    public Builder addCleanseEffect(PotionEffectType type) {
      if (type != null) {
        cleanseEffects.add(type);
      }
      return this;
    }

    public MobCombatSpec build() {
      return new MobCombatSpec(this);
    }
  }
}
