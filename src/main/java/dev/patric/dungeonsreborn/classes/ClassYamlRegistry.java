package dev.patric.dungeonsreborn.classes;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.integration.ItemMatcher;
import dev.patric.dungeonsreborn.effects.integration.ItemMatchers;
import dev.patric.dungeonsreborn.effects.editor.EditorItemLore;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.system.SystemStatusStore;
import dev.patric.dungeonsreborn.util.PluginResources;
import dev.patric.dungeonsreborn.util.YamlValues;
import dev.patric.dungeonsreborn.classes.skills.SkillAttributeSpec;
import dev.patric.dungeonsreborn.classes.skills.SkillAbilitySpec;
import dev.patric.dungeonsreborn.classes.skills.SkillAbilityTrigger;
import dev.patric.dungeonsreborn.classes.skills.SkillCurve;
import dev.patric.dungeonsreborn.classes.skills.SkillEdgeSpec;
import dev.patric.dungeonsreborn.classes.skills.SkillNodeSpec;
import dev.patric.dungeonsreborn.classes.skills.SkillNodeType;
import dev.patric.dungeonsreborn.classes.skills.SkillPotionSpec;
import dev.patric.dungeonsreborn.classes.skills.SkillScalingMode;
import dev.patric.dungeonsreborn.classes.skills.SkillScalingSpec;
import dev.patric.dungeonsreborn.classes.skills.SkillStatSpec;
import dev.patric.dungeonsreborn.classes.skills.SkillSynergySpec;
import dev.patric.dungeonsreborn.classes.skills.SkillTreeSpec;
import dev.patric.dungeonsreborn.effects.damage.DamageType;
import net.kyori.adventure.text.Component;
import dev.patric.dungeonsreborn.quests.QuestRegion;

public final class ClassYamlRegistry {
  public record ReloadResult(int loaded, List<String> errors) {
  }

  private final JavaPlugin plugin;
  private final Logger logger;
  private final Map<String, ClassSpec> classes = new LinkedHashMap<>();
  private List<String> lastErrors = List.of();

  public ClassYamlRegistry(JavaPlugin plugin, Logger logger) {
    this.plugin = plugin;
    this.logger = logger;
  }

  public File file() {
    return new File(plugin.getDataFolder(), "classes.yml");
  }

  public Map<String, ClassSpec> classes() {
    return Map.copyOf(classes);
  }

  public ClassSpec classSpec(String id) {
    if (id == null) {
      return null;
    }
    try {
      return classes.get(Ids.normalize(id));
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  public List<String> lastErrors() {
    return lastErrors;
  }

  public ReloadResult reload() {
    ensureFile();
    List<String> errors = new ArrayList<>();
    boolean safeMode = plugin.getConfig().getBoolean("classes.validation.safeMode", true);
    boolean logWarnings = plugin.getConfig().getBoolean("classes.validation.logWarnings", true);
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file());
    Map<String, ClassSpec> next = parseClasses(cfg, errors);
    if (errors.isEmpty() || safeMode) {
      classes.clear();
      classes.putAll(next);
    }
    lastErrors = List.copyOf(errors);
    if (!errors.isEmpty()) {
      if (logWarnings) {
        String label = safeMode ? "warnings" : "errors";
        logger.warning("[Classes] YAML reload had " + errors.size() + " " + label);
        for (String error : errors) {
          logger.warning("[Classes] YAML: " + error);
        }
      }
      if (!safeMode) {
        logger.warning("[Classes] YAML reload aborted; enable classes.validation.safeMode to keep valid classes loaded.");
      }
    } else {
      logger.info("[Classes] YAML loaded " + next.size() + " classes");
    }
    int loadedCount = (errors.isEmpty() || safeMode) ? next.size() : classes.size();
    SystemStatusStore.get().record(
        "classes",
        "Classes",
        file().getPath(),
        "classes=" + loadedCount,
        errors);
    return new ReloadResult(loadedCount, errors);
  }

  public ReloadResult validate() {
    ensureFile();
    List<String> errors = new ArrayList<>();
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file());
    Map<String, ClassSpec> parsed = parseClasses(cfg, errors);
    return new ReloadResult(parsed.size(), List.copyOf(errors));
  }

  public File exportDir() {
    File dir = new File(plugin.getDataFolder(), "classes_exports");
    if (!dir.exists()) {
      dir.mkdirs();
    }
    return dir;
  }

  public ReloadResult exportTo(File target) {
    List<String> errors = new ArrayList<>();
    if (target == null) {
      errors.add("export: missing target path");
      return new ReloadResult(classes.size(), errors);
    }
    ensureFile();
    if (target.getParentFile() != null) {
      target.getParentFile().mkdirs();
    }
    try {
      YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file());
      cfg.save(target);
    } catch (Exception ex) {
      errors.add(target.getPath() + ": " + ex.getMessage());
    }
    return new ReloadResult(classes.size(), List.copyOf(errors));
  }

  public ReloadResult importFrom(File source, boolean merge) {
    List<String> errors = new ArrayList<>();
    if (source == null) {
      errors.add("import: missing source path");
      return new ReloadResult(0, errors);
    }
    if (!source.exists()) {
      errors.add(source.getPath() + ": file not found");
      return new ReloadResult(0, errors);
    }
    YamlConfiguration incoming = YamlConfiguration.loadConfiguration(source);
    Map<String, ClassSpec> parsed = parseClasses(incoming, errors);
    if (!errors.isEmpty()) {
      return new ReloadResult(0, List.copyOf(errors));
    }
    ConfigurationSection incomingClasses = incoming.getConfigurationSection("classes");
    if (incomingClasses == null) {
      errors.add(source.getPath() + ": missing classes section");
      return new ReloadResult(0, List.copyOf(errors));
    }
    ensureFile();
    YamlConfiguration target = YamlConfiguration.loadConfiguration(file());
    if (!merge) {
      target.set("classes", null);
    }
    ConfigurationSection targetClasses = target.getConfigurationSection("classes");
    if (targetClasses == null) {
      targetClasses = target.createSection("classes");
    }
    for (String key : incomingClasses.getKeys(false)) {
      targetClasses.set(key, incomingClasses.get(key));
    }
    try {
      target.save(file());
    } catch (Exception ex) {
      errors.add(file().getPath() + ": " + ex.getMessage());
      return new ReloadResult(0, List.copyOf(errors));
    }
    return new ReloadResult(parsed.size(), List.copyOf(errors));
  }

  private void ensureFile() {
    PluginResources.ensureYamlFile(plugin, file(), "classes.yml", cfg -> cfg.createSection("classes"), logger,
        "Classes");
  }

  private Map<String, ClassSpec> parseClasses(YamlConfiguration cfg, List<String> errors) {
    ConfigurationSection classesSec = cfg.getConfigurationSection("classes");
    if (classesSec == null) {
      return Map.of();
    }
    Map<String, ClassSpec> out = new LinkedHashMap<>();
    for (String rawId : classesSec.getKeys(false)) {
      String base = "classes." + rawId;
      ConfigurationSection node = classesSec.getConfigurationSection(rawId);
      if (node == null) {
        errors.add(base + ": must be an object");
        continue;
      }
      try {
        String id = Ids.normalize(rawId);
        String nameRaw = YamlValues.string(node, "name", id);
        String nameKey = YamlValues.string(node, "nameKey", null);
        boolean enabled = node.getBoolean("enabled", true);
        List<String> descRaw = listOf(node, "description");
        String descriptionKey = YamlValues.string(node, "descriptionKey", null);
        ItemStack icon = parseIcon(node.get("icon"), base + ".icon", errors);
        if (icon == null) {
          icon = new ItemStack(Material.BOOK);
        }
        ClassUnlockSpec unlock = parseUnlock(node.getConfigurationSection("unlock"), base + ".unlock", errors);
        SkillTreeSpec skillTree = parseSkillTree(node.getConfigurationSection("path"), base + ".path", errors);
        ClassBonusSpec bonuses = parseBonuses(node.getConfigurationSection("bonuses"), base + ".bonuses", errors);
        List<ClassConditionalBonusSpec> conditionalBonuses = parseConditionalBonuses(node.get("bonuses"),
            base + ".bonuses", errors);
        Component name = EditorItemLore.parseRichText(nameRaw);
        List<Component> desc = new ArrayList<>();
        for (String line : descRaw) {
          desc.add(EditorItemLore.parseRichText(line));
        }
        out.put(id, new ClassSpec(id, enabled, nameKey, descriptionKey, name, List.copyOf(desc), icon, unlock, skillTree,
            bonuses, conditionalBonuses));
      } catch (Exception ex) {
        errors.add(base + ": " + ex.getMessage());
      }
    }
    return out;
  }

  private ClassUnlockSpec parseUnlock(ConfigurationSection section, String base, List<String> errors) {
    if (section == null) {
      return ClassUnlockSpec.none();
    }
    int level = Math.max(0, section.getInt("level", 0));
    int tokens = Math.max(0, section.getInt("tokens", 0));
    if (level < 0) {
      errors.add(base + ".level: must be >= 0");
      level = 0;
    }
    if (tokens < 0) {
      errors.add(base + ".tokens: must be >= 0");
      tokens = 0;
    }
    List<String> quests = new ArrayList<>();
    for (String quest : section.getStringList("quests")) {
      if (quest == null || quest.isBlank()) {
        continue;
      }
      try {
        quests.add(Ids.normalize(quest));
      } catch (IllegalArgumentException ex) {
        errors.add(base + ".quests: invalid quest id " + quest);
      }
    }
    Object itemsRaw = section.get("items");
    if (itemsRaw == null) {
      itemsRaw = section.get("item");
    }
    List<ClassUnlockItemSpec> items = parseUnlockItems(itemsRaw, base + ".items", errors);
    Object currenciesRaw = section.get("currencies");
    if (currenciesRaw == null) {
      currenciesRaw = section.get("currency");
    }
    List<ClassUnlockCurrencySpec> currencies = parseUnlockCurrencies(currenciesRaw, base + ".currencies", errors);
    return new ClassUnlockSpec(level, tokens, List.copyOf(quests), items, currencies);
  }

  private List<ClassUnlockItemSpec> parseUnlockItems(Object raw, String base, List<String> errors) {
    if (raw == null) {
      return List.of();
    }
    List<?> list;
    if (raw instanceof List<?> rawList) {
      list = rawList;
    } else {
      list = List.of(raw);
    }
    List<ClassUnlockItemSpec> out = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      Object entry = list.get(i);
      String path = base + "[" + i + "]";
      ClassUnlockItemSpec spec = parseUnlockItem(entry, path, errors);
      if (spec != null) {
        out.add(spec);
      }
    }
    return List.copyOf(out);
  }

  private ClassUnlockItemSpec parseUnlockItem(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof String text) {
      String trimmed = text.trim();
      if (trimmed.isEmpty()) {
        return null;
      }
      ItemMatcher matcher = parseItemMatcher(trimmed, path, errors);
      return new ClassUnlockItemSpec(trimmed, 1, matcher, null);
    }
    if (raw instanceof ConfigurationSection section) {
      raw = section.getValues(false);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      errors.add(path + ": invalid item unlock entry");
      return null;
    }
    int amount = Math.max(1, YamlValues.intValue(map.get("amount"), 1));
    String label = YamlValues.string(map, "label", null);
    Object matcherRaw = map.get("matcher");
    ItemMatcher matcher = null;
    ItemStack preview = null;
    String itemId = YamlValues.string(map, "itemId", YamlValues.string(map, "item_id", YamlValues.string(map, "id", null)));
    if (itemId != null && !itemId.isBlank()) {
      try {
        itemId = Ids.normalize(itemId);
        matcher = ItemMatchers.itemId(itemId);
        if (label == null || label.isBlank()) {
          label = itemId;
        }
      } catch (IllegalArgumentException ex) {
        errors.add(path + ".itemId: invalid item id " + itemId);
      }
    } else if (map.containsKey("material")) {
      String materialRaw = YamlValues.string(map, "material", null);
      Material material = parseMaterial(materialRaw, path + ".material", errors);
      if (material != null) {
        matcher = ItemMatchers.material(material);
        if (label == null || label.isBlank()) {
          label = material.name().toLowerCase(Locale.ROOT);
        }
      }
    } else if (map.containsKey("item")) {
      preview = parseItemStack(map.get("item"), path + ".item", errors);
      if (preview != null) {
        matcher = ItemMatchers.similar(preview);
        if (label == null || label.isBlank()) {
          label = preview.getType().name().toLowerCase(Locale.ROOT);
        }
      }
    } else if (map.containsKey("customModelData")) {
      int cmd = YamlValues.intValue(map.get("customModelData"), 0);
      matcher = ItemMatchers.customModelData(cmd);
      if (label == null || label.isBlank()) {
        label = "custom_model_data:" + cmd;
      }
    } else if (map.containsKey("tag")) {
      String tag = YamlValues.string(map, "tag", null);
      if (tag != null && !tag.isBlank()) {
        matcher = ItemMatchers.itemTag(tag.trim());
        if (label == null || label.isBlank()) {
          label = tag.trim();
        }
      }
    } else if (map.containsKey("category")) {
      String category = YamlValues.string(map, "category", null);
      if (category != null && !category.isBlank()) {
        matcher = ItemMatchers.itemCategory(Ids.normalize(category));
        if (label == null || label.isBlank()) {
          label = category.trim();
        }
      }
    } else if (map.containsKey("loreContains") || map.containsKey("lore_contains")) {
      String text = YamlValues.string(map, "loreContains", YamlValues.string(map, "lore_contains", null));
      if (text != null && !text.isBlank()) {
        matcher = ItemMatchers.loreContains(text);
        if (label == null || label.isBlank()) {
          label = text;
        }
      }
    } else if (matcherRaw != null) {
      matcher = parseItemMatcher(matcherRaw, path + ".matcher", errors);
    }
    if (matcher == null) {
      errors.add(path + ": missing item matcher");
      return null;
    }
    if (label == null || label.isBlank()) {
      label = "item";
    }
    return new ClassUnlockItemSpec(label, amount, matcher, preview);
  }

  private List<ClassUnlockCurrencySpec> parseUnlockCurrencies(Object raw, String base, List<String> errors) {
    if (raw == null) {
      return List.of();
    }
    List<?> list;
    if (raw instanceof List<?> rawList) {
      list = rawList;
    } else {
      list = List.of(raw);
    }
    List<ClassUnlockCurrencySpec> out = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      Object entry = list.get(i);
      String path = base + "[" + i + "]";
      ClassUnlockCurrencySpec spec = parseUnlockCurrency(entry, path, errors);
      if (spec != null) {
        out.add(spec);
      }
    }
    return List.copyOf(out);
  }

  private ClassUnlockCurrencySpec parseUnlockCurrency(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    String id;
    int amount = 1;
    if (raw instanceof String text) {
      id = text.trim();
    } else {
      if (raw instanceof ConfigurationSection section) {
        raw = section.getValues(false);
      }
      if (!(raw instanceof Map<?, ?> map)) {
        errors.add(path + ": invalid currency entry");
        return null;
      }
      id = YamlValues.string(map, "currency", YamlValues.string(map, "id", null));
      amount = Math.max(1, YamlValues.intValue(map.get("amount"), 1));
    }
    if (id == null || id.isBlank()) {
      errors.add(path + ": missing currency id");
      return null;
    }
    try {
      id = Ids.normalize(id);
    } catch (IllegalArgumentException ex) {
      errors.add(path + ": invalid currency id " + id);
      return null;
    }
    return new ClassUnlockCurrencySpec(id, amount);
  }

  private ItemMatcher parseItemMatcher(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return ItemMatchers.anyNonAir();
    }
    if (raw instanceof String text) {
      String trimmed = text.trim();
      if (trimmed.isEmpty()) {
        return ItemMatchers.anyNonAir();
      }
      if (trimmed.equalsIgnoreCase("any") || trimmed.equalsIgnoreCase("any_non_air")
          || trimmed.equalsIgnoreCase("any-non-air") || trimmed.equalsIgnoreCase("any_nonair")) {
        return ItemMatchers.anyNonAir();
      }
      Material material = Material.matchMaterial(trimmed);
      if (material != null) {
        return ItemMatchers.material(material);
      }
      try {
        return ItemMatchers.itemId(Ids.normalize(trimmed));
      } catch (IllegalArgumentException ex) {
        errors.add(path + ": invalid item id " + trimmed);
        return ItemMatchers.anyNonAir();
      }
    }
    if (raw instanceof ConfigurationSection section) {
      raw = section.getValues(false);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      errors.add(path + ": invalid matcher");
      return ItemMatchers.anyNonAir();
    }
    String typeRaw = YamlValues.string(map, "type", null);
    if (typeRaw != null) {
      String type = typeRaw.toLowerCase(Locale.ROOT);
      switch (type) {
        case "any", "any_non_air", "any-non-air", "any_nonair":
          return ItemMatchers.anyNonAir();
        case "material": {
          String materialRaw = YamlValues.string(map, "material", null);
          Material material = parseMaterial(materialRaw, path + ".material", errors);
          return material == null ? ItemMatchers.anyNonAir() : ItemMatchers.material(material);
        }
        case "item_id":
        case "itemid":
        case "item-id": {
          String itemId = YamlValues.string(map, "itemId", YamlValues.string(map, "id", null));
          if (itemId == null || itemId.isBlank()) {
            errors.add(path + ".itemId: missing item id");
            return ItemMatchers.anyNonAir();
          }
          return ItemMatchers.itemId(Ids.normalize(itemId));
        }
        case "custom_model_data":
        case "custommodeldata": {
          int cmd = YamlValues.intValue(map.get("customModelData"), 0);
          return ItemMatchers.customModelData(cmd);
        }
        case "tag": {
          String tag = YamlValues.string(map, "tag", null);
          return tag == null ? ItemMatchers.anyNonAir() : ItemMatchers.itemTag(tag);
        }
        case "category": {
          String category = YamlValues.string(map, "category", null);
          if (category == null) {
            return ItemMatchers.anyNonAir();
          }
          try {
            return ItemMatchers.itemCategory(Ids.normalize(category));
          } catch (IllegalArgumentException ex) {
            errors.add(path + ".category: invalid category " + category);
            return ItemMatchers.anyNonAir();
          }
        }
        case "lore_contains":
        case "lore-contains": {
          String text = YamlValues.string(map, "text", null);
          return text == null ? ItemMatchers.anyNonAir() : ItemMatchers.loreContains(text);
        }
        case "item": {
          ItemStack stack = parseItemStack(map.get("item"), path + ".item", errors);
          return stack == null ? ItemMatchers.anyNonAir() : ItemMatchers.similar(stack);
        }
        default:
          errors.add(path + ".type: unknown matcher type " + typeRaw);
          return ItemMatchers.anyNonAir();
      }
    }
    if (map.containsKey("itemId") || map.containsKey("item_id") || map.containsKey("id")) {
      String itemId = YamlValues.string(map, "itemId", YamlValues.string(map, "item_id", YamlValues.string(map, "id", null)));
      if (itemId == null) {
        return ItemMatchers.anyNonAir();
      }
      try {
        return ItemMatchers.itemId(Ids.normalize(itemId));
      } catch (IllegalArgumentException ex) {
        errors.add(path + ".itemId: invalid item id " + itemId);
        return ItemMatchers.anyNonAir();
      }
    }
    if (map.containsKey("material")) {
      String materialRaw = YamlValues.string(map, "material", null);
      Material material = parseMaterial(materialRaw, path + ".material", errors);
      return material == null ? ItemMatchers.anyNonAir() : ItemMatchers.material(material);
    }
    if (map.containsKey("customModelData")) {
      int cmd = YamlValues.intValue(map.get("customModelData"), 0);
      return ItemMatchers.customModelData(cmd);
    }
    if (map.containsKey("tag")) {
      String tag = YamlValues.string(map, "tag", null);
      return tag == null ? ItemMatchers.anyNonAir() : ItemMatchers.itemTag(tag);
    }
    if (map.containsKey("category")) {
      String category = YamlValues.string(map, "category", null);
      if (category == null) {
        return ItemMatchers.anyNonAir();
      }
      try {
        return ItemMatchers.itemCategory(Ids.normalize(category));
      } catch (IllegalArgumentException ex) {
        errors.add(path + ".category: invalid category " + category);
        return ItemMatchers.anyNonAir();
      }
    }
    if (map.containsKey("loreContains") || map.containsKey("lore_contains")) {
      String text = YamlValues.string(map, "loreContains", YamlValues.string(map, "lore_contains", null));
      return text == null ? ItemMatchers.anyNonAir() : ItemMatchers.loreContains(text);
    }
    if (map.containsKey("item")) {
      ItemStack stack = parseItemStack(map.get("item"), path + ".item", errors);
      return stack == null ? ItemMatchers.anyNonAir() : ItemMatchers.similar(stack);
    }
    return ItemMatchers.anyNonAir();
  }

  private ItemStack parseIcon(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof ItemStack stack) {
      return stack.clone();
    }
    if (raw instanceof ConfigurationSection sec) {
      raw = sec.getValues(false);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      errors.add(path + ": invalid icon");
      return null;
    }
    String materialRaw = YamlValues.string(map, "material", null);
    Material material = parseMaterial(materialRaw, path + ".material", errors);
    if (material == null) {
      Object itemRaw = map.get("item");
      if (itemRaw == null) {
        return null;
      }
      return parseItemStack(itemRaw, path + ".item", errors);
    }
    int amount = YamlValues.intValue(map.get("amount"), 1);
    ItemStack stack = new ItemStack(material, amount);
    ItemMeta meta = stack.getItemMeta();
    if (meta != null) {
      String name = YamlValues.string(map, "name", null);
      if (name != null && !name.isBlank()) {
        meta.displayName(EditorItemLore.parseRichText(name));
      }
      List<String> loreRaw = listOf(map.get("lore"));
      if (!loreRaw.isEmpty()) {
        meta.lore(GuiMini.loreMm(loreRaw));
      }
      stack.setItemMeta(meta);
    }
    return stack;
  }

  private SkillTreeSpec parseSkillTree(ConfigurationSection section, String base, List<String> errors) {
    if (section == null) {
      return SkillTreeSpec.empty();
    }
    List<Map<?, ?>> nodesRaw = section.getMapList("nodes");
    List<SkillNodeSpec> nodes = new ArrayList<>();
    java.util.Set<String> seenIds = new java.util.HashSet<>();
    for (int i = 0; i < nodesRaw.size(); i++) {
      Map<?, ?> map = nodesRaw.get(i);
      String path = base + ".nodes[" + i + "]";
      try {
        SkillNodeSpec node = parseNode(map, path, errors);
        if (node != null) {
          if (!seenIds.add(node.id())) {
            errors.add(path + ".id: duplicate node id " + node.id());
            continue;
          }
          nodes.add(node);
        }
      } catch (Exception ex) {
        errors.add(path + ": " + ex.getMessage());
      }
    }
    List<SkillEdgeSpec> edges = new ArrayList<>();
    List<Map<?, ?>> edgesRaw = section.getMapList("edges");
    for (int i = 0; i < edgesRaw.size(); i++) {
      Map<?, ?> map = edgesRaw.get(i);
      String path = base + ".edges[" + i + "]";
      String from = YamlValues.string(map, "from", null);
      String to = YamlValues.string(map, "to", null);
      if (from == null || to == null) {
        errors.add(path + ": missing from/to");
        continue;
      }
      edges.add(new SkillEdgeSpec(Ids.normalize(from), Ids.normalize(to)));
    }
    List<SkillSynergySpec> synergies = parseSynergies(section, base, errors);
    java.util.Set<String> nodeIds = new java.util.HashSet<>();
    for (SkillNodeSpec node : nodes) {
      if (node != null && node.id() != null) {
        nodeIds.add(node.id());
      }
    }
    for (SkillNodeSpec node : nodes) {
      if (node == null || node.id() == null) {
        continue;
      }
      for (String req : node.requiresOrEmpty()) {
        if (!nodeIds.contains(req)) {
          errors.add(base + ".nodes: node " + node.id() + " requires missing node " + req);
        }
      }
    }
    for (int i = 0; i < synergies.size(); i++) {
      SkillSynergySpec synergy = synergies.get(i);
      if (synergy == null) {
        continue;
      }
      for (String req : synergy.requiresOrEmpty()) {
        if (!nodeIds.contains(req)) {
          errors.add(base + ".synergies[" + i + "]: requires missing node " + req);
        }
      }
    }
    java.util.Set<String> edgeKeys = new java.util.HashSet<>();
    for (int i = 0; i < edges.size(); i++) {
      SkillEdgeSpec edge = edges.get(i);
      if (edge == null) {
        continue;
      }
      if (!nodeIds.contains(edge.from())) {
        errors.add(base + ".edges[" + i + "]: missing from node " + edge.from());
      }
      if (!nodeIds.contains(edge.to())) {
        errors.add(base + ".edges[" + i + "]: missing to node " + edge.to());
      }
      if (edge.from().equals(edge.to())) {
        errors.add(base + ".edges[" + i + "]: from and to are the same (" + edge.from() + ")");
      }
      String key = edge.from() + "->" + edge.to();
      if (!edgeKeys.add(key)) {
        errors.add(base + ".edges[" + i + "]: duplicate edge " + key);
      }
    }
    ConfigurationSection respec = section.getConfigurationSection("respec");
    int respecTokens = respec == null ? 0 : Math.max(0, respec.getInt("tokens", 0));
    int respecPoints = respec == null ? 0 : Math.max(0, respec.getInt("points", 0));
    if (edges.isEmpty() && nodes.size() > 1) {
      for (int i = 1; i < nodes.size(); i++) {
        edges.add(new SkillEdgeSpec(nodes.get(i - 1).id(), nodes.get(i).id()));
      }
    }
    return new SkillTreeSpec(List.copyOf(nodes), List.copyOf(edges), List.copyOf(synergies), respecTokens,
        respecPoints);
  }

  private List<SkillSynergySpec> parseSynergies(ConfigurationSection section, String base, List<String> errors) {
    if (section == null) {
      return List.of();
    }
    List<SkillSynergySpec> out = new ArrayList<>();
    List<Map<?, ?>> raw = section.getMapList("synergies");
    for (int i = 0; i < raw.size(); i++) {
      Map<?, ?> map = raw.get(i);
      String path = base + ".synergies[" + i + "]";
      String idRaw = YamlValues.string(map, "id", null);
      if (idRaw == null || idRaw.isBlank()) {
        errors.add(path + ".id: missing id");
        continue;
      }
      List<String> requires = normalizeIds(parseStringList(map.get("requires")));
      if (requires.isEmpty()) {
        requires = normalizeIds(parseStringList(map.get("nodes")));
      }
      if (requires.isEmpty()) {
        errors.add(path + ".requires: missing nodes");
        continue;
      }
      Map<?, ?> bonusMap = castMap(map.get("bonuses"), path + ".bonuses", errors);
      if (bonusMap == null || bonusMap.isEmpty()) {
        bonusMap = castMap(map.get("bonus"), path + ".bonus", errors);
      }
      if (bonusMap == null || bonusMap.isEmpty()) {
        errors.add(path + ".bonuses: missing bonuses");
        continue;
      }
      ClassBonusSpec bonuses = parseBonusesMap(bonusMap, path + ".bonuses", errors);
      out.add(new SkillSynergySpec(Ids.normalize(idRaw), List.copyOf(requires), bonuses));
    }
    return out;
  }

  private ClassBonusSpec parseBonuses(ConfigurationSection section, String base, List<String> errors) {
    if (section == null) {
      return ClassBonusSpec.empty();
    }
    ConfigurationSection stats = section.getConfigurationSection("stats");
    int strength = stats == null ? 0 : Math.max(0, stats.getInt("strength", 0));
    int dexterity = stats == null ? 0 : Math.max(0, stats.getInt("dexterity", 0));
    int intelligence = stats == null ? 0 : Math.max(0, stats.getInt("intelligence", 0));
    int vitality = stats == null ? 0 : Math.max(0, stats.getInt("vitality", 0));

    ConfigurationSection mana = section.getConfigurationSection("mana");
    String manaResourceId = dev.patric.dungeonsreborn.effects.mana.ManaProvider.DEFAULT_RESOURCE;
    double manaMax = 0.0;
    double manaRegen = 0.0;
    if (mana != null) {
      String resourceRaw = YamlValues.string(mana, "resource", YamlValues.string(mana, "resourceId", null));
      if (resourceRaw != null && !resourceRaw.isBlank()) {
        manaResourceId = resourceRaw.trim().toLowerCase(java.util.Locale.ROOT);
      }
      manaMax = YamlValues.doubleValue(mana.get("max"), 0.0);
      manaRegen = YamlValues.doubleValue(mana.get("regen"), 0.0);
    }

    List<ClassAttributeBonus> attributes = new ArrayList<>();
    List<Map<?, ?>> attributeRaw = section.getMapList("attributes");
    for (int i = 0; i < attributeRaw.size(); i++) {
      Map<?, ?> map = attributeRaw.get(i);
      String path = base + ".attributes[" + i + "]";
      String key = YamlValues.string(map, "attribute", YamlValues.string(map, "key", null));
      if (key == null || key.isBlank()) {
        errors.add(path + ".attribute: missing attribute");
        continue;
      }
      double amount = YamlValues.doubleValue(map.get("amount"), 0.0);
      String op = YamlValues.string(map, "operation", "ADD_NUMBER");
      attributes.add(new ClassAttributeBonus(key, amount, op));
    }

    List<ClassPotionBonus> potions = new ArrayList<>();
    List<Map<?, ?>> potionRaw = section.getMapList("potions");
    for (int i = 0; i < potionRaw.size(); i++) {
      Map<?, ?> map = potionRaw.get(i);
      String path = base + ".potions[" + i + "]";
      String effect = YamlValues.string(map, "effect", YamlValues.string(map, "type", null));
      if (effect == null || effect.isBlank()) {
        errors.add(path + ".effect: missing potion effect");
        continue;
      }
      int amplifier = YamlValues.intValue(map.get("amplifier"), 0);
      boolean ambient = YamlValues.bool(map.get("ambient"), true);
      boolean particles = YamlValues.bool(map.get("particles"), true);
      boolean icon = YamlValues.bool(map.get("icon"), true);
      potions.add(new ClassPotionBonus(effect, amplifier, ambient, particles, icon));
    }

    Map<DamageType, Double> resistances = new java.util.EnumMap<>(DamageType.class);
    ConfigurationSection resistancesSec = section.getConfigurationSection("resistances");
    if (resistancesSec != null) {
      for (String key : resistancesSec.getKeys(false)) {
        DamageType type = parseDamageType(key, base + ".resistances." + key, errors);
        double multiplier = resistancesSec.getDouble(key, 1.0);
        if (type != null) {
          resistances.put(type, multiplier);
        }
      }
    }

    Map<String, Double> caps = new java.util.LinkedHashMap<>();
    ConfigurationSection capsSec = section.getConfigurationSection("caps");
    ConfigurationSection attrCaps = capsSec == null ? null : capsSec.getConfigurationSection("attributes");
    if (attrCaps == null) {
      attrCaps = section.getConfigurationSection("attributeCaps");
    }
    if (attrCaps != null) {
      for (String key : attrCaps.getKeys(false)) {
        double limit = attrCaps.getDouble(key, 0.0);
        if (limit <= 0.0) {
          continue;
        }
        caps.put(key, limit);
      }
    }

    return new ClassBonusSpec(
        strength,
        dexterity,
        intelligence,
        vitality,
        manaResourceId,
        manaMax,
        manaRegen,
        List.copyOf(attributes),
        List.copyOf(potions),
        java.util.Map.copyOf(resistances),
        java.util.Map.copyOf(caps));
  }

  private ClassBonusSpec parseBonusesMap(Map<?, ?> map, String base, List<String> errors) {
    if (map == null || map.isEmpty()) {
      return ClassBonusSpec.empty();
    }
    Map<?, ?> stats = castMap(map.get("stats"), base + ".stats", errors);
    int strength = stats == null ? 0 : Math.max(0, YamlValues.intValue(stats.get("strength"), 0));
    int dexterity = stats == null ? 0 : Math.max(0, YamlValues.intValue(stats.get("dexterity"), 0));
    int intelligence = stats == null ? 0 : Math.max(0, YamlValues.intValue(stats.get("intelligence"), 0));
    int vitality = stats == null ? 0 : Math.max(0, YamlValues.intValue(stats.get("vitality"), 0));

    Map<?, ?> mana = castMap(map.get("mana"), base + ".mana", errors);
    String manaResourceId = dev.patric.dungeonsreborn.effects.mana.ManaProvider.DEFAULT_RESOURCE;
    double manaMax = 0.0;
    double manaRegen = 0.0;
    if (mana != null && !mana.isEmpty()) {
      String resourceRaw = YamlValues.string(mana, "resource", YamlValues.string(mana, "resourceId", null));
      if (resourceRaw != null && !resourceRaw.isBlank()) {
        manaResourceId = resourceRaw.trim().toLowerCase(java.util.Locale.ROOT);
      }
      manaMax = YamlValues.doubleValue(mana.get("max"), 0.0);
      manaRegen = YamlValues.doubleValue(mana.get("regen"), 0.0);
    }

    List<ClassAttributeBonus> attributes = new ArrayList<>();
    for (Map<?, ?> attrMap : mapList(map.get("attributes"), base + ".attributes", errors)) {
      String path = base + ".attributes";
      String key = YamlValues.string(attrMap, "attribute", YamlValues.string(attrMap, "key", null));
      if (key == null || key.isBlank()) {
        errors.add(path + ".attribute: missing attribute");
        continue;
      }
      double amount = YamlValues.doubleValue(attrMap.get("amount"), 0.0);
      String op = YamlValues.string(attrMap, "operation", "ADD_NUMBER");
      attributes.add(new ClassAttributeBonus(key, amount, op));
    }

    List<ClassPotionBonus> potions = new ArrayList<>();
    for (Map<?, ?> potionMap : mapList(map.get("potions"), base + ".potions", errors)) {
      String path = base + ".potions";
      String effect = YamlValues.string(potionMap, "effect", YamlValues.string(potionMap, "type", null));
      if (effect == null || effect.isBlank()) {
        errors.add(path + ".effect: missing potion effect");
        continue;
      }
      int amplifier = YamlValues.intValue(potionMap.get("amplifier"), 0);
      boolean ambient = YamlValues.bool(potionMap.get("ambient"), true);
      boolean particles = YamlValues.bool(potionMap.get("particles"), true);
      boolean icon = YamlValues.bool(potionMap.get("icon"), true);
      potions.add(new ClassPotionBonus(effect, amplifier, ambient, particles, icon));
    }

    Map<DamageType, Double> resistances = new java.util.EnumMap<>(DamageType.class);
    Map<?, ?> resistancesMap = castMap(map.get("resistances"), base + ".resistances", errors);
    if (resistancesMap != null) {
      for (Map.Entry<?, ?> entry : resistancesMap.entrySet()) {
        String key = entry.getKey() == null ? null : entry.getKey().toString();
        DamageType type = parseDamageType(key, base + ".resistances." + key, errors);
        double multiplier = YamlValues.doubleValue(entry.getValue(), 1.0);
        if (type != null) {
          resistances.put(type, multiplier);
        }
      }
    }

    Map<String, Double> caps = new java.util.LinkedHashMap<>();
    Map<?, ?> capsMap = castMap(map.get("caps"), base + ".caps", errors);
    Map<?, ?> attrCaps = capsMap == null ? null : castMap(capsMap.get("attributes"), base + ".caps.attributes", errors);
    if (attrCaps == null) {
      attrCaps = castMap(map.get("attributeCaps"), base + ".attributeCaps", errors);
    }
    if (attrCaps != null) {
      for (Map.Entry<?, ?> entry : attrCaps.entrySet()) {
        String key = entry.getKey() == null ? null : entry.getKey().toString();
        double limit = YamlValues.doubleValue(entry.getValue(), 0.0);
        if (key == null || limit <= 0.0) {
          continue;
        }
        caps.put(key, limit);
      }
    }

    return new ClassBonusSpec(
        strength,
        dexterity,
        intelligence,
        vitality,
        manaResourceId,
        manaMax,
        manaRegen,
        List.copyOf(attributes),
        List.copyOf(potions),
        java.util.Map.copyOf(resistances),
        java.util.Map.copyOf(caps));
  }

  private List<ClassConditionalBonusSpec> parseConditionalBonuses(Object raw, String base, List<String> errors) {
    Map<?, ?> map = castMap(raw, base, errors);
    if (map == null) {
      return List.of();
    }
    Object conditionalRaw = map.get("conditional");
    if (conditionalRaw == null) {
      conditionalRaw = map.get("conditionalBonuses");
    }
    List<Map<?, ?>> entries = mapList(conditionalRaw, base + ".conditional", errors);
    if (entries.isEmpty()) {
      return List.of();
    }
    List<ClassConditionalBonusSpec> out = new ArrayList<>();
    for (int i = 0; i < entries.size(); i++) {
      Map<?, ?> entry = entries.get(i);
      String path = base + ".conditional[" + i + "]";
      List<String> worlds = parseStringList(entry.get("worlds"));
      if (worlds.isEmpty()) {
        worlds = parseStringList(entry.get("world"));
      }
      List<QuestRegion> regions = parseRegions(entry.get("regions"), path + ".regions", errors);
      if (regions.isEmpty()) {
        regions = parseRegions(entry.get("region"), path + ".region", errors);
      }
      Map<?, ?> bonusMap = castMap(entry.get("bonuses"), path + ".bonuses", errors);
      if (bonusMap == null || bonusMap.isEmpty()) {
        bonusMap = castMap(entry.get("bonus"), path + ".bonus", errors);
      }
      if (bonusMap == null || bonusMap.isEmpty()) {
        errors.add(path + ".bonuses: missing bonuses");
        continue;
      }
      if (worlds.isEmpty() && regions.isEmpty()) {
        errors.add(path + ": missing worlds or regions");
        continue;
      }
      ClassBonusSpec bonuses = parseBonusesMap(bonusMap, path + ".bonuses", errors);
      out.add(new ClassConditionalBonusSpec(List.copyOf(worlds), List.copyOf(regions), bonuses));
    }
    return List.copyOf(out);
  }

  private List<QuestRegion> parseRegions(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return List.of();
    }
    List<QuestRegion> out = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        Map<?, ?> map = castMap(list.get(i), path + "[" + i + "]", errors);
        if (map == null || map.isEmpty()) {
          continue;
        }
        QuestRegion region = parseRegion(map, path + "[" + i + "]", errors);
        if (region != null) {
          out.add(region);
        }
      }
      return List.copyOf(out);
    }
    Map<?, ?> map = castMap(raw, path, errors);
    if (map != null && !map.isEmpty()) {
      QuestRegion region = parseRegion(map, path, errors);
      if (region != null) {
        out.add(region);
      }
    }
    return List.copyOf(out);
  }

  private QuestRegion parseRegion(Map<?, ?> map, String path, List<String> errors) {
    if (map == null) {
      return null;
    }
    String world = YamlValues.string(map, "world", null);
    if (world == null || world.isBlank()) {
      errors.add(path + ".world: missing world");
      return null;
    }
    double x = YamlValues.doubleValue(map.get("x"), 0.0);
    double y = YamlValues.doubleValue(map.get("y"), 0.0);
    double z = YamlValues.doubleValue(map.get("z"), 0.0);
    double radius = YamlValues.doubleValue(map.get("radius"), YamlValues.doubleValue(map.get("r"), 0.0));
    if (radius <= 0.0) {
      errors.add(path + ".radius: must be > 0");
      return null;
    }
    return new QuestRegion(world, x, y, z, radius);
  }

  private Map<?, ?> castMap(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof Map<?, ?> map) {
      return map;
    }
    if (raw instanceof ConfigurationSection section) {
      return section.getValues(false);
    }
    if (errors != null && path != null) {
      errors.add(path + ": must be an object");
    }
    return null;
  }

  private List<Map<?, ?>> mapList(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return List.of();
    }
    List<Map<?, ?>> out = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        Map<?, ?> map = castMap(list.get(i), path + "[" + i + "]", errors);
        if (map != null) {
          out.add(map);
        }
      }
      return List.copyOf(out);
    }
    Map<?, ?> map = castMap(raw, path, errors);
    if (map != null) {
      out.add(map);
    }
    return List.copyOf(out);
  }

  private List<String> parseStringList(Object raw) {
    if (raw == null) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object entry : list) {
        if (entry == null) {
          continue;
        }
        String value = entry.toString().trim();
        if (!value.isEmpty()) {
          out.add(value);
        }
      }
      return List.copyOf(out);
    }
    if (raw instanceof String str) {
      String value = str.trim();
      if (!value.isEmpty()) {
        out.add(value);
      }
    }
    return List.copyOf(out);
  }

  private List<String> normalizeIds(List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String value : values) {
      if (value == null || value.isBlank()) {
        continue;
      }
      out.add(Ids.normalize(value));
    }
    return List.copyOf(out);
  }

  private DamageType parseDamageType(String raw, String path, List<String> errors) {
    if (raw == null || raw.isBlank()) {
      errors.add(path + ": missing damage type");
      return null;
    }
    try {
      return DamageType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      errors.add(path + ": invalid damage type " + raw);
      return null;
    }
  }

  private SkillNodeSpec parseNode(Map<?, ?> map, String path, List<String> errors) {
    if (map == null) {
      throw new IllegalArgumentException("node must be an object");
    }
    String rawId = YamlValues.string(map, "id", null);
    if (rawId == null) {
      throw new IllegalArgumentException("missing id");
    }
    String id = Ids.normalize(rawId);
    String nameRaw = YamlValues.string(map, "name", id);
    String nameKey = YamlValues.string(map, "nameKey", null);
    List<String> descRaw = listOf(map.get("description"));
    String descriptionKey = YamlValues.string(map, "descriptionKey", null);
    ItemStack icon = parseIcon(map.get("icon"), path + ".icon", errors);
    if (icon == null) {
      String materialRaw = YamlValues.string(map, "material", null);
      Material material = parseMaterial(materialRaw, path + ".material", errors);
      icon = material == null ? new ItemStack(Material.PAPER) : new ItemStack(material);
    }
    int cost = Math.max(0, YamlValues.intValue(map.get("cost"), 1));
    int maxRank = Math.max(1, YamlValues.intValue(map.get("maxRank"), YamlValues.intValue(map.get("maxLevel"), 1)));
    List<String> requires = new ArrayList<>();
    Object requiresRaw = map.containsKey("requires") ? map.get("requires") : map.get("prerequisites");
    for (String req : listOf(requiresRaw)) {
      if (req == null || req.isBlank()) {
        continue;
      }
      requires.add(Ids.normalize(req));
    }
    String typeRaw = YamlValues.string(map, "type", null);
    SkillNodeType type = SkillNodeType.parse(typeRaw);
    SkillStatSpec stat = null;
    SkillAttributeSpec attribute = null;
    SkillPotionSpec potion = null;
    SkillAbilitySpec ability = null;
    Object statRaw = map.get("stat");
    if (statRaw instanceof Map<?, ?> statMap) {
      String key = YamlValues.string(statMap, "key", YamlValues.string(statMap, "stat", null));
      double amount = YamlValues.doubleValue(statMap.get("amount"), 0.0);
      SkillScalingSpec scaling = parseScaling(statMap, path + ".stat", errors);
      if (key != null) {
        stat = new SkillStatSpec(key, amount, scaling);
        type = typeRaw == null ? SkillNodeType.STAT : type;
      }
    }
    Object attrRaw = map.get("attribute");
    if (attrRaw instanceof Map<?, ?> attrMap) {
      String key = YamlValues.string(attrMap, "attribute", null);
      double amount = YamlValues.doubleValue(attrMap.get("amount"), 0.0);
      String op = YamlValues.string(attrMap, "operation", "ADD_NUMBER");
      SkillScalingSpec scaling = parseScaling(attrMap, path + ".attribute", errors);
      if (key != null) {
        attribute = new SkillAttributeSpec(key, amount, op, scaling);
        type = typeRaw == null ? SkillNodeType.ATTRIBUTE : type;
      }
    }
    Object potionRaw = map.get("potion");
    if (potionRaw instanceof Map<?, ?> potionMap) {
      String effect = YamlValues.string(potionMap, "effect", null);
      int amp = YamlValues.intValue(potionMap.get("amplifier"), 0);
      boolean ambient = YamlValues.bool(potionMap.get("ambient"), true);
      boolean particles = YamlValues.bool(potionMap.get("particles"), true);
      if (effect != null) {
        potion = new SkillPotionSpec(effect, amp, ambient, particles);
        type = typeRaw == null ? SkillNodeType.POTION : type;
      }
    }
    Object abilityRaw = map.get("ability");
    if (abilityRaw instanceof Map<?, ?> abilityMap) {
      String abilityId = YamlValues.string(abilityMap, "id", YamlValues.string(abilityMap, "ability", null));
      if (abilityId == null || abilityId.isBlank()) {
        errors.add(path + ".ability.id: missing ability id");
      } else {
        String triggerRaw = YamlValues.string(abilityMap, "trigger", "passive");
        SkillAbilityTrigger trigger = SkillAbilityTrigger.parse(triggerRaw);
        boolean requireSneaking = YamlValues.bool(abilityMap.get("requireSneaking"), false);
        String permission = YamlValues.string(abilityMap, "permission", null);
        long periodTicks = Math.max(1L, (long) YamlValues.intValue(abilityMap.get("periodTicks"), 20));
        boolean cancelEvent = YamlValues.bool(abilityMap.get("cancelEvent"), true);
        ability = new SkillAbilitySpec(abilityId, trigger, requireSneaking, permission, periodTicks, cancelEvent);
        if (typeRaw == null) {
          type = SkillNodeType.CUSTOM;
        }
      }
    }
    Component name = EditorItemLore.parseRichText(nameRaw);
    List<Component> desc = new ArrayList<>();
    for (String line : descRaw) {
      desc.add(EditorItemLore.parseRichText(line));
    }
    return new SkillNodeSpec(id, name, List.copyOf(desc), icon, type, cost, maxRank, List.copyOf(requires), stat, attribute, potion,
        ability, nameKey, descriptionKey);
  }

  private SkillScalingSpec parseScaling(Map<?, ?> map, String path, List<String> errors) {
    if (map == null) {
      return SkillScalingSpec.flat();
    }
    String modeRaw = YamlValues.string(map, "scaling", YamlValues.string(map, "scaleMode", null));
    SkillScalingMode mode = SkillScalingMode.parse(modeRaw);
    if (modeRaw != null && mode == SkillScalingMode.FLAT && !modeRaw.equalsIgnoreCase("flat")) {
      errors.add(path + ".scaling: invalid mode " + modeRaw);
    }
    String curveRaw = YamlValues.string(map, "curve", null);
    SkillCurve curve = SkillCurve.parse(curveRaw);
    if (curveRaw != null && curve == SkillCurve.LINEAR && !curveRaw.equalsIgnoreCase("linear")) {
      errors.add(path + ".curve: invalid curve " + curveRaw);
    }
    double scale = YamlValues.doubleValue(map.get("curveScale"), 1.0);
    double offset = YamlValues.doubleValue(map.get("curveOffset"), 0.0);
    return new SkillScalingSpec(mode, curve, scale, offset);
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
    String materialRaw = YamlValues.string(map, "material", null);
    Material material = parseMaterial(materialRaw, path + ".material", errors);
    if (material == null) {
      return null;
    }
    int amount = YamlValues.intValue(map.get("amount"), 1);
    ItemStack stack = new ItemStack(material, amount);
    ItemMeta meta = stack.getItemMeta();
    if (meta != null) {
      String name = YamlValues.string(map, "name", null);
      if (name != null && !name.isBlank()) {
        meta.displayName(EditorItemLore.parseRichText(name));
      }
      List<String> loreRaw = listOf(map.get("lore"));
      if (!loreRaw.isEmpty()) {
        meta.lore(GuiMini.loreMm(loreRaw));
      }
      stack.setItemMeta(meta);
    }
    return stack;
  }

  private Material parseMaterial(String raw, String path, List<String> errors) {
    if (raw == null || raw.isBlank()) {
      errors.add(path + ": missing material");
      return null;
    }
    Material material = Material.matchMaterial(raw);
    if (material == null) {
      material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
    }
    if (material == null) {
      errors.add(path + ": invalid material " + raw);
    }
    return material;
  }

  private List<String> listOf(ConfigurationSection section, String key) {
    if (section == null) {
      return List.of();
    }
    if (section.isList(key)) {
      return section.getStringList(key);
    }
    String single = section.getString(key);
    if (single == null || single.isBlank()) {
      return List.of();
    }
    return List.of(single);
  }

  private List<String> listOf(Object raw) {
    if (raw == null) {
      return List.of();
    }
    if (raw instanceof List<?> list) {
      List<String> out = new ArrayList<>();
      for (Object item : list) {
        if (item != null) {
          out.add(String.valueOf(item));
        }
      }
      return out;
    }
    return List.of(String.valueOf(raw));
  }
}
