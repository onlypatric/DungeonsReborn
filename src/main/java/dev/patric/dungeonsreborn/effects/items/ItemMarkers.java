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
  public static final NamespacedKey SHIFT_RIGHT_CLICK_ABILITIES = new NamespacedKey("dungeonsreborn", "effects_shift_right_click_abilities");
  public static final NamespacedKey SHIFT_LEFT_CLICK_ABILITIES = new NamespacedKey("dungeonsreborn", "effects_shift_left_click_abilities");
  public static final NamespacedKey PASSIVE_ABILITIES = new NamespacedKey("dungeonsreborn", "effects_passive_abilities");
  public static final NamespacedKey ITEM_ID = new NamespacedKey("dungeonsreborn", "effects_item_id");
  public static final NamespacedKey ITEM_VERSION = new NamespacedKey("dungeonsreborn", "effects_item_version");
  public static final NamespacedKey ITEM_STATS = new NamespacedKey("dungeonsreborn", "effects_item_stats");
  public static final NamespacedKey ITEM_AFFIXES = new NamespacedKey("dungeonsreborn", "effects_item_affixes");
  public static final NamespacedKey ITEM_TIER = new NamespacedKey("dungeonsreborn", "effects_item_tier");
  public static final NamespacedKey ITEM_RARITY = new NamespacedKey("dungeonsreborn", "effects_item_rarity");
  public static final NamespacedKey ITEM_TAGS = new NamespacedKey("dungeonsreborn", "effects_item_tags");
  public static final NamespacedKey ITEM_CATEGORY = new NamespacedKey("dungeonsreborn", "effects_item_category");
  public static final NamespacedKey MANA_MAX_BONUS = new NamespacedKey("dungeonsreborn", "effects_mana_max_bonus");
  public static final NamespacedKey MANA_REGEN_BONUS = new NamespacedKey("dungeonsreborn", "effects_mana_regen_bonus");
  public static final NamespacedKey MANA_REGEN_MULTIPLIER = new NamespacedKey("dungeonsreborn", "effects_mana_regen_multiplier");
  public static final NamespacedKey MANA_REGEN_PERCENT = new NamespacedKey("dungeonsreborn", "effects_mana_regen_percent");
  public static final NamespacedKey MANA_REGEN_MODE = new NamespacedKey("dungeonsreborn", "effects_mana_regen_mode");
  public static final NamespacedKey MANA_COST_MULTIPLIER = new NamespacedKey("dungeonsreborn", "effects_mana_cost_multiplier");
  public static final NamespacedKey MANA_COST_ADD = new NamespacedKey("dungeonsreborn", "effects_mana_cost_add");
  public static final NamespacedKey CONSUME_MODE = new NamespacedKey("dungeonsreborn", "effects_item_consume_mode");
  public static final NamespacedKey CONSUME_AMOUNT = new NamespacedKey("dungeonsreborn", "effects_item_consume_amount");
  public static final NamespacedKey UPGRADE_ID = new NamespacedKey("dungeonsreborn", "effects_upgrade_id");
  public static final NamespacedKey UPGRADE_RECORDS = new NamespacedKey("dungeonsreborn", "effects_upgrade_records");
  public static final NamespacedKey UPGRADE_MODIFIERS = new NamespacedKey("dungeonsreborn", "effects_upgrade_modifiers");
  public static final NamespacedKey UPGRADE_SECONDARY_ABILITIES = new NamespacedKey("dungeonsreborn", "effects_upgrade_secondary_abilities");
  public static final NamespacedKey UPGRADE_STATUS_EFFECTS = new NamespacedKey("dungeonsreborn", "effects_upgrade_status_effects");
  public static final NamespacedKey UPGRADE_SPELL_BINDINGS = new NamespacedKey("dungeonsreborn", "effects_upgrade_spell_bindings");
  public static final NamespacedKey UPGRADE_LORE_COMPACT = new NamespacedKey("dungeonsreborn", "effects_upgrade_lore_compact");
  public static final NamespacedKey UPGRADE_SLOT_LIMITS = new NamespacedKey("dungeonsreborn", "effects_upgrade_slot_limits");
  public static final NamespacedKey UPGRADE_MAX_COUNT = new NamespacedKey("dungeonsreborn", "effects_upgrade_max_count");
  public static final NamespacedKey UPGRADE_TIER_BUDGET = new NamespacedKey("dungeonsreborn", "effects_upgrade_tier_budget");
  public static final NamespacedKey UPGRADE_SCALE = new NamespacedKey("dungeonsreborn", "effects_upgrade_scale");
  public static final NamespacedKey ITEM_INSTANCE_ID = new NamespacedKey("dungeonsreborn", "effects_item_instance_id");

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

  public static List<String> getRawStringList(ItemStack item, NamespacedKey key) {
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

  public static ItemStack setRawStringList(ItemStack item, NamespacedKey key, List<String> values) {
    Objects.requireNonNull(item, "item");
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(values, "values");
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("ItemMarkers.setRawStringList must be called on the primary thread");
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
      normalized.add(s);
    }
    if (normalized.isEmpty()) {
      meta.getPersistentDataContainer().remove(key);
    } else {
      meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, String.join("\n", normalized));
    }
    item.setItemMeta(meta);
    return item;
  }

  public static List<String> getUpgradeStatusEffects(ItemStack item) {
    return getRawStringList(item, UPGRADE_STATUS_EFFECTS);
  }

  public static ItemStack setUpgradeStatusEffects(ItemStack item, List<String> records) {
    return setRawStringList(item, UPGRADE_STATUS_EFFECTS, records);
  }

  public static List<String> getUpgradeSpellBindings(ItemStack item) {
    return getRawStringList(item, UPGRADE_SPELL_BINDINGS);
  }

  public static ItemStack setUpgradeSpellBindings(ItemStack item, List<String> records) {
    return setRawStringList(item, UPGRADE_SPELL_BINDINGS, records);
  }

  public static String getItemInstanceId(ItemStack item) {
    return getString(item, ITEM_INSTANCE_ID);
  }

  public static String getOrCreateItemInstanceId(ItemStack item) {
    if (item == null) {
      return "unknown";
    }
    String existing = getString(item, ITEM_INSTANCE_ID);
    if (existing != null && !existing.isBlank()) {
      return existing;
    }
    String created = java.util.UUID.randomUUID().toString();
    setString(item, ITEM_INSTANCE_ID, created);
    return created;
  }

  public static String getItemTier(ItemStack item) {
    return getString(item, ITEM_TIER);
  }

  public static ItemStack setItemTier(ItemStack item, String tier) {
    return setString(item, ITEM_TIER, tier);
  }

  public static String getItemRarity(ItemStack item) {
    return getString(item, ITEM_RARITY);
  }

  public static ItemStack setItemRarity(ItemStack item, String rarity) {
    return setString(item, ITEM_RARITY, rarity);
  }

  public static List<String> getItemTags(ItemStack item) {
    return getStringList(item, ITEM_TAGS);
  }

  public static ItemStack setItemTags(ItemStack item, List<String> tags) {
    return setStringList(item, ITEM_TAGS, tags);
  }

  public static String getItemCategory(ItemStack item) {
    return getString(item, ITEM_CATEGORY);
  }

  public static ItemStack setItemCategory(ItemStack item, String category) {
    return setString(item, ITEM_CATEGORY, category);
  }

  public static java.util.Map<String, Integer> getUpgradeSlots(ItemStack item) {
    List<String> raw = getRawStringList(item, UPGRADE_SLOT_LIMITS);
    if (raw.isEmpty()) {
      return java.util.Map.of();
    }
    java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
    for (String entry : raw) {
      if (entry == null || entry.isBlank()) {
        continue;
      }
      String trimmed = entry.trim();
      int split = trimmed.indexOf('=');
      if (split < 0) {
        split = trimmed.indexOf(':');
      }
      if (split <= 0 || split >= trimmed.length() - 1) {
        continue;
      }
      String type = trimmed.substring(0, split).trim();
      String countRaw = trimmed.substring(split + 1).trim();
      if (type.isEmpty()) {
        continue;
      }
      try {
        int count = Integer.parseInt(countRaw);
        if (count >= 0) {
          out.put(type, count);
        }
      } catch (NumberFormatException ignored) {
      }
    }
    return java.util.Collections.unmodifiableMap(out);
  }

  public static ItemStack setUpgradeSlots(ItemStack item, java.util.Map<String, Integer> slots) {
    Objects.requireNonNull(item, "item");
    Objects.requireNonNull(slots, "slots");
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("ItemMarkers.setUpgradeSlots must be called on the primary thread");
    }
    if (slots.isEmpty()) {
      return setRawStringList(item, UPGRADE_SLOT_LIMITS, List.of());
    }
    java.util.List<String> out = new java.util.ArrayList<>();
    for (var entry : slots.entrySet()) {
      if (entry.getKey() == null) {
        continue;
      }
      String key = entry.getKey().trim();
      if (key.isEmpty()) {
        continue;
      }
      int count = entry.getValue() == null ? 0 : entry.getValue();
      if (count < 0) {
        continue;
      }
      out.add(key + "=" + count);
    }
    return setRawStringList(item, UPGRADE_SLOT_LIMITS, out);
  }

  public static int getUpgradeMaxCount(ItemStack item) {
    return getInt(item, UPGRADE_MAX_COUNT);
  }

  public static ItemStack setUpgradeMaxCount(ItemStack item, int max) {
    return setInt(item, UPGRADE_MAX_COUNT, max);
  }

  public static int getUpgradeTierBudget(ItemStack item) {
    return getInt(item, UPGRADE_TIER_BUDGET);
  }

  public static ItemStack setUpgradeTierBudget(ItemStack item, int max) {
    return setInt(item, UPGRADE_TIER_BUDGET, max);
  }

  public static double getUpgradeScale(ItemStack item) {
    double value = getDouble(item, UPGRADE_SCALE);
    return value <= 0.0 ? 1.0 : value;
  }

  public static ItemStack setUpgradeScale(ItemStack item, double scale) {
    if (scale <= 0.0) {
      return setDouble(item, UPGRADE_SCALE, null);
    }
    return setDouble(item, UPGRADE_SCALE, scale);
  }

  private static int getInt(ItemStack item, NamespacedKey key) {
    Objects.requireNonNull(key, "key");
    if (item == null) {
      return 0;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return 0;
    }
    Integer value = meta.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
    return value == null ? 0 : value;
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
    String value = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    if (value == null || value.isBlank()) {
      return null;
    }
    return value;
  }

  private static ItemStack setString(ItemStack item, NamespacedKey key, String value) {
    Objects.requireNonNull(item, "item");
    Objects.requireNonNull(key, "key");
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("ItemMarkers.setString must be called on the primary thread");
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    if (value == null || value.isBlank()) {
      meta.getPersistentDataContainer().remove(key);
    } else {
      meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
    }
    item.setItemMeta(meta);
    return item;
  }

  private static ItemStack setInt(ItemStack item, NamespacedKey key, int value) {
    Objects.requireNonNull(item, "item");
    Objects.requireNonNull(key, "key");
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("ItemMarkers.setInt must be called on the primary thread");
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    if (value <= 0) {
      meta.getPersistentDataContainer().remove(key);
    } else {
      meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
    }
    item.setItemMeta(meta);
    return item;
  }

  public static boolean isUpgradeLoreCompact(ItemStack item) {
    if (item == null) {
      return false;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return false;
    }
    return meta.getPersistentDataContainer().has(UPGRADE_LORE_COMPACT, PersistentDataType.BYTE);
  }

  public static ItemStack setUpgradeLoreCompact(ItemStack item, boolean compact) {
    Objects.requireNonNull(item, "item");
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("ItemMarkers.setUpgradeLoreCompact must be called on the primary thread");
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    if (compact) {
      meta.getPersistentDataContainer().set(UPGRADE_LORE_COMPACT, PersistentDataType.BYTE, (byte) 1);
    } else {
      meta.getPersistentDataContainer().remove(UPGRADE_LORE_COMPACT);
    }
    item.setItemMeta(meta);
    return item;
  }

  public static java.util.Map<String, Double> getUpgradeModifiers(ItemStack item) {
    if (item == null) {
      return java.util.Map.of();
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return java.util.Map.of();
    }
    String raw = meta.getPersistentDataContainer().get(UPGRADE_MODIFIERS, PersistentDataType.STRING);
    if (raw == null || raw.isBlank()) {
      return java.util.Map.of();
    }
    java.util.LinkedHashMap<String, Double> out = new java.util.LinkedHashMap<>();
    for (String line : raw.split("\n")) {
      if (line == null) {
        continue;
      }
      String trimmed = line.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      int eq = trimmed.indexOf('=');
      if (eq <= 0 || eq >= trimmed.length() - 1) {
        continue;
      }
      String key = trimmed.substring(0, eq).trim();
      String value = trimmed.substring(eq + 1).trim();
      if (key.isEmpty() || value.isEmpty()) {
        continue;
      }
      try {
        out.put(key, Double.parseDouble(value));
      } catch (NumberFormatException ignored) {
      }
    }
    return java.util.Collections.unmodifiableMap(out);
  }

  public static ItemStack setUpgradeModifiers(ItemStack item, java.util.Map<String, Double> modifiers) {
    Objects.requireNonNull(item, "item");
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("ItemMarkers.setUpgradeModifiers must be called on the primary thread");
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    if (modifiers == null || modifiers.isEmpty()) {
      meta.getPersistentDataContainer().remove(UPGRADE_MODIFIERS);
    } else {
      java.util.List<String> lines = new java.util.ArrayList<>(modifiers.size());
      for (var entry : modifiers.entrySet()) {
        String key = entry.getKey();
        Double value = entry.getValue();
        if (key == null || key.isBlank() || value == null || !Double.isFinite(value)) {
          continue;
        }
        lines.add(key.trim() + "=" + value);
      }
      if (lines.isEmpty()) {
        meta.getPersistentDataContainer().remove(UPGRADE_MODIFIERS);
      } else {
        meta.getPersistentDataContainer().set(UPGRADE_MODIFIERS, PersistentDataType.STRING, String.join("\n", lines));
      }
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

  public static int getItemVersion(ItemStack item) {
    if (item == null) {
      return 0;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return 0;
    }
    Integer raw = meta.getPersistentDataContainer().get(ITEM_VERSION, PersistentDataType.INTEGER);
    if (raw == null || raw < 0) {
      return 0;
    }
    return raw;
  }

  public static ItemStack setItemVersion(ItemStack item, Integer version) {
    Objects.requireNonNull(item, "item");
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("ItemMarkers.setItemVersion must be called on the primary thread");
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    if (version == null || version < 0) {
      meta.getPersistentDataContainer().remove(ITEM_VERSION);
    } else {
      meta.getPersistentDataContainer().set(ITEM_VERSION, PersistentDataType.INTEGER, version);
    }
    item.setItemMeta(meta);
    return item;
  }

  public static String getUpgradeId(ItemStack item) {
    if (item == null) {
      return null;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return null;
    }
    String raw = meta.getPersistentDataContainer().get(UPGRADE_ID, PersistentDataType.STRING);
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim();
  }

  public static ItemStack setUpgradeId(ItemStack item, String id) {
    Objects.requireNonNull(item, "item");
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("ItemMarkers.setUpgradeId must be called on the primary thread");
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    if (id == null || id.isBlank()) {
      meta.getPersistentDataContainer().remove(UPGRADE_ID);
    } else {
      meta.getPersistentDataContainer().set(UPGRADE_ID, PersistentDataType.STRING, Ids.normalize(id));
    }
    item.setItemMeta(meta);
    return item;
  }

  public static List<String> getUpgradeRecords(ItemStack item) {
    return getStringList(item, UPGRADE_RECORDS);
  }

  public static ItemStack setUpgradeRecords(ItemStack item, List<String> values) {
    return setStringList(item, UPGRADE_RECORDS, values);
  }

  public static ItemStack addUpgradeRecord(ItemStack item, String record) {
    return addToStringList(item, UPGRADE_RECORDS, record);
  }

  public static java.util.Map<String, Double> getItemStats(ItemStack item) {
    return getDoubleMap(item, ITEM_STATS);
  }

  public static ItemStack setItemStats(ItemStack item, java.util.Map<String, Double> stats) {
    return setDoubleMap(item, ITEM_STATS, stats);
  }

  public static List<String> getItemAffixes(ItemStack item) {
    return getStringList(item, ITEM_AFFIXES);
  }

  public static ItemStack setItemAffixes(ItemStack item, List<String> affixes) {
    return setStringList(item, ITEM_AFFIXES, affixes == null ? List.of() : affixes);
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

  public static double getManaRegenMultiplier(ItemStack item) {
    return getDouble(item, MANA_REGEN_MULTIPLIER);
  }

  public static ItemStack setManaRegenMultiplier(ItemStack item, double bonus) {
    return setDouble(item, MANA_REGEN_MULTIPLIER, bonus);
  }

  public static double getManaRegenPercent(ItemStack item) {
    return getDouble(item, MANA_REGEN_PERCENT);
  }

  public static ItemStack setManaRegenPercent(ItemStack item, double bonus) {
    return setDouble(item, MANA_REGEN_PERCENT, bonus);
  }

  public static String getManaRegenMode(ItemStack item) {
    String value = getString(item, MANA_REGEN_MODE);
    return value == null || value.isBlank() ? null : value;
  }

  public static ItemStack setManaRegenMode(ItemStack item, String mode) {
    String value = mode == null ? null : mode.trim().toLowerCase(java.util.Locale.ROOT);
    return setString(item, MANA_REGEN_MODE, value);
  }

  public static double getManaCostMultiplier(ItemStack item) {
    return getDouble(item, MANA_COST_MULTIPLIER);
  }

  public static ItemStack setManaCostMultiplier(ItemStack item, double bonus) {
    return setDouble(item, MANA_COST_MULTIPLIER, bonus);
  }

  public static double getManaCostAdd(ItemStack item) {
    return getDouble(item, MANA_COST_ADD);
  }

  public static ItemStack setManaCostAdd(ItemStack item, double bonus) {
    return setDouble(item, MANA_COST_ADD, bonus);
  }

  public static ItemConsumeMode getConsumeMode(ItemStack item) {
    if (item == null) {
      return ItemConsumeMode.NONE;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return ItemConsumeMode.NONE;
    }
    String raw = meta.getPersistentDataContainer().get(CONSUME_MODE, PersistentDataType.STRING);
    if (raw == null || raw.isBlank()) {
      return ItemConsumeMode.NONE;
    }
    return ItemConsumeMode.parse(raw);
  }

  public static ItemStack setConsumeMode(ItemStack item, ItemConsumeMode mode) {
    Objects.requireNonNull(item, "item");
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("ItemMarkers.setConsumeMode must be called on the primary thread");
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    if (mode == null || mode == ItemConsumeMode.NONE) {
      meta.getPersistentDataContainer().remove(CONSUME_MODE);
    } else {
      meta.getPersistentDataContainer().set(CONSUME_MODE, PersistentDataType.STRING, mode.name().toLowerCase(java.util.Locale.ROOT));
    }
    item.setItemMeta(meta);
    return item;
  }

  public static int getConsumeAmount(ItemStack item) {
    if (item == null) {
      return 0;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return 0;
    }
    Integer value = meta.getPersistentDataContainer().get(CONSUME_AMOUNT, PersistentDataType.INTEGER);
    if (value == null || value <= 0) {
      return 0;
    }
    return value;
  }

  public static ItemStack setConsumeAmount(ItemStack item, int amount) {
    Objects.requireNonNull(item, "item");
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("ItemMarkers.setConsumeAmount must be called on the primary thread");
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    if (amount <= 0) {
      meta.getPersistentDataContainer().remove(CONSUME_AMOUNT);
    } else {
      meta.getPersistentDataContainer().set(CONSUME_AMOUNT, PersistentDataType.INTEGER, amount);
    }
    item.setItemMeta(meta);
    return item;
  }

  private static java.util.Map<String, Double> getDoubleMap(ItemStack item, NamespacedKey key) {
    Objects.requireNonNull(key, "key");
    if (item == null) {
      return java.util.Map.of();
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return java.util.Map.of();
    }
    String raw = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    if (raw == null || raw.isBlank()) {
      return java.util.Map.of();
    }
    java.util.LinkedHashMap<String, Double> out = new java.util.LinkedHashMap<>();
    for (String line : raw.split("\n")) {
      if (line == null) {
        continue;
      }
      String trimmed = line.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      int eq = trimmed.indexOf('=');
      if (eq <= 0 || eq >= trimmed.length() - 1) {
        continue;
      }
      String stat = trimmed.substring(0, eq).trim();
      String value = trimmed.substring(eq + 1).trim();
      if (stat.isEmpty() || value.isEmpty()) {
        continue;
      }
      try {
        out.put(stat, Double.parseDouble(value));
      } catch (NumberFormatException ignored) {
      }
    }
    return java.util.Collections.unmodifiableMap(out);
  }

  private static ItemStack setDoubleMap(ItemStack item, NamespacedKey key, java.util.Map<String, Double> values) {
    Objects.requireNonNull(item, "item");
    Objects.requireNonNull(key, "key");
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("ItemMarkers.setDoubleMap must be called on the primary thread");
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    if (values == null || values.isEmpty()) {
      meta.getPersistentDataContainer().remove(key);
    } else {
      java.util.List<String> lines = new java.util.ArrayList<>(values.size());
      for (var entry : values.entrySet()) {
        String stat = entry.getKey();
        Double value = entry.getValue();
        if (stat == null || stat.isBlank() || value == null || !Double.isFinite(value)) {
          continue;
        }
        lines.add(stat.trim() + "=" + value);
      }
      if (lines.isEmpty()) {
        meta.getPersistentDataContainer().remove(key);
      } else {
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, String.join("\n", lines));
      }
    }
    item.setItemMeta(meta);
    return item;
  }
}
