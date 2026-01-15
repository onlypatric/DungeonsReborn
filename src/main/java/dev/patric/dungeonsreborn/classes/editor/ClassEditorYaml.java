package dev.patric.dungeonsreborn.classes.editor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public final class ClassEditorYaml {
  private ClassEditorYaml() {
  }

  public static List<String> classIds(File file) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection classes = cfg.getConfigurationSection("classes");
    if (classes == null) {
      return List.of();
    }
    List<String> ids = new ArrayList<>(classes.getKeys(false));
    ids.sort(Comparator.naturalOrder());
    return ids;
  }

  public static String name(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection cls = classSection(cfg, id, false);
    return cls == null ? null : cls.getString("name");
  }

  public static List<String> description(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection cls = classSection(cfg, id, false);
    if (cls == null) {
      return List.of();
    }
    if (cls.isList("description")) {
      return cls.getStringList("description");
    }
    String single = cls.getString("description");
    return single == null ? List.of() : List.of(single);
  }

  public static boolean enabled(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection cls = classSection(cfg, id, false);
    return cls == null || cls.getBoolean("enabled", true);
  }

  public static int unlockLevel(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection cls = classSection(cfg, id, false);
    if (cls == null) {
      return 0;
    }
    ConfigurationSection unlock = cls.getConfigurationSection("unlock");
    return unlock == null ? 0 : unlock.getInt("level", 0);
  }

  public static int unlockTokens(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection cls = classSection(cfg, id, false);
    if (cls == null) {
      return 0;
    }
    ConfigurationSection unlock = cls.getConfigurationSection("unlock");
    return unlock == null ? 0 : unlock.getInt("tokens", 0);
  }

  public static List<String> unlockQuests(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection cls = classSection(cfg, id, false);
    if (cls == null) {
      return List.of();
    }
    ConfigurationSection unlock = cls.getConfigurationSection("unlock");
    return unlock == null ? List.of() : unlock.getStringList("quests");
  }

  public static void createClass(File file, String id) {
    update(file, id, cls -> {
      if (!cls.contains("name")) {
        cls.set("name", id);
      }
      if (!cls.contains("icon.material")) {
        cls.set("icon.material", "BOOK");
      }
    });
  }

  public static void deleteClass(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection classes = cfg.getConfigurationSection("classes");
    if (classes == null) {
      return;
    }
    classes.set(id, null);
    save(cfg, file);
  }

  public static void setName(File file, String id, String name) {
    update(file, id, cls -> {
      if (name == null || name.isBlank()) {
        cls.set("name", null);
      } else {
        cls.set("name", name);
      }
    });
  }

  public static void setDescription(File file, String id, List<String> description) {
    update(file, id, cls -> {
      if (description == null || description.isEmpty()) {
        cls.set("description", null);
      } else {
        cls.set("description", description);
      }
    });
  }

  public static void setEnabled(File file, String id, boolean enabled) {
    update(file, id, cls -> cls.set("enabled", enabled ? null : false));
  }

  public static void setIcon(File file, String id, ItemStack item) {
    update(file, id, cls -> {
      if (item == null || item.getType().isAir()) {
        cls.set("icon", null);
        return;
      }
      ConfigurationSection icon = cls.getConfigurationSection("icon");
      if (icon == null) {
        icon = cls.createSection("icon");
      }
      icon.set("item", item.clone());
      icon.set("material", null);
      icon.set("name", null);
      icon.set("lore", null);
    });
  }

  public static void setUnlockLevel(File file, String id, int level) {
    update(file, id, cls -> setUnlockValue(cls, "level", Math.max(0, level)));
  }

  public static void setUnlockTokens(File file, String id, int tokens) {
    update(file, id, cls -> setUnlockValue(cls, "tokens", Math.max(0, tokens)));
  }

  public static void setUnlockQuests(File file, String id, List<String> quests) {
    update(file, id, cls -> {
      ConfigurationSection unlock = unlockSection(cls, quests != null && !quests.isEmpty());
      if (unlock == null) {
        return;
      }
      if (quests == null || quests.isEmpty()) {
        unlock.set("quests", null);
      } else {
        unlock.set("quests", quests);
      }
      tidyUnlock(cls, unlock);
    });
  }

  private static void setUnlockValue(ConfigurationSection cls, String key, int value) {
    ConfigurationSection unlock = unlockSection(cls, value > 0);
    if (unlock == null) {
      return;
    }
    if (value <= 0) {
      unlock.set(key, null);
    } else {
      unlock.set(key, value);
    }
    tidyUnlock(cls, unlock);
  }

  private static ConfigurationSection unlockSection(ConfigurationSection cls, boolean create) {
    ConfigurationSection unlock = cls.getConfigurationSection("unlock");
    if (unlock == null && create) {
      unlock = cls.createSection("unlock");
    }
    return unlock;
  }

  private static void tidyUnlock(ConfigurationSection cls, ConfigurationSection unlock) {
    if (unlock != null && unlock.getKeys(false).isEmpty()) {
      cls.set("unlock", null);
    }
  }

  private static void update(File file, String id, Consumer<ConfigurationSection> mutator) {
    Objects.requireNonNull(mutator, "mutator");
    YamlConfiguration cfg = load(file);
    ConfigurationSection cls = classSection(cfg, id, true);
    if (cls == null) {
      return;
    }
    mutator.accept(cls);
    save(cfg, file);
  }

  private static ConfigurationSection classSection(YamlConfiguration cfg, String id, boolean create) {
    ConfigurationSection classes = cfg.getConfigurationSection("classes");
    if (classes == null) {
      if (!create) {
        return null;
      }
      classes = cfg.createSection("classes");
    }
    ConfigurationSection cls = classes.getConfigurationSection(id);
    if (cls == null && create) {
      cls = classes.createSection(id);
    }
    return cls;
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
