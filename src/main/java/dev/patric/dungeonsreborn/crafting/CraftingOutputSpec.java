package dev.patric.dungeonsreborn.crafting;

import org.bukkit.inventory.ItemStack;

public final class CraftingOutputSpec {
  private final String itemId;
  private final ItemStack item;
  private final int amount;
  private final Integer minAmount;
  private final Integer maxAmount;
  private final double chance;
  private final String pool;
  private final int weight;
  private final boolean byproduct;
  private final java.util.List<OutputScaleRule> scaleRules;
  private final OutputMutation mutation;

  public record OutputScaleRule(String permission, double multiplier, int add) {
  }

  public record OutputMutation(String displayName, java.util.List<String> loreSet, java.util.List<String> loreAdd) {
  }

  public CraftingOutputSpec(String itemId, ItemStack item, int amount, Integer minAmount, Integer maxAmount,
                            double chance, String pool, int weight, boolean byproduct,
                            java.util.List<OutputScaleRule> scaleRules, OutputMutation mutation) {
    this.itemId = itemId;
    this.item = item == null ? null : item.clone();
    this.amount = Math.max(1, amount);
    this.minAmount = minAmount;
    this.maxAmount = maxAmount;
    this.chance = Math.max(0.0, Math.min(1.0, chance));
    this.pool = pool == null || pool.isBlank() ? null : pool;
    this.weight = Math.max(1, weight);
    this.byproduct = byproduct;
    this.scaleRules = java.util.List.copyOf(scaleRules == null ? java.util.List.of() : scaleRules);
    this.mutation = mutation;
  }

  public String itemId() {
    return itemId;
  }

  public ItemStack item() {
    return item == null ? null : item.clone();
  }

  public int amount() {
    return amount;
  }

  public Integer minAmount() {
    return minAmount;
  }

  public Integer maxAmount() {
    return maxAmount;
  }

  public double chance() {
    return chance;
  }

  public String pool() {
    return pool;
  }

  public int weight() {
    return weight;
  }

  public boolean byproduct() {
    return byproduct;
  }

  public java.util.List<OutputScaleRule> scaleRules() {
    return scaleRules;
  }

  public OutputMutation mutation() {
    return mutation;
  }

  public int previewAmount() {
    if (minAmount != null || maxAmount != null) {
      int min = minAmount == null ? amount : minAmount;
      int max = maxAmount == null ? min : maxAmount;
      return Math.max(1, Math.max(min, max));
    }
    return amount;
  }

  public int rollAmount(java.util.Random random) {
    if (minAmount != null || maxAmount != null) {
      int min = minAmount == null ? amount : minAmount;
      int max = maxAmount == null ? min : maxAmount;
      if (max <= min) {
        return Math.max(1, min);
      }
      return Math.max(1, min + random.nextInt(max - min + 1));
    }
    return Math.max(1, amount);
  }

  public boolean isDefined() {
    return itemId != null || item != null;
  }

  public CraftingOutputSpec withAmount(int nextAmount) {
    return new CraftingOutputSpec(itemId, item, nextAmount, minAmount, maxAmount, chance, pool, weight, byproduct,
        scaleRules, mutation);
  }

  public CraftingOutputSpec withByproduct(boolean nextByproduct) {
    return new CraftingOutputSpec(itemId, item, amount, minAmount, maxAmount, chance, pool, weight, nextByproduct,
        scaleRules, mutation);
  }
}
