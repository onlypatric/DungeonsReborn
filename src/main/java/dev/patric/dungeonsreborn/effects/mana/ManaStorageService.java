package dev.patric.dungeonsreborn.effects.mana;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ManaStorageService {
  private static final int SCHEMA_VERSION = 2;
  private static final String ROOT_PLAYERS = "players";
  private static final String ROOT_RESOURCES = "resources";

  private final File file;
  private final Logger logger;
  private final Map<UUID, ManaSnapshot> cache = new HashMap<>();

  public ManaStorageService(JavaPlugin plugin) {
    Objects.requireNonNull(plugin, "plugin");
    this.file = new File(plugin.getDataFolder(), "mana.yml");
    this.logger = plugin.getLogger();
    load();
  }

  public Optional<ManaSnapshot> get(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    return Optional.ofNullable(cache.get(playerId));
  }

  public void set(UUID playerId, ManaSnapshot snapshot) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(snapshot, "snapshot");
    cache.put(playerId, snapshot);
  }

  public void remove(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    cache.remove(playerId);
  }

  public Map<UUID, ManaSnapshot> snapshotAll() {
    return Collections.unmodifiableMap(cache);
  }

  public void saveNow() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("schemaVersion", SCHEMA_VERSION);
    ConfigurationSection players = yaml.createSection(ROOT_PLAYERS);
    for (Map.Entry<UUID, ManaSnapshot> entry : cache.entrySet()) {
      ConfigurationSection section = players.createSection(entry.getKey().toString());
      ConfigurationSection resources = section.createSection(ROOT_RESOURCES);
      for (Map.Entry<String, ManaSnapshot.ResourceStateSnapshot> resource : entry.getValue().resources().entrySet()) {
        ConfigurationSection resourceSection = resources.createSection(resource.getKey());
        ManaSnapshot.ResourceStateSnapshot snapshot = resource.getValue();
        resourceSection.set("current", snapshot.current());
        resourceSection.set("baseMax", snapshot.baseMax());
        resourceSection.set("maxBonus", snapshot.maxBonus());
        resourceSection.set("regenBonus", snapshot.regenBonus());
        resourceSection.set("classMaxBonus", snapshot.classMaxBonus());
        resourceSection.set("classRegenBonus", snapshot.classRegenBonus());
      }
    }
    try {
      yaml.save(file);
    } catch (IOException ex) {
      logger.warning("[Mana] Unable to save mana.yml: " + ex.getMessage());
    }
  }

  private void load() {
    if (!file.exists()) {
      return;
    }
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection players = yaml.getConfigurationSection(ROOT_PLAYERS);
    if (players == null) {
      return;
    }
    for (String key : players.getKeys(false)) {
      try {
        UUID id = UUID.fromString(key);
        ConfigurationSection section = players.getConfigurationSection(key);
        if (section == null) {
          continue;
        }
        Map<String, ManaSnapshot.ResourceStateSnapshot> resources = new HashMap<>();
        ConfigurationSection resourcesSection = section.getConfigurationSection(ROOT_RESOURCES);
        if (resourcesSection != null) {
          for (String resourceId : resourcesSection.getKeys(false)) {
            ConfigurationSection resourceSection = resourcesSection.getConfigurationSection(resourceId);
            if (resourceSection == null) {
              continue;
            }
            resources.put(resourceId, new ManaSnapshot.ResourceStateSnapshot(
                resourceSection.getDouble("current", 0.0),
                resourceSection.getDouble("baseMax", 0.0),
                resourceSection.getDouble("maxBonus", 0.0),
                resourceSection.getDouble("regenBonus", 0.0),
                resourceSection.getDouble("classMaxBonus", 0.0),
                resourceSection.getDouble("classRegenBonus", 0.0)));
          }
        } else {
          resources.put(ManaProvider.DEFAULT_RESOURCE, new ManaSnapshot.ResourceStateSnapshot(
              section.getDouble("current", 0.0),
              section.getDouble("baseMax", 0.0),
              section.getDouble("maxBonus", 0.0),
              section.getDouble("regenBonus", 0.0),
              section.getDouble("classMaxBonus", 0.0),
              section.getDouble("classRegenBonus", 0.0)));
        }
        cache.put(id, new ManaSnapshot(resources));
      } catch (IllegalArgumentException ex) {
        logger.warning("[Mana] Invalid UUID in mana.yml: " + key);
      }
    }
  }
}
