package dev.patric.dungeonsreborn.mobs;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.logging.ServiceLogger;

public final class MobSpawnerBlockStore {
  public record Entry(String blockId, String spawnId, String mobId, String world, int x, int y, int z) {
  }

  private final File file;
  private final ServiceLogger logger;
  private final Map<String, Entry> entries = new HashMap<>();

  public MobSpawnerBlockStore(File dataFolder, ServiceLogger logger) {
    Objects.requireNonNull(dataFolder, "dataFolder");
    this.file = new File(dataFolder, "spawner_blocks.yml");
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
      if (world == null || world.isBlank()) {
        continue;
      }
      int x = node.getInt("x");
      int y = node.getInt("y");
      int z = node.getInt("z");
      String spawnId = node.getString("spawnId", null);
      String mobId = node.getString("mobId", null);
      String blockId = node.getString("blockId", null);
      entries.put(keyFor(world, x, y, z), new Entry(blockId, spawnId, mobId, world, x, y, z));
    }
  }

  public void save() {
    YamlConfiguration cfg = new YamlConfiguration();
    ConfigurationSection sec = cfg.createSection("blocks");
    for (Entry entry : entries.values()) {
      String key = keyFor(entry.world(), entry.x(), entry.y(), entry.z());
      ConfigurationSection node = sec.createSection(key);
      node.set("world", entry.world());
      node.set("x", entry.x());
      node.set("y", entry.y());
      node.set("z", entry.z());
      if (entry.spawnId() != null) {
        node.set("spawnId", entry.spawnId());
      }
      if (entry.mobId() != null) {
        node.set("mobId", entry.mobId());
      }
      if (entry.blockId() != null) {
        node.set("blockId", entry.blockId());
      }
    }
    try {
      file.getParentFile().mkdirs();
      cfg.save(file);
    } catch (Exception ex) {
      logger.warn("[Mobs] Failed to save spawner_blocks.yml: " + ex.getMessage());
    }
  }

  public Entry entry(Block block) {
    if (block == null) {
      return null;
    }
    return entries.get(keyFor(block.getWorld(), block.getX(), block.getY(), block.getZ()));
  }

  public void upsert(Block block, String blockId, String spawnId, String mobId) {
    if (block == null || block.getWorld() == null) {
      return;
    }
    String world = block.getWorld().getName();
    Entry entry = new Entry(
        normalizeOrNull(blockId),
        normalizeOrNull(spawnId),
        normalizeOrNull(mobId),
        world,
        block.getX(),
        block.getY(),
        block.getZ());
    entries.put(keyFor(world, block.getX(), block.getY(), block.getZ()), entry);
    save();
  }

  public void remove(Block block) {
    if (block == null || block.getWorld() == null) {
      return;
    }
    entries.remove(keyFor(block.getWorld(), block.getX(), block.getY(), block.getZ()));
    save();
  }

  public Entry removeBySpawnId(String spawnId) {
    if (spawnId == null || spawnId.isBlank()) {
      return null;
    }
    String normalized = Ids.normalize(spawnId);
    Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, Entry> entry = it.next();
      if (normalized.equals(entry.getValue().spawnId())) {
        it.remove();
        save();
        return entry.getValue();
      }
    }
    return null;
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
      if (block.getType() != org.bukkit.Material.SPAWNER) {
        removeKeys.add(keyFor(entry.world(), entry.x(), entry.y(), entry.z()));
        continue;
      }
      if (entry.spawnId() != null) {
        MobSpawnerMarkers.setSpawnerId(block, entry.spawnId());
      }
      if (entry.mobId() != null) {
        MobSpawnerMarkers.setSpawnerMobId(block, entry.mobId());
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

  private static String keyFor(World world, int x, int y, int z) {
    if (world == null) {
      return "unknown:" + x + ":" + y + ":" + z;
    }
    return keyFor(world.getName(), x, y, z);
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
