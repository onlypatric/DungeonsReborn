package dev.patric.dungeonsreborn.quests.editor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.quests.QuestObjectiveType;
import dev.patric.dungeonsreborn.quests.QuestRotation;

public final class QuestEditorYaml {
  private QuestEditorYaml() {
  }

  public record ObjectiveData(int index, QuestObjectiveType type, Map<String, Object> raw) {
  }

  public static List<String> questIds(File file) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection quests = cfg.getConfigurationSection("quests");
    if (quests == null) {
      return List.of();
    }
    List<String> ids = new ArrayList<>(quests.getKeys(false));
    ids.sort(Comparator.naturalOrder());
    return ids;
  }

  public static String name(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection quest = questSection(cfg, id, false);
    return quest == null ? null : quest.getString("name");
  }

  public static List<String> description(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection quest = questSection(cfg, id, false);
    if (quest == null) {
      return List.of();
    }
    if (quest.isList("description")) {
      return quest.getStringList("description");
    }
    String single = quest.getString("description");
    return single == null ? List.of() : List.of(single);
  }

  public static boolean enabled(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection quest = questSection(cfg, id, false);
    return quest == null || quest.getBoolean("enabled", true);
  }

  public static long cooldownSeconds(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection quest = questSection(cfg, id, false);
    return quest == null ? 0L : quest.getLong("cooldownSeconds", 0L);
  }

  public static QuestRotation rotation(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection quest = questSection(cfg, id, false);
    String raw = quest == null ? null : quest.getString("rotation");
    if (raw == null || raw.isBlank()) {
      return QuestRotation.NONE;
    }
    try {
      return QuestRotation.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      return QuestRotation.NONE;
    }
  }

  public static List<ObjectiveData> objectives(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection quest = questSection(cfg, id, false);
    if (quest == null) {
      return List.of();
    }
    List<Map<?, ?>> raw = quest.getMapList("objectives");
    List<ObjectiveData> out = new ArrayList<>();
    for (int i = 0; i < raw.size(); i++) {
      Map<?, ?> entry = raw.get(i);
      Map<String, Object> map = new HashMap<>();
      for (Map.Entry<?, ?> kv : entry.entrySet()) {
        if (kv.getKey() != null) {
          map.put(kv.getKey().toString(), kv.getValue());
        }
      }
      QuestObjectiveType type = QuestObjectiveType.parse(string(map, "type", null));
      if (type == null) {
        type = QuestObjectiveType.KILL_MOB;
      }
      out.add(new ObjectiveData(i, type, map));
    }
    return out;
  }

  public static void createQuest(File file, String id) {
    update(file, id, quest -> {
      if (!quest.contains("name")) {
        quest.set("name", id);
      }
      if (!quest.contains("description")) {
        quest.set("description", List.of("Describe the quest here."));
      }
      if (!quest.contains("objectives")) {
        List<Map<String, Object>> objectives = new ArrayList<>();
        objectives.add(defaultObjective(QuestObjectiveType.KILL_MOB, 1));
        quest.set("objectives", objectives);
      }
    });
  }

  public static void deleteQuest(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection quests = cfg.getConfigurationSection("quests");
    if (quests == null) {
      return;
    }
    quests.set(id, null);
    save(cfg, file);
  }

  public static void setName(File file, String id, String name) {
    update(file, id, quest -> {
      if (name == null || name.isBlank()) {
        quest.set("name", null);
      } else {
        quest.set("name", name);
      }
    });
  }

  public static void setDescription(File file, String id, List<String> description) {
    update(file, id, quest -> {
      if (description == null || description.isEmpty()) {
        quest.set("description", null);
      } else {
        quest.set("description", description);
      }
    });
  }

  public static void setEnabled(File file, String id, boolean enabled) {
    update(file, id, quest -> quest.set("enabled", enabled ? null : false));
  }

  public static void setCooldownSeconds(File file, String id, long seconds) {
    update(file, id, quest -> {
      if (seconds <= 0) {
        quest.set("cooldownSeconds", null);
      } else {
        quest.set("cooldownSeconds", seconds);
      }
    });
  }

  public static void setRotation(File file, String id, QuestRotation rotation) {
    update(file, id, quest -> {
      if (rotation == null || rotation == QuestRotation.NONE) {
        quest.set("rotation", null);
      } else {
        quest.set("rotation", rotation.name().toLowerCase());
      }
    });
  }

  public static void addObjective(File file, String id, QuestObjectiveType type) {
    updateObjectives(file, id, list -> list.add(defaultObjective(type, 1)));
  }

  public static void removeObjective(File file, String id, int index) {
    updateObjectives(file, id, list -> {
      if (index < 0 || index >= list.size()) {
        return;
      }
      list.remove(index);
      if (list.isEmpty()) {
        list.add(defaultObjective(QuestObjectiveType.KILL_MOB, 1));
      }
    });
  }

  public static void setObjectiveType(File file, String id, int index, QuestObjectiveType type) {
    updateObjectives(file, id, list -> {
      if (index < 0 || index >= list.size()) {
        return;
      }
      int count = intValue(list.get(index).get("count"), 1);
      list.set(index, defaultObjective(type, count));
    });
  }

  public static void setObjectiveCount(File file, String id, int index, int count) {
    updateObjectives(file, id, list -> {
      Map<String, Object> map = getObjective(list, index);
      if (map == null) {
        return;
      }
      map.put("count", Math.max(1, count));
    });
  }

  public static void setObjectiveMob(File file, String id, int index, String mobId) {
    updateObjectives(file, id, list -> {
      Map<String, Object> map = getObjective(list, index);
      if (map == null) {
        return;
      }
      if (mobId == null || mobId.isBlank()) {
        map.remove("mob");
        map.remove("mobId");
      } else {
        map.put("mob", Ids.normalize(mobId));
        map.remove("mobId");
      }
    });
  }

  public static void setObjectiveEntity(File file, String id, int index, String entityType) {
    updateObjectives(file, id, list -> {
      Map<String, Object> map = getObjective(list, index);
      if (map == null) {
        return;
      }
      if (entityType == null || entityType.isBlank()) {
        map.remove("entity");
        map.remove("entityType");
      } else {
        map.put("entity", entityType.toLowerCase());
        map.remove("entityType");
      }
    });
  }

  public static void setObjectiveItemId(File file, String id, int index, String itemId) {
    updateObjectives(file, id, list -> {
      Map<String, Object> map = getObjective(list, index);
      if (map == null) {
        return;
      }
      if (itemId == null || itemId.isBlank()) {
        map.remove("itemId");
      } else {
        map.put("itemId", Ids.normalize(itemId));
      }
    });
  }

  public static void setObjectiveMaterial(File file, String id, int index, String material) {
    updateObjectives(file, id, list -> {
      Map<String, Object> map = getObjective(list, index);
      if (map == null) {
        return;
      }
      if (material == null || material.isBlank()) {
        map.remove("material");
      } else {
        map.put("material", material.toUpperCase());
      }
    });
  }

  public static void setObjectiveRecipeId(File file, String id, int index, String recipeId) {
    updateObjectives(file, id, list -> {
      Map<String, Object> map = getObjective(list, index);
      if (map == null) {
        return;
      }
      if (recipeId == null || recipeId.isBlank()) {
        map.remove("recipeId");
      } else {
        map.put("recipeId", Ids.normalize(recipeId));
      }
    });
  }

  public static void setObjectiveRegion(File file, String id, int index, String world, double x, double y, double z,
      double radius) {
    updateObjectives(file, id, list -> {
      Map<String, Object> map = getObjective(list, index);
      if (map == null) {
        return;
      }
      map.put("world", world == null ? "minecraft:world" : world);
      map.put("x", x);
      map.put("y", y);
      map.put("z", z);
      map.put("radius", Math.max(0.1, radius));
    });
  }

  private static Map<String, Object> defaultObjective(QuestObjectiveType type, int count) {
    Map<String, Object> map = new HashMap<>();
    switch (type) {
      case KILL_MOB -> {
        map.put("type", "kill_mob");
        map.put("mob", "example_mob");
        map.put("count", Math.max(1, count));
      }
      case USE_ITEM -> {
        map.put("type", "use_item");
        map.put("itemId", "example_item");
        map.put("count", Math.max(1, count));
      }
      case VISIT_REGION -> {
        map.put("type", "visit_region");
        map.put("world", "minecraft:world");
        map.put("x", 0.0);
        map.put("y", 64.0);
        map.put("z", 0.0);
        map.put("radius", 4.0);
      }
      case CRAFT_ITEM -> {
        map.put("type", "craft_item");
        map.put("recipeId", "example_recipe");
        map.put("count", Math.max(1, count));
      }
      case BREAK_BLOCK -> {
        map.put("type", "break_block");
        map.put("material", "STONE");
        map.put("count", Math.max(1, count));
      }
      case PLACE_BLOCK -> {
        map.put("type", "place_block");
        map.put("material", "STONE");
        map.put("count", Math.max(1, count));
      }
    }
    return map;
  }

  private static void updateObjectives(File file, String id, Consumer<List<Map<String, Object>>> mutator) {
    update(file, id, quest -> {
      List<Map<?, ?>> raw = quest.getMapList("objectives");
      List<Map<String, Object>> list = new ArrayList<>();
      for (Map<?, ?> entry : raw) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<?, ?> kv : entry.entrySet()) {
          if (kv.getKey() != null) {
            map.put(kv.getKey().toString(), kv.getValue());
          }
        }
        list.add(map);
      }
      mutator.accept(list);
      quest.set("objectives", list);
    });
  }

  private static Map<String, Object> getObjective(List<Map<String, Object>> list, int index) {
    if (index < 0 || index >= list.size()) {
      return null;
    }
    return list.get(index);
  }

  private static int intValue(Object raw, int def) {
    if (raw instanceof Number number) {
      return number.intValue();
    }
    if (raw instanceof String str) {
      try {
        return Integer.parseInt(str.trim());
      } catch (NumberFormatException ex) {
        return def;
      }
    }
    return def;
  }

  private static String string(Map<String, Object> map, String key, String def) {
    Object raw = map.get(key);
    if (raw == null) {
      return def;
    }
    String val = raw.toString();
    return val.isBlank() ? def : val;
  }

  private static void update(File file, String id, Consumer<ConfigurationSection> mutator) {
    Objects.requireNonNull(mutator, "mutator");
    YamlConfiguration cfg = load(file);
    ConfigurationSection quest = questSection(cfg, id, true);
    if (quest == null) {
      return;
    }
    mutator.accept(quest);
    save(cfg, file);
  }

  private static ConfigurationSection questSection(YamlConfiguration cfg, String id, boolean create) {
    ConfigurationSection quests = cfg.getConfigurationSection("quests");
    if (quests == null) {
      if (!create) {
        return null;
      }
      quests = cfg.createSection("quests");
    }
    ConfigurationSection quest = quests.getConfigurationSection(id);
    if (quest == null && create) {
      quest = quests.createSection(id);
    }
    return quest;
  }

  private static YamlConfiguration load(File file) {
    return YamlConfiguration.loadConfiguration(file);
  }

  private static void save(YamlConfiguration cfg, File file) {
    try {
      cfg.save(file);
    } catch (IOException ignored) {
    }
  }
}
