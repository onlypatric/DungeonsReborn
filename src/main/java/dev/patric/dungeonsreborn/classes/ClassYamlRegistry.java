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
import dev.patric.dungeonsreborn.effects.editor.EditorItemLore;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.system.SystemStatusStore;
import dev.patric.dungeonsreborn.util.YamlValues;
import dev.patric.dungeonsreborn.classes.skills.SkillAttributeSpec;
import dev.patric.dungeonsreborn.classes.skills.SkillEdgeSpec;
import dev.patric.dungeonsreborn.classes.skills.SkillNodeSpec;
import dev.patric.dungeonsreborn.classes.skills.SkillNodeType;
import dev.patric.dungeonsreborn.classes.skills.SkillPotionSpec;
import dev.patric.dungeonsreborn.classes.skills.SkillStatSpec;
import dev.patric.dungeonsreborn.classes.skills.SkillTreeSpec;
import dev.patric.dungeonsreborn.effects.damage.DamageType;
import net.kyori.adventure.text.Component;

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
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file());
    Map<String, ClassSpec> next = parseClasses(cfg, errors);
    if (errors.isEmpty()) {
      classes.clear();
      classes.putAll(next);
    }
    lastErrors = List.copyOf(errors);
    if (!errors.isEmpty()) {
      logger.warning("[Classes] YAML reload had " + errors.size() + " errors");
      for (String error : errors) {
        logger.warning("[Classes] YAML: " + error);
      }
    } else {
      logger.info("[Classes] YAML loaded " + next.size() + " classes");
    }
    SystemStatusStore.get().record(
        "classes",
        "Classes",
        file().getPath(),
        "classes=" + (errors.isEmpty() ? next.size() : classes.size()),
        errors);
    return new ReloadResult(errors.isEmpty() ? next.size() : classes.size(), errors);
  }

  private void ensureFile() {
    File file = file();
    if (file.exists()) {
      return;
    }
    plugin.saveResource("classes.yml", false);
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
        boolean enabled = node.getBoolean("enabled", true);
        List<String> descRaw = listOf(node, "description");
        ItemStack icon = parseIcon(node.get("icon"), base + ".icon", errors);
        if (icon == null) {
          icon = new ItemStack(Material.BOOK);
        }
        ClassUnlockSpec unlock = parseUnlock(node.getConfigurationSection("unlock"), base + ".unlock", errors);
        SkillTreeSpec skillTree = parseSkillTree(node.getConfigurationSection("path"), base + ".path", errors);
        ClassBonusSpec bonuses = parseBonuses(node.getConfigurationSection("bonuses"), base + ".bonuses", errors);
        Component name = EditorItemLore.parseRichText(nameRaw);
        List<Component> desc = new ArrayList<>();
        for (String line : descRaw) {
          desc.add(EditorItemLore.parseRichText(line));
        }
        out.put(id, new ClassSpec(id, enabled, name, List.copyOf(desc), icon, unlock, skillTree, bonuses));
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
    return new ClassUnlockSpec(level, tokens, List.copyOf(quests));
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
    for (int i = 0; i < nodesRaw.size(); i++) {
      Map<?, ?> map = nodesRaw.get(i);
      String path = base + ".nodes[" + i + "]";
      try {
        SkillNodeSpec node = parseNode(map, path, errors);
        if (node != null) {
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
    ConfigurationSection respec = section.getConfigurationSection("respec");
    int respecTokens = respec == null ? 0 : Math.max(0, respec.getInt("tokens", 0));
    int respecPoints = respec == null ? 0 : Math.max(0, respec.getInt("points", 0));
    if (edges.isEmpty() && nodes.size() > 1) {
      for (int i = 1; i < nodes.size(); i++) {
        edges.add(new SkillEdgeSpec(nodes.get(i - 1).id(), nodes.get(i).id()));
      }
    }
    return new SkillTreeSpec(List.copyOf(nodes), List.copyOf(edges), respecTokens, respecPoints);
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
    double manaMax = mana == null ? 0.0 : YamlValues.doubleValue(mana.get("max"), 0.0);
    double manaRegen = mana == null ? 0.0 : YamlValues.doubleValue(mana.get("regen"), 0.0);

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
        manaMax,
        manaRegen,
        List.copyOf(attributes),
        List.copyOf(potions),
        java.util.Map.copyOf(resistances),
        java.util.Map.copyOf(caps));
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
    List<String> descRaw = listOf(map.get("description"));
    ItemStack icon = parseIcon(map.get("icon"), path + ".icon", errors);
    if (icon == null) {
      String materialRaw = YamlValues.string(map, "material", null);
      Material material = parseMaterial(materialRaw, path + ".material", errors);
      icon = material == null ? new ItemStack(Material.PAPER) : new ItemStack(material);
    }
    int cost = Math.max(0, YamlValues.intValue(map.get("cost"), 1));
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
    Object statRaw = map.get("stat");
    if (statRaw instanceof Map<?, ?> statMap) {
      String key = YamlValues.string(statMap, "key", YamlValues.string(statMap, "stat", null));
      int amount = YamlValues.intValue(statMap.get("amount"), 0);
      if (key != null) {
        stat = new SkillStatSpec(key, amount);
        type = typeRaw == null ? SkillNodeType.STAT : type;
      }
    }
    Object attrRaw = map.get("attribute");
    if (attrRaw instanceof Map<?, ?> attrMap) {
      String key = YamlValues.string(attrMap, "attribute", null);
      double amount = YamlValues.doubleValue(attrMap.get("amount"), 0.0);
      String op = YamlValues.string(attrMap, "operation", "ADD_NUMBER");
      if (key != null) {
        attribute = new SkillAttributeSpec(key, amount, op);
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
    Component name = EditorItemLore.parseRichText(nameRaw);
    List<Component> desc = new ArrayList<>();
    for (String line : descRaw) {
      desc.add(EditorItemLore.parseRichText(line));
    }
    return new SkillNodeSpec(id, name, List.copyOf(desc), icon, type, cost, List.copyOf(requires), stat, attribute, potion);
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
