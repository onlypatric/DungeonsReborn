package dev.patric.dungeonsreborn.quests;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.system.SystemStatusStore;
import dev.patric.dungeonsreborn.util.YamlValues;

public final class QuestGiverYamlRegistry {
  public record ReloadResult(int loaded, List<String> errors) {
  }

  private final JavaPlugin plugin;
  private final Logger logger;
  private final Map<String, QuestGiverSpec> givers = new LinkedHashMap<>();
  private List<String> lastErrors = List.of();

  public QuestGiverYamlRegistry(JavaPlugin plugin, Logger logger) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  public File file() {
    return new File(plugin.getDataFolder(), "quest_givers.yml");
  }

  public Map<String, QuestGiverSpec> givers() {
    return Map.copyOf(givers);
  }

  public QuestGiverSpec giver(String id) {
    if (id == null) {
      return null;
    }
    return givers.get(Ids.normalize(id));
  }

  public List<String> lastErrors() {
    return lastErrors;
  }

  public ReloadResult reload() {
    ensureFile();
    List<String> errors = new ArrayList<>();
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file());
    Map<String, QuestGiverSpec> next = parseGivers(cfg, errors);
    if (errors.isEmpty()) {
      givers.clear();
      givers.putAll(next);
    }
    lastErrors = List.copyOf(errors);
    if (!errors.isEmpty()) {
      logger.warning("[Quests] Givers YAML reload had " + errors.size() + " errors");
      for (String error : errors) {
        logger.warning("[Quests] Givers YAML: " + error);
      }
    } else {
      logger.info("[Quests] Givers YAML loaded " + next.size() + " givers");
    }
    SystemStatusStore.get().record(
        "quest_givers",
        "Quest Givers",
        file().getPath(),
        "givers=" + (errors.isEmpty() ? next.size() : givers.size()),
        errors);
    return new ReloadResult(errors.isEmpty() ? next.size() : givers.size(), errors);
  }

  private void ensureFile() {
    File file = file();
    if (file.exists()) {
      return;
    }
    plugin.saveResource("quest_givers.yml", false);
  }

  private Map<String, QuestGiverSpec> parseGivers(YamlConfiguration cfg, List<String> errors) {
    ConfigurationSection giversSec = cfg.getConfigurationSection("givers");
    if (giversSec == null) {
      return Map.of();
    }
    Map<String, QuestGiverSpec> out = new LinkedHashMap<>();
    for (String rawId : giversSec.getKeys(false)) {
      String base = "givers." + rawId;
      ConfigurationSection node = giversSec.getConfigurationSection(rawId);
      if (node == null) {
        errors.add(base + ": must be an object");
        continue;
      }
      try {
        String id = Ids.normalize(rawId);
        String title = YamlValues.string(node, "title", id);
        List<String> dialogue = new ArrayList<>();
        for (String line : node.getStringList("dialogue")) {
          if (line == null || line.isBlank()) {
            continue;
          }
          dialogue.add(line);
        }
        QuestGiverMode mode = QuestGiverMode.parse(node.getString("mode"));
        int poolSize = Math.max(0, node.getInt("poolSize", 0));
        List<String> quests = parseQuestList(node.getStringList("quests"), base + ".quests", errors);
        List<String> pool = parseQuestList(node.getStringList("pool"), base + ".pool", errors);
        out.put(id, new QuestGiverSpec(id, title, List.copyOf(dialogue), mode, poolSize, quests, pool));
      } catch (Exception ex) {
        errors.add(base + ": " + ex.getMessage());
      }
    }
    return out;
  }

  private List<String> parseQuestList(List<String> raw, String path, List<String> errors) {
    List<String> out = new ArrayList<>();
    if (raw == null) {
      return List.of();
    }
    for (String questId : raw) {
      if (questId == null || questId.isBlank()) {
        continue;
      }
      try {
        out.add(Ids.normalize(questId));
      } catch (IllegalArgumentException ex) {
        errors.add(path + ": invalid quest id " + questId.toLowerCase(Locale.ROOT));
      }
    }
    return List.copyOf(out);
  }
}
