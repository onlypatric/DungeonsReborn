package dev.patric.dungeonsreborn.classes;

import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.integration.ItemMatcher;

public record ClassUnlockItemSpec(String label, int amount, ItemMatcher matcher, ItemStack preview) {
  public ClassUnlockItemSpec {
    if (amount < 0) {
      throw new IllegalArgumentException("amount must be >= 0");
    }
    matcher = Objects.requireNonNull(matcher, "matcher");
    if (preview != null) {
      preview = preview.clone();
    }
  }
}
