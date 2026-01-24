package dev.patric.dungeonsreborn.mobs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import dev.patric.dungeonsreborn.logging.ServiceLogger;

public final class MobPersistenceStore {
  private final File file;
  private final ServiceLogger logger;

  public MobPersistenceStore(File dataFolder, ServiceLogger logger) {
    Objects.requireNonNull(dataFolder, "dataFolder");
    this.file = new File(dataFolder, "mobs_persistent.yml");
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  public List<MobRegistry.MobSnapshot> load() {
    if (!file.exists()) {
      return List.of();
    }
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection sec = cfg.getConfigurationSection("mobs");
    if (sec == null) {
      return List.of();
    }
    List<MobRegistry.MobSnapshot> out = new ArrayList<>();
    for (String key : sec.getKeys(false)) {
      ConfigurationSection node = sec.getConfigurationSection(key);
      if (node == null) {
        continue;
      }
      String mobId = node.getString("mobId", null);
      String world = node.getString("world", null);
      if (mobId == null || mobId.isBlank() || world == null || world.isBlank()) {
        continue;
      }
      String variantId = node.getString("variantId", null);
      String traitId = node.getString("traitId", null);
      UUID ownerId = parseUuid(node.getString("ownerId", null));
      double x = node.getDouble("x");
      double y = node.getDouble("y");
      double z = node.getDouble("z");
      double health = node.getDouble("health", 1.0);
      double maxHealth = node.getDouble("maxHealth", health);
      out.add(new MobRegistry.MobSnapshot(null, mobId, variantId, traitId, ownerId, world, x, y, z, health, maxHealth));
    }
    return out;
  }

  public void save(List<MobRegistry.MobSnapshot> snapshots) {
    YamlConfiguration cfg = new YamlConfiguration();
    ConfigurationSection sec = cfg.createSection("mobs");
    int index = 0;
    for (MobRegistry.MobSnapshot snapshot : snapshots) {
      if (snapshot == null || snapshot.mobId() == null || snapshot.world() == null) {
        continue;
      }
      String key = "mob_" + index++;
      ConfigurationSection node = sec.createSection(key);
      node.set("mobId", snapshot.mobId());
      node.set("variantId", snapshot.variantId());
      node.set("traitId", snapshot.traitId());
      if (snapshot.ownerId() != null) {
        node.set("ownerId", snapshot.ownerId().toString());
      }
      node.set("world", snapshot.world());
      node.set("x", snapshot.x());
      node.set("y", snapshot.y());
      node.set("z", snapshot.z());
      node.set("health", snapshot.health());
      node.set("maxHealth", snapshot.maxHealth());
    }
    try {
      file.getParentFile().mkdirs();
      cfg.save(file);
    } catch (Exception ex) {
      logger.warn("[Mobs] Failed to save mobs_persistent.yml: " + ex.getMessage());
    }
  }

  private static UUID parseUuid(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
