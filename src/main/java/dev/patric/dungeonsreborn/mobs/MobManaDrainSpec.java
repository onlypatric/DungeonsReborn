package dev.patric.dungeonsreborn.mobs;

public record MobManaDrainSpec(String resourceId, double amount, double chance, long cooldownTicks) {
  public MobManaDrainSpec {
    if (resourceId == null || resourceId.isBlank()) {
      resourceId = "mana";
    }
    if (!Double.isFinite(amount) || amount < 0.0) {
      throw new IllegalArgumentException("amount must be >= 0");
    }
    if (!Double.isFinite(chance) || chance < 0.0 || chance > 1.0) {
      throw new IllegalArgumentException("chance must be in [0,1]");
    }
    if (cooldownTicks < 0L) {
      throw new IllegalArgumentException("cooldownTicks must be >= 0");
    }
  }

  public boolean isEmpty() {
    return amount <= 0.0 || chance <= 0.0;
  }
}
