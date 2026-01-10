package dev.patric.dungeonsreborn.effects.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import dev.patric.dungeonsreborn.effects.Ids;

/**
 * Small helper for tagging items with persistent markers (PDC).
 */
public final class ItemMarkers {
  public static final NamespacedKey DEBUG_MARKER = new NamespacedKey("dungeonsreborn", "effects_debug_marker");
  public static final NamespacedKey RIGHT_CLICK_ABILITIES = new NamespacedKey("dungeonsreborn", "effects_right_click_abilities");
  public static final NamespacedKey LEFT_CLICK_ABILITIES = new NamespacedKey("dungeonsreborn", "effects_left_click_abilities");
  public static final NamespacedKey ITEM_ID = new NamespacedKey("dungeonsreborn", "effects_item_id");
  public static final NamespacedKey MANA_MAX_BONUS = new NamespacedKey("dungeonsreborn", "effects_mana_max_bonus");
  public static final NamespacedKey MANA_REGEN_BONUS = new NamespacedKey("dungeonsreborn", "effects_mana_regen_bonus");

  private ItemMarkers() {
  }

  public static boolean has(ItemStack item, NamespacedKey key) {
    Objects.requireNonNull(key, "key");
    if (item == null) {
      return false;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return false;
    }
    return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
  }

  public static ItemStack set(ItemStack item, NamespacedKey key, boolean enabled) {
    Objects.requireNonNull(item, "item");
    Objects.requireNonNull(key, "key");
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("ItemMarkers.set must be called on the primary thread");
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    if (enabled) {
      meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    } else {
      meta.getPersistentDataContainer().remove(key);
    }
    item.setItemMeta(meta);
    return item;
  }

  public static List<String> getStringList(ItemStack item, NamespacedKey key) {
    Objects.requireNonNull(key, "key");
    if (item == null) {
      return List.of();
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return List.of();
    }
    String raw = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    String[] parts = raw.split("\n");
    ArrayList<String> out = new ArrayList<>(parts.length);
    for (String p : parts) {
      if (p == null) {
        continue;
      }
      String s = p.trim();
      if (s.isEmpty()) {
        continue;
      }
      out.add(s);
    }
    return Collections.unmodifiableList(out);
  }

  public static ItemStack setStringList(ItemStack item, NamespacedKey key, List<String> values) {
    Objects.requireNonNull(item, "item");
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(values, "values");
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("ItemMarkers.setStringList must be called on the primary thread");
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String v : values) {
      if (v == null) {
        continue;
      }
      String s = v.trim();
      if (s.isEmpty()) {
        continue;
      }
      normalized.add(Ids.normalize(s));
    }
    if (normalized.isEmpty()) {
      meta.getPersistentDataContainer().remove(key);
    } else {
      meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, String.join("\n", normalized));
    }
    item.setItemMeta(meta);
    return item;
  }

  public static ItemStack addToStringList(ItemStack item, NamespacedKey key, String value) {
    Objects.requireNonNull(item, "item");
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
    List<String> existing = getStringList(item, key);
    ArrayList<String> next = new ArrayList<>(existing);
    next.add(value);
    return setStringList(item, key, next);
  }

  public static ItemStack removeFromStringList(ItemStack item, NamespacedKey key, String value) {
    Objects.requireNonNull(item, "item");
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
    String normalized;
    try {
      normalized = Ids.normalize(value);
    } catch (IllegalArgumentException ex) {
      return item;
    }
    List<String> existing = getStringList(item, key);
    if (existing.isEmpty()) {
      return item;
    }
    ArrayList<String> next = new ArrayList<>();
    for (String s : existing) {
      if (!s.equals(normalized)) {
        next.add(s);
      }
    }
    return setStringList(item, key, next);
  }

  public static String getItemId(ItemStack item) {
    if (item == null) {
      return null;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return null;
    }
    String raw = meta.getPersistentDataContainer().get(ITEM_ID, PersistentDataType.STRING);
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim();
  }

  public static ItemStack setItemId(ItemStack item, String id) {
    Objects.requireNonNull(item, "item");
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("ItemMarkers.setItemId must be called on the primary thread");
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    if (id == null || id.isBlank()) {
      meta.getPersistentDataContainer().remove(ITEM_ID);
    } else {
      meta.getPersistentDataContainer().set(ITEM_ID, PersistentDataType.STRING, Ids.normalize(id));
    }
    item.setItemMeta(meta);
    return item;
  }

  public static double getDouble(ItemStack item, NamespacedKey key) {
    Objects.requireNonNull(key, "key");
    if (item == null) {
      return 0.0;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return 0.0;
    }
    Double value = meta.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
    if (value == null || !Double.isFinite(value)) {
      return 0.0;
    }
    return value;
  }

  public static ItemStack setDouble(ItemStack item, NamespacedKey key, Double value) {
    Objects.requireNonNull(item, "item");
    Objects.requireNonNull(key, "key");
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("ItemMarkers.setDouble must be called on the primary thread");
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    if (value == null || !Double.isFinite(value) || Math.abs(value) < 1e-9) {
      meta.getPersistentDataContainer().remove(key);
    } else {
      meta.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, value);
    }
    item.setItemMeta(meta);
    return item;
  }

  public static double getManaMaxBonus(ItemStack item) {
    return getDouble(item, MANA_MAX_BONUS);
  }

  public static ItemStack setManaMaxBonus(ItemStack item, double bonus) {
    return setDouble(item, MANA_MAX_BONUS, bonus);
  }

  public static double getManaRegenBonus(ItemStack item) {
    return getDouble(item, MANA_REGEN_BONUS);
  }

  public static ItemStack setManaRegenBonus(ItemStack item, double bonus) {
    return setDouble(item, MANA_REGEN_BONUS, bonus);
  }
}
