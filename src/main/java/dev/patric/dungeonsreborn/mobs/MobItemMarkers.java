package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import dev.patric.dungeonsreborn.effects.Ids;

public final class MobItemMarkers {
  public static final NamespacedKey EGG_ID = new NamespacedKey("dungeonsreborn", "mob_egg_id");
  public static final NamespacedKey EGG_MOB_ID = new NamespacedKey("dungeonsreborn", "mob_egg_mob_id");

  private MobItemMarkers() {
  }

  public static String getEggId(ItemStack item) {
    return getString(item, EGG_ID);
  }

  public static String getMobId(ItemStack item) {
    return getString(item, EGG_MOB_ID);
  }

  public static ItemStack setEggId(ItemStack item, String id) {
    return setString(item, EGG_ID, id);
  }

  public static ItemStack setMobId(ItemStack item, String id) {
    return setString(item, EGG_MOB_ID, id);
  }

  private static String getString(ItemStack item, NamespacedKey key) {
    Objects.requireNonNull(key, "key");
    if (item == null) {
      return null;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return null;
    }
    String raw = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim();
  }

  private static ItemStack setString(ItemStack item, NamespacedKey key, String value) {
    Objects.requireNonNull(item, "item");
    Objects.requireNonNull(key, "key");
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("MobItemMarkers.setString must be called on the primary thread");
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    if (value == null || value.isBlank()) {
      meta.getPersistentDataContainer().remove(key);
    } else {
      meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, Ids.normalize(value));
    }
    item.setItemMeta(meta);
    return item;
  }
}
