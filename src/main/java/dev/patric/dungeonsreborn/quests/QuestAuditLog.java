package dev.patric.dungeonsreborn.quests;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.DungeonsRebornPlugin;

public final class QuestAuditLog {
  public record Entry(String type, String questId, String player, long timestamp) {
  }

  private static final int MAX_ENTRIES = 2000;
  private static volatile QuestAuditLog instance;

  private final File file;
  private final List<Entry> entries = new CopyOnWriteArrayList<>();

  private QuestAuditLog(JavaPlugin plugin) {
    this.file = new File(plugin.getDataFolder(), "quest-audit.yml");
    load();
  }

  public static QuestAuditLog get() {
    QuestAuditLog current = instance;
    if (current != null) {
      return current;
    }
    synchronized (QuestAuditLog.class) {
      if (instance == null) {
        JavaPlugin plugin = JavaPlugin.getPlugin(DungeonsRebornPlugin.class);
        instance = new QuestAuditLog(plugin);
      }
      return instance;
    }
  }

  public List<Entry> entries() {
    return List.copyOf(entries);
  }

  public synchronized void record(String type, String questId, String player) {
    if (type == null || type.isBlank() || questId == null || questId.isBlank()) {
      return;
    }
    String normalizedType = type.trim().toLowerCase();
    String normalizedQuest = questId.trim();
    String normalizedPlayer = player == null || player.isBlank() ? "unknown" : player.trim();
    entries.add(new Entry(normalizedType, normalizedQuest, normalizedPlayer, System.currentTimeMillis()));
    if (entries.size() > MAX_ENTRIES) {
      int trim = entries.size() - MAX_ENTRIES;
      if (trim > 0) {
        entries.subList(0, trim).clear();
      }
    }
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
    List<Entry> loaded = new ArrayList<>();
    for (String key : entriesSec.getKeys(false)) {
      ConfigurationSection node = entriesSec.getConfigurationSection(key);
      if (node == null) {
        continue;
      }
      String type = node.getString("type", "");
      String questId = node.getString("questId", "");
      String player = node.getString("player", "unknown");
      long timestamp = node.getLong("timestamp", 0L);
      if (type.isBlank() || questId.isBlank()) {
        continue;
      }
      loaded.add(new Entry(type, questId, player, timestamp));
    }
    entries.clear();
    entries.addAll(loaded);
  }

  private void save() {
    YamlConfiguration cfg = new YamlConfiguration();
    ConfigurationSection entriesSec = cfg.createSection("entries");
    int index = 0;
    for (Entry entry : entries) {
      if (entry == null) {
        continue;
      }
      String key = String.valueOf(index++);
      ConfigurationSection node = entriesSec.createSection(key);
      node.set("type", entry.type());
      node.set("questId", entry.questId());
      node.set("player", entry.player());
      node.set("timestamp", entry.timestamp());
    }
    try {
      cfg.save(file);
    } catch (IOException ex) {
      // avoid failing quest flows on audit write
    }
  }
}
