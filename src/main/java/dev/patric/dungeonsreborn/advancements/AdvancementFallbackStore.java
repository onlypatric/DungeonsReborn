package dev.patric.dungeonsreborn.advancements;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

final class AdvancementFallbackStore {
  private final JavaPlugin plugin;
  private final File file;
  private final Map<UUID, Map<String, Integer>> progress = new HashMap<>();

  AdvancementFallbackStore(JavaPlugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.file = new File(plugin.getDataFolder(), "advancement_progress.yml");
  }

  void load() {
    progress.clear();
    if (!file.exists()) {
      return;
    }
    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection players = config.getConfigurationSection("players");
    if (players == null) {
      return;
    }
    for (String playerId : players.getKeys(false)) {
      UUID uuid = parseUuid(playerId);
      if (uuid == null) {
        continue;
      }
      ConfigurationSection playerSection = players.getConfigurationSection(playerId);
      if (playerSection == null) {
        continue;
      }
      ConfigurationSection advSection = playerSection.getConfigurationSection("advancements");
      if (advSection == null) {
        continue;
      }
      Map<String, Integer> map = new HashMap<>();
      for (String key : advSection.getKeys(false)) {
        map.put(key, advSection.getInt(key, 0));
      }
      if (!map.isEmpty()) {
        progress.put(uuid, map);
      }
    }
  }

  int getProgress(UUID playerId, String advancementId) {
    if (playerId == null || advancementId == null) {
      return 0;
    }
    return progress.getOrDefault(playerId, Map.of()).getOrDefault(advancementId, 0);
  }

  boolean isGranted(UUID playerId, String advancementId, int required) {
    if (required <= 0) {
      return true;
    }
    return getProgress(playerId, advancementId) >= required;
  }

  ProgressUpdate addProgress(UUID playerId, String advancementId, int amount) {
    if (playerId == null || advancementId == null || advancementId.isBlank() || amount <= 0) {
      return new ProgressUpdate(0, 0);
    }
    Map<String, Integer> map = progress.computeIfAbsent(playerId, id -> new HashMap<>());
    int current = map.getOrDefault(advancementId, 0);
    int next = current + amount;
    map.put(advancementId, next);
    return new ProgressUpdate(current, next);
  }

  void saveAsync() {
    Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveNow);
  }

  private void saveNow() {
    YamlConfiguration config = new YamlConfiguration();
    ConfigurationSection players = config.createSection("players");
    for (Map.Entry<UUID, Map<String, Integer>> entry : progress.entrySet()) {
      ConfigurationSection playerSection = players.createSection(entry.getKey().toString());
      ConfigurationSection advSection = playerSection.createSection("advancements");
      for (Map.Entry<String, Integer> adv : entry.getValue().entrySet()) {
        advSection.set(adv.getKey(), adv.getValue());
      }
    }
    try {
      config.save(file);
    } catch (Exception ex) {
      plugin.getLogger().warning("[Advancements] Failed to save fallback progress: " + ex.getMessage());
    }
  }

  private static UUID parseUuid(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  record ProgressUpdate(int previous, int current) {
  }
}
