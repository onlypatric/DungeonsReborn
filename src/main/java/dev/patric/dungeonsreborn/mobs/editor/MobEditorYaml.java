package dev.patric.dungeonsreborn.mobs.editor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

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

  public static boolean lootClearVanilla(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection mob = mobSection(cfg, id, false);
    if (mob == null) {
      return false;
    }
    ConfigurationSection loot = mob.getConfigurationSection("loot");
    return loot != null && loot.getBoolean("clearVanilla", false);
  }

  public static int lootRolls(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection mob = mobSection(cfg, id, false);
    if (mob == null) {
      return 1;
    }
    ConfigurationSection loot = mob.getConfigurationSection("loot");
    return loot == null ? 1 : loot.getInt("rolls", 1);
  }

  public static int lootBonusRolls(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection mob = mobSection(cfg, id, false);
    if (mob == null) {
      return 0;
    }
    ConfigurationSection loot = mob.getConfigurationSection("loot");
    return loot == null ? 0 : loot.getInt("bonusRolls", 0);
  }

  public static double lootLuckMultiplier(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection mob = mobSection(cfg, id, false);
    if (mob == null) {
      return 0.0;
    }
    ConfigurationSection loot = mob.getConfigurationSection("loot");
    return loot == null ? 0.0 : loot.getDouble("luckMultiplier", 0.0);
  }

  public static String lootAnnounceTemplate(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection mob = mobSection(cfg, id, false);
    if (mob == null) {
      return null;
    }
    ConfigurationSection loot = mob.getConfigurationSection("loot");
    return loot == null ? null : loot.getString("announceTemplate");
  }

  public static List<String> lootAnnounceTiers(File file, String id) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection mob = mobSection(cfg, id, false);
    if (mob == null) {
      return List.of();
    }
    ConfigurationSection loot = mob.getConfigurationSection("loot");
    return loot == null ? List.of() : loot.getStringList("announceTiers");
  }

  public static List<Map<String, Object>> lootEntries(File file, String id, String listKey) {
    YamlConfiguration cfg = load(file);
    ConfigurationSection mob = mobSection(cfg, id, false);
    if (mob == null) {
      return List.of();
    }
    ConfigurationSection loot = mob.getConfigurationSection("loot");
    if (loot == null) {
      return List.of();
    }
    List<Map<?, ?>> raw = loot.getMapList(listKey);
    if (raw.isEmpty()) {
      return List.of();
    }
    List<Map<String, Object>> out = new java.util.ArrayList<>();
    for (Map<?, ?> entry : raw) {
      out.add(copyMap(entry));
    }
    return out;
  }

  public static void setLootEntries(File file, String id, String listKey, List<Map<String, Object>> entries) {
    update(file, id, mob -> {
      ConfigurationSection loot = mob.getConfigurationSection("loot");
      if (loot == null) {
        loot = mob.createSection("loot");
      }
      if (entries == null || entries.isEmpty()) {
        loot.set(listKey, null);
      } else {
        loot.set(listKey, entries);
      }
    });
  }

  public static void setLootValue(File file, String id, String key, Object value) {
    update(file, id, mob -> {
      ConfigurationSection loot = mob.getConfigurationSection("loot");
      if (loot == null) {
        loot = mob.createSection("loot");
      }
      loot.set(key, value);
    });
  }

  public static ItemStack previewLootItem(Map<?, ?> raw) {
    if (raw == null) {
      return new ItemStack(Material.BARRIER);
    }
    Object itemRaw = raw.get("item");
    if (itemRaw instanceof ItemStack stack) {
      return stack.clone();
    }
    if (itemRaw instanceof Map<?, ?> mapItem) {
      return ItemStack.deserialize(copyMap(mapItem));
    }
    if (itemRaw instanceof String str) {
      return new ItemStack(parseMaterialSafe(str));
    }
    Object materialRaw = raw.get("material");
    if (materialRaw != null) {
      return new ItemStack(parseMaterialSafe(materialRaw.toString()));
    }
    Object idRaw = raw.containsKey("itemId") ? raw.get("itemId") : raw.get("id");
    if (idRaw != null) {
      ItemStack item = new ItemStack(Material.PAPER);
      var meta = item.getItemMeta();
      meta.displayName(dev.patric.dungeonsreborn.gui.GuiMini.mm("<yellow>Item:</yellow> <white>" + idRaw + "</white>"));
      item.setItemMeta(meta);
      return item;
    }
    String token = raw.containsKey("token") ? String.valueOf(raw.get("token")) : null;
    if (token == null || token.isBlank()) {
      token = raw.containsKey("tokenTier") ? String.valueOf(raw.get("tokenTier")) : null;
    }
    if (token != null && !token.isBlank()) {
      ItemStack item = new ItemStack(Material.SUNFLOWER);
      var meta = item.getItemMeta();
      meta.displayName(dev.patric.dungeonsreborn.gui.GuiMini.mm("<gold>Token:</gold> <white>" + token + "</white>"));
      item.setItemMeta(meta);
      return item;
    }
    return new ItemStack(Material.PAPER);
  }

  private static Material parseMaterialSafe(String raw) {
    try {
      return Material.valueOf(raw.trim().toUpperCase());
    } catch (Exception ex) {
      return Material.PAPER;
    }
  }

  private static Map<String, Object> copyMap(Map<?, ?> input) {
    Map<String, Object> out = new java.util.LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : input.entrySet()) {
      out.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return out;
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
