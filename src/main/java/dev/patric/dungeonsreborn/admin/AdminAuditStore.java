package dev.patric.dungeonsreborn.admin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.DungeonsRebornPlugin;

public final class AdminAuditStore {
  public record Entry(String editor, long timestamp) {
  }

  private static volatile AdminAuditStore instance;

  private final File file;
  private final Map<String, Entry> entries = new ConcurrentHashMap<>();

  private AdminAuditStore(JavaPlugin plugin) {
    this.file = new File(plugin.getDataFolder(), "admin-audit.yml");
    load();
  }

  public static AdminAuditStore get() {
    AdminAuditStore current = instance;
    if (current != null) {
      return current;
    }
    synchronized (AdminAuditStore.class) {
      if (instance == null) {
        JavaPlugin plugin = JavaPlugin.getPlugin(DungeonsRebornPlugin.class);
        instance = new AdminAuditStore(plugin);
      }
      return instance;
    }
  }

  public Entry entry(String key) {
    if (key == null || key.isBlank()) {
      return null;
    }
    return entries.get(key);
  }

  public synchronized void record(String key, String editor) {
    if (key == null || key.isBlank()) {
      return;
    }
    String name = editor == null || editor.isBlank() ? "unknown" : editor.trim();
    entries.put(key, new Entry(name, System.currentTimeMillis()));
    save();
  }

  private void load() {
    if (!file.exists()) {
      return;
    }
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection entriesSec = cfg.getConfigurationSection("entries");
    if (entriesSec == null) {
      return;
    }
    for (String key : entriesSec.getKeys(false)) {
      ConfigurationSection node = entriesSec.getConfigurationSection(key);
      if (node == null) {
        continue;
      }
      String editor = node.getString("editor", "unknown");
      long timestamp = node.getLong("timestamp", 0L);
      entries.put(key, new Entry(editor, timestamp));
    }
  }

  private void save() {
    YamlConfiguration cfg = new YamlConfiguration();
    ConfigurationSection entriesSec = cfg.createSection("entries");
    for (Map.Entry<String, Entry> entry : entries.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank()) {
        continue;
      }
      Entry value = entry.getValue();
      ConfigurationSection node = entriesSec.createSection(entry.getKey());
      node.set("editor", value == null ? "unknown" : value.editor());
      node.set("timestamp", value == null ? 0L : value.timestamp());
    }
    try {
      cfg.save(file);
    } catch (IOException ex) {
      // Avoid crashing UI flows on audit failures.
    }
  }
}
