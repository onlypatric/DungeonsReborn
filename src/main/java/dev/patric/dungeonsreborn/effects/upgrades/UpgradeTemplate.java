package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

public record UpgradeTemplate(UpgradeSpec spec, ItemStack itemTemplate, Map<String, ItemStack> variants) {
  public UpgradeTemplate {
    Objects.requireNonNull(spec, "spec");
    Objects.requireNonNull(itemTemplate, "itemTemplate");
    variants = variants == null ? Map.of() : Map.copyOf(variants);
  }

  public ItemStack buildItem() {
    return itemTemplate.clone();
  }

  public ItemStack buildVariant(String id) {
    if (id == null || id.isBlank()) {
      return buildItem();
    }
    ItemStack variant = variants.get(id);
    return variant == null ? buildItem() : variant.clone();
  }

  public Set<String> variantIds() {
    return variants.keySet();
  }
}
