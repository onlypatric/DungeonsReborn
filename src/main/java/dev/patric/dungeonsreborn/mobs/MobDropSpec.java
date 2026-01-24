package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;
import java.util.Random;

import org.bukkit.inventory.ItemStack;

public record MobDropSpec(ItemStack item, String tier, double chance, int minAmount, int maxAmount,
    boolean tokenDrop, MobDropConditions conditions, Integer minDamage, Integer maxDamage) {
  public MobDropSpec {
    Objects.requireNonNull(item, "item");
    if (tier != null && tier.isBlank()) {
      tier = null;
    }
    if (!(chance >= 0.0 && chance <= 1.0)) {
      throw new IllegalArgumentException("chance must be in [0,1]");
    }
    if (minAmount <= 0 || maxAmount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    if (maxAmount < minAmount) {
      throw new IllegalArgumentException("maxAmount must be >= minAmount");
    }
    if (!tokenDrop) {
      int maxStack = item.getMaxStackSize();
      if (minAmount > maxStack || maxAmount > maxStack) {
        throw new IllegalArgumentException("amount must be <= max stack size (" + maxStack + ")");
      }
    }
    if (conditions == null) {
      conditions = MobDropConditions.none();
    }
    if (minDamage != null || maxDamage != null) {
      int min = minDamage == null ? 0 : minDamage;
      int max = maxDamage == null ? min : maxDamage;
      if (min < 0 || max < 0) {
        throw new IllegalArgumentException("durability range must be >= 0");
      }
      if (max < min) {
        throw new IllegalArgumentException("durability max must be >= min");
      }
      minDamage = min;
      maxDamage = max;
    }
  }

  public int rollAmount(Random rng) {
    if (chance <= 0.0) {
      return 0;
    }
    if (chance < 1.0 && rng.nextDouble() > chance) {
      return 0;
    }
    if (minAmount == maxAmount) {
      return minAmount;
    }
    return minAmount + rng.nextInt(maxAmount - minAmount + 1);
  }
}
