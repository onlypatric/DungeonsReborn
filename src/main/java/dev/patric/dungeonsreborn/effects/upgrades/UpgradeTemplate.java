package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.Objects;

import org.bukkit.inventory.ItemStack;

public record UpgradeTemplate(UpgradeSpec spec, ItemStack itemTemplate) {
  public UpgradeTemplate {
    Objects.requireNonNull(spec, "spec");
    Objects.requireNonNull(itemTemplate, "itemTemplate");
  }

  public ItemStack buildItem() {
    return itemTemplate.clone();
  }
}
