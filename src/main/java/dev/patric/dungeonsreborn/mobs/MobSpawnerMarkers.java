package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import dev.patric.dungeonsreborn.effects.Ids;

public final class MobSpawnerMarkers {
  public static final NamespacedKey SPAWNER_ID = new NamespacedKey("dungeonsreborn", "mob_spawner_id");
  public static final NamespacedKey SPAWNER_MOB_ID = new NamespacedKey("dungeonsreborn", "mob_spawner_mob_id");
  public static final NamespacedKey SPAWNER_BLOCK_ID = new NamespacedKey("dungeonsreborn", "mob_spawner_block_id");

  private MobSpawnerMarkers() {
  }

  public static String getSpawnerId(ItemStack item) {
    return getString(item, SPAWNER_ID);
  }

  public static String getSpawnerMobId(ItemStack item) {
    return getString(item, SPAWNER_MOB_ID);
  }

  public static ItemStack setSpawnerId(ItemStack item, String id) {
    return setString(item, SPAWNER_ID, id);
  }

  public static ItemStack setSpawnerMobId(ItemStack item, String id) {
    return setString(item, SPAWNER_MOB_ID, id);
  }

  public static String getSpawnerBlockId(ItemStack item) {
    return getString(item, SPAWNER_BLOCK_ID);
  }

  public static ItemStack setSpawnerBlockId(ItemStack item, String id) {
    return setString(item, SPAWNER_BLOCK_ID, id);
  }

  public static String getSpawnerId(Block block) {
    return getString(block, SPAWNER_ID);
  }

  public static String getSpawnerMobId(Block block) {
    return getString(block, SPAWNER_MOB_ID);
  }

  public static void setSpawnerId(Block block, String id) {
    setString(block, SPAWNER_ID, id);
  }

  public static void setSpawnerMobId(Block block, String id) {
    setString(block, SPAWNER_MOB_ID, id);
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
      throw new IllegalStateException("MobSpawnerMarkers.setString must be called on the primary thread");
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

  private static String getString(Block block, NamespacedKey key) {
    Objects.requireNonNull(key, "key");
    if (block == null) {
      return null;
    }
    BlockState state = block.getState();
    if (!(state instanceof TileState tile)) {
      return null;
    }
    PersistentDataContainer pdc = tile.getPersistentDataContainer();
    String raw = pdc.get(key, PersistentDataType.STRING);
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim();
  }

  private static void setString(Block block, NamespacedKey key, String value) {
    Objects.requireNonNull(key, "key");
    if (block == null) {
      return;
    }
    BlockState state = block.getState();
    if (!(state instanceof TileState tile)) {
      return;
    }
    PersistentDataContainer pdc = tile.getPersistentDataContainer();
    if (value == null || value.isBlank()) {
      pdc.remove(key);
    } else {
      pdc.set(key, PersistentDataType.STRING, Ids.normalize(value));
    }
    tile.update();
  }
}
