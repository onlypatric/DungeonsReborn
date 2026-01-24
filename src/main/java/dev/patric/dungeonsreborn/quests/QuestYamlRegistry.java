package dev.patric.dungeonsreborn.quests;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.shops.ShopAvailabilitySpec;
import dev.patric.dungeonsreborn.shops.ShopTimeWindowSpec;
import dev.patric.dungeonsreborn.system.SystemStatusStore;
import dev.patric.dungeonsreborn.util.YamlValues;

public final class QuestYamlRegistry {
  public record ReloadResult(int loaded, List<String> errors) {
  }

  private static final int CURRENT_SCHEMA_VERSION = 2;

  private final JavaPlugin plugin;
  private final Logger logger;
  private final Function<String, ItemStack> itemResolver;
  private final Map<String, QuestSpec> quests = new LinkedHashMap<>();
  private final Map<String, QuestRotationPoolSpec> rotationPools = new LinkedHashMap<>();
  private final Map<String, List<QuestRotationPoolSpec>> rotationPoolsByQuest = new LinkedHashMap<>();
  private List<String> lastErrors = List.of();
  private QuestValidationReport lastReport = new QuestValidationReport(1, CURRENT_SCHEMA_VERSION, 0, 0, 0, 0,
      List.of());

  public QuestYamlRegistry(JavaPlugin plugin, Logger logger, Function<String, ItemStack> itemResolver) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.itemResolver = itemResolver;
  }

  public File file() {
    return new File(plugin.getDataFolder(), "quests.yml");
  }

  public File questsDir() {
    return new File(plugin.getDataFolder(), "quests");
  }

  public Map<String, QuestSpec> quests() {
    return Map.copyOf(quests);
  }

  public Map<String, QuestRotationPoolSpec> rotationPools() {
    return Map.copyOf(rotationPools);
  }

  public List<QuestRotationPoolSpec> rotationPoolsForQuest(String questId) {
    if (questId == null) {
      return List.of();
    }
    return rotationPoolsByQuest.getOrDefault(Ids.normalize(questId), List.of());
  }

  public QuestSpec quest(String id) {
    if (id == null) {
      return null;
    }
    return quests.get(Ids.normalize(id));
  }

  public List<String> lastErrors() {
    return lastErrors;
  }

  public QuestValidationReport validationReport() {
    return lastReport;
  }

  public ReloadResult reload() {
    ensureFile();
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    Map<String, QuestSpec> next = new LinkedHashMap<>();
    Map<String, QuestRotationPoolSpec> pools = new LinkedHashMap<>();
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file());
    int schemaVersion = readSchemaVersion(cfg, warnings);
    mergeQuests(next, parseQuests(cfg, errors, file().getPath()), errors, file().getPath());
    mergeRotationPools(pools, parseRotationPools(cfg, errors, file().getPath()), errors, file().getPath());
    File[] files = questsDir().listFiles((dir, name) -> name.endsWith(".yml"));
    if (files != null) {
      java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
      for (File questFile : files) {
        YamlConfiguration questCfg = YamlConfiguration.loadConfiguration(questFile);
        mergeQuests(next, parseQuests(questCfg, errors, questFile.getPath()), errors, questFile.getPath());
        mergeRotationPools(pools, parseRotationPools(questCfg, errors, questFile.getPath()), errors, questFile.getPath());
      }
    }
    if (errors.isEmpty()) {
      quests.clear();
      quests.putAll(next);
      rotationPools.clear();
      rotationPools.putAll(pools);
      rebuildRotationPoolIndex();
    }
    lastErrors = List.copyOf(errors);
    lastReport = buildValidationReport(schemaVersion, warnings, errors, next, pools);
    if (!errors.isEmpty()) {
      logger.warning("[Quests] YAML reload had " + errors.size() + " errors");
      for (String error : errors) {
        logger.warning("[Quests] YAML: " + error);
      }
    } else {
      logger.info("[Quests] YAML loaded " + next.size() + " quests");
      if (!warnings.isEmpty()) {
        logger.warning("[Quests] YAML validation had " + warnings.size() + " warnings");
        for (String warning : warnings) {
          logger.warning("[Quests] YAML: " + warning);
        }
      }
    }
    SystemStatusStore.get().record(
        "quests",
        "Quests",
        file().getPath(),
        "quests=" + (errors.isEmpty() ? next.size() : quests.size()) + "; schemaVersion=" + schemaVersion,
        errors);
    return new ReloadResult(errors.isEmpty() ? next.size() : quests.size(), errors);
  }

  private void ensureFile() {
    File file = file();
    if (file.exists()) {
      File dir = questsDir();
      if (!dir.exists()) {
        dir.mkdirs();
      }
      return;
    }
    plugin.saveResource("quests.yml", false);
    File dir = questsDir();
    if (!dir.exists()) {
      dir.mkdirs();
    }
  }

  private Map<String, QuestSpec> parseQuests(YamlConfiguration cfg, List<String> errors, String source) {
    ConfigurationSection questsSec = cfg.getConfigurationSection("quests");
    if (questsSec == null) {
      return Map.of();
    }
    Map<String, QuestSpec> out = new LinkedHashMap<>();
    for (String rawId : questsSec.getKeys(false)) {
      String base = "quests." + rawId;
      String baseWithSource = source == null || source.isBlank() ? base : source + ": " + base;
      ConfigurationSection node = questsSec.getConfigurationSection(rawId);
      if (node == null) {
        errors.add(baseWithSource + ": must be an object");
        continue;
      }
      try {
        String id = Ids.normalize(rawId);
        String name = YamlValues.string(node, "name", id);
        boolean enabled = node.getBoolean("enabled", true);
        List<String> description = parseDescription(node.get("description"));
        QuestRequirements requirements = parseRequirements(node.getConfigurationSection("requirements"),
            baseWithSource + ".requirements", errors);
        requirements = mergePrerequisites(requirements, node.get("prerequisites"),
            baseWithSource + ".prerequisites", errors);
        requirements = mergePrerequisites(requirements, node.get("requires"),
            baseWithSource + ".requires", errors);
        QuestRewards rewards = parseRewards(node.getConfigurationSection("rewards"), baseWithSource + ".rewards",
            errors);
        List<QuestObjectiveSpec> objectives = parseObjectives(node.getList("objectives"),
            baseWithSource + ".objectives", errors);
        if (objectives.isEmpty()) {
          errors.add(baseWithSource + ".objectives: at least one objective is required");
        }
        long cooldownSeconds = Math.max(0L, node.getLong("cooldownSeconds", 0L));
        QuestRotation rotation = QuestRotation.parse(node.getString("rotation"));
        QuestRepeatSpec repeat = parseRepeatSpec(node, baseWithSource + ".repeat", errors);
        long progressThrottleSeconds = Math.max(0L, node.getLong("progressThrottleSeconds", 0L));
        ConfigurationSection progressSection = node.getConfigurationSection("progress");
        if (progressSection != null) {
          progressThrottleSeconds = Math.max(0L, progressSection.getLong("throttleSeconds",
              progressSection.getLong("throttle", progressThrottleSeconds)));
        }
        QuestPartyShareSpec partyShare = parsePartyShare(node, baseWithSource + ".partyShare", errors);
        boolean partyLocked = false;
        Object partyLockRaw = node.get("partyLocked");
        if (partyLockRaw == null) {
          partyLockRaw = node.get("party_lock");
        }
        if (partyLockRaw == null) {
          partyLockRaw = node.get("party_locked");
        }
        if (partyLockRaw == null) {
          partyLockRaw = node.get("partyRequired");
        }
        if (partyLockRaw == null) {
          ConfigurationSection partySection = node.getConfigurationSection("party");
          if (partySection != null) {
            partyLockRaw = partySection.get("locked");
            if (partyLockRaw == null) {
              partyLockRaw = partySection.get("partyLocked");
            }
            if (partyLockRaw == null) {
              partyLockRaw = partySection.get("required");
            }
          }
        }
        if (partyLockRaw != null) {
          partyLocked = YamlValues.bool(partyLockRaw, false);
        }
        String rotationPool = YamlValues.string(node, "rotationPool",
            YamlValues.string(node, "rotation_pool", YamlValues.string(node, "pool", null)));
        if (rotationPool != null && !rotationPool.isBlank()) {
          try {
            rotationPool = Ids.normalize(rotationPool);
          } catch (IllegalArgumentException ex) {
            errors.add(baseWithSource + ".rotationPool: invalid pool id " + rotationPool);
            rotationPool = null;
          }
        }
        QuestBranchLock branchLock = QuestBranchLock.parse(null);
        String branchId = null;
        ConfigurationSection branchSection = node.getConfigurationSection("branch");
        if (branchSection != null) {
          branchId = YamlValues.string(branchSection, "id", YamlValues.string(branchSection, "group", null));
          branchLock = QuestBranchLock.parse(YamlValues.string(branchSection, "lockout", null));
        } else {
          branchId = YamlValues.string(node, "branchId", YamlValues.string(node, "branch", null));
          branchLock = QuestBranchLock.parse(YamlValues.string(node, "branchLockout", null));
        }
        if (branchId != null && !branchId.isBlank()) {
          try {
            branchId = Ids.normalize(branchId);
          } catch (IllegalArgumentException ex) {
            errors.add(baseWithSource + ".branch: invalid branch id " + branchId);
            branchId = null;
          }
        }
        QuestFailSpec fail = parseFailSpec(node, baseWithSource + ".fail", errors);
        QuestVisibilitySpec visibility = parseVisibility(node, baseWithSource + ".visibility", errors);
        List<String> categories = parseStringList(node.get("categories"));
        if (categories.isEmpty()) {
          categories = parseStringList(node.get("category"));
        }
        String tier = YamlValues.string(node, "tier", null);
        if (tier != null && tier.isBlank()) {
          tier = null;
        }
        List<String> tags = parseStringList(node.get("tags"));
        out.put(id, new QuestSpec(id, name, enabled, description, requirements, rewards, objectives,
            cooldownSeconds, rotation, repeat, progressThrottleSeconds, partyShare, partyLocked, rotationPool,
            branchId, branchLock, fail, visibility, categories, tier, tags));
      } catch (Exception ex) {
        errors.add(baseWithSource + ": " + ex.getMessage());
      }
    }
    return out;
  }

  private QuestRepeatSpec parseRepeatSpec(ConfigurationSection node, String base, List<String> errors) {
    if (node == null) {
      return QuestRepeatSpec.none();
    }
    int daily = 0;
    int weekly = 0;
    Object repeatRaw = node.get("repeat");
    if (repeatRaw == null) {
      repeatRaw = node.get("repeatLimits");
    }
    if (repeatRaw == null) {
      repeatRaw = node.get("repeat_limits");
    }
    if (repeatRaw instanceof ConfigurationSection section) {
      repeatRaw = section.getValues(false);
    }
    if (repeatRaw instanceof Map<?, ?> map) {
      daily = Math.max(0, YamlValues.intValue(map.get("daily"),
          YamlValues.intValue(map.get("dailyLimit"),
              YamlValues.intValue(map.get("daily_limit"), 0))));
      weekly = Math.max(0, YamlValues.intValue(map.get("weekly"),
          YamlValues.intValue(map.get("weeklyLimit"),
              YamlValues.intValue(map.get("weekly_limit"), 0))));
    } else {
      daily = Math.max(0, node.getInt("repeatDaily", 0));
      weekly = Math.max(0, node.getInt("repeatWeekly", 0));
    }
    if (daily < 0) {
      errors.add(base + ".daily: must be >= 0");
      daily = 0;
    }
    if (weekly < 0) {
      errors.add(base + ".weekly: must be >= 0");
      weekly = 0;
    }
    if (daily == 0 && weekly == 0) {
      return QuestRepeatSpec.none();
    }
    return new QuestRepeatSpec(daily, weekly);
  }

  private QuestVisibilitySpec parseVisibility(ConfigurationSection node, String base, List<String> errors) {
    if (node == null) {
      return QuestVisibilitySpec.visible();
    }
    Object raw = node.get("visibility");
    if (raw == null) {
      raw = node.get("hidden");
      if (raw == null) {
        return QuestVisibilitySpec.visible();
      }
    }
    Map<String, Object> map = castMap(raw, base, errors);
    if (map == null) {
      boolean hidden = YamlValues.bool(raw, false);
      if (!hidden) {
        return QuestVisibilitySpec.visible();
      }
      return new QuestVisibilitySpec(true, true, true, List.of(), List.of(), List.of());
    }
    boolean hidden = YamlValues.bool(map.get("hidden"), false);
    boolean showInLog = YamlValues.bool(map.get("showInLog"), true);
    boolean showInGiver = YamlValues.bool(map.get("showInGiver"), true);
    List<String> hints = parseStringList(pickFirst(map, "hints", "hint"));
    List<QuestRequiredStatus> revealOn = parseRevealOn(pickFirst(map, "revealOn", "reveal", "showOn"));
    List<QuestVisibilityCondition> requires = parseVisibilityConditions(pickFirst(map, "requires", "conditions", "if"),
        base + ".requires", errors);
    if (!hidden && hints.isEmpty() && revealOn.isEmpty() && requires.isEmpty()) {
      return QuestVisibilitySpec.visible();
    }
    return new QuestVisibilitySpec(hidden, showInLog, showInGiver, hints, revealOn, requires);
  }

  private List<QuestRequiredStatus> parseRevealOn(Object raw) {
    if (raw == null) {
      return List.of();
    }
    List<QuestRequiredStatus> out = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object entry : list) {
        QuestRequiredStatus status = QuestRequiredStatus.parse(YamlValues.string(entry, null));
        if (status != null) {
          out.add(status);
        }
      }
      return List.copyOf(out);
    }
    QuestRequiredStatus single = QuestRequiredStatus.parse(YamlValues.string(raw, null));
    return single == null ? List.of() : List.of(single);
  }

  private List<QuestVisibilityCondition> parseVisibilityConditions(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return List.of();
    }
    List<QuestVisibilityCondition> out = new ArrayList<>();
    if (raw instanceof Map<?, ?> map) {
      QuestVisibilityCondition condition = parseVisibilityCondition(map, path, errors);
      if (condition != null) {
        out.add(condition);
      }
      return List.copyOf(out);
    }
    if (raw instanceof List<?> list) {
      int index = 0;
      for (Object entry : list) {
        String entryPath = path + "[" + index + "]";
        index++;
        if (entry instanceof String rawText) {
          String trimmed = rawText.trim();
          if (trimmed.isEmpty()) {
            continue;
          }
          String[] parts = trimmed.split(":", 2);
          String questId = parts[0].trim();
          if (questId.isEmpty()) {
            continue;
          }
          try {
            questId = Ids.normalize(questId);
          } catch (IllegalArgumentException ex) {
            errors.add(entryPath + ": invalid quest id " + questId.toLowerCase(Locale.ROOT));
            continue;
          }
          QuestRequiredStatus required = parts.length > 1 ? QuestRequiredStatus.parse(parts[1].trim()) : null;
          out.add(new QuestVisibilityCondition(questId, required));
          continue;
        }
        if (entry instanceof Map<?, ?> map) {
          QuestVisibilityCondition condition = parseVisibilityCondition(map, entryPath, errors);
          if (condition != null) {
            out.add(condition);
          }
        }
      }
      return List.copyOf(out);
    }
    return List.of();
  }

  private QuestVisibilityCondition parseVisibilityCondition(Map<?, ?> map, String path, List<String> errors) {
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
    QuestRequiredStatus required = QuestRequiredStatus.parse(YamlValues.string(map.get("status"), null));
    return new QuestVisibilityCondition(questId, required);
  }

  private Object pickFirst(Map<String, Object> map, String... keys) {
    for (String key : keys) {
      if (map.containsKey(key)) {
        return map.get(key);
      }
    }
    return null;
  }

  private void mergeQuests(Map<String, QuestSpec> target, Map<String, QuestSpec> incoming, List<String> errors,
      String source) {
    if (incoming == null || incoming.isEmpty()) {
      return;
    }
    for (Map.Entry<String, QuestSpec> entry : incoming.entrySet()) {
      String id = entry.getKey();
      if (target.containsKey(id)) {
        String prefix = source == null || source.isBlank() ? "" : source + ": ";
        errors.add(prefix + "duplicate quest id " + id);
        continue;
      }
      target.put(id, entry.getValue());
    }
  }

  private void mergeRotationPools(Map<String, QuestRotationPoolSpec> target, Map<String, QuestRotationPoolSpec> incoming,
      List<String> errors, String source) {
    if (incoming == null || incoming.isEmpty()) {
      return;
    }
    for (Map.Entry<String, QuestRotationPoolSpec> entry : incoming.entrySet()) {
      String id = entry.getKey();
      if (target.containsKey(id)) {
        String prefix = source == null || source.isBlank() ? "" : source + ": ";
        errors.add(prefix + "duplicate rotation pool id " + id);
        continue;
      }
      target.put(id, entry.getValue());
    }
  }

  private Map<String, QuestRotationPoolSpec> parseRotationPools(YamlConfiguration cfg, List<String> errors,
      String source) {
    ConfigurationSection poolsSec = cfg.getConfigurationSection("rotationPools");
    if (poolsSec == null) {
      poolsSec = cfg.getConfigurationSection("rotation_pools");
    }
    if (poolsSec == null) {
      return Map.of();
    }
    Map<String, QuestRotationPoolSpec> out = new LinkedHashMap<>();
    for (String rawId : poolsSec.getKeys(false)) {
      String base = "rotationPools." + rawId;
      String baseWithSource = source == null || source.isBlank() ? base : source + ": " + base;
      ConfigurationSection node = poolsSec.getConfigurationSection(rawId);
      if (node == null) {
        errors.add(baseWithSource + ": must be an object");
        continue;
      }
      String id;
      try {
        id = Ids.normalize(rawId);
      } catch (IllegalArgumentException ex) {
        errors.add(baseWithSource + ": invalid pool id " + rawId);
        continue;
      }
      QuestRotation rotation = QuestRotation.parse(node.getString("rotation"));
      QuestRotationPoolScope scope = QuestRotationPoolScope.parse(node.getString("scope"));
      int size = Math.max(0, node.getInt("size", node.getInt("count", 0)));
      List<String> questIds = parseStringList(node.get("quests"));
      if (questIds.isEmpty()) {
        questIds = parseStringList(node.get("questIds"));
      }
      List<String> normalized = new ArrayList<>();
      for (String questId : questIds) {
        if (questId == null || questId.isBlank()) {
          continue;
        }
        try {
          normalized.add(Ids.normalize(questId));
        } catch (IllegalArgumentException ex) {
          errors.add(baseWithSource + ".quests: invalid quest id " + questId);
        }
      }
      out.put(id, new QuestRotationPoolSpec(id, rotation, scope, size, List.copyOf(normalized)));
    }
    return out;
  }

  private void rebuildRotationPoolIndex() {
    rotationPoolsByQuest.clear();
    for (QuestRotationPoolSpec pool : rotationPools.values()) {
      for (String questId : pool.questIds()) {
        rotationPoolsByQuest.computeIfAbsent(questId, key -> new ArrayList<>()).add(pool);
      }
    }
    for (Map.Entry<String, List<QuestRotationPoolSpec>> entry : rotationPoolsByQuest.entrySet()) {
      entry.setValue(List.copyOf(entry.getValue()));
    }
  }

  private List<String> parseDescription(Object raw) {
    if (raw == null) {
      return List.of();
    }
    if (raw instanceof List<?> list) {
      List<String> out = new ArrayList<>();
      for (Object entry : list) {
        if (entry == null) {
          continue;
        }
        String line = String.valueOf(entry);
        if (!line.isBlank()) {
          out.add(line);
        }
      }
      return List.copyOf(out);
    }
    String single = String.valueOf(raw).trim();
    return single.isBlank() ? List.of() : List.of(single);
  }

  private QuestRequirements parseRequirements(ConfigurationSection section, String base, List<String> errors) {
    if (section == null) {
      return QuestRequirements.empty();
    }
    int level = Math.max(0, section.getInt("level", 0));
    List<String> quests = new ArrayList<>();
    for (String questId : section.getStringList("quests")) {
      if (questId == null || questId.isBlank()) {
        continue;
      }
      try {
        quests.add(Ids.normalize(questId));
      } catch (IllegalArgumentException ex) {
        errors.add(base + ".quests: invalid quest id " + questId);
      }
    }
    List<String> permissions = parseStringList(section.get("permissions"));
    if (permissions.isEmpty()) {
      permissions = parseStringList(section.get("permission"));
    }
    List<String> classIds = parseStringList(section.get("classes"));
    if (classIds.isEmpty()) {
      classIds = parseStringList(section.get("class"));
    }
    List<String> skillNodes = parseStringList(section.get("skillNodes"));
    if (skillNodes.isEmpty()) {
      skillNodes = parseStringList(section.get("skills"));
    }
    int minCustomLevel = 0;
    long minCustomPoints = 0L;
    ConfigurationSection customXp = section.getConfigurationSection("customXp");
    if (customXp != null) {
      minCustomLevel = Math.max(0, customXp.getInt("level", 0));
      minCustomPoints = Math.max(0L, customXp.getLong("points", 0L));
    } else {
      minCustomLevel = Math.max(0, section.getInt("customLevel", 0));
      minCustomPoints = Math.max(0L, section.getLong("customPoints", 0L));
    }
    String factionId = null;
    int minFactionRank = 0;
    ConfigurationSection faction = section.getConfigurationSection("faction");
    if (faction != null) {
      factionId = YamlValues.string(faction, "id", null);
      minFactionRank = Math.max(0, faction.getInt("rank", faction.getInt("minRank", 0)));
    }
    List<QuestStageRequirement> questStages = parseQuestStages(section.get("questStages"),
        base + ".questStages", errors);
    if (questStages.isEmpty()) {
      questStages = parseQuestStages(section.get("questStage"), base + ".questStage", errors);
    }
    ConfigurationSection accept = section.getConfigurationSection("accept");
    List<String> acceptWorlds = parseStringList(accept == null ? section.get("acceptWorlds") : accept.get("worlds"));
    List<QuestRegion> acceptRegions = parseRegions(accept == null ? section.get("acceptRegions") : accept.get("regions"),
        base + ".acceptRegions", errors);
    if (acceptRegions.isEmpty()) {
      acceptRegions = parseRegions(accept == null ? section.get("acceptRegion") : accept.get("region"),
          base + ".acceptRegion", errors);
    }
    ShopAvailabilitySpec availability = parseAvailability(
        accept == null ? section.get("availability") : accept.get("availability"),
        base + ".availability", errors);
    ConfigurationSection turnIn = section.getConfigurationSection("turnIn");
    if (turnIn == null) {
      turnIn = section.getConfigurationSection("turn-in");
    }
    List<String> turnInWorlds = parseStringList(turnIn == null ? section.get("turnInWorlds") : turnIn.get("worlds"));
    List<QuestRegion> turnInRegions = parseRegions(turnIn == null ? section.get("turnInRegions") : turnIn.get("regions"),
        base + ".turnInRegions", errors);
    if (turnInRegions.isEmpty()) {
      turnInRegions = parseRegions(turnIn == null ? section.get("turnInRegion") : turnIn.get("region"),
          base + ".turnInRegion", errors);
    }
    ShopAvailabilitySpec turnInAvailability = parseAvailability(
        turnIn == null ? section.get("turnInAvailability") : turnIn.get("availability"),
        base + ".turnInAvailability", errors);
    return new QuestRequirements(level, List.copyOf(quests), List.copyOf(permissions), List.copyOf(classIds),
        List.copyOf(skillNodes), minCustomLevel, minCustomPoints, factionId, minFactionRank, List.copyOf(questStages),
        List.copyOf(acceptWorlds), List.copyOf(acceptRegions), List.copyOf(turnInWorlds), List.copyOf(turnInRegions),
        availability, turnInAvailability);
  }

  private QuestRewards parseRewards(ConfigurationSection section, String base, List<String> errors) {
    if (section == null) {
      return QuestRewards.empty();
    }
    int xp = section.getInt("xp", 0);
    int tokens = section.getInt("tokens", section.getInt("token", section.getInt("normal", 0)));
    int compressed = section.getInt("compressed", 0);
    int pallet = section.getInt("pallet", 0);
    double mana = section.getDouble("mana", 0.0);
    if (xp < 0) {
      errors.add(base + ".xp: must be >= 0");
      xp = 0;
    }
    if (tokens < 0) {
      errors.add(base + ".tokens: must be >= 0");
      tokens = 0;
    }
    if (compressed < 0) {
      errors.add(base + ".compressed: must be >= 0");
      compressed = 0;
    }
    if (pallet < 0) {
      errors.add(base + ".pallet: must be >= 0");
      pallet = 0;
    }
    if (mana < 0.0) {
      errors.add(base + ".mana: must be >= 0");
      mana = 0.0;
    }
    Map<String, Double> resources = new java.util.LinkedHashMap<>();
    ConfigurationSection resourcesSec = section.getConfigurationSection("resources");
    if (resourcesSec != null) {
      for (String key : resourcesSec.getKeys(false)) {
        double amount = resourcesSec.getDouble(key, 0.0);
        if (amount < 0.0) {
          errors.add(base + ".resources." + key + ": must be >= 0");
          continue;
        }
        if (amount > 0.0) {
          resources.put(key.trim(), amount);
        }
      }
    }
    List<QuestRewardItem> items = new ArrayList<>();
    List<?> list = section.getList("items");
    if (list != null) {
      for (int i = 0; i < list.size(); i++) {
        Object raw = list.get(i);
        String path = base + ".items[" + i + "]";
        try {
          items.add(parseRewardItem(raw, path, errors));
        } catch (Exception ex) {
          errors.add(path + ": " + ex.getMessage());
        }
      }
    }
    List<QuestRewardEntry> entries = parseRewardEntries(section.getList("entries"), base + ".entries", errors);
    if (entries.isEmpty()) {
      entries = parseRewardEntries(section.getList("extraRewards"), base + ".extraRewards", errors);
    }
    List<QuestRewardPool> pools = parseRewardPools(section.getList("pools"), base + ".pools", errors);
    if (pools.isEmpty()) {
      pools = parseRewardPools(section.getList("rewardPools"), base + ".rewardPools", errors);
    }
    QuestRewardScaling scaling = parseRewardScaling(section.getConfigurationSection("scale"),
        base + ".scale", errors);
    if (scaling == null) {
      scaling = parseRewardScaling(section.getConfigurationSection("scaling"), base + ".scaling", errors);
    }
    return new QuestRewards(xp, tokens, compressed, pallet, mana, Map.copyOf(resources), List.copyOf(items),
        List.copyOf(entries), List.copyOf(pools), scaling == null ? QuestRewardScaling.none() : scaling);
  }

  private QuestRequirements mergePrerequisites(QuestRequirements requirements, Object raw, String path,
      List<String> errors) {
    if (raw == null || requirements == null) {
      return requirements;
    }
    List<String> values = parseStringList(raw);
    if (values.isEmpty()) {
      return requirements;
    }
    List<String> merged = new ArrayList<>(requirements.quests());
    for (String questId : values) {
      if (questId == null || questId.isBlank()) {
        continue;
      }
      try {
        merged.add(Ids.normalize(questId));
      } catch (IllegalArgumentException ex) {
        errors.add(path + ": invalid quest id " + questId);
      }
    }
    return new QuestRequirements(
        requirements.level(),
        List.copyOf(merged),
        requirements.permissions(),
        requirements.classIds(),
        requirements.skillNodes(),
        requirements.minCustomLevel(),
        requirements.minCustomPoints(),
        requirements.factionId(),
        requirements.minFactionRank(),
        requirements.questStages(),
        requirements.acceptWorlds(),
        requirements.acceptRegions(),
        requirements.turnInWorlds(),
        requirements.turnInRegions(),
        requirements.availability(),
        requirements.turnInAvailability());
  }

  private QuestRewardItem parseRewardItem(Object raw, String path, List<String> errors) {
    if (raw == null) {
      throw new IllegalArgumentException("item must be an object");
    }
    if (raw instanceof ConfigurationSection sec) {
      raw = sec.getValues(false);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException("item must be an object");
    }
    String typeRaw = YamlValues.string(map, "type", null);
    if (typeRaw == null) {
      if (map.containsKey("upgradeId") || map.containsKey("upgrade")) {
        typeRaw = "itemId";
      } else
      if (map.containsKey("itemId") || map.containsKey("id")) {
        typeRaw = "itemId";
      } else if (map.containsKey("material")) {
        typeRaw = "material";
      } else if (map.containsKey("item")) {
        typeRaw = "itemstack";
      }
    }
    QuestRewardItemType type = parseRewardType(typeRaw, path + ".type");
    int amount = Math.max(1, YamlValues.intValue(map.get("amount"), 1));
    return switch (type) {
      case ITEM_ID -> {
        String itemId = YamlValues.string(map, "upgradeId",
            YamlValues.string(map, "upgrade",
                YamlValues.string(map, "itemId", YamlValues.string(map, "id", null))));
        if (itemId != null && !itemId.isBlank()) {
          itemId = Ids.normalize(itemId);
        }
        yield new QuestRewardItem(type, itemId, null, null, amount);
      }
      case MATERIAL -> {
        String materialRaw = YamlValues.string(map, "material", null);
        Material material = parseMaterial(materialRaw, path + ".material", errors);
        yield new QuestRewardItem(type, null, material, null, amount);
      }
      case ITEMSTACK -> {
        Object itemRaw = map.get("item");
        ItemStack item = parseItemStack(itemRaw, path + ".item", errors);
        yield new QuestRewardItem(type, null, null, item, amount);
      }
    };
  }

  private List<QuestRewardEntry> parseRewardEntries(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return List.of();
    }
    if (raw instanceof ConfigurationSection sec) {
      raw = sec.getValues(false);
    }
    if (raw instanceof Map<?, ?> single) {
      raw = List.of(single);
    }
    if (!(raw instanceof List<?> list) || list.isEmpty()) {
      return List.of();
    }
    List<QuestRewardEntry> out = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      Object entry = list.get(i);
      String entryPath = path + "[" + i + "]";
      try {
        out.add(parseRewardEntry(entry, entryPath, errors));
      } catch (Exception ex) {
        errors.add(entryPath + ": " + ex.getMessage());
      }
    }
    return List.copyOf(out);
  }

  private QuestRewardEntry parseRewardEntry(Object raw, String path, List<String> errors) {
    if (raw == null) {
      throw new IllegalArgumentException("reward entry must be an object");
    }
    if (raw instanceof ConfigurationSection sec) {
      raw = sec.getValues(false);
    }
    if (!(raw instanceof Map<?, ?> rawMap)) {
      throw new IllegalArgumentException("reward entry must be an object");
    }
    Map<String, Object> map = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
      if (entry.getKey() == null) {
        continue;
      }
      map.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    String typeRaw = YamlValues.string(map, "type", null);
    if (typeRaw == null) {
      if (map.containsKey("xp")) {
        typeRaw = "xp";
      } else if (map.containsKey("tokens") || map.containsKey("token")) {
        typeRaw = "tokens";
      } else if (map.containsKey("compressed")) {
        typeRaw = "compressed";
      } else if (map.containsKey("pallet")) {
        typeRaw = "pallet";
      } else if (map.containsKey("mana")) {
        typeRaw = "mana";
      } else if (map.containsKey("resource") || map.containsKey("resourceId")) {
        typeRaw = "resource";
      } else if (map.containsKey("currency") || map.containsKey("currencyId") || map.containsKey("currency_id")) {
        typeRaw = "currency";
      } else if (map.containsKey("item") || map.containsKey("itemId") || map.containsKey("material")
          || map.containsKey("cosmetic") || map.containsKey("cosmeticId")) {
        typeRaw = "item";
      } else if (map.containsKey("title") || map.containsKey("subtitle")) {
        typeRaw = "title";
      } else if (map.containsKey("buff") || map.containsKey("effect") || map.containsKey("potion")) {
        typeRaw = "buff";
      } else if (map.containsKey("quest") || map.containsKey("questId") || map.containsKey("unlock")) {
        typeRaw = "unlockQuest";
      } else if (map.containsKey("faction") || map.containsKey("factionId")) {
        typeRaw = "faction";
      }
    }
    QuestRewardEntryType type = parseRewardEntryType(typeRaw, path + ".type");
    int weight = Math.max(1, YamlValues.intValue(map.get("weight"), 1));
    double chance = parseChance(map.get("chance"));
    return switch (type) {
      case XP -> new QuestRewardEntry(type, null,
          YamlValues.doubleValue(map.get("amount"), YamlValues.doubleValue(map.get("xp"), 0.0)),
          null, null, null, weight, chance);
      case TOKENS -> new QuestRewardEntry(type, null,
          YamlValues.doubleValue(map.get("amount"),
              YamlValues.doubleValue(map.get("tokens"), YamlValues.doubleValue(map.get("token"), 0.0))),
          null, null, null, weight, chance);
      case COMPRESSED -> new QuestRewardEntry(type, null,
          YamlValues.doubleValue(map.get("amount"), YamlValues.doubleValue(map.get("compressed"), 0.0)),
          null, null, null, weight, chance);
      case PALLET -> new QuestRewardEntry(type, null,
          YamlValues.doubleValue(map.get("amount"), YamlValues.doubleValue(map.get("pallet"), 0.0)),
          null, null, null, weight, chance);
      case MANA -> new QuestRewardEntry(type, null,
          YamlValues.doubleValue(map.get("amount"), YamlValues.doubleValue(map.get("mana"), 0.0)),
          null, null, null, weight, chance);
      case RESOURCE -> {
        String resourceId = YamlValues.string(map, "resource",
            YamlValues.string(map, "resourceId", null));
        yield new QuestRewardEntry(type, resourceId,
            YamlValues.doubleValue(map.get("amount"), 0.0),
            null, null, null, weight, chance);
      }
      case CURRENCY -> {
        String currencyId = YamlValues.string(map, "currency",
            YamlValues.string(map, "currencyId", YamlValues.string(map, "currency_id", null)));
        if (currencyId != null && !currencyId.isBlank()) {
          currencyId = Ids.normalize(currencyId);
        }
        yield new QuestRewardEntry(type, currencyId,
            YamlValues.doubleValue(map.get("amount"), 0.0),
            null, null, null, weight, chance);
      }
      case ITEM -> {
        if (map.containsKey("cosmetic") && !map.containsKey("itemId") && !map.containsKey("id")) {
          map = new java.util.LinkedHashMap<>(map);
          map.put("itemId", map.get("cosmetic"));
        }
        if (map.containsKey("cosmeticId") && !map.containsKey("itemId") && !map.containsKey("id")) {
          map = new java.util.LinkedHashMap<>(map);
          map.put("itemId", map.get("cosmeticId"));
        }
        String mapType = YamlValues.string(map, "type", null);
        if (mapType != null) {
          String normalizedType = mapType.trim().toLowerCase(Locale.ROOT);
          if (normalizedType.equals("cosmetic") || normalizedType.equals("cosmetic_id")) {
            map = new java.util.LinkedHashMap<>(map);
            map.put("type", "itemId");
          }
        }
        QuestRewardItem item = parseRewardItem(map, path + ".item", errors);
        yield new QuestRewardEntry(type, null, item.amount(), item, null, null, weight, chance);
      }
      case TITLE -> {
        QuestRewardTitle title = parseRewardTitle(map, path + ".title");
        yield new QuestRewardEntry(type, null, 0.0, null, title, null, weight, chance);
      }
      case BUFF -> {
        QuestRewardBuff buff = parseRewardBuff(map, path + ".buff");
        yield new QuestRewardEntry(type, null, 0.0, null, null, buff, weight, chance);
      }
      case UNLOCK_QUEST -> {
        String questId = YamlValues.string(map, "quest", YamlValues.string(map, "questId",
            YamlValues.string(map, "unlock", null)));
        if (questId != null && !questId.isBlank()) {
          questId = Ids.normalize(questId);
        }
        yield new QuestRewardEntry(type, questId, 0.0, null, null, null, weight, chance);
      }
      case FACTION_REP -> {
        String factionId = YamlValues.string(map, "faction", YamlValues.string(map, "factionId", null));
        if (factionId != null && !factionId.isBlank()) {
          factionId = Ids.normalize(factionId);
        }
        yield new QuestRewardEntry(type, factionId,
            YamlValues.doubleValue(map.get("amount"), YamlValues.doubleValue(map.get("rep"), 0.0)),
            null, null, null, weight, chance);
      }
    };
  }

  private QuestRewardEntryType parseRewardEntryType(String raw, String path) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException(path + ": type is required");
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "xp", "experience" -> QuestRewardEntryType.XP;
      case "tokens", "token" -> QuestRewardEntryType.TOKENS;
      case "compressed" -> QuestRewardEntryType.COMPRESSED;
      case "pallet", "pallets" -> QuestRewardEntryType.PALLET;
      case "mana" -> QuestRewardEntryType.MANA;
      case "resource", "resource_id", "resourceid" -> QuestRewardEntryType.RESOURCE;
      case "item", "itemid", "item_id", "material", "itemstack", "stack", "cosmetic", "cosmetic_id"
          -> QuestRewardEntryType.ITEM;
      case "currency", "currencies", "money" -> QuestRewardEntryType.CURRENCY;
      case "title", "titles" -> QuestRewardEntryType.TITLE;
      case "buff", "buffs", "effect", "potion" -> QuestRewardEntryType.BUFF;
      case "unlock", "unlock_quest", "quest_unlock", "quest" -> QuestRewardEntryType.UNLOCK_QUEST;
      case "faction", "reputation", "rep" -> QuestRewardEntryType.FACTION_REP;
      default -> throw new IllegalArgumentException(path + ": unknown type " + raw);
    };
  }

  private QuestRewardTitle parseRewardTitle(Map<?, ?> map, String path) {
    String title = YamlValues.string(map, "title", "");
    String subtitle = YamlValues.string(map, "subtitle", "");
    int fadeIn = YamlValues.intValue(map.get("fadeInTicks"), -1);
    if (fadeIn < 0) {
      fadeIn = YamlValues.intValue(map.get("fadeIn"), -1);
    }
    if (fadeIn < 0) {
      fadeIn = secondsToTicks(YamlValues.intValue(map.get("fadeInSeconds"), 10));
    }
    int stay = YamlValues.intValue(map.get("stayTicks"), -1);
    if (stay < 0) {
      stay = YamlValues.intValue(map.get("stay"), -1);
    }
    if (stay < 0) {
      stay = secondsToTicks(YamlValues.intValue(map.get("staySeconds"), 40));
    }
    int fadeOut = YamlValues.intValue(map.get("fadeOutTicks"), -1);
    if (fadeOut < 0) {
      fadeOut = YamlValues.intValue(map.get("fadeOut"), -1);
    }
    if (fadeOut < 0) {
      fadeOut = secondsToTicks(YamlValues.intValue(map.get("fadeOutSeconds"), 10));
    }
    return new QuestRewardTitle(title, subtitle, fadeIn, stay, fadeOut);
  }

  private QuestRewardBuff parseRewardBuff(Map<?, ?> map, String path) {
    String effectRaw = YamlValues.string(map, "effect", YamlValues.string(map, "type",
        YamlValues.string(map, "potion", null)));
    PotionEffectType type = parsePotionEffect(effectRaw, path + ".effect");
    int duration = YamlValues.intValue(map.get("durationTicks"), -1);
    if (duration < 0) {
      duration = YamlValues.intValue(map.get("duration"), -1);
    }
    if (duration < 0) {
      duration = secondsToTicks(YamlValues.intValue(map.get("durationSeconds"), 0));
    }
    int amplifier = Math.max(0, YamlValues.intValue(map.get("amplifier"), YamlValues.intValue(map.get("amp"), 0)));
    boolean ambient = YamlValues.bool(map.get("ambient"), false);
    boolean particles = YamlValues.bool(map.get("particles"), true);
    boolean icon = YamlValues.bool(map.get("icon"), true);
    return new QuestRewardBuff(type, duration, amplifier, ambient, particles, icon);
  }

  private List<QuestRewardPool> parseRewardPools(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return List.of();
    }
    if (raw instanceof ConfigurationSection sec) {
      raw = sec.getValues(false);
    }
    if (raw instanceof Map<?, ?> single) {
      raw = List.of(single);
    }
    if (!(raw instanceof List<?> list) || list.isEmpty()) {
      return List.of();
    }
    List<QuestRewardPool> out = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      Object entry = list.get(i);
      String entryPath = path + "[" + i + "]";
      try {
        out.add(parseRewardPool(entry, entryPath, errors));
      } catch (Exception ex) {
        errors.add(entryPath + ": " + ex.getMessage());
      }
    }
    return List.copyOf(out);
  }

  private QuestRewardPool parseRewardPool(Object raw, String path, List<String> errors) {
    if (raw == null) {
      throw new IllegalArgumentException("pool must be an object");
    }
    if (raw instanceof ConfigurationSection sec) {
      raw = sec.getValues(false);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException("pool must be an object");
    }
    String id = YamlValues.string(map, "id", null);
    int rolls = Math.max(1, YamlValues.intValue(map.get("rolls"), YamlValues.intValue(map.get("count"), 1)));
    boolean unique = YamlValues.bool(map.get("unique"), false);
    List<QuestRewardEntry> entries = parseRewardEntries(map.get("entries"), path + ".entries", errors);
    if (entries.isEmpty()) {
      entries = parseRewardEntries(map.get("items"), path + ".items", errors);
    }
    if (entries.isEmpty()) {
      throw new IllegalArgumentException("pool must define entries");
    }
    return new QuestRewardPool(id, rolls, unique, entries);
  }

  private QuestRewardScaling parseRewardScaling(ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return null;
    }
    double levelFactor = YamlValues.doubleValue(section.get("levelFactor"), section.getDouble("level", 0.0));
    double partyFactor = YamlValues.doubleValue(section.get("partyFactor"), section.getDouble("party", 0.0));
    double min = section.getDouble("min", 1.0);
    double max = section.getDouble("max", 1.0);
    boolean applyToItems = section.getBoolean("items", false);
    if (min <= 0.0) {
      errors.add(path + ".min: must be > 0");
      min = 1.0;
    }
    if (max < min) {
      errors.add(path + ".max: must be >= min");
      max = min;
    }
    return new QuestRewardScaling(levelFactor, partyFactor, min, max, applyToItems);
  }

  private double parseChance(Object raw) {
    double chance = YamlValues.doubleValue(raw, 1.0);
    if (!Double.isFinite(chance) || chance <= 0.0) {
      return 0.0;
    }
    if (chance > 1.0 && chance <= 100.0) {
      chance = chance / 100.0;
    }
    if (chance > 1.0) {
      chance = 1.0;
    }
    return chance;
  }

  private PotionEffectType parsePotionEffect(String raw, String path) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException(path + ": empty potion effect");
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    org.bukkit.NamespacedKey key = normalized.contains(":")
        ? org.bukkit.NamespacedKey.fromString(normalized)
        : org.bukkit.NamespacedKey.minecraft(normalized);
    PotionEffectType type = key == null ? null : org.bukkit.Registry.POTION_EFFECT_TYPE.get(key);
    if (type == null) {
      throw new IllegalArgumentException(path + ": invalid potion effect=" + raw);
    }
    return type;
  }

  private int secondsToTicks(int seconds) {
    if (seconds <= 0) {
      return 0;
    }
    long ticks = Math.round(seconds * 20.0);
    if (ticks > Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    return (int) ticks;
  }

  private QuestRewardItemType parseRewardType(String raw, String path) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException(path + ": type is required");
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "itemid", "item_id", "item" -> QuestRewardItemType.ITEM_ID;
      case "material" -> QuestRewardItemType.MATERIAL;
      case "itemstack", "stack" -> QuestRewardItemType.ITEMSTACK;
      default -> throw new IllegalArgumentException(path + ": unknown type " + raw);
    };
  }

  private List<QuestObjectiveSpec> parseObjectives(List<?> raw, String path, List<String> errors) {
    if (raw == null) {
      return List.of();
    }
    List<QuestObjectiveSpec> out = new ArrayList<>();
    for (int i = 0; i < raw.size(); i++) {
      Object entry = raw.get(i);
      String entryPath = path + "[" + i + "]";
      parseObjectiveEntry(entry, entryPath, errors, out, null, QuestCompositeMode.NONE, 0, 0L, false, -1);
    }
    return List.copyOf(out);
  }

  private void parseObjectiveEntry(Object entry, String path, List<String> errors, List<QuestObjectiveSpec> out,
      String groupId, QuestCompositeMode mode, int inheritedStage, long inheritedTimeLimit, boolean inheritedOptional,
      int sequenceOrder) {
    if (entry instanceof ConfigurationSection sec) {
      entry = sec.getValues(false);
    }
    if (!(entry instanceof Map<?, ?> map)) {
      errors.add(path + ": objective must be an object");
      return;
    }
    String compositeKey = null;
    QuestCompositeMode compositeMode = QuestCompositeMode.NONE;
    if (map.containsKey("all_of")) {
      compositeKey = "all_of";
      compositeMode = QuestCompositeMode.ALL_OF;
    } else if (map.containsKey("any_of")) {
      compositeKey = "any_of";
      compositeMode = QuestCompositeMode.ANY_OF;
    } else if (map.containsKey("sequence")) {
      compositeKey = "sequence";
      compositeMode = QuestCompositeMode.SEQUENCE;
    } else if (map.containsKey("optional") && (map.get("optional") instanceof List || map.get("optional") instanceof Map)) {
      compositeKey = "optional";
      compositeMode = mode;
    }

    int stage = Math.max(0, YamlValues.intValue(map.get("stage"), inheritedStage));
    long timeLimit = Math.max(0L, YamlValues.longValue(map.get("timeLimitSeconds"), inheritedTimeLimit));
    boolean optional = inheritedOptional || YamlValues.bool(map.get("optional"), false);

    if (compositeKey != null) {
      Object listRaw = map.get(compositeKey);
      if (listRaw instanceof ConfigurationSection sec) {
        listRaw = sec.getValues(false);
      }
      if (listRaw instanceof Map<?, ?> single) {
        listRaw = List.of(single);
      }
      if (!(listRaw instanceof List<?> list) || list.isEmpty()) {
        errors.add(path + "." + compositeKey + ": must be a non-empty list");
        return;
      }
      String nextGroupId = groupId == null ? path + "." + compositeKey : groupId;
      boolean nextOptional = optional || "optional".equals(compositeKey);
      for (int i = 0; i < list.size(); i++) {
        Object child = list.get(i);
        String childPath = path + "." + compositeKey + "[" + i + "]";
        int order = compositeMode == QuestCompositeMode.SEQUENCE ? i : -1;
        parseObjectiveEntry(child, childPath, errors, out, nextGroupId,
            compositeMode == QuestCompositeMode.NONE ? mode : compositeMode,
            stage, timeLimit, nextOptional, order);
      }
      return;
    }

    try {
      QuestObjectiveSpec spec = parseObjective(map, path, errors);
      int order = sequenceOrder >= 0 ? sequenceOrder : YamlValues.intValue(map.get("order"), 0);
      out.add(spec.withMeta(groupId, mode, order, optional, stage, timeLimit));
    } catch (Exception ex) {
      errors.add(path + ": " + ex.getMessage());
    }
  }

  private QuestObjectiveSpec parseObjective(Map<?, ?> map, String path, List<String> errors) {
    String typeRaw = YamlValues.string(map, "type", null);
    QuestObjectiveType type = QuestObjectiveType.parse(typeRaw);
    QuestPartyRole partyRole = QuestPartyRole.parse(YamlValues.string(map, "party", YamlValues.string(map, "partyRole", null)));
    QuestObjectiveShareSpec share = parseObjectiveShare(map, path, errors);
    if (type == null) {
      if (map.containsKey("mob") || map.containsKey("mobId") || map.containsKey("entity")) {
        type = QuestObjectiveType.KILL_MOB;
      } else if (map.containsKey("itemId") || map.containsKey("material") || map.containsKey("item")) {
        type = QuestObjectiveType.USE_ITEM;
      } else if (map.containsKey("world") || map.containsKey("region")) {
        type = QuestObjectiveType.VISIT_REGION;
      } else if (map.containsKey("recipeId") || map.containsKey("craft")) {
        type = QuestObjectiveType.CRAFT_ITEM;
      } else if (map.containsKey("break") || map.containsKey("breakBlock")) {
        type = QuestObjectiveType.BREAK_BLOCK;
      } else if (map.containsKey("place") || map.containsKey("placeBlock")) {
        type = QuestObjectiveType.PLACE_BLOCK;
      }
    }
    if (type == null) {
      throw new IllegalArgumentException("type is required");
    }
    int count = Math.max(1, YamlValues.intValue(map.get("count"), 1));
    QuestObjectiveSpec spec = switch (type) {
      case KILL_MOB -> parseKillObjective(map, path, errors, count);
      case USE_ITEM -> parseUseItemObjective(map, path, errors, count);
      case VISIT_REGION -> parseVisitRegionObjective(map, path, errors);
      case CRAFT_ITEM -> parseCraftObjective(map, path, errors, count);
      case BREAK_BLOCK -> parseBreakObjective(map, path, errors, count);
      case PLACE_BLOCK -> parsePlaceObjective(map, path, errors, count);
    };
    return spec.withPartyRole(partyRole).withShare(share);
  }

  private QuestObjectiveSpec parseKillObjective(Map<?, ?> map, String path, List<String> errors, int count) {
    String mobId = YamlValues.string(map, "mob", YamlValues.string(map, "mobId", null));
    if (mobId != null && !mobId.isBlank()) {
      try {
        mobId = Ids.normalize(mobId);
      } catch (IllegalArgumentException ex) {
        errors.add(path + ".mob: invalid mob id " + mobId);
        mobId = null;
      }
    }
    String entityRaw = YamlValues.string(map, "entity", YamlValues.string(map, "entityType", null));
    org.bukkit.entity.EntityType entityType = null;
    if (entityRaw != null && !entityRaw.isBlank()) {
      entityType = org.bukkit.entity.EntityType.fromName(entityRaw.toLowerCase(Locale.ROOT));
      if (entityType == null) {
        try {
          entityType = org.bukkit.entity.EntityType.valueOf(entityRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
          errors.add(path + ".entity: invalid entity type " + entityRaw);
        }
      }
    }
    String tier = YamlValues.string(map, "tier", YamlValues.string(map, "mobTier", null));
    String phase = YamlValues.string(map, "phase", YamlValues.string(map, "mobPhase", null));
    String variant = YamlValues.string(map, "variant", YamlValues.string(map, "mobVariant", null));
    String trait = YamlValues.string(map, "trait", YamlValues.string(map, "mobTrait", null));
    List<String> tags = parseStringList(map.get("mobTags"));
    if (tags.isEmpty()) {
      tags = parseStringList(map.get("tags"));
    }
    return new QuestObjectiveSpec(QuestObjectiveType.KILL_MOB, mobId, entityType, tier, phase, variant, trait, tags,
        null, null, List.of(), Map.of(), List.of(), null, null, List.of(), List.of(), List.of(), null, count,
        QuestPartyRole.ANY, null, QuestCompositeMode.NONE, 0, false, 0, 0L, QuestObjectiveShareSpec.none());
  }

  private QuestObjectiveSpec parseUseItemObjective(Map<?, ?> map, String path, List<String> errors, int count) {
    String itemId = YamlValues.string(map, "itemId", YamlValues.string(map, "id", null));
    if (itemId != null && !itemId.isBlank()) {
      itemId = Ids.normalize(itemId);
    }
    String materialRaw = YamlValues.string(map, "material", null);
    Material material = parseMaterial(materialRaw, path + ".material", errors);
    List<String> tags = parseStringList(map.get("itemTags"));
    if (tags.isEmpty()) {
      tags = parseStringList(map.get("tags"));
    }
    List<String> lore = parseStringList(map.get("loreContains"));
    if (lore.isEmpty()) {
      lore = parseStringList(map.get("lore"));
    }
    Integer customModelData = null;
    if (map.containsKey("customModelData")) {
      customModelData = YamlValues.intValue(map.get("customModelData"), 0);
    } else if (map.containsKey("custom_model_data")) {
      customModelData = YamlValues.intValue(map.get("custom_model_data"), 0);
    }
    Map<String, String> pdc = parseStringMap(map.get("pdc"), path + ".pdc", errors);
    return new QuestObjectiveSpec(QuestObjectiveType.USE_ITEM, null, null, null, null, null, null, List.of(),
        itemId, material, tags, pdc, lore, customModelData, null, List.of(), List.of(), List.of(), null, count,
        QuestPartyRole.ANY, null, QuestCompositeMode.NONE, 0, false, 0, 0L, QuestObjectiveShareSpec.none());
  }

  private QuestObjectiveSpec parseVisitRegionObjective(Map<?, ?> map, String path, List<String> errors) {
    String world = YamlValues.string(map, "world", null);
    Object regionRaw = map.get("region");
    if (regionRaw instanceof Map<?, ?> regionMap) {
      world = YamlValues.string(regionMap, "world", world);
      map = regionMap;
      path = path + ".region";
    }
    if (world == null || world.isBlank()) {
      errors.add(path + ": world is required");
    }
    double x = YamlValues.doubleValue(map.get("x"), 0.0);
    double y = YamlValues.doubleValue(map.get("y"), 0.0);
    double z = YamlValues.doubleValue(map.get("z"), 0.0);
    double radius = Math.max(0.1, YamlValues.doubleValue(map.get("radius"), 1.0));
    List<String> worlds = parseStringList(map.get("worlds"));
    if (world != null && !world.isBlank()) {
      worlds = new java.util.ArrayList<>(worlds);
      worlds.add(0, world);
    }
    List<String> biomes = parseStringList(map.get("biomes"));
    List<String> structures = parseStringList(map.get("structures"));
    QuestRegion region = new QuestRegion(world == null ? "" : world, x, y, z, radius);
    return new QuestObjectiveSpec(QuestObjectiveType.VISIT_REGION, null, null, null, null, null, null, List.of(),
        null, null, List.of(), Map.of(), List.of(), null, region, worlds, biomes, structures, null, 1,
        QuestPartyRole.ANY, null, QuestCompositeMode.NONE, 0, false, 0, 0L, QuestObjectiveShareSpec.none());
  }

  private QuestObjectiveSpec parseCraftObjective(Map<?, ?> map, String path, List<String> errors, int count) {
    String recipeId = YamlValues.string(map, "recipeId", YamlValues.string(map, "recipe", null));
    if (recipeId != null && !recipeId.isBlank()) {
      recipeId = Ids.normalize(recipeId);
    }
    String itemId = YamlValues.string(map, "itemId", YamlValues.string(map, "id", null));
    if (itemId != null && !itemId.isBlank()) {
      itemId = Ids.normalize(itemId);
    }
    String materialRaw = YamlValues.string(map, "material", null);
    Material material = parseMaterial(materialRaw, path + ".material", errors);
    List<String> tags = parseStringList(map.get("itemTags"));
    if (tags.isEmpty()) {
      tags = parseStringList(map.get("tags"));
    }
    List<String> lore = parseStringList(map.get("loreContains"));
    if (lore.isEmpty()) {
      lore = parseStringList(map.get("lore"));
    }
    Integer customModelData = null;
    if (map.containsKey("customModelData")) {
      customModelData = YamlValues.intValue(map.get("customModelData"), 0);
    } else if (map.containsKey("custom_model_data")) {
      customModelData = YamlValues.intValue(map.get("custom_model_data"), 0);
    }
    Map<String, String> pdc = parseStringMap(map.get("pdc"), path + ".pdc", errors);
    return new QuestObjectiveSpec(QuestObjectiveType.CRAFT_ITEM, null, null, null, null, null, null, List.of(),
        itemId, material, tags, pdc, lore, customModelData, null, List.of(), List.of(), List.of(), recipeId, count,
        QuestPartyRole.ANY, null, QuestCompositeMode.NONE, 0, false, 0, 0L, QuestObjectiveShareSpec.none());
  }

  private QuestObjectiveSpec parseBreakObjective(Map<?, ?> map, String path, List<String> errors, int count) {
    String materialRaw = YamlValues.string(map, "material", YamlValues.string(map, "block", null));
    Material material = parseMaterial(materialRaw, path + ".material", errors);
    return QuestObjectiveSpec.breakBlock(material, count);
  }

  private QuestObjectiveSpec parsePlaceObjective(Map<?, ?> map, String path, List<String> errors, int count) {
    String materialRaw = YamlValues.string(map, "material", YamlValues.string(map, "block", null));
    Material material = parseMaterial(materialRaw, path + ".material", errors);
    return QuestObjectiveSpec.placeBlock(material, count);
  }

  private ItemStack parseItemStack(Object raw, String path, List<String> errors) {
    if (raw == null) {
      errors.add(path + ": missing item");
      return null;
    }
    if (raw instanceof ItemStack stack) {
      return stack.clone();
    }
    if (raw instanceof ConfigurationSection sec) {
      raw = sec.getValues(false);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      errors.add(path + ": invalid item");
      return null;
    }
    ItemStack built = itemResolver == null ? null : itemResolver.apply(YamlValues.string(map, "id", null));
    if (built != null) {
      return built;
    }
    String materialRaw = YamlValues.string(map, "material", null);
    Material material = parseMaterial(materialRaw, path + ".material", errors);
    if (material == null) {
      return null;
    }
    ItemStack item = new ItemStack(material);
    int amount = Math.max(1, YamlValues.intValue(map.get("amount"), 1));
    item.setAmount(amount);
    return item;
  }

  private Material parseMaterial(String raw, String path, List<String> errors) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    Material material = Material.matchMaterial(raw);
    if (material == null) {
      errors.add(path + ": invalid material " + raw);
      return null;
    }
    return material;
  }

  private List<String> parseStringList(Object raw) {
    if (raw == null) {
      return List.of();
    }
    if (raw instanceof String s) {
      if (s.contains(",")) {
        String[] parts = s.split(",");
        List<String> out = new java.util.ArrayList<>();
        for (String part : parts) {
          if (part == null) {
            continue;
          }
          String trimmed = part.trim();
          if (!trimmed.isEmpty()) {
            out.add(trimmed);
          }
        }
        return out;
      }
      return s.isBlank() ? List.of() : List.of(s.trim());
    }
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<String> out = new java.util.ArrayList<>();
    for (Object entry : list) {
      if (entry == null) {
        continue;
      }
      String value = String.valueOf(entry).trim();
      if (!value.isEmpty()) {
        out.add(value);
      }
    }
    return out;
  }

  private Map<String, String> parseStringMap(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return Map.of();
    }
    if (raw instanceof ConfigurationSection section) {
      raw = section.getValues(false);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      errors.add(path + ": expected map");
      return Map.of();
    }
    Map<String, String> out = new LinkedHashMap<>();
    for (var entry : map.entrySet()) {
      if (entry.getKey() == null) {
        continue;
      }
      String key = String.valueOf(entry.getKey()).trim();
      if (key.isEmpty()) {
        continue;
      }
      String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
      out.put(key, value);
    }
    return out;
  }

  private List<QuestStageRequirement> parseQuestStages(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return List.of();
    }
    if (raw instanceof ConfigurationSection section) {
      raw = section.getValues(false);
    }
    List<?> list = null;
    if (raw instanceof List<?> rawList) {
      list = rawList;
    } else if (raw instanceof Map<?, ?> single) {
      list = List.of(single);
    }
    if (list == null) {
      errors.add(path + ": must be a list or object");
      return List.of();
    }
    List<QuestStageRequirement> out = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      String entryPath = path + "[" + i + "]";
      Map<String, Object> entry = castMap(list.get(i), entryPath, errors);
      if (entry == null) {
        continue;
      }
      String questId = YamlValues.string(entry, "quest", YamlValues.string(entry, "id", null));
      if (questId == null || questId.isBlank()) {
        errors.add(entryPath + ".quest: required");
        continue;
      }
      try {
        questId = Ids.normalize(questId);
      } catch (IllegalArgumentException ex) {
        errors.add(entryPath + ".quest: invalid quest id " + questId);
        continue;
      }
      int stage = Math.max(0, YamlValues.intValue(entry.get("stage"), 0));
      out.add(new QuestStageRequirement(questId, stage));
    }
    return out.isEmpty() ? List.of() : List.copyOf(out);
  }

  private List<QuestRegion> parseRegions(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return List.of();
    }
    if (raw instanceof ConfigurationSection section) {
      raw = section.getValues(false);
    }
    List<?> list = null;
    if (raw instanceof List<?> rawList) {
      list = rawList;
    } else if (raw instanceof Map<?, ?> single) {
      list = List.of(single);
    }
    if (list == null) {
      errors.add(path + ": must be a list or object");
      return List.of();
    }
    List<QuestRegion> out = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      String entryPath = path + "[" + i + "]";
      Map<String, Object> entry = castMap(list.get(i), entryPath, errors);
      if (entry == null) {
        continue;
      }
      QuestRegion region = parseRegion(entry, entryPath, errors);
      if (region != null) {
        out.add(region);
      }
    }
    return out.isEmpty() ? List.of() : List.copyOf(out);
  }

  private QuestRegion parseRegion(Map<String, Object> map, String path, List<String> errors) {
    String world = YamlValues.string(map.get("world"), "");
    double x = YamlValues.doubleValue(map.get("x"), 0.0);
    double y = YamlValues.doubleValue(map.get("y"), 0.0);
    double z = YamlValues.doubleValue(map.get("z"), 0.0);
    double radius = YamlValues.doubleValue(map.get("radius"), YamlValues.doubleValue(map.get("r"), 0.0));
    if (radius <= 0.0) {
      errors.add(path + ".radius: must be > 0");
      return null;
    }
    if (world == null || world.isBlank()) {
      errors.add(path + ".world: required");
      return null;
    }
    return new QuestRegion(world, x, y, z, radius);
  }

  private ShopAvailabilitySpec parseAvailability(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof ConfigurationSection section) {
      raw = section.getValues(false);
    }
    ZoneId zoneId = null;
    Object windowsRaw = null;
    if (raw instanceof Map<?, ?> map) {
      String zone = YamlValues.string(map, "timezone", YamlValues.string(map, "zone", null));
      if (zone != null && !zone.isBlank()) {
        try {
          zoneId = ZoneId.of(zone);
        } catch (Exception ex) {
          errors.add(path + ".timezone: invalid timezone " + zone);
        }
      }
      windowsRaw = map.get("windows");
      if (windowsRaw == null) {
        windowsRaw = map.get("timeWindows");
      }
      if (windowsRaw == null && (map.containsKey("start") || map.containsKey("end"))) {
        windowsRaw = List.of(map);
      }
    } else if (raw instanceof List<?> list) {
      windowsRaw = list;
    } else {
      errors.add(path + ": availability must be an object or list");
      return null;
    }
    if (!(windowsRaw instanceof List<?> list)) {
      errors.add(path + ".windows: must be a list");
      return null;
    }
    List<ShopTimeWindowSpec> windows = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      String entryPath = path + ".windows[" + i + "]";
      Map<String, Object> entry = castMap(list.get(i), entryPath, errors);
      if (entry == null) {
        continue;
      }
      Set<DayOfWeek> days = parseDays(entry, entryPath + ".days", errors);
      LocalTime start = parseTimeValue(entry.get("start"), entryPath + ".start", errors);
      LocalTime end = parseTimeValue(entry.get("end"), entryPath + ".end", errors);
      if (start == null || end == null) {
        errors.add(entryPath + ": start/end required");
        continue;
      }
      windows.add(new ShopTimeWindowSpec(days, start, end));
    }
    if (windows.isEmpty()) {
      return null;
    }
    return new ShopAvailabilitySpec(zoneId, windows);
  }

  private QuestFailSpec parseFailSpec(ConfigurationSection node, String base, List<String> errors) {
    if (node == null) {
      return QuestFailSpec.none();
    }
    ConfigurationSection failSection = node.getConfigurationSection("fail");
    if (failSection == null) {
      failSection = node.getConfigurationSection("failure");
    }
    if (failSection == null) {
      failSection = node.getConfigurationSection("failState");
    }
    ConfigurationSection source = failSection == null ? node : failSection;
    boolean failOnDeath = source.getBoolean("death", source.getBoolean("failOnDeath", false));
    boolean failOnLeaveRegion = source.getBoolean("leaveRegion",
        source.getBoolean("failOnLeaveRegion", source.getBoolean("leave_region", false)));
    long timeoutSeconds = Math.max(0L, source.getLong("timeoutSeconds",
        source.getLong("failAfterSeconds", source.getLong("timeout", 0L))));
    QuestRegion region = null;
    Object regionRaw = source.get("region");
    if (regionRaw == null) {
      regionRaw = source.get("failRegion");
    }
    if (regionRaw != null) {
      Map<String, Object> map = castMap(regionRaw, base + ".region", errors);
      if (map != null) {
        region = parseRegion(map, base + ".region", errors);
      }
    }
    if (!failOnDeath && !failOnLeaveRegion && timeoutSeconds <= 0L && region == null) {
      return QuestFailSpec.none();
    }
    return new QuestFailSpec(failOnDeath, failOnLeaveRegion, timeoutSeconds, region);
  }

  private QuestPartyShareSpec parsePartyShare(ConfigurationSection node, String base, List<String> errors) {
    if (node == null) {
      return QuestPartyShareSpec.none();
    }
    ConfigurationSection shareSection = node.getConfigurationSection("partyShare");
    if (shareSection == null) {
      shareSection = node.getConfigurationSection("share");
    }
    if (shareSection == null) {
      return QuestPartyShareSpec.none();
    }
    boolean enabled = shareSection.getBoolean("enabled", true);
    double radius = Math.max(0.0, shareSection.getDouble("radius", shareSection.getDouble("distance", 0.0)));
    int minContributors = Math.max(0, shareSection.getInt("minContributors",
        shareSection.getInt("contributors", 0)));
    boolean leaderOnly = shareSection.getBoolean("leaderOnly", shareSection.getBoolean("leader_only", false));
    long idleTimeoutSeconds = Math.max(0L, shareSection.getLong("idleTimeoutSeconds",
        shareSection.getLong("idle_timeout_seconds", shareSection.getLong("idleTimeout", 0L))));
    if (!enabled && radius <= 0.0 && minContributors <= 0 && !leaderOnly && idleTimeoutSeconds <= 0L) {
      return QuestPartyShareSpec.none();
    }
    return new QuestPartyShareSpec(enabled, radius, minContributors, leaderOnly, idleTimeoutSeconds);
  }

  private QuestObjectiveShareSpec parseObjectiveShare(Map<?, ?> map, String path, List<String> errors) {
    Object raw = map.get("share");
    if (raw == null) {
      raw = map.get("partyShare");
    }
    if (raw == null) {
      return QuestObjectiveShareSpec.none();
    }
    if (raw instanceof ConfigurationSection sec) {
      raw = sec.getValues(false);
    }
    if (raw instanceof Boolean bool) {
      return new QuestObjectiveShareSpec(bool, null, null, null, null);
    }
    if (!(raw instanceof Map<?, ?> shareMap)) {
      errors.add(path + ".share: must be an object or boolean");
      return QuestObjectiveShareSpec.none();
    }
    Boolean enabled = null;
    if (shareMap.containsKey("enabled")) {
      enabled = YamlValues.bool(shareMap.get("enabled"), true);
    }
    Double radius = null;
    if (shareMap.containsKey("radius")) {
      radius = YamlValues.doubleValue(shareMap.get("radius"), 0.0);
    } else if (shareMap.containsKey("distance")) {
      radius = YamlValues.doubleValue(shareMap.get("distance"), 0.0);
    }
    Integer minContributors = null;
    if (shareMap.containsKey("minContributors")) {
      minContributors = YamlValues.intValue(shareMap.get("minContributors"), 0);
    } else if (shareMap.containsKey("contributors")) {
      minContributors = YamlValues.intValue(shareMap.get("contributors"), 0);
    }
    Boolean leaderOnly = null;
    if (shareMap.containsKey("leaderOnly")) {
      leaderOnly = YamlValues.bool(shareMap.get("leaderOnly"), false);
    } else if (shareMap.containsKey("leader_only")) {
      leaderOnly = YamlValues.bool(shareMap.get("leader_only"), false);
    }
    Long idleTimeoutSeconds = null;
    if (shareMap.containsKey("idleTimeoutSeconds")) {
      idleTimeoutSeconds = YamlValues.longValue(shareMap.get("idleTimeoutSeconds"), 0L);
    } else if (shareMap.containsKey("idle_timeout_seconds")) {
      idleTimeoutSeconds = YamlValues.longValue(shareMap.get("idle_timeout_seconds"), 0L);
    } else if (shareMap.containsKey("idleTimeout")) {
      idleTimeoutSeconds = YamlValues.longValue(shareMap.get("idleTimeout"), 0L);
    }
    return new QuestObjectiveShareSpec(enabled, radius, minContributors, leaderOnly, idleTimeoutSeconds);
  }

  private Set<DayOfWeek> parseDays(Map<String, Object> map, String path, List<String> errors) {
    Object raw = map.get("days");
    if (raw == null) {
      raw = map.get("day");
    }
    if (raw == null) {
      return Set.of();
    }
    List<String> values = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object entry : list) {
        String value = YamlValues.string(entry, null);
        if (value != null && !value.isBlank()) {
          values.add(value);
        }
      }
    } else {
      String value = YamlValues.string(raw, null);
      if (value != null && !value.isBlank()) {
        values.add(value);
      }
    }
    if (values.isEmpty()) {
      return Set.of();
    }
    Set<DayOfWeek> days = new java.util.LinkedHashSet<>();
    for (String value : values) {
      try {
        days.add(DayOfWeek.valueOf(value.trim().toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException ex) {
        errors.add(path + ": invalid day " + value);
      }
    }
    return days.isEmpty() ? Set.of() : Set.copyOf(days);
  }

  private LocalTime parseTimeValue(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof Number number) {
      int hour = number.intValue();
      if (hour < 0 || hour > 23) {
        errors.add(path + ": invalid hour " + hour);
        return null;
      }
      return LocalTime.of(hour, 0);
    }
    String value = YamlValues.string(raw, null);
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    if (!trimmed.contains(":")) {
      try {
        int hour = Integer.parseInt(trimmed);
        if (hour < 0 || hour > 23) {
          errors.add(path + ": invalid hour " + trimmed);
          return null;
        }
        return LocalTime.of(hour, 0);
      } catch (NumberFormatException ex) {
        errors.add(path + ": invalid time " + value);
        return null;
      }
    }
    try {
      return LocalTime.parse(trimmed);
    } catch (Exception ex) {
      errors.add(path + ": invalid time " + value);
      return null;
    }
  }

  private Map<String, Object> castMap(Object raw, String path, List<String> errors) {
    if (raw == null) {
      errors.add(path + ": must be an object");
      return null;
    }
    if (raw instanceof ConfigurationSection section) {
      raw = section.getValues(false);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      errors.add(path + ": must be an object");
      return null;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (entry.getKey() == null) {
        continue;
      }
      out.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return out;
  }

  private int readSchemaVersion(YamlConfiguration cfg, List<String> warnings) {
    int schemaVersion = cfg.getInt("schemaVersion", 1);
    if (schemaVersion < 1) {
      warnings.add("schemaVersion: invalid version " + schemaVersion + " (expected >= 1)");
      schemaVersion = 1;
    }
    if (schemaVersion < CURRENT_SCHEMA_VERSION) {
      warnings.add("schemaVersion: " + schemaVersion + " (current " + CURRENT_SCHEMA_VERSION
          + "); legacy keys are still supported");
    } else if (schemaVersion > CURRENT_SCHEMA_VERSION) {
      warnings.add("schemaVersion: " + schemaVersion + " is newer than supported " + CURRENT_SCHEMA_VERSION);
    }
    return schemaVersion;
  }

  private QuestValidationReport buildValidationReport(int schemaVersion,
      List<String> warnings,
      List<String> errors,
      Map<String, QuestSpec> next,
      Map<String, QuestRotationPoolSpec> pools) {
    if (next != null) {
      for (QuestSpec spec : next.values()) {
        if (spec == null) {
          continue;
        }
        QuestVisibilitySpec visibility = spec.visibility();
        if (visibility != null && visibility.hidden() && visibility.hints().isEmpty()) {
          warnings.add("quests." + spec.id() + ".visibility: hidden quest has no hints");
        }
        if (visibility != null && visibility.hidden() && !visibility.showInLog() && !visibility.showInGiver()) {
          warnings.add("quests." + spec.id() + ".visibility: hidden quest is not shown anywhere");
        }
        if (spec.rotationPool() != null && !spec.rotationPool().isBlank()) {
          if (pools == null || !pools.containsKey(spec.rotationPool())) {
            warnings.add("quests." + spec.id() + ".rotationPool: unknown pool " + spec.rotationPool());
          }
        }
      }
    }
    int warningCount = warnings == null ? 0 : warnings.size();
    int errorCount = errors == null ? 0 : errors.size();
    return new QuestValidationReport(
        schemaVersion,
        CURRENT_SCHEMA_VERSION,
        next == null ? 0 : next.size(),
        pools == null ? 0 : pools.size(),
        errorCount,
        warningCount,
        warnings == null ? List.of() : List.copyOf(warnings));
  }
}
