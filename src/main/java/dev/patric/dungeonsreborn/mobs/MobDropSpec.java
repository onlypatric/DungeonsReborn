package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;
import java.util.Random;

import org.bukkit.inventory.ItemStack;

public record MobDropSpec(ItemStack item, String tier, double chance, int minAmount, int maxAmount,
    boolean tokenDrop) {
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
