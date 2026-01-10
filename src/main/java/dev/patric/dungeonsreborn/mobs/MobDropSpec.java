package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;
import java.util.Random;

import org.bukkit.inventory.ItemStack;

public record MobDropSpec(ItemStack item, double chance, int minAmount, int maxAmount) {
  public MobDropSpec {
    Objects.requireNonNull(item, "item");
    if (!(chance >= 0.0 && chance <= 1.0)) {
      throw new IllegalArgumentException("chance must be in [0,1]");
    }
    if (minAmount <= 0 || maxAmount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    if (maxAmount < minAmount) {
      throw new IllegalArgumentException("maxAmount must be >= minAmount");
    }
  }

  public ItemStack roll(Random rng) {
    if (chance <= 0.0) {
      return null;
    }
    if (chance < 1.0 && rng.nextDouble() > chance) {
      return null;
    }
    int amount = minAmount == maxAmount
        ? minAmount
        : minAmount + rng.nextInt(maxAmount - minAmount + 1);
    ItemStack out = item.clone();
    out.setAmount(amount);
    return out;
  }
}
