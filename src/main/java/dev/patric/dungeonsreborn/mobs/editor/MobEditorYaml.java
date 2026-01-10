package dev.patric.dungeonsreborn.mobs.editor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class MobEditorYaml {
  private MobEditorYaml() {
  }

  public static List<String> mobIds(File file) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection mobs = cfg.getConfigurationSection("mobs");
    if (mobs == null) {
      return List.of();
    }
    List<String> ids = new ArrayList<>(mobs.getKeys(false));
    ids.sort(Comparator.naturalOrder());
    return ids;
  }

  public static String name(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection mob = mobSection(cfg, id, false);
    return mob == null ? null : mob.getString("name");
  }

  public static String mainAbility(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection mob = mobSection(cfg, id, false);
    return mob == null ? null : mob.getString("attacks.main.ability");
  }

  public static String secondaryAbility(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection mob = mobSection(cfg, id, false);
    return mob == null ? null : mob.getString("attacks.secondary.ability");
  }

  public static Boolean showName(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection mob = mobSection(cfg, id, false);
    return mob == null || !mob.contains("showName") ? null : mob.getBoolean("showName");
  }

  public static Double stat(File file, String id, String key) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection mob = mobSection(cfg, id, false);
    if (mob == null) {
      return null;
    }
    ConfigurationSection stats = mob.getConfigurationSection("stats");
    if (stats == null || !stats.contains(key)) {
      return null;
    }
    return stats.getDouble(key);
  }

  public static void setName(File file, String id, String name) {
    update(file, id, mob -> {
      if (name == null || name.isBlank()) {
        mob.set("name", null);
      } else {
        mob.set("name", name);
      }
    });
  }

  public static void setShowName(File file, String id, boolean show) {
    update(file, id, mob -> mob.set("showName", show));
  }

  public static void setMainAbility(File file, String id, String ability) {
    update(file, id, mob -> setString(mob, "attacks.main.ability", ability));
  }

  public static void setSecondaryAbility(File file, String id, String ability) {
    update(file, id, mob -> setString(mob, "attacks.secondary.ability", ability));
  }

  public static void setStat(File file, String id, String key, Double value) {
    update(file, id, mob -> {
      ConfigurationSection stats = mob.getConfigurationSection("stats");
      if (value == null) {
        if (stats != null) {
          stats.set(key, null);
          if (stats.getKeys(false).isEmpty()) {
            mob.set("stats", null);
          }
        }
        return;
      }
      if (stats == null) {
        stats = mob.createSection("stats");
      }
      stats.set(key, value);
    });
  }

  public static void exportSingle(File file, String id, File outFile) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection mob = mobSection(cfg, id, false);
    if (mob == null) {
      throw new IllegalArgumentException("Unknown mob id: " + id);
    }
    YamlConfiguration out = new YamlConfiguration();
    out.set("id", id);
    for (String key : mob.getKeys(false)) {
      out.set(key, mob.get(key));
    }
    save(out, outFile);
  }

  private static void update(File file, String id, Consumer<ConfigurationSection> updater) {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(id, "id");
    YamlConfiguration cfg = load(file);
    ConfigurationSection mob = mobSection(cfg, id, true);
    updater.accept(mob);
    save(cfg, file);
  }

  private static void setString(ConfigurationSection section, String path, String value) {
    if (value == null || value.isBlank()) {
      section.set(path, null);
    } else {
      section.set(path, value);
    }
  }

  private static ConfigurationSection mobSection(YamlConfiguration cfg, String id, boolean create) {
    ConfigurationSection mobs = cfg.getConfigurationSection("mobs");
    if (mobs == null) {
      if (!create) {
        return null;
      }
      mobs = cfg.createSection("mobs");
    }
    ConfigurationSection mob = mobs.getConfigurationSection(id);
    if (mob == null && create) {
      mob = mobs.createSection(id, Map.of("type", "ZOMBIE"));
    }
    return mob;
  }

  private static YamlConfiguration load(File file) {
    return YamlConfiguration.loadConfiguration(file);
  }

  private static void save(YamlConfiguration cfg, File file) {
    try {
      cfg.save(file);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to save " + file + " (" + ex.getMessage() + ")", ex);
    }
  }
}
