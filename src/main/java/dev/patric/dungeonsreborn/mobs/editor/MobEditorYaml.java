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
    return mobIds(file, defaultFolder(file));
  }

  public static List<String> mobIds(File file, File folder) {
    List<String> ids = new ArrayList<>();
    YamlConfiguration cfg = load(file);
    ConfigurationSection mobs = cfg.getConfigurationSection("mobs");
    if (mobs != null) {
      ids.addAll(mobs.getKeys(false));
    }
    for (File extra : listYamlFiles(folder)) {
      YamlConfiguration extraCfg = load(extra);
      ConfigurationSection extraMobs = extraCfg.getConfigurationSection("mobs");
      if (extraMobs != null) {
        ids.addAll(extraMobs.getKeys(false));
      }
    }
    ids = ids.stream().distinct().sorted(Comparator.naturalOrder()).toList();
    return ids;
  }

  public static java.util.Map<String, String> mobNames(File file) {
    return mobNames(file, defaultFolder(file));
  }

  public static java.util.Map<String, String> mobNames(File file, File folder) {
    java.util.Map<String, String> names = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    YamlConfiguration cfg = load(file);
    ConfigurationSection mobs = cfg.getConfigurationSection("mobs");
    if (mobs != null) {
      for (String id : mobs.getKeys(false)) {
        ConfigurationSection section = mobs.getConfigurationSection(id);
        String name = section == null ? null : section.getString("name");
        names.putIfAbsent(id, name);
      }
    }
    for (File extra : listYamlFiles(folder)) {
      YamlConfiguration extraCfg = load(extra);
      ConfigurationSection extraMobs = extraCfg.getConfigurationSection("mobs");
      if (extraMobs == null) {
        continue;
      }
      for (String id : extraMobs.getKeys(false)) {
        if (names.containsKey(id)) {
          continue;
        }
        ConfigurationSection section = extraMobs.getConfigurationSection(id);
        String name = section == null ? null : section.getString("name");
        names.put(id, name);
      }
    }
    return names;
  }

  public static String name(File file, String id) {
    MobLocation loc = findMob(file, defaultFolder(file), id, false);
    if (loc == null) {
      return null;
    }
    ConfigurationSection mob = loc.section;
    return mob == null ? null : mob.getString("name");
  }

  public static String mainAbility(File file, String id) {
    MobLocation loc = findMob(file, defaultFolder(file), id, false);
    ConfigurationSection mob = loc == null ? null : loc.section;
    return mob == null ? null : mob.getString("attacks.main.ability");
  }

  public static String secondaryAbility(File file, String id) {
    MobLocation loc = findMob(file, defaultFolder(file), id, false);
    ConfigurationSection mob = loc == null ? null : loc.section;
    return mob == null ? null : mob.getString("attacks.secondary.ability");
  }

  public static Boolean showName(File file, String id) {
    MobLocation loc = findMob(file, defaultFolder(file), id, false);
    ConfigurationSection mob = loc == null ? null : loc.section;
    return mob == null || !mob.contains("showName") ? null : mob.getBoolean("showName");
  }

  public static Double stat(File file, String id, String key) {
    MobLocation loc = findMob(file, defaultFolder(file), id, false);
    ConfigurationSection mob = loc == null ? null : loc.section;
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
    update(file, defaultFolder(file), id, mob -> {
      if (name == null || name.isBlank()) {
        mob.set("name", null);
      } else {
        mob.set("name", name);
      }
    });
  }

  public static void setShowName(File file, String id, boolean show) {
    update(file, defaultFolder(file), id, mob -> mob.set("showName", show));
  }

  public static void setMainAbility(File file, String id, String ability) {
    update(file, defaultFolder(file), id, mob -> setString(mob, "attacks.main.ability", ability));
  }

  public static void setSecondaryAbility(File file, String id, String ability) {
    update(file, defaultFolder(file), id, mob -> setString(mob, "attacks.secondary.ability", ability));
  }

  public static void setStat(File file, String id, String key, Double value) {
    update(file, defaultFolder(file), id, mob -> {
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
    MobLocation loc = findMob(file, defaultFolder(file), id, false);
    ConfigurationSection mob = loc == null ? null : loc.section;
    if (mob == null) {
      return false;
    }
    ConfigurationSection loot = mob.getConfigurationSection("loot");
    return loot != null && loot.getBoolean("clearVanilla", false);
  }

  public static int lootRolls(File file, String id) {
    MobLocation loc = findMob(file, defaultFolder(file), id, false);
    ConfigurationSection mob = loc == null ? null : loc.section;
    if (mob == null) {
      return 1;
    }
    ConfigurationSection loot = mob.getConfigurationSection("loot");
    return loot == null ? 1 : loot.getInt("rolls", 1);
  }

  public static int lootBonusRolls(File file, String id) {
    MobLocation loc = findMob(file, defaultFolder(file), id, false);
    ConfigurationSection mob = loc == null ? null : loc.section;
    if (mob == null) {
      return 0;
    }
    ConfigurationSection loot = mob.getConfigurationSection("loot");
    return loot == null ? 0 : loot.getInt("bonusRolls", 0);
  }

  public static double lootLuckMultiplier(File file, String id) {
    MobLocation loc = findMob(file, defaultFolder(file), id, false);
    ConfigurationSection mob = loc == null ? null : loc.section;
    if (mob == null) {
      return 0.0;
    }
    ConfigurationSection loot = mob.getConfigurationSection("loot");
    return loot == null ? 0.0 : loot.getDouble("luckMultiplier", 0.0);
  }

  public static String lootAnnounceTemplate(File file, String id) {
    MobLocation loc = findMob(file, defaultFolder(file), id, false);
    ConfigurationSection mob = loc == null ? null : loc.section;
    if (mob == null) {
      return null;
    }
    ConfigurationSection loot = mob.getConfigurationSection("loot");
    return loot == null ? null : loot.getString("announceTemplate");
  }

  public static List<String> lootAnnounceTiers(File file, String id) {
    MobLocation loc = findMob(file, defaultFolder(file), id, false);
    ConfigurationSection mob = loc == null ? null : loc.section;
    if (mob == null) {
      return List.of();
    }
    ConfigurationSection loot = mob.getConfigurationSection("loot");
    return loot == null ? List.of() : loot.getStringList("announceTiers");
  }

  public static List<Map<String, Object>> lootEntries(File file, String id, String listKey) {
    MobLocation loc = findMob(file, defaultFolder(file), id, false);
    ConfigurationSection mob = loc == null ? null : loc.section;
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

  public static int passiveCount(File file, String id) {
    MobLocation loc = findMob(file, defaultFolder(file), id, false);
    ConfigurationSection mob = loc == null ? null : loc.section;
    if (mob == null) {
      return 0;
    }
    List<?> list = mob.getMapList("passives");
    return list == null ? 0 : list.size();
  }

  public static void setLootEntries(File file, String id, String listKey, List<Map<String, Object>> entries) {
    update(file, defaultFolder(file), id, mob -> {
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
    update(file, defaultFolder(file), id, mob -> {
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
    MobLocation loc = findMob(file, defaultFolder(file), id, false);
    if (loc == null) {
      throw new IllegalArgumentException("Unknown mob id: " + id);
    }
    ConfigurationSection mob = loc.section;
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

  private static void update(File file, File folder, String id, Consumer<ConfigurationSection> updater) {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(id, "id");
    if (folder != null) {
      folder.mkdirs();
    }
    MobLocation loc = findMob(file, folder, id, true);
    YamlConfiguration cfg = loc.cfg;
    ConfigurationSection mob = loc.section;
    updater.accept(mob);
    save(cfg, loc.file);
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

  private static MobLocation findMob(File file, File folder, String id, boolean create) {
    MobLocation inFile = findMobInFile(file, id, create);
    if (inFile != null && inFile.section != null) {
      return inFile;
    }
    for (File extra : listYamlFiles(folder)) {
      MobLocation inExtra = findMobInFile(extra, id, create);
      if (inExtra != null && inExtra.section != null) {
        return inExtra;
      }
    }
    if (!create) {
      return null;
    }
    File target = folder == null ? file : new File(folder, id + ".yml");
    return findMobInFile(target, id, true);
  }

  private static MobLocation findMobInFile(File file, String id, boolean create) {
    if (file == null) {
      return null;
    }
    YamlConfiguration cfg = load(file);
    ConfigurationSection mob = mobSection(cfg, id, create);
    if (mob == null) {
      return null;
    }
    return new MobLocation(file, cfg, mob);
  }

  private static File defaultFolder(File file) {
    if (file == null || file.getParentFile() == null) {
      return null;
    }
    return new File(file.getParentFile(), "mobs");
  }

  private static List<File> listYamlFiles(File folder) {
    if (folder == null || !folder.exists()) {
      return List.of();
    }
    File[] entries = folder.listFiles();
    if (entries == null) {
      return List.of();
    }
    List<File> out = new ArrayList<>();
    for (File entry : entries) {
      if (entry.isDirectory()) {
        continue;
      }
      String name = entry.getName().toLowerCase(java.util.Locale.ROOT);
      if (name.endsWith(".yml") || name.endsWith(".yaml")) {
        out.add(entry);
      }
    }
    out.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
    return out;
  }

  private record MobLocation(File file, YamlConfiguration cfg, ConfigurationSection section) {
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
