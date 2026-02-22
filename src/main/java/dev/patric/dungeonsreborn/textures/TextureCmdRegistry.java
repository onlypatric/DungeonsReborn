package dev.patric.dungeonsreborn.textures;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class TextureCmdRegistry {
  private final File file;
  private final int registryStart;
  private final Map<String, Integer> modelToCmd = new LinkedHashMap<>();

  public TextureCmdRegistry(File file, int registryStart) {
    this.file = file;
    this.registryStart = Math.max(1, registryStart);
  }

  public synchronized void load() {
    modelToCmd.clear();
    if (!file.exists()) {
      return;
    }
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection mappings = cfg.getConfigurationSection("mappings");
    if (mappings == null) {
      return;
    }
    for (String key : mappings.getKeys(false)) {
      int cmd = mappings.getInt(key, -1);
      if (cmd > 0) {
        modelToCmd.put(key, cmd);
      }
    }
  }

  public synchronized int assign(String namespacedModelKey) {
    Integer existing = modelToCmd.get(namespacedModelKey);
    if (existing != null) {
      return existing;
    }
    int next = nextId();
    modelToCmd.put(namespacedModelKey, next);
    save();
    return next;
  }

  public synchronized int size() {
    return modelToCmd.size();
  }

  public synchronized Map<String, Integer> snapshot() {
    return Map.copyOf(modelToCmd);
  }

  private int nextId() {
    int max = registryStart - 1;
    for (int value : modelToCmd.values()) {
      if (value > max) {
        max = value;
      }
    }
    return Math.max(registryStart, max + 1);
  }

  private void save() {
    YamlConfiguration cfg = new YamlConfiguration();
    cfg.set("registryStart", registryStart);
    for (Map.Entry<String, Integer> entry : modelToCmd.entrySet()) {
      cfg.set("mappings." + entry.getKey(), entry.getValue());
    }
    try {
      File parent = file.getParentFile();
      if (parent != null && !parent.exists()) {
        parent.mkdirs();
      }
      cfg.save(file);
    } catch (IOException ignored) {
    }
  }
}
