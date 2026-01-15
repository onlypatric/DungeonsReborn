package dev.patric.dungeonsreborn.shops;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiMini;

public final class ShopItems {
  private ShopItems() {
  }

  public static ItemStack shopOpenItem(ShopSpec spec, ShopYamlRegistry registry) {
    ItemStack base = iconFor(spec, registry);
    List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
    lore.add(GuiMini.mm("<gray>Right click to open this shop.</gray>"));
    lore.add(GuiMini.mm("<dark_gray>Shop ID: " + spec.id() + "</dark_gray>"));
    ItemStack item = GuiItem.of(base)
        .displayName(GuiMini.mm(spec.title()))
        .lore(lore)
        .build();
    ShopMarkers.setShopId(item, spec.id());
    return item;
  }

  private static ItemStack iconFor(ShopSpec spec, ShopYamlRegistry registry) {
    if (spec.icon() != null) {
      ItemStack resolved = spec.icon().resolve(registry.itemResolver(), registry.tokenSpec());
      if (resolved != null && !resolved.getType().isAir()) {
        return resolved.clone();
      }
    }
    return new ItemStack(Material.EMERALD);
  }
}
