package dev.patric.dungeonsreborn.quests;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.system.SystemStatusStore;
import dev.patric.dungeonsreborn.util.YamlValues;

public final class QuestYamlRegistry {
  public record ReloadResult(int loaded, List<String> errors) {
  }

  private final JavaPlugin plugin;
  private final Logger logger;
  private final Function<String, ItemStack> itemResolver;
  private final Map<String, QuestSpec> quests = new LinkedHashMap<>();
  private List<String> lastErrors = List.of();

  public QuestYamlRegistry(JavaPlugin plugin, Logger logger, Function<String, ItemStack> itemResolver) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.itemResolver = itemResolver;
  }

  public File file() {
    return new File(plugin.getDataFolder(), "quests.yml");
  }

  public Map<String, QuestSpec> quests() {
    return Map.copyOf(quests);
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

  public ReloadResult reload() {
    ensureFile();
    List<String> errors = new ArrayList<>();
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file());
    Map<String, QuestSpec> next = parseQuests(cfg, errors);
    if (errors.isEmpty()) {
      quests.clear();
      quests.putAll(next);
    }
    lastErrors = List.copyOf(errors);
    if (!errors.isEmpty()) {
      logger.warning("[Quests] YAML reload had " + errors.size() + " errors");
      for (String error : errors) {
        logger.warning("[Quests] YAML: " + error);
      }
    } else {
      logger.info("[Quests] YAML loaded " + next.size() + " quests");
    }
    SystemStatusStore.get().record(
        "quests",
        "Quests",
        file().getPath(),
        "quests=" + (errors.isEmpty() ? next.size() : quests.size()),
        errors);
    return new ReloadResult(errors.isEmpty() ? next.size() : quests.size(), errors);
  }

  private void ensureFile() {
    File file = file();
    if (file.exists()) {
      return;
    }
    plugin.saveResource("quests.yml", false);
  }

  private Map<String, QuestSpec> parseQuests(YamlConfiguration cfg, List<String> errors) {
    ConfigurationSection questsSec = cfg.getConfigurationSection("quests");
    if (questsSec == null) {
      return Map.of();
    }
    Map<String, QuestSpec> out = new LinkedHashMap<>();
    for (String rawId : questsSec.getKeys(false)) {
      String base = "quests." + rawId;
      ConfigurationSection node = questsSec.getConfigurationSection(rawId);
      if (node == null) {
        errors.add(base + ": must be an object");
        continue;
      }
      try {
        String id = Ids.normalize(rawId);
        String name = YamlValues.string(node, "name", id);
        boolean enabled = node.getBoolean("enabled", true);
        List<String> description = parseDescription(node.get("description"));
        QuestRequirements requirements = parseRequirements(node.getConfigurationSection("requirements"), base + ".requirements", errors);
        QuestRewards rewards = parseRewards(node.getConfigurationSection("rewards"), base + ".rewards", errors);
        List<QuestObjectiveSpec> objectives = parseObjectives(node.getList("objectives"), base + ".objectives", errors);
        if (objectives.isEmpty()) {
          errors.add(base + ".objectives: at least one objective is required");
        }
        long cooldownSeconds = Math.max(0L, node.getLong("cooldownSeconds", 0L));
        QuestRotation rotation = QuestRotation.parse(node.getString("rotation"));
        out.put(id, new QuestSpec(id, name, enabled, description, requirements, rewards, objectives,
            cooldownSeconds, rotation));
      } catch (Exception ex) {
        errors.add(base + ": " + ex.getMessage());
      }
    }
    return out;
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
    return new QuestRequirements(level, List.copyOf(quests));
  }

  private QuestRewards parseRewards(ConfigurationSection section, String base, List<String> errors) {
    if (section == null) {
      return QuestRewards.empty();
    }
    int xp = section.getInt("xp", 0);
    int tokens = section.getInt("tokens", section.getInt("token", section.getInt("normal", 0)));
    int compressed = section.getInt("compressed", 0);
    int pallet = section.getInt("pallet", 0);
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
    return new QuestRewards(xp, tokens, compressed, pallet, List.copyOf(items));
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
        String itemId = YamlValues.string(map, "itemId", YamlValues.string(map, "id", null));
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
      if (entry instanceof ConfigurationSection sec) {
        entry = sec.getValues(false);
      }
      if (!(entry instanceof Map<?, ?> map)) {
        errors.add(entryPath + ": objective must be an object");
        continue;
      }
      try {
        out.add(parseObjective(map, entryPath, errors));
      } catch (Exception ex) {
        errors.add(entryPath + ": " + ex.getMessage());
      }
    }
    return List.copyOf(out);
  }

  private QuestObjectiveSpec parseObjective(Map<?, ?> map, String path, List<String> errors) {
    String typeRaw = YamlValues.string(map, "type", null);
    QuestObjectiveType type = QuestObjectiveType.parse(typeRaw);
    if (type == null) {
      if (map.containsKey("mob") || map.containsKey("mobId") || map.containsKey("entity")) {
        type = QuestObjectiveType.KILL_MOB;
      } else if (map.containsKey("itemId") || map.containsKey("material") || map.containsKey("item")) {
        type = QuestObjectiveType.USE_ITEM;
      } else if (map.containsKey("world") || map.containsKey("region")) {
        type = QuestObjectiveType.VISIT_REGION;
      } else if (map.containsKey("recipeId") || map.containsKey("craft")) {
        type = QuestObjectiveType.CRAFT_ITEM;
      }
    }
    if (type == null) {
      throw new IllegalArgumentException("type is required");
    }
    int count = Math.max(1, YamlValues.intValue(map.get("count"), 1));
    return switch (type) {
      case KILL_MOB -> parseKillObjective(map, path, errors, count);
      case USE_ITEM -> parseUseItemObjective(map, path, errors, count);
      case VISIT_REGION -> parseVisitRegionObjective(map, path, errors);
      case CRAFT_ITEM -> parseCraftObjective(map, path, errors, count);
    };
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
    return QuestObjectiveSpec.killMob(mobId, entityType, count);
  }

  private QuestObjectiveSpec parseUseItemObjective(Map<?, ?> map, String path, List<String> errors, int count) {
    String itemId = YamlValues.string(map, "itemId", YamlValues.string(map, "id", null));
    if (itemId != null && !itemId.isBlank()) {
      itemId = Ids.normalize(itemId);
    }
    String materialRaw = YamlValues.string(map, "material", null);
    Material material = parseMaterial(materialRaw, path + ".material", errors);
    return QuestObjectiveSpec.useItem(itemId, material, count);
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
    return QuestObjectiveSpec.visitRegion(new QuestRegion(world == null ? "" : world, x, y, z, radius));
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
    return QuestObjectiveSpec.craftItem(recipeId, itemId, material, count);
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
}
