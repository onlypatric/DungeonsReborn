package dev.patric.dungeonsreborn.dungeons;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.editor.EditorItemLore;
import dev.patric.dungeonsreborn.logging.ServiceLogger;
import dev.patric.dungeonsreborn.system.SystemStatusStore;
import dev.patric.dungeonsreborn.util.WorldAllowlist;
import dev.patric.dungeonsreborn.util.YamlValues;
import net.kyori.adventure.text.Component;

public final class DungeonYamlRegistry {
  public record ReloadResult(boolean loaded, List<String> errors) {
  }

  private final JavaPlugin plugin;
  private final ServiceLogger logger;
  private final WorldAllowlist worldAllowlist;
  private DungeonSpec dungeon;
  private List<String> lastErrors = List.of();

  public DungeonYamlRegistry(JavaPlugin plugin, ServiceLogger logger, WorldAllowlist worldAllowlist) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.worldAllowlist = worldAllowlist;
  }

  public File file() {
    return new File(plugin.getDataFolder(), "dungeon.yml");
  }

  public DungeonSpec dungeon() {
    return dungeon;
  }

  public List<String> lastErrors() {
    return lastErrors;
  }

  public Component unavailableMessage() {
    String raw = plugin.getConfig().getString("dungeons.unavailableMessage", "Dungeons coming soon...");
    if (raw == null || raw.isBlank()) {
      raw = "Dungeons coming soon...";
    }
    return EditorItemLore.parseRichText(raw);
  }

  public ReloadResult reload() {
    ensureFile();
    List<String> errors = new ArrayList<>();
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file());
    DungeonSpec next = parseDungeon(cfg, errors);
    if (errors.isEmpty()) {
      dungeon = next;
    }
    lastErrors = List.copyOf(errors);
    if (!errors.isEmpty()) {
      logger.warn("[Dungeon] YAML reload had " + errors.size() + " errors");
      for (String error : errors) {
        logger.warn("[Dungeon] YAML: " + error);
      }
    } else if (next != null) {
      logger.info("[Dungeon] YAML loaded dungeon=" + next.id());
    } else {
      logger.info("[Dungeon] YAML has no dungeon configured");
    }
    SystemStatusStore.get().record(
        "dungeon",
        "Dungeon",
        file().getPath(),
        (errors.isEmpty() ? next : dungeon) == null ? "configured=false" : "configured=true",
        errors);
    return new ReloadResult((errors.isEmpty() ? next : dungeon) != null, errors);
  }

  private void ensureFile() {
    File file = file();
    if (file.exists()) {
      return;
    }
    plugin.saveResource("dungeon.yml", false);
  }

  private DungeonSpec parseDungeon(YamlConfiguration cfg, List<String> errors) {
    ConfigurationSection root = cfg.getConfigurationSection("dungeon");
    if (root == null) {
      return null;
    }
    String base = "dungeon";
    String rawId = YamlValues.string(root, "id", "dungeon");
    String id;
    try {
      id = Ids.normalize(rawId);
    } catch (IllegalArgumentException ex) {
      errors.add(base + ".id: " + ex.getMessage());
      id = "dungeon";
    }
    String nameRaw = YamlValues.string(root, "name", id);
    Component name = EditorItemLore.parseRichText(nameRaw);
    String world = YamlValues.string(root, "world", "");
    if (world.isBlank()) {
      errors.add(base + ".world: missing world");
    } else if (worldAllowlist != null && !worldAllowlist.allowAll() && !worldAllowlist.isAllowed(world)) {
      errors.add(base + ".world: not in world allowlist");
    }
    DungeonSpec.DungeonRegion region = parseRegion(root.getConfigurationSection("region"), base + ".region", errors);
    DungeonSpec.DungeonEntry entry = parseEntry(root.getConfigurationSection("entry"), base + ".entry", errors);
    DungeonSpec.DungeonQueueConfig queue = parseQueue(root.getConfigurationSection("queue"), base + ".queue", errors);
    Map<Integer, DungeonSpec.DungeonLevel> levels = parseLevels(root.getConfigurationSection("levels"), base + ".levels", errors);
    return new DungeonSpec(id, name, world, region, entry, queue, levels);
  }

  private DungeonSpec.DungeonRegion parseRegion(ConfigurationSection sec, String path, List<String> errors) {
    if (sec == null) {
      errors.add(path + ": missing region");
      return null;
    }
    DungeonSpec.DungeonPoint min = parsePoint(sec.get("min"), path + ".min", errors);
    DungeonSpec.DungeonPoint max = parsePoint(sec.get("max"), path + ".max", errors);
    if (min == null || max == null) {
      return null;
    }
    return new DungeonSpec.DungeonRegion(min, max);
  }

  private DungeonSpec.DungeonEntry parseEntry(ConfigurationSection sec, String path, List<String> errors) {
    if (sec == null) {
      errors.add(path + ": missing entry");
      return null;
    }
    DungeonSpec.DungeonPoint spawn = parsePoint(sec.get("spawn"), path + ".spawn", errors);
    DungeonSpec.DungeonPoint exit = parsePoint(sec.get("exit"), path + ".exit", errors);
    if (spawn == null || exit == null) {
      return null;
    }
    return new DungeonSpec.DungeonEntry(spawn, exit);
  }

  private DungeonSpec.DungeonQueueConfig parseQueue(ConfigurationSection sec, String path, List<String> errors) {
    if (sec == null) {
      return new DungeonSpec.DungeonQueueConfig(0, 0);
    }
    int maxSize = Math.max(0, sec.getInt("maxSizePerLevel", 0));
    int timeout = Math.max(0, sec.getInt("entryTimeoutSeconds", 0));
    if (maxSize < 0) {
      errors.add(path + ".maxSizePerLevel: must be >= 0");
      maxSize = 0;
    }
    if (timeout < 0) {
      errors.add(path + ".entryTimeoutSeconds: must be >= 0");
      timeout = 0;
    }
    return new DungeonSpec.DungeonQueueConfig(maxSize, timeout);
  }

  private Map<Integer, DungeonSpec.DungeonLevel> parseLevels(ConfigurationSection sec, String path, List<String> errors) {
    if (sec == null) {
      errors.add(path + ": missing levels");
      return Map.of();
    }
    Map<Integer, DungeonSpec.DungeonLevel> out = new LinkedHashMap<>();
    for (String rawLevel : sec.getKeys(false)) {
      String levelPath = path + "." + rawLevel;
      ConfigurationSection node = sec.getConfigurationSection(rawLevel);
      if (node == null) {
        errors.add(levelPath + ": must be an object");
        continue;
      }
      int level;
      try {
        level = Integer.parseInt(rawLevel);
      } catch (NumberFormatException ex) {
        errors.add(levelPath + ": level key must be an integer");
        continue;
      }
      DungeonSpec.DungeonLevel parsed = parseLevel(node, level, levelPath, errors);
      if (parsed != null) {
        out.put(level, parsed);
      }
    }
    return out;
  }

  private DungeonSpec.DungeonLevel parseLevel(ConfigurationSection node, int level, String path, List<String> errors) {
    int queueTokens = Math.max(0, node.getInt("queueTokens", 0));
    int waitSeconds = Math.max(0, node.getInt("waitSeconds", 0));
    int timeLimitSeconds = Math.max(0, node.getInt("timeLimitSeconds", 0));
    DungeonSpec.DungeonCheckpoint checkpoint = parseCheckpoint(node.getConfigurationSection("checkpoint"),
        path + ".checkpoint", errors);
    DungeonSpec.DungeonModifiers modifiers = parseModifiers(node.getConfigurationSection("modifiers"),
        path + ".modifiers", errors);
    List<DungeonSpec.DungeonSpawnPoint> spawnPoints = parseSpawnPoints(node.getMapList("spawnPoints"), path + ".spawnPoints", errors);
    Map<String, String> overrides = parseOverrides(node.getConfigurationSection("spawnOverrides"), path + ".spawnOverrides", errors);
    List<DungeonSpec.DungeonWave> waves = parseWaves(node.getList("waves"), path + ".waves", errors);
    DungeonSpec.DungeonWave bossWave = parseBossWave(node.get("bossWave"), path + ".bossWave", errors);
    String bossMob = parseBossMob(node.get("boss"), path + ".boss", errors);
    DungeonSpec.DungeonReward rewards = parseRewards(node.getConfigurationSection("rewards"), path + ".rewards", errors);
    return new DungeonSpec.DungeonLevel(level, queueTokens, waitSeconds, timeLimitSeconds, checkpoint, modifiers,
        spawnPoints, overrides, waves, bossWave, bossMob, rewards);
  }

  private List<DungeonSpec.DungeonSpawnPoint> parseSpawnPoints(List<Map<?, ?>> list, String path, List<String> errors) {
    if (list == null || list.isEmpty()) {
      return List.of();
    }
    List<DungeonSpec.DungeonSpawnPoint> out = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      Map<?, ?> map = list.get(i);
      String base = path + "[" + i + "]";
      String id = YamlValues.string(map, "id", "point_" + i);
      DungeonSpec.DungeonPoint pos = parsePoint(map.get("pos"), base + ".pos", errors);
      if (pos == null) {
        continue;
      }
      out.add(new DungeonSpec.DungeonSpawnPoint(id, pos));
    }
    return out;
  }

  private DungeonSpec.DungeonCheckpoint parseCheckpoint(ConfigurationSection sec, String path, List<String> errors) {
    if (sec == null) {
      return new DungeonSpec.DungeonCheckpoint(false, false, null);
    }
    boolean enabled = sec.getBoolean("enabled", false);
    boolean onWave = sec.getBoolean("onWave", false);
    DungeonSpec.DungeonPoint location = parsePoint(sec.get("location"), path + ".location", errors);
    return new DungeonSpec.DungeonCheckpoint(enabled, onWave, location);
  }

  private DungeonSpec.DungeonModifiers parseModifiers(ConfigurationSection sec, String path, List<String> errors) {
    if (sec == null) {
      return new DungeonSpec.DungeonModifiers(1.0, 1.0, List.of());
    }
    double health = Math.max(0.1, sec.getDouble("healthMultiplier", 1.0));
    double damage = Math.max(0.0, sec.getDouble("damageMultiplier", 1.0));
    List<String> affixes = new ArrayList<>();
    for (String raw : sec.getStringList("affixes")) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      affixes.add(raw.trim());
    }
    return new DungeonSpec.DungeonModifiers(health, damage, affixes);
  }

  private Map<String, String> parseOverrides(ConfigurationSection sec, String path, List<String> errors) {
    if (sec == null) {
      return Map.of();
    }
    Map<String, String> out = new LinkedHashMap<>();
    for (String key : sec.getKeys(false)) {
      String mobId;
      try {
        mobId = Ids.normalize(key);
      } catch (IllegalArgumentException ex) {
        errors.add(path + "." + key + ": " + ex.getMessage());
        continue;
      }
      String target = YamlValues.string(sec, key, "");
      if (target.isBlank()) {
        errors.add(path + "." + key + ": missing spawn point id");
        continue;
      }
      out.put(mobId, target);
    }
    return out;
  }

  private List<DungeonSpec.DungeonWave> parseWaves(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return List.of();
    }
    if (!(raw instanceof List<?> list)) {
      errors.add(path + ": expected list");
      return List.of();
    }
    List<DungeonSpec.DungeonWave> out = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      Object entry = list.get(i);
      String base = path + "[" + i + "]";
      DungeonSpec.DungeonWave wave = parseWave(entry, base, errors);
      if (wave != null) {
        out.add(wave);
      }
    }
    return out;
  }

  private DungeonSpec.DungeonWave parseBossWave(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    return parseWave(raw, path, errors);
  }

  private DungeonSpec.DungeonWave parseWave(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    List<String> mobs = new ArrayList<>();
    if (raw instanceof Map<?, ?> map) {
      Object list = map.get("mobs");
      mobs.addAll(parseMobList(list, path + ".mobs", errors));
    } else if (raw instanceof List<?> list) {
      mobs.addAll(parseMobList(list, path, errors));
    } else {
      errors.add(path + ": expected list or object with mobs");
      return null;
    }
    if (mobs.isEmpty()) {
      errors.add(path + ": missing mobs");
      return null;
    }
    return new DungeonSpec.DungeonWave(List.copyOf(mobs));
  }

  private List<String> parseMobList(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return List.of();
    }
    if (!(raw instanceof List<?> list)) {
      errors.add(path + ": expected list");
      return List.of();
    }
    List<String> mobs = new ArrayList<>();
    for (Object entry : list) {
      if (entry == null) {
        continue;
      }
      String id = String.valueOf(entry).trim();
      if (id.isEmpty()) {
        continue;
      }
      try {
        mobs.add(Ids.normalize(id));
      } catch (IllegalArgumentException ex) {
        errors.add(path + ": invalid mob id " + id);
      }
    }
    return mobs;
  }

  private String parseBossMob(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof Map<?, ?> map) {
      raw = map.get("mob");
    }
    String id = String.valueOf(raw).trim();
    if (id.isEmpty()) {
      errors.add(path + ": missing boss mob");
      return null;
    }
    try {
      return Ids.normalize(id);
    } catch (IllegalArgumentException ex) {
      errors.add(path + ": " + ex.getMessage());
      return null;
    }
  }

  private DungeonSpec.DungeonReward parseRewards(ConfigurationSection sec, String path, List<String> errors) {
    if (sec == null) {
      return null;
    }
    DungeonSpec.IntRange tokens = parseTokenRange(sec.get("tokens"), path + ".tokens", errors);
    int skillPoints = Math.max(0, sec.getInt("skillPoints", 0));
    List<DungeonSpec.DungeonExtraLoot> extra = parseExtraLoot(sec.getMapList("extraLoot"), path + ".extraLoot", errors);
    return new DungeonSpec.DungeonReward(tokens, skillPoints, extra);
  }

  private DungeonSpec.IntRange parseTokenRange(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return new DungeonSpec.IntRange(0, 0);
    }
    if (raw instanceof List<?> list) {
      if (list.size() < 2) {
        errors.add(path + ": expected [min, max]");
        return new DungeonSpec.IntRange(0, 0);
      }
      int min = YamlValues.intValue(list.get(0), 0);
      int max = YamlValues.intValue(list.get(1), min);
      return new DungeonSpec.IntRange(Math.max(0, min), Math.max(0, max));
    }
    if (raw instanceof Map<?, ?> map) {
      int min = YamlValues.intValue(map.get("min"), 0);
      int max = YamlValues.intValue(map.get("max"), min);
      return new DungeonSpec.IntRange(Math.max(0, min), Math.max(0, max));
    }
    errors.add(path + ": expected list or map");
    return new DungeonSpec.IntRange(0, 0);
  }

  private List<DungeonSpec.DungeonExtraLoot> parseExtraLoot(List<Map<?, ?>> list, String path, List<String> errors) {
    if (list == null || list.isEmpty()) {
      return List.of();
    }
    List<DungeonSpec.DungeonExtraLoot> out = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      Map<?, ?> map = list.get(i);
      String base = path + "[" + i + "]";
      String itemId = YamlValues.string(map, "itemId", "");
      if (itemId.isBlank()) {
        errors.add(base + ".itemId: missing item id");
        continue;
      }
      int chance = Math.max(0, Math.min(100, YamlValues.intValue(map.get("chancePercent"), 0)));
      out.add(new DungeonSpec.DungeonExtraLoot(itemId, chance));
    }
    return out;
  }

  private DungeonSpec.DungeonPoint parsePoint(Object raw, String path, List<String> errors) {
    if (raw == null) {
      errors.add(path + ": missing point");
      return null;
    }
    if (raw instanceof ConfigurationSection sec) {
      raw = sec.getValues(false);
    }
    if (raw instanceof Map<?, ?> map) {
      int x = YamlValues.intValue(map.get("x"), 0);
      int y = YamlValues.intValue(map.get("y"), 0);
      int z = YamlValues.intValue(map.get("z"), 0);
      return new DungeonSpec.DungeonPoint(x, y, z);
    }
    if (raw instanceof List<?> list) {
      if (list.size() < 3) {
        errors.add(path + ": expected [x, y, z]");
        return null;
      }
      int x = YamlValues.intValue(list.get(0), 0);
      int y = YamlValues.intValue(list.get(1), 0);
      int z = YamlValues.intValue(list.get(2), 0);
      return new DungeonSpec.DungeonPoint(x, y, z);
    }
    errors.add(path + ": invalid point");
    return null;
  }
}
