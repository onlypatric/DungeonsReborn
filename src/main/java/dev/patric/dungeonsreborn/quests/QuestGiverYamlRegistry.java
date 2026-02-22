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
import dev.patric.dungeonsreborn.util.PluginResources;
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
    PluginResources.ensureYamlFile(plugin, file(), "quest_givers.yml", cfg -> cfg.createSection("givers"), logger,
        "Quest Givers");
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
        Object dialogueRaw = node.get("dialogue");
        Map<String, Object> dialogueMap = dialogueRaw instanceof Map<?, ?>
            ? castMap(dialogueRaw, base + ".dialogue", errors)
            : null;
        List<String> dialogue = parseDialogueLines(
            dialogueMap != null ? pickFirst(dialogueMap, "default", "lines", "dialogue") : dialogueRaw,
            base + ".dialogue",
            errors);
        List<String> acceptDialogue = parseDialogueLines(
            dialogueMap != null ? pickFirst(dialogueMap, "accept", "acceptDialogue") : node.get("dialogueAccept"),
            base + ".dialogue.accept",
            errors);
        List<String> activeDialogue = parseDialogueLines(
            dialogueMap != null ? pickFirst(dialogueMap, "active", "activeDialogue") : node.get("dialogueActive"),
            base + ".dialogue.active",
            errors);
        List<String> turnInDialogue = parseDialogueLines(
            dialogueMap != null ? pickFirst(dialogueMap, "turnIn", "turnin", "turn_in") : node.get("dialogueTurnIn"),
            base + ".dialogue.turnIn",
            errors);
        List<String> completedDialogue = parseDialogueLines(
            dialogueMap != null ? pickFirst(dialogueMap, "completed", "complete") : node.get("dialogueCompleted"),
            base + ".dialogue.completed",
            errors);
        QuestDialogueTree dialogueTree = null;
        if (dialogueMap != null && (dialogueMap.containsKey("nodes") || dialogueMap.containsKey("tree")
            || dialogueMap.containsKey("start"))) {
          Object treeRaw = dialogueMap.containsKey("tree") ? dialogueMap.get("tree") : dialogueMap;
          dialogueTree = parseDialogueTree(treeRaw, base + ".dialogue", errors);
        }
        if (dialogueTree == null) {
          dialogueTree = parseDialogueTree(node.get("dialogueTree"), base + ".dialogueTree", errors);
        }
        QuestGiverFilter filter = parseFilter(node.get("filter"), base + ".filter", errors);
        QuestGiverMode mode = QuestGiverMode.parse(node.getString("mode"));
        int poolSize = Math.max(0, node.getInt("poolSize", 0));
        List<String> quests = parseQuestList(node.getStringList("quests"), base + ".quests", errors);
        List<String> pool = parseQuestList(node.getStringList("pool"), base + ".pool", errors);
        out.put(id, new QuestGiverSpec(
            id,
            title,
            dialogue,
            acceptDialogue,
            activeDialogue,
            turnInDialogue,
            completedDialogue,
            dialogueTree,
            mode,
            poolSize,
            quests,
            pool,
            filter));
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

  private QuestDialogueTree parseDialogueTree(Object raw, String path, List<String> errors) {
    Map<String, Object> treeMap = castMap(raw, path, errors);
    if (treeMap == null || treeMap.isEmpty()) {
      return null;
    }
    String start = YamlValues.string(treeMap, "start", null);
    Map<String, Object> nodesRaw = castMap(treeMap.get("nodes"), path + ".nodes", errors);
    if (nodesRaw == null) {
      return null;
    }
    Map<String, QuestDialogueNode> nodes = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : nodesRaw.entrySet()) {
      String nodeId = entry.getKey();
      String nodePath = path + ".nodes." + nodeId;
      Map<String, Object> nodeMap = castMap(entry.getValue(), nodePath, errors);
      if (nodeMap == null) {
        continue;
      }
      List<String> lines = parseDialogueLines(pickFirst(nodeMap, "lines", "dialogue"), nodePath + ".lines", errors);
      List<QuestDialogueCondition> conditions = parseDialogueConditions(
          pickFirst(nodeMap, "requires", "conditions", "if"),
          nodePath + ".conditions",
          errors);
      List<QuestDialogueChoice> choices = parseDialogueChoices(
          nodeMap.get("choices"),
          nodePath + ".choices",
          errors);
      nodes.put(nodeId, new QuestDialogueNode(nodeId, lines, choices, conditions));
    }
    if (nodes.isEmpty()) {
      return null;
    }
    return new QuestDialogueTree(start, nodes);
  }

  private List<QuestDialogueChoice> parseDialogueChoices(Object raw, String path, List<String> errors) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<QuestDialogueChoice> out = new ArrayList<>();
    int index = 0;
    for (Object entry : list) {
      String choicePath = path + "[" + index + "]";
      index++;
      if (entry instanceof String text) {
        if (!text.isBlank()) {
          out.add(new QuestDialogueChoice(text, null, List.of()));
        }
        continue;
      }
      Map<String, Object> map = castMap(entry, choicePath, errors);
      if (map == null) {
        continue;
      }
      String text = YamlValues.string(map, "text", "").trim();
      if (text.isEmpty()) {
        errors.add(choicePath + ": missing text");
        continue;
      }
      String next = YamlValues.string(map, "next", null);
      List<QuestDialogueCondition> conditions = parseDialogueConditions(
          pickFirst(map, "requires", "conditions", "if"),
          choicePath + ".conditions",
          errors);
      out.add(new QuestDialogueChoice(text, next, conditions));
    }
    return List.copyOf(out);
  }

  private List<QuestDialogueCondition> parseDialogueConditions(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return List.of();
    }
    List<QuestDialogueCondition> out = new ArrayList<>();
    if (raw instanceof Map<?, ?> map) {
      QuestDialogueCondition condition = parseDialogueCondition(map, path, errors);
      if (condition != null) {
        out.add(condition);
      }
      return List.copyOf(out);
    }
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    int index = 0;
    for (Object entry : list) {
      String condPath = path + "[" + index + "]";
      index++;
      if (entry instanceof String rawText) {
        String trimmed = rawText.trim();
        if (trimmed.isEmpty()) {
          continue;
        }
        String[] parts = trimmed.split(":", 2);
        String questId = Ids.normalize(parts[0].trim());
        QuestRequiredStatus required = parts.length > 1 ? QuestRequiredStatus.parse(parts[1].trim()) : null;
        out.add(new QuestDialogueCondition(questId, required));
        continue;
      }
      if (entry instanceof Map<?, ?> map) {
        QuestDialogueCondition condition = parseDialogueCondition(map, condPath, errors);
        if (condition != null) {
          out.add(condition);
        }
      }
    }
    return List.copyOf(out);
  }

  private QuestDialogueCondition parseDialogueCondition(Map<?, ?> map, String path, List<String> errors) {
    Object questRaw = map.get("quest");
    String questId = questRaw == null ? null : String.valueOf(questRaw);
    if (questId == null || questId.isBlank()) {
      errors.add(path + ": missing quest");
      return null;
    }
    try {
      questId = Ids.normalize(questId);
    } catch (IllegalArgumentException ex) {
      errors.add(path + ": invalid quest id " + questId.toLowerCase(Locale.ROOT));
      return null;
    }
    QuestRequiredStatus required = QuestRequiredStatus.parse(YamlValues.string(map, "status", null));
    return new QuestDialogueCondition(questId, required);
  }

  private List<String> parseDialogueLines(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object entry : list) {
        if (entry == null) {
          continue;
        }
        String line = String.valueOf(entry).trim();
        if (!line.isEmpty()) {
          out.add(line);
        }
      }
      return List.copyOf(out);
    }
    if (raw instanceof String text) {
      String line = text.trim();
      if (!line.isEmpty()) {
        out.add(line);
      }
      return List.copyOf(out);
    }
    errors.add(path + ": expected list of strings");
    return List.of();
  }

  private QuestGiverFilter parseFilter(Object raw, String path, List<String> errors) {
    Map<String, Object> map = castMap(raw, path, errors);
    if (map == null || map.isEmpty()) {
      return QuestGiverFilter.all();
    }
    boolean showAvailable = bool(map, true, "available", "showAvailable");
    boolean showActive = bool(map, true, "active", "showActive");
    boolean showTurnIn = bool(map, true, "turnIn", "turnin", "showTurnIn");
    boolean showCompleted = bool(map, true, "completed", "showCompleted");
    boolean showFailed = bool(map, true, "failed", "showFailed");
    boolean showCooldown = bool(map, true, "cooldown", "showCooldown");
    boolean showLocked = bool(map, true, "locked", "showLocked");
    return new QuestGiverFilter(
        showAvailable,
        showActive,
        showTurnIn,
        showCompleted,
        showFailed,
        showCooldown,
        showLocked);
  }

  private boolean bool(Map<String, Object> map, boolean def, String... keys) {
    for (String key : keys) {
      if (map.containsKey(key)) {
        return YamlValues.bool(map, key, def);
      }
    }
    return def;
  }

  private Object pickFirst(Map<String, Object> map, String... keys) {
    for (String key : keys) {
      if (map.containsKey(key)) {
        return map.get(key);
      }
    }
    return null;
  }

  private Map<String, Object> castMap(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof Map<?, ?> map) {
      Map<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        Object key = entry.getKey();
        if (key == null) {
          continue;
        }
        out.put(String.valueOf(key), entry.getValue());
      }
      return out;
    }
    errors.add(path + ": expected object");
    return null;
  }
}
