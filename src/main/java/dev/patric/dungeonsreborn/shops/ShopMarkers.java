package dev.patric.dungeonsreborn.shops;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import dev.patric.dungeonsreborn.effects.Ids;

public final class ShopMarkers {
  public static final NamespacedKey SHOP_ID = new NamespacedKey("dungeonsreborn", "shop_id");

  private ShopMarkers() {
  }

  public static String getShopId(ItemStack item) {
    if (item == null) {
      return null;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return null;
    }
    return meta.getPersistentDataContainer().get(SHOP_ID, PersistentDataType.STRING);
  }

  public static ItemStack setShopId(ItemStack item, String shopId) {
    if (item == null) {
      return null;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    String normalized = shopId == null ? null : Ids.normalize(shopId);
    if (normalized == null || normalized.isBlank()) {
      meta.getPersistentDataContainer().remove(SHOP_ID);
    } else {
      meta.getPersistentDataContainer().set(SHOP_ID, PersistentDataType.STRING, normalized);
    }
    item.setItemMeta(meta);
    return item;
  }

  public static String getShopId(Entity entity) {
    if (entity == null) {
      return null;
    }
    PersistentDataContainer pdc = entity.getPersistentDataContainer();
    return pdc.get(SHOP_ID, PersistentDataType.STRING);
  }

  public static void setShopId(Entity entity, String shopId) {
    if (entity == null) {
      return;
    }
    PersistentDataContainer pdc = entity.getPersistentDataContainer();
    String normalized = shopId == null ? null : Ids.normalize(shopId);
    if (normalized == null || normalized.isBlank()) {
      pdc.remove(SHOP_ID);
    } else {
      pdc.set(SHOP_ID, PersistentDataType.STRING, normalized);
    }
  }
}
