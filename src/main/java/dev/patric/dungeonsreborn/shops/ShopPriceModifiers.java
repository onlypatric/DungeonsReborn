package dev.patric.dungeonsreborn.shops;

import java.util.Map;
import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.items.ItemMarkers;

public record ShopPriceModifiers(
    Map<String, Double> tierMultipliers,
    Map<String, Double> rarityMultipliers,
    double defaultTierMultiplier,
    double defaultRarityMultiplier) {

  public ShopPriceModifiers {
    Objects.requireNonNull(tierMultipliers, "tierMultipliers");
    Objects.requireNonNull(rarityMultipliers, "rarityMultipliers");
  }

  public static ShopPriceModifiers empty() {
    return new ShopPriceModifiers(Map.of(), Map.of(), 1.0, 1.0);
  }

  public boolean isEmpty() {
    return tierMultipliers.isEmpty() && rarityMultipliers.isEmpty()
        && defaultTierMultiplier == 1.0
        && defaultRarityMultiplier == 1.0;
  }

  public double multiplierFor(ItemStack item) {
    if (item == null) {
      return 1.0;
    }
    double mult = 1.0;
    String tier = ItemMarkers.getItemTier(item);
    if (tier != null) {
      mult *= tierMultipliers.getOrDefault(tier, defaultTierMultiplier);
    } else {
      mult *= defaultTierMultiplier;
    }
    String rarity = ItemMarkers.getItemRarity(item);
    if (rarity != null) {
      mult *= rarityMultipliers.getOrDefault(rarity, defaultRarityMultiplier);
    } else {
      mult *= defaultRarityMultiplier;
    }
    return mult;
  }
}
