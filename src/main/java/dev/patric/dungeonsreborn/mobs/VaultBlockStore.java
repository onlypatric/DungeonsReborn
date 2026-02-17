package dev.patric.dungeonsreborn.mobs;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.logging.ServiceLogger;

public final class VaultBlockStore {
  public record Entry(String vaultId, String world, int x, int y, int z, String ownerId, long createdAtMillis) {
  }

  private final File file;
  private final ServiceLogger logger;
  private final Map<String, Entry> entries = new HashMap<>();

  public VaultBlockStore(File dataFolder, ServiceLogger logger) {
    Objects.requireNonNull(dataFolder, "dataFolder");
    this.file = new File(dataFolder, "vault_blocks.yml");
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  public void load() {
    entries.clear();
    if (!file.exists()) {
      return;
    }
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection sec = cfg.getConfigurationSection("blocks");
    if (sec == null) {
      return;
    }
    for (String key : sec.getKeys(false)) {
      ConfigurationSection node = sec.getConfigurationSection(key);
      if (node == null) {
        continue;
      }
      String world = node.getString("world", null);
      String vaultId = node.getString("vaultId", null);
      if (world == null || world.isBlank() || vaultId == null || vaultId.isBlank()) {
        continue;
      }
      entries.put(
          keyFor(world, node.getInt("x"), node.getInt("y"), node.getInt("z")),
          new Entry(
              Ids.normalize(vaultId),
              world,
              node.getInt("x"),
              node.getInt("y"),
              node.getInt("z"),
              normalizeOrNull(node.getString("ownerId", null)),
              node.getLong("createdAtMillis", 0L)));
    }
  }

  public void save() {
    YamlConfiguration cfg = new YamlConfiguration();
    ConfigurationSection sec = cfg.createSection("blocks");
    for (Entry entry : entries.values()) {
      String key = keyFor(entry.world(), entry.x(), entry.y(), entry.z());
      ConfigurationSection node = sec.createSection(key);
      node.set("vaultId", entry.vaultId());
      node.set("world", entry.world());
      node.set("x", entry.x());
      node.set("y", entry.y());
      node.set("z", entry.z());
      if (entry.ownerId() != null) {
        node.set("ownerId", entry.ownerId());
      }
      if (entry.createdAtMillis() > 0L) {
        node.set("createdAtMillis", entry.createdAtMillis());
      }
    }
    try {
      file.getParentFile().mkdirs();
      cfg.save(file);
    } catch (Exception ex) {
      logger.warn("[Mobs] Failed to save vault_blocks.yml: " + ex.getMessage());
    }
  }

  public void upsert(Block block, String vaultId, String ownerId) {
    if (block == null || block.getWorld() == null || vaultId == null || vaultId.isBlank()) {
      return;
    }
    String world = block.getWorld().getName();
    entries.put(
        keyFor(world, block.getX(), block.getY(), block.getZ()),
        new Entry(
            Ids.normalize(vaultId),
            world,
            block.getX(),
            block.getY(),
            block.getZ(),
            normalizeOrNull(ownerId),
            System.currentTimeMillis()));
    save();
  }

  public Entry entry(Block block) {
    if (block == null || block.getWorld() == null) {
      return null;
    }
    return entries.get(keyFor(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
  }

  public void remove(Block block) {
    if (block == null || block.getWorld() == null) {
      return;
    }
    entries.remove(keyFor(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
    save();
  }

  public List<Entry> entries() {
    return new ArrayList<>(entries.values());
  }

  public int rehydrateMarkers() {
    int restored = 0;
    List<String> removeKeys = new ArrayList<>();
    for (Entry entry : entries.values()) {
      World world = Bukkit.getWorld(entry.world());
      if (world == null) {
        continue;
      }
      Block block = world.getBlockAt(entry.x(), entry.y(), entry.z());
      if (block.getType() != Material.VAULT) {
        removeKeys.add(keyFor(entry.world(), entry.x(), entry.y(), entry.z()));
        continue;
      }
      MobSpawnerMarkers.setVaultId(block, entry.vaultId());
      if (entry.ownerId() != null) {
        try {
          MobSpawnerMarkers.setVaultOwner(block, java.util.UUID.fromString(entry.ownerId()));
        } catch (IllegalArgumentException ignored) {
        }
      }
      restored++;
    }
    for (String key : removeKeys) {
      entries.remove(key);
    }
    if (!removeKeys.isEmpty()) {
      save();
    }
    return restored;
  }

  private static String keyFor(String world, int x, int y, int z) {
    return world.toLowerCase(Locale.ROOT) + ":" + x + ":" + y + ":" + z;
  }

  private static String normalizeOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return Ids.normalize(value);
  }
}
