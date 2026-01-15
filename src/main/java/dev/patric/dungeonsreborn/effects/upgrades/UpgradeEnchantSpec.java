package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.Objects;

import org.bukkit.enchantments.Enchantment;

public record UpgradeEnchantSpec(Enchantment enchantment, int level) {
  public UpgradeEnchantSpec {
    Objects.requireNonNull(enchantment, "enchantment");
    if (level <= 0) {
      throw new IllegalArgumentException("level must be > 0");
    }
  }
}
