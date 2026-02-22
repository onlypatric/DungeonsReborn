package dev.patric.dungeonsreborn.effects.upgrades;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.logging.ServiceLogger;
import dev.patric.dungeonsreborn.system.SystemStatusStore;
import dev.patric.dungeonsreborn.util.PluginResources;
import dev.patric.dungeonsreborn.util.YamlValues;

public final class UpgradeYamlRegistry {
  public record ReloadResult(int loaded, List<String> errors) {
  }

  private final JavaPlugin plugin;
  private final EffectsEngine engine;
  private final ServiceLogger logger;
  private final Map<String, UpgradeTemplate> upgrades = new LinkedHashMap<>();

  public UpgradeYamlRegistry(JavaPlugin plugin, EffectsEngine engine, ServiceLogger logger) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.engine = Objects.requireNonNull(engine, "engine");
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  public File upgradesDir() {
    return new File(plugin.getDataFolder(), "effects/upgrades");
  }

  public Map<String, UpgradeTemplate> upgrades() {
    return java.util.Collections.unmodifiableMap(upgrades);
  }

  public UpgradeSpec upgradeSpec(String id) {
    if (id == null) {
      return null;
    }
    UpgradeTemplate template = upgrades.get(Ids.normalize(id));
    return template == null ? null : template.spec();
  }

  public UpgradeTemplate upgradeTemplate(String id) {
    if (id == null) {
      return null;
    }
    return upgrades.get(Ids.normalize(id));
  }

  public ItemStack upgradeItem(String id) {
    UpgradeTemplate template = upgradeTemplate(id);
    return template == null ? null : template.buildItem();
  }

  public ReloadResult reload() {
    List<String> errors = new ArrayList<>();
    boolean safeMode = plugin.getConfig().getBoolean("upgrades.validation.safeMode", true);
    boolean logWarnings = plugin.getConfig().getBoolean("upgrades.validation.logWarnings", true);
    File dir = upgradesDir();
    if (!dir.exists()) {
      dir.mkdirs();
    }
    UpgradeLore.configure(plugin.getConfig().getConfigurationSection("upgrades.lore"), engine);
    ensureDefaultUpgrades(dir);
    Map<String, UpgradeTemplate> next = new LinkedHashMap<>();
    File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml")
        || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
    if (files == null) {
      files = new File[0];
    }
    java.util.Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
    for (File file : files) {
      String base = file.getPath();
      try {
        UpgradeTemplate template = loadUpgrade(file, base, errors);
        if (template != null) {
          next.put(template.spec().id(), template);
        }
      } catch (Exception ex) {
        errors.add(base + ": " + ex.getMessage());
      }
    }
    if (errors.isEmpty() || safeMode) {
      upgrades.clear();
      upgrades.putAll(next);
    }
    if (!errors.isEmpty()) {
      if (logWarnings) {
        String label = safeMode ? "warnings" : "errors";
        logger.warn("[Upgrades] YAML reload had " + errors.size() + " " + label);
        for (String e : errors) {
          logger.warn("[Upgrades] YAML: " + e);
        }
      }
      if (!safeMode) {
        logger.warn("[Upgrades] YAML reload aborted; enable upgrades.validation.safeMode to keep valid upgrades loaded.");
      }
    } else {
      logger.info("[Upgrades] YAML loaded " + next.size() + " upgrades");
    }
    int loadedCount = (errors.isEmpty() || safeMode) ? next.size() : upgrades.size();
    SystemStatusStore.get().record(
        "upgrades",
        "Upgrades",
        upgradesDir().getPath(),
        "upgrades=" + loadedCount,
        errors);
    return new ReloadResult(loadedCount, errors);
  }

  private void ensureDefaultUpgrades(File dir) {
    List<String> entries = readResourceIndex("effects/upgrades/index.txt");
    if (entries.isEmpty()) {
      return;
    }
    for (String entry : entries) {
      String trimmed = entry.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      if (!trimmed.endsWith(".yml") && !trimmed.endsWith(".yaml")) {
        continue;
      }
      File out = new File(dir, trimmed);
      if (out.exists()) {
        continue;
      }
      String resourcePath = "effects/upgrades/" + trimmed;
      if (!PluginResources.saveResourceIfPresent(plugin, resourcePath, false)) {
        logger.warn("[Upgrades] Missing bundled upgrade: " + resourcePath + " (skipping copy)");
      }
    }
  }

  private List<String> readResourceIndex(String path) {
    InputStream stream = plugin.getResource(path);
    if (stream == null) {
      return List.of();
    }
    List<String> lines = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        lines.add(line);
      }
    } catch (Exception ex) {
      logger.warn("[Upgrades] Unable to read " + path + ": " + ex.getMessage());
      return List.of();
    }
    return lines;
  }

  private UpgradeTemplate loadUpgrade(File file, String base, List<String> errors) {
    String filename = file.getName();
    int dot = filename.lastIndexOf('.');
    if (dot <= 0) {
      errors.add(base + ": filename must be <upgradeId>.yml");
      return null;
    }
    String fileIdRaw = filename.substring(0, dot);
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    int schemaVersion = cfg.getInt("schemaVersion", 1);
    if (schemaVersion != 1) {
      errors.add(base + ": unsupported schemaVersion=" + schemaVersion + " (expected 1)");
      return null;
    }
    String idRaw = YamlValues.string(cfg.get("id"), fileIdRaw);
    String id = normalizeId(idRaw, base + ".id");
    if (upgrades.containsKey(id)) {
      errors.add(base + ": duplicate upgrade id=" + id);
      return null;
    }

    String name = YamlValues.string(cfg.get("name"), "");
    String description = YamlValues.string(cfg.get("description"), "");
    boolean allowUnsafe = YamlValues.bool(cfg.get("allowUnsafe"), false);

    UpgradeRequirements requirements = parseRequirements(cfg.getConfigurationSection("requirements"));
    UpgradePriceSpec price = parsePrice(cfg.getConfigurationSection("price"), base, errors);
    UpgradeTargetSpec target = parseTarget(cfg.getConfigurationSection("target"), base, errors);
    UpgradeCompatibilitySpec compatibility = parseCompatibility(cfg.getConfigurationSection("compatibility"), base, errors);
    UpgradeLimitsSpec limits = parseLimits(cfg.getConfigurationSection("limits"), base, errors);
    UpgradeProgressionSpec progression = parseProgression(cfg.getConfigurationSection("progression"), limits, base, errors);
    UpgradeFusionSpec fusion = parseFusion(cfg.getConfigurationSection("fusion"), base, errors);
    UpgradeSalvageSpec salvage = parseSalvage(cfg.getConfigurationSection("salvage"), base, errors);
    UpgradeBehaviorSpec behaviors = parseBehaviors(cfg.getConfigurationSection("behaviors"), base, errors);
    List<UpgradeSpellSpec> spells = parseSpells(cfg, base, errors);
    List<UpgradeModifierSpec> modifiers = parseModifiers(cfg.get("modifiers"), base + ".modifiers", errors);
    List<UpgradeAttributeSpec> attributes = parseAttributes(cfg.get("attributes"), base + ".attributes", errors);
    List<UpgradeEnchantSpec> enchants = parseEnchants(cfg.get("enchants"), base + ".enchants", errors);

    UpgradeSpec spec = new UpgradeSpec(id, name, description, requirements, price, target, compatibility, limits, progression,
        fusion, salvage, behaviors, allowUnsafe, modifiers, attributes, enchants, spells);
    ItemStack template = buildUpgradeItem(cfg, spec, base, errors);
    if (template == null) {
      return null;
    }
    Map<String, ItemStack> variants = parseUpgradeVariants(cfg, spec, base, errors);
    return new UpgradeTemplate(spec, template, variants);
  }

  private ItemStack buildUpgradeItem(YamlConfiguration cfg, UpgradeSpec spec, String base, List<String> errors) {
    ItemStack item = resolveUpgradeItem(cfg.get("item"), base + ".item", errors);
    if (item == null || item.getType().isAir()) {
      item = new ItemStack(Material.ENCHANTED_BOOK);
    }
    item = item.clone();
    ItemMarkers.setUpgradeId(item, spec.id());
    UpgradeLore.applyUpgradeBookLore(item, spec);
    item = applyUpgradeVisuals(item, cfg.getConfigurationSection("visual"), base + ".visual", errors);
    return item;
  }

  private Map<String, ItemStack> parseUpgradeVariants(
      YamlConfiguration cfg,
      UpgradeSpec spec,
      String base,
      List<String> errors) {
    Object raw = cfg.get("variants");
    if (raw == null) {
      raw = cfg.get("templates");
    }
    if (!(raw instanceof List<?> list)) {
      return Map.of();
    }
    Map<String, ItemStack> variants = new LinkedHashMap<>();
    int index = 0;
    for (Object entry : list) {
      String entryPath = base + ".variants[" + index + "]";
      ItemStack item = null;
      ConfigurationSection visual = null;
      String id = null;
      if (entry instanceof ConfigurationSection section) {
        id = YamlValues.string(section.get("id"), null);
        Object itemRaw = section.contains("item") ? section.get("item") : section;
        item = resolveUpgradeItem(itemRaw, entryPath + ".item", errors);
        visual = section.getConfigurationSection("visual");
      } else if (entry instanceof Map<?, ?> map) {
        Object idRaw = map.get("id");
        if (idRaw != null) {
          id = String.valueOf(idRaw);
        }
        Object itemRaw = map.containsKey("item") ? map.get("item") : map;
        item = resolveUpgradeItem(itemRaw, entryPath + ".item", errors);
        Object visualRaw = map.get("visual");
        if (visualRaw instanceof ConfigurationSection section) {
          visual = section;
        }
      } else {
        item = resolveUpgradeItem(entry, entryPath, errors);
      }
      if (item == null || item.getType().isAir()) {
        index++;
        continue;
      }
      item = item.clone();
      ItemMarkers.setUpgradeId(item, spec.id());
      UpgradeLore.applyUpgradeBookLore(item, spec);
      item = applyUpgradeVisuals(item, visual, entryPath + ".visual", errors);
      if (id == null || id.isBlank()) {
        id = "variant_" + index;
      }
      variants.put(Ids.normalize(id), item);
      index++;
    }
    return variants.isEmpty() ? Map.of() : variants;
  }

  private ItemStack resolveUpgradeItem(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof ItemStack stack) {
      return stack;
    }
    if (raw instanceof String materialRaw) {
      Material material = Material.matchMaterial(materialRaw.trim());
      if (material == null) {
        errors.add(path + ": unknown material=" + materialRaw);
        return null;
      }
      return new ItemStack(material);
    }
    if (raw instanceof ConfigurationSection section) {
      ItemStack stack = section.getItemStack("item");
      if (stack != null) {
        return stack;
      }
      String materialRaw = YamlValues.string(section.get("material"), YamlValues.string(section.get("type"), null));
      if (materialRaw == null || materialRaw.isBlank()) {
        errors.add(path + ".material: missing material");
        return null;
      }
      Material material = Material.matchMaterial(materialRaw);
      if (material == null) {
        errors.add(path + ".material: unknown material=" + materialRaw);
        return null;
      }
      return new ItemStack(material);
    }
    if (raw instanceof Map<?, ?> map) {
      Object materialRaw = map.containsKey("material") ? map.get("material") : map.get("type");
      if (materialRaw == null) {
        errors.add(path + ".material: missing material");
        return null;
      }
      Material material = Material.matchMaterial(String.valueOf(materialRaw));
      if (material == null) {
        errors.add(path + ".material: unknown material=" + materialRaw);
        return null;
      }
      return new ItemStack(material);
    }
    errors.add(path + ": expected item stack, material string, or object");
    return null;
  }

  private ItemStack applyUpgradeVisuals(ItemStack item, ConfigurationSection visual, String path, List<String> errors) {
    if (item == null || visual == null) {
      return item;
    }
    ItemStack working = item;
    ItemStack glinted = applyGlint(working, visual);
    if (glinted != null) {
      working = glinted;
    }
    ItemMeta meta = working.getItemMeta();
    if (meta == null) {
      return working;
    }
    String nameKey = YamlValues.string(visual.get("nameKey"), null);
    String nameRaw = YamlValues.string(visual.get("name"), null);
    if (nameKey != null && !nameKey.isBlank()) {
      String name = Locales.text(null, nameKey);
      if (name != null && !name.isBlank()) {
        meta.displayName(UpgradeLore.parseRichText(name));
      }
    } else if (nameRaw != null && !nameRaw.isBlank()) {
      meta.displayName(UpgradeLore.parseRichText(nameRaw));
    }
    if (visual.contains("customModelData") || visual.contains("custom_model_data")) {
      int cmd = visual.getInt("customModelData", visual.getInt("custom_model_data", 0));
      if (cmd < 0) {
        errors.add(path + ".customModelData: must be >= 0");
      } else {
        setCustomModelData(meta, cmd);
      }
    }
    working.setItemMeta(meta);
    return working;
  }

  private ItemStack applyGlint(ItemStack item, ConfigurationSection visual) {
    if (visual == null || !visual.contains("glint")) {
      return item;
    }
    boolean enabled = visual.getBoolean("glint", false);
    return dev.patric.dungeonsreborn.gui.GuiItem.of(item).glint(enabled).build();
  }

  private void setCustomModelData(ItemMeta meta, int value) {
    if (meta == null) {
      return;
    }
    CustomModelDataComponent component = meta.getCustomModelDataComponent();
    component.setFloats(List.of((float) value));
  }

  private UpgradeRequirements parseRequirements(ConfigurationSection section) {
    if (section == null) {
      return UpgradeRequirements.none();
    }
    int minXp = section.getInt("minXp", 0);
    int consumeXp = section.getInt("consumeXp", 0);
    int minTotalXp = section.getInt("minTotalXp", 0);
    int consumeTotalXp = section.getInt("consumeTotalXp", 0);
    double minProgress = normalizeProgress(section.getDouble("minProgress", 0.0));
    double consumeProgress = normalizeProgress(section.getDouble("consumeProgress", 0.0));
    double minMaxMana = section.contains("minMaxMana") ? section.getDouble("minMaxMana")
        : section.getDouble("minManaMax", section.getDouble("minMana", 0.0));
    return new UpgradeRequirements(minXp, consumeXp, minTotalXp, consumeTotalXp, minProgress, consumeProgress, minMaxMana);
  }

  private UpgradePriceSpec parsePrice(ConfigurationSection section, String base, List<String> errors) {
    if (section == null) {
      return UpgradePriceSpec.none();
    }
    int normal = section.getInt("normal", section.getInt("token", section.getInt("tokens", 0)));
    int compressed = section.getInt("compressed", 0);
    int pallet = section.getInt("pallet", 0);
    if (normal < 0) {
      errors.add(base + ".price.normal: must be >= 0");
      normal = 0;
    }
    if (compressed < 0) {
      errors.add(base + ".price.compressed: must be >= 0");
      compressed = 0;
    }
    if (pallet < 0) {
      errors.add(base + ".price.pallet: must be >= 0");
      pallet = 0;
    }
    return new UpgradePriceSpec(normal, compressed, pallet);
  }

  private double normalizeProgress(double value) {
    if (!Double.isFinite(value)) {
      return 0.0;
    }
    double normalized = value;
    if (normalized > 1.0) {
      normalized = normalized / 100.0;
    }
    if (normalized < 0.0) {
      return 0.0;
    }
    if (normalized > 1.0) {
      return 1.0;
    }
    return normalized;
  }

  private List<UpgradeSpellSpec> parseSpells(ConfigurationSection section, String base, List<String> errors) {
    if (section == null) {
      return List.of();
    }
    List<UpgradeSpellSpec> out = new ArrayList<>();
    Object spellValue = section.get("spell");
    if (spellValue != null) {
      out.addAll(parseSpellValue(spellValue, base + ".spell", errors));
    }
    Object spellsValue = section.get("spells");
    if (spellsValue != null) {
      out.addAll(parseSpellValue(spellsValue, base + ".spells", errors));
    }
    return out;
  }

  private List<UpgradeSpellSpec> parseSpellValue(Object value, String base, List<String> errors) {
    if (value instanceof ConfigurationSection section) {
      UpgradeSpellSpec spell = parseSpellEntry(section, base, errors);
      return spell == null ? List.of() : List.of(spell);
    }
    if (value instanceof Map<?, ?> map) {
      UpgradeSpellSpec spell = parseSpellEntry(map, base, errors);
      return spell == null ? List.of() : List.of(spell);
    }
    if (value instanceof List<?> list) {
      List<UpgradeSpellSpec> out = new ArrayList<>();
      int index = 0;
      for (Object entry : list) {
        UpgradeSpellSpec spell = parseSpellEntry(entry, base + "[" + index + "]", errors);
        if (spell != null) {
          out.add(spell);
        }
        index++;
      }
      return out;
    }
    errors.add(base + ": expected map or list for spell definition");
    return List.of();
  }

  private UpgradeSpellSpec parseSpellEntry(Object entry, String base, List<String> errors) {
    if (entry instanceof ConfigurationSection section) {
      String abilityRaw = YamlValues.string(section.get("ability"), null);
      String activatorRaw = YamlValues.string(section.get("activator"), YamlValues.string(section.get("activation"), null));
      return parseSpellData(abilityRaw, activatorRaw, section, base, errors);
    }
    if (entry instanceof Map<?, ?> map) {
      String abilityRaw = YamlValues.string(map.get("ability"), null);
      String activatorRaw = YamlValues.string(map.get("activator"), YamlValues.string(map.get("activation"), null));
      return parseSpellData(abilityRaw, activatorRaw, map, base, errors);
    }
    errors.add(base + ": expected map for spell entry");
    return null;
  }

  private UpgradeSpellSpec parseSpellData(String abilityRaw, String activatorRaw, ConfigurationSection section, String base,
      List<String> errors) {
    long cooldownTicks = resolveCooldownTicks(section);
    Integer manaCost = resolveInt(section, "manaCost", "mana");
    Integer durabilityCost = resolveInt(section, "durabilityCost", "durability", "damage");
    Integer consumeAmount = resolveInt(section, "consumeAmount", "consume", "amount");
    String scopeRaw = YamlValues.string(section.get("cooldownScope"), YamlValues.string(section.get("cooldown_scope"), null));
    boolean requireSneaking = resolveBool(section, "requireSneaking", "sneaking", "requireSneak");
    boolean requireSprinting = resolveBool(section, "requireSprinting", "sprinting", "requireSprint");
    boolean requireAirborne = resolveBool(section, "requireAirborne", "airborne");
    boolean requireOnGround = resolveBool(section, "requireOnGround", "onGround", "grounded");
    return parseSpellData(abilityRaw, activatorRaw, cooldownTicks, manaCost, durabilityCost, consumeAmount, scopeRaw,
        requireSneaking, requireSprinting, requireAirborne, requireOnGround, base, errors);
  }

  private UpgradeSpellSpec parseSpellData(String abilityRaw, String activatorRaw, Map<?, ?> map, String base,
      List<String> errors) {
    long cooldownTicks = resolveCooldownTicks(map);
    Integer manaCost = resolveInt(map, "manaCost", "mana");
    Integer durabilityCost = resolveInt(map, "durabilityCost", "durability", "damage");
    Integer consumeAmount = resolveInt(map, "consumeAmount", "consume", "amount");
    String scopeRaw = YamlValues.string(map.get("cooldownScope"), YamlValues.string(map.get("cooldown_scope"), null));
    boolean requireSneaking = resolveBool(map, "requireSneaking", "sneaking", "requireSneak");
    boolean requireSprinting = resolveBool(map, "requireSprinting", "sprinting", "requireSprint");
    boolean requireAirborne = resolveBool(map, "requireAirborne", "airborne");
    boolean requireOnGround = resolveBool(map, "requireOnGround", "onGround", "grounded");
    return parseSpellData(abilityRaw, activatorRaw, cooldownTicks, manaCost, durabilityCost, consumeAmount, scopeRaw,
        requireSneaking, requireSprinting, requireAirborne, requireOnGround, base, errors);
  }

  private UpgradeSpellSpec parseSpellData(String abilityRaw, String activatorRaw, long cooldownTicks, Integer manaCost,
      Integer durabilityCost, Integer consumeAmount, String scopeRaw, boolean requireSneaking, boolean requireSprinting,
      boolean requireAirborne, boolean requireOnGround, String base, List<String> errors) {
    if (abilityRaw == null || abilityRaw.isBlank()) {
      errors.add(base + ".ability: missing ability id");
      return null;
    }
    String abilityId;
    try {
      abilityId = Ids.normalize(abilityRaw);
    } catch (Exception ex) {
      errors.add(base + ".ability: invalid id (" + ex.getMessage() + ")");
      return null;
    }
    if (!engine.hasAbility(abilityId)) {
      errors.add(base + ".ability: ability not registered: " + abilityId);
      return null;
    }
    try {
      UpgradeActivator activator = UpgradeActivator.parse(activatorRaw, base + ".activator");
      UpgradeCooldownScope scope = UpgradeCooldownScope.parse(scopeRaw, base + ".cooldownScope");
      Long cooldown = cooldownTicks <= 0 ? null : cooldownTicks;
      return new UpgradeSpellSpec(abilityId, activator, cooldown, manaCost, durabilityCost, consumeAmount, scope,
          requireSneaking, requireSprinting, requireAirborne, requireOnGround);
    } catch (Exception ex) {
      errors.add(ex.getMessage());
      return null;
    }
  }

  private static long resolveCooldownTicks(ConfigurationSection section) {
    long cooldown = YamlValues.longValue(section.get("cooldownTicks"), 0L);
    if (cooldown <= 0) {
      double seconds = YamlValues.doubleValue(section.get("cooldownSeconds"), 0.0);
      if (seconds > 0) {
        cooldown = Math.round(seconds * 20.0);
      }
    }
    if (cooldown <= 0) {
      double seconds = YamlValues.doubleValue(section.get("cooldown"), 0.0);
      if (seconds > 0) {
        cooldown = Math.round(seconds * 20.0);
      }
    }
    return cooldown;
  }

  private static long resolveCooldownTicks(Map<?, ?> map) {
    long cooldown = YamlValues.longValue(map.get("cooldownTicks"), 0L);
    if (cooldown <= 0) {
      double seconds = YamlValues.doubleValue(map.get("cooldownSeconds"), 0.0);
      if (seconds > 0) {
        cooldown = Math.round(seconds * 20.0);
      }
    }
    if (cooldown <= 0) {
      double seconds = YamlValues.doubleValue(map.get("cooldown"), 0.0);
      if (seconds > 0) {
        cooldown = Math.round(seconds * 20.0);
      }
    }
    return cooldown;
  }

  private static Integer resolveInt(ConfigurationSection section, String... keys) {
    for (String key : keys) {
      if (section.contains(key)) {
        int value = YamlValues.intValue(section.get(key), 0);
        if (value > 0) {
          return value;
        }
      }
    }
    return null;
  }

  private static Integer resolveInt(Map<?, ?> map, String... keys) {
    for (String key : keys) {
      if (map.containsKey(key)) {
        int value = YamlValues.intValue(map.get(key), 0);
        if (value > 0) {
          return value;
        }
      }
    }
    return null;
  }

  private static boolean resolveBool(ConfigurationSection section, String... keys) {
    for (String key : keys) {
      if (section.contains(key) && YamlValues.bool(section.get(key), false)) {
        return true;
      }
    }
    return false;
  }

  private static boolean resolveBool(Map<?, ?> map, String... keys) {
    for (String key : keys) {
      if (map.containsKey(key) && YamlValues.bool(map.get(key), false)) {
        return true;
      }
    }
    return false;
  }

  private UpgradeTargetSpec parseTarget(ConfigurationSection section, String base, List<String> errors) {
    if (section == null) {
      return UpgradeTargetSpec.none();
    }
    List<String> abilityIds = new ArrayList<>();
    List<String> rawAbilities = readStringList(section.get("abilities"));
    if (rawAbilities.isEmpty()) {
      rawAbilities = readStringList(section.get("ability"));
    }
    for (String raw : rawAbilities) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      String normalized;
      try {
        normalized = Ids.normalize(raw);
      } catch (Exception ex) {
        errors.add(base + ".target.abilities: invalid id " + raw);
        continue;
      }
      if (!engine.hasAbility(normalized)) {
        errors.add(base + ".target.abilities: ability not registered: " + normalized);
        continue;
      }
      abilityIds.add(normalized);
    }
    List<String> abilityTags = new ArrayList<>();
    List<String> rawTags = readStringList(section.get("tags"));
    if (rawTags.isEmpty()) {
      rawTags = readStringList(section.get("tag"));
    }
    for (String raw : rawTags) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      String normalized;
      try {
        normalized = Ids.normalize(raw);
      } catch (Exception ex) {
        errors.add(base + ".target.tags: invalid tag " + raw);
        continue;
      }
      abilityTags.add(normalized);
    }
    List<String> itemIds = new ArrayList<>();
    List<String> rawItemIds = readStringList(section.get("itemIds"));
    if (rawItemIds.isEmpty()) {
      rawItemIds = readStringList(section.get("items"));
    }
    if (rawItemIds.isEmpty()) {
      rawItemIds = readStringList(section.get("item"));
    }
    for (String raw : rawItemIds) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      try {
        itemIds.add(Ids.normalize(raw));
      } catch (Exception ex) {
        errors.add(base + ".target.itemIds: invalid id " + raw);
      }
    }
    List<String> itemTags = new ArrayList<>();
    List<String> rawItemTags = readStringList(section.get("itemTags"));
    if (rawItemTags.isEmpty()) {
      rawItemTags = readStringList(section.get("itemTag"));
    }
    for (String raw : rawItemTags) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      try {
        itemTags.add(Ids.normalize(raw));
      } catch (Exception ex) {
        errors.add(base + ".target.itemTags: invalid tag " + raw);
      }
    }
    List<String> itemCategories = new ArrayList<>();
    List<String> rawCategories = readStringList(section.get("itemCategories"));
    if (rawCategories.isEmpty()) {
      rawCategories = readStringList(section.get("itemCategory"));
    }
    if (rawCategories.isEmpty()) {
      rawCategories = readStringList(section.get("categories"));
    }
    if (rawCategories.isEmpty()) {
      rawCategories = readStringList(section.get("category"));
    }
    for (String raw : rawCategories) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      try {
        itemCategories.add(Ids.normalize(raw));
      } catch (Exception ex) {
        errors.add(base + ".target.itemCategories: invalid category " + raw);
      }
    }
    return new UpgradeTargetSpec(
        List.copyOf(abilityIds),
        List.copyOf(abilityTags),
        List.copyOf(itemIds),
        List.copyOf(itemTags),
        List.copyOf(itemCategories));
  }

  private UpgradeCompatibilitySpec parseCompatibility(ConfigurationSection section, String base, List<String> errors) {
    if (section == null) {
      return UpgradeCompatibilitySpec.none();
    }
    Set<String> allowItems = normalizeIds(readStringList(section.get("allowItems")), base + ".compatibility.allowItems", errors);
    Set<String> denyItems = normalizeIds(readStringList(section.get("denyItems")), base + ".compatibility.denyItems", errors);
    Set<Material> allowMaterials = parseMaterials(readStringList(section.get("allowMaterials")), base + ".compatibility.allowMaterials", errors);
    Set<Material> denyMaterials = parseMaterials(readStringList(section.get("denyMaterials")), base + ".compatibility.denyMaterials", errors);
    Set<String> groups = normalizeIds(readStringList(section.get("groups")), base + ".compatibility.groups", errors);
    if (groups.isEmpty()) {
      groups = normalizeIds(readStringList(section.get("group")), base + ".compatibility.group", errors);
    }
    if (groups.isEmpty()) {
      groups = normalizeIds(readStringList(section.get("incompatibilityGroups")), base + ".compatibility.incompatibilityGroups", errors);
    }
    if (groups.isEmpty()) {
      groups = normalizeIds(readStringList(section.get("incompatibilityGroup")), base + ".compatibility.incompatibilityGroup", errors);
    }
    int priority = section.getInt("priority", 0);
    return new UpgradeCompatibilitySpec(allowItems, denyItems, allowMaterials, denyMaterials, groups, priority);
  }

  private UpgradeBehaviorSpec parseBehaviors(ConfigurationSection section, String base, List<String> errors) {
    if (section == null) {
      return UpgradeBehaviorSpec.none();
    }
    List<String> secondaryAbilities = parseAbilityList(section.get("secondaryAbilities"), base + ".behaviors.secondaryAbilities", errors);
    if (secondaryAbilities.isEmpty()) {
      secondaryAbilities = parseAbilityList(section.get("secondaryAbility"), base + ".behaviors.secondaryAbility", errors);
    }
    List<String> secondaryDescriptions = readStringList(section.get("secondaryDescriptions"));
    if (secondaryDescriptions.isEmpty()) {
      secondaryDescriptions = readStringList(section.get("secondaryDescription"));
    }
    List<String> particlePresets = parseAbilityList(section.get("particlePresets"), base + ".behaviors.particlePresets", errors);
    if (particlePresets.isEmpty()) {
      particlePresets = parseAbilityList(section.get("particlePreset"), base + ".behaviors.particlePreset", errors);
    }
    List<UpgradeStatusEffectSpec> statusEffects = parseStatusEffects(section.get("statusEffects"), base + ".behaviors.statusEffects", errors);
    List<UpgradeStatusEffectSpec> inventoryEffects = parseStatusEffects(section.get("inventoryEffects"),
        base + ".behaviors.inventoryEffects", errors);
    if (inventoryEffects.isEmpty()) {
      inventoryEffects = parseStatusEffects(section.get("inventoryEffect"), base + ".behaviors.inventoryEffect", errors);
    }
    List<UpgradeOnDamagedSpec> onDamagedEffects = parseOnDamagedEffects(section.get("onDamaged"),
        base + ".behaviors.onDamaged", errors);
    if (onDamagedEffects.isEmpty()) {
      onDamagedEffects = parseOnDamagedEffects(section.get("onDamagedEffects"),
          base + ".behaviors.onDamagedEffects", errors);
    }
    boolean inventoryActive = YamlValues.bool(section.get("inventoryActive"), YamlValues.bool(section.get("activeInInventory"), false));
    if (!inventoryEffects.isEmpty()) {
      inventoryActive = true;
    }
    return new UpgradeBehaviorSpec(List.copyOf(secondaryAbilities), List.copyOf(secondaryDescriptions),
        List.copyOf(particlePresets),
        List.copyOf(statusEffects), List.copyOf(inventoryEffects), inventoryActive, List.copyOf(onDamagedEffects));
  }

  private List<UpgradeOnDamagedSpec> parseOnDamagedEffects(Object raw, String path, List<String> errors) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<UpgradeOnDamagedSpec> out = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      String entryPath = path + "[" + i + "]";
      Map<String, Object> map = castMap(list.get(i), entryPath, errors);
      if (map == null) {
        continue;
      }
      UpgradeStatusEffectSpec effect = parseStatusEffectEntry(map, entryPath, errors);
      if (effect == null) {
        continue;
      }
      long cooldown = 0L;
      if (map.containsKey("cooldownTicks")) {
        cooldown = YamlValues.longValue(map.get("cooldownTicks"), 0L);
      } else if (map.containsKey("cooldownSeconds")) {
        cooldown = Math.round(YamlValues.doubleValue(map.get("cooldownSeconds"), 0.0) * 20.0);
      } else if (map.containsKey("cooldown")) {
        cooldown = Math.round(YamlValues.doubleValue(map.get("cooldown"), 0.0) * 20.0);
      }
      if (cooldown < 0L) {
        errors.add(entryPath + ".cooldown: must be >= 0");
        continue;
      }
      out.add(new UpgradeOnDamagedSpec(effect, cooldown));
    }
    return out;
  }

  private UpgradeLimitsSpec parseLimits(ConfigurationSection section, String base, List<String> errors) {
    if (section == null) {
      return UpgradeLimitsSpec.none();
    }
    String categoryRaw = YamlValues.string(section.get("category"), null);
    String category = null;
    if (categoryRaw != null && !categoryRaw.isBlank()) {
      try {
        category = Ids.normalize(categoryRaw);
      } catch (Exception ex) {
        errors.add(base + ".limits.category: invalid category " + categoryRaw);
      }
    }
    boolean exclusive = YamlValues.bool(section.get("exclusive"), false);
    int tier = YamlValues.intValue(section.get("tier"), 1);
    int maxTier = YamlValues.intValue(section.get("maxTier"), 0);
    int maxPerItem = YamlValues.intValue(section.get("maxPerItem"), 0);
    int maxCopies = YamlValues.intValue(section.get("maxCopies"), 0);
    double diminishingFactor = YamlValues.doubleValue(section.get("diminishingFactor"), 1.0);
    double diminishingFloor = YamlValues.doubleValue(section.get("diminishingFloor"), 0.0);
    if (tier < 1) {
      errors.add(base + ".limits.tier: must be >= 1");
      tier = 1;
    }
    if (maxTier < 0) {
      errors.add(base + ".limits.maxTier: must be >= 0");
      maxTier = 0;
    }
    if (maxPerItem < 0) {
      errors.add(base + ".limits.maxPerItem: must be >= 0");
      maxPerItem = 0;
    }
    if (maxCopies < 0) {
      errors.add(base + ".limits.maxCopies: must be >= 0");
      maxCopies = 0;
    }
    if (!Double.isFinite(diminishingFactor) || diminishingFactor <= 0.0) {
      errors.add(base + ".limits.diminishingFactor: must be > 0");
      diminishingFactor = 1.0;
    }
    if (!Double.isFinite(diminishingFloor) || diminishingFloor < 0.0) {
      errors.add(base + ".limits.diminishingFloor: must be >= 0");
      diminishingFloor = 0.0;
    }
    if (maxTier > 0 && tier > maxTier) {
      errors.add(base + ".limits.tier: cannot exceed maxTier (" + maxTier + ")");
      tier = maxTier;
    }
    return new UpgradeLimitsSpec(category, exclusive, tier, maxTier, maxPerItem, maxCopies, diminishingFactor,
        diminishingFloor);
  }

  private UpgradeProgressionSpec parseProgression(ConfigurationSection section, UpgradeLimitsSpec limits, String base,
      List<String> errors) {
    if (section == null) {
      return UpgradeProgressionSpec.none();
    }
    String category = YamlValues.string(section.get("category"), null);
    int tier = YamlValues.intValue(section.get("tier"), limits == null ? 1 : limits.tier());
    if (tier < 1) {
      errors.add(base + ".progression.tier: must be >= 1");
      tier = 1;
    }
    boolean requirePreviousTier = YamlValues.bool(section.get("requirePreviousTier"),
        YamlValues.bool(section.get("requirePrevious"), false));
    boolean consumePreviousTier = YamlValues.bool(section.get("consumePreviousTier"),
        YamlValues.bool(section.get("consumePrevious"), false));
    List<String> requires = normalizeIdList(readStringList(section.get("requires")), base + ".progression.requires", errors);
    List<String> consumes = normalizeIdList(readStringList(section.get("consumes")), base + ".progression.consumes", errors);
    if ((category == null || category.isBlank()) && limits != null && limits.category() != null) {
      category = limits.category();
    }
    return new UpgradeProgressionSpec(category, tier, requirePreviousTier, consumePreviousTier, requires, consumes);
  }

  private UpgradeFusionSpec parseFusion(ConfigurationSection section, String base, List<String> errors) {
    if (section == null) {
      return UpgradeFusionSpec.none();
    }
    UpgradePriceSpec price = parsePrice(section.getConfigurationSection("price"), base + ".fusion", errors);
    double successChance = YamlValues.doubleValue(section.get("successChance"),
        YamlValues.doubleValue(section.get("success"), 1.0));
    boolean consumeOnFail = YamlValues.bool(section.get("consumeOnFail"), true);
    boolean destroyTargetOnFail = YamlValues.bool(section.get("destroyTargetOnFail"), false);
    String downgradeTo = YamlValues.string(section.get("downgradeTo"), null);
    if (successChance < 0.0 || successChance > 1.0 || !Double.isFinite(successChance)) {
      errors.add(base + ".fusion.successChance: must be between 0 and 1");
      successChance = 1.0;
    }
    return new UpgradeFusionSpec(price, successChance, consumeOnFail, destroyTargetOnFail, downgradeTo);
  }

  private UpgradeSalvageSpec parseSalvage(ConfigurationSection section, String base, List<String> errors) {
    if (section == null) {
      return UpgradeSalvageSpec.none();
    }
    UpgradePriceSpec tokens = parsePrice(section.getConfigurationSection("tokens"), base + ".salvage", errors);
    List<UpgradeSalvageItemSpec> items = parseSalvageItems(section.get("items"), base + ".salvage.items", errors);
    return new UpgradeSalvageSpec(tokens, items);
  }

  private List<UpgradeSalvageItemSpec> parseSalvageItems(Object raw, String base, List<String> errors) {
    if (raw == null) {
      return List.of();
    }
    List<UpgradeSalvageItemSpec> out = new ArrayList<>();
    if (raw instanceof List<?> list) {
      int index = 0;
      for (Object entry : list) {
        String entryBase = base + "[" + index + "]";
        UpgradeSalvageItemSpec spec = parseSalvageItem(entry, entryBase, errors);
        if (spec != null) {
          out.add(spec);
        }
        index++;
      }
      return out;
    }
    UpgradeSalvageItemSpec spec = parseSalvageItem(raw, base, errors);
    return spec == null ? List.of() : List.of(spec);
  }

  private UpgradeSalvageItemSpec parseSalvageItem(Object raw, String base, List<String> errors) {
    if (raw instanceof ConfigurationSection section) {
      String itemRaw = YamlValues.string(section.get("item"), YamlValues.string(section.get("itemId"), null));
      int amount = YamlValues.intValue(section.get("amount"), 1);
      return buildSalvageItem(itemRaw, amount, base, errors);
    }
    if (raw instanceof Map<?, ?> map) {
      String itemRaw = YamlValues.string(map.get("item"), YamlValues.string(map.get("itemId"), null));
      int amount = YamlValues.intValue(map.get("amount"), 1);
      return buildSalvageItem(itemRaw, amount, base, errors);
    }
    if (raw instanceof String str) {
      return buildSalvageItem(str, 1, base, errors);
    }
    errors.add(base + ": expected item salvage entry");
    return null;
  }

  private UpgradeSalvageItemSpec buildSalvageItem(String itemRaw, int amount, String base, List<String> errors) {
    if (itemRaw == null || itemRaw.isBlank()) {
      errors.add(base + ".item: missing item id");
      return null;
    }
    if (amount <= 0) {
      errors.add(base + ".amount: must be > 0");
      return null;
    }
    try {
      return new UpgradeSalvageItemSpec(Ids.normalize(itemRaw), amount);
    } catch (Exception ex) {
      errors.add(base + ".item: invalid id (" + ex.getMessage() + ")");
      return null;
    }
  }

  private List<String> normalizeIdList(List<String> raw, String base, List<String> errors) {
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }
    List<String> out = new ArrayList<>(raw.size());
    for (String entry : raw) {
      if (entry == null || entry.isBlank()) {
        continue;
      }
      try {
        out.add(Ids.normalize(entry));
      } catch (Exception ex) {
        errors.add(base + ": invalid id " + entry);
      }
    }
    return out;
  }

  private List<UpgradeModifierSpec> parseModifiers(Object raw, String path, List<String> errors) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<UpgradeModifierSpec> out = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      String entryPath = path + "[" + i + "]";
      Map<String, Object> map = castMap(list.get(i), entryPath, errors);
      if (map == null) {
        continue;
      }
      String typeRaw = YamlValues.string(map.get("type"), YamlValues.string(map.get("id"), null));
      if (typeRaw == null || typeRaw.isBlank()) {
        errors.add(entryPath + ".type: missing modifier type");
        continue;
      }
      UpgradeModifierType type = UpgradeModifierType.parse(typeRaw);
      if (type == null) {
        errors.add(entryPath + ".type: unknown modifier type " + typeRaw);
        continue;
      }
      double value = YamlValues.doubleValue(map.get("value"), type.defaultValue());
      Double min = map.containsKey("min") ? YamlValues.doubleValue(map.get("min"), Double.NaN) : Double.NaN;
      Double max = map.containsKey("max") ? YamlValues.doubleValue(map.get("max"), Double.NaN) : Double.NaN;
      if (Double.isFinite(min) && Double.isFinite(max) && min > max) {
        errors.add(entryPath + ": min cannot be greater than max");
        continue;
      }
      if (Double.isFinite(min)) {
        value = Math.max(value, min);
      }
      if (Double.isFinite(max)) {
        value = Math.min(value, max);
      }
      out.add(new UpgradeModifierSpec(type, value));
    }
    return out;
  }

  private List<UpgradeAttributeSpec> parseAttributes(Object raw, String path, List<String> errors) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<UpgradeAttributeSpec> out = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      String entryPath = path + "[" + i + "]";
      Map<String, Object> map = castMap(list.get(i), entryPath, errors);
      if (map == null) {
        continue;
      }
      String attributeRaw = YamlValues.string(map.get("attribute"), null);
      if (attributeRaw == null || attributeRaw.isBlank()) {
        errors.add(entryPath + ".attribute: missing attribute");
        continue;
      }
      Attribute attribute = parseAttribute(attributeRaw, entryPath + ".attribute", errors);
      if (attribute == null) {
        continue;
      }
      double amount = YamlValues.doubleValue(map.get("amount"), 0.0);
      String operationRaw = YamlValues.string(map.get("operation"), "ADD_NUMBER");
      AttributeModifier.Operation operation = parseOperation(operationRaw, entryPath + ".operation", errors);
      if (operation == null) {
        continue;
      }
      String slotRaw = YamlValues.string(map.get("slot"), "ANY");
      EquipmentSlotGroup slot = parseSlot(slotRaw, entryPath + ".slot", errors);
      if (slot == null) {
        continue;
      }
      out.add(new UpgradeAttributeSpec(attribute, operation, amount, slot));
    }
    return out;
  }

  private static List<String> readStringList(Object raw) {
    if (raw == null) {
      return List.of();
    }
    if (raw instanceof String str) {
      return str.isBlank() ? List.of() : List.of(str);
    }
    if (raw instanceof List<?> list) {
      List<String> out = new ArrayList<>();
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
    return List.of();
  }

  private static Set<String> normalizeIds(List<String> values, String path, List<String> errors) {
    if (values.isEmpty()) {
      return Set.of();
    }
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String raw : values) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      try {
        out.add(Ids.normalize(raw));
      } catch (Exception ex) {
        errors.add(path + ": invalid id " + raw);
      }
    }
    return out;
  }

  private static Set<Material> parseMaterials(List<String> values, String path, List<String> errors) {
    if (values.isEmpty()) {
      return Set.of();
    }
    LinkedHashSet<Material> out = new LinkedHashSet<>();
    for (String raw : values) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      String value = raw.trim();
      Material mat = Material.matchMaterial(value, true);
      if (mat == null && value.contains(":")) {
        NamespacedKey key = NamespacedKey.fromString(value.toLowerCase(Locale.ROOT));
        if (key != null) {
          mat = Material.matchMaterial(key.getKey(), true);
        }
      }
      if (mat == null) {
        String normalized = value.toUpperCase(Locale.ROOT);
        if (normalized.startsWith("MINECRAFT:")) {
          normalized = normalized.substring("MINECRAFT:".length());
        }
        try {
          mat = Material.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
          mat = null;
        }
      }
      if (mat == null) {
        errors.add(path + ": invalid material " + raw);
        continue;
      }
      out.add(mat);
    }
    return out;
  }

  private List<String> parseAbilityList(Object raw, String path, List<String> errors) {
    List<String> values = readStringList(raw);
    if (values.isEmpty()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String value : values) {
      if (value == null || value.isBlank()) {
        continue;
      }
      String normalized;
      try {
        normalized = Ids.normalize(value);
      } catch (Exception ex) {
        errors.add(path + ": invalid ability id " + value);
        continue;
      }
      if (!engine.hasAbility(normalized)) {
        errors.add(path + ": ability not registered: " + normalized);
        continue;
      }
      out.add(normalized);
    }
    return out;
  }

  private List<UpgradeStatusEffectSpec> parseStatusEffects(Object raw, String path, List<String> errors) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<UpgradeStatusEffectSpec> out = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      String entryPath = path + "[" + i + "]";
      Map<String, Object> map = castMap(list.get(i), entryPath, errors);
      if (map == null) {
        continue;
      }
      UpgradeStatusEffectSpec spec = parseStatusEffectEntry(map, entryPath, errors);
      if (spec != null) {
        out.add(spec);
      }
    }
    return out;
  }

  private UpgradeStatusEffectSpec parseStatusEffectEntry(Map<String, Object> map, String entryPath, List<String> errors) {
    String typeRaw = YamlValues.string(map.get("type"), null);
    if (typeRaw == null || typeRaw.isBlank()) {
      errors.add(entryPath + ".type: missing effect type");
      return null;
    }
    PotionEffectType type = parsePotionEffect(typeRaw, entryPath + ".type", errors);
    if (type == null) {
      return null;
    }
    int duration = YamlValues.intValue(map.get("duration"), YamlValues.intValue(map.get("durationTicks"), 40));
    if (map.containsKey("durationSeconds")) {
      duration = (int) Math.round(YamlValues.doubleValue(map.get("durationSeconds"), 0.0) * 20.0);
    }
    if (duration <= 0) {
      errors.add(entryPath + ".duration: must be > 0");
      return null;
    }
    int amplifier = YamlValues.intValue(map.get("amplifier"), 0);
    double chance = YamlValues.doubleValue(map.get("chance"), 1.0);
    boolean ambient = YamlValues.bool(map.get("ambient"), false);
    boolean particles = YamlValues.bool(map.get("particles"), true);
    boolean icon = YamlValues.bool(map.get("icon"), true);
    return new UpgradeStatusEffectSpec(type, duration, amplifier, chance, ambient, particles, icon);
  }

  private List<UpgradeEnchantSpec> parseEnchants(Object raw, String path, List<String> errors) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<UpgradeEnchantSpec> out = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      String entryPath = path + "[" + i + "]";
      Map<String, Object> map = castMap(list.get(i), entryPath, errors);
      if (map == null) {
        continue;
      }
      String enchantRaw = YamlValues.string(map.get("enchant"), YamlValues.string(map.get("id"), null));
      if (enchantRaw == null || enchantRaw.isBlank()) {
        errors.add(entryPath + ".enchant: missing enchant key");
        continue;
      }
      Enchantment enchant = parseEnchantment(enchantRaw, entryPath + ".enchant", errors);
      if (enchant == null) {
        continue;
      }
      int level = YamlValues.intValue(map.get("level"), 1);
      out.add(new UpgradeEnchantSpec(enchant, level));
    }
    return out;
  }

  private Attribute parseAttribute(String raw, String path, List<String> errors) {
    String normalized = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    List<String> candidates = new ArrayList<>();
    if (normalized.contains(":")) {
      candidates.add(normalized);
      String[] parts = normalized.split(":", 2);
      if (parts.length == 2) {
        candidates.add(parts[0] + ":" + parts[1].replace('_', '.'));
      }
    } else {
      candidates.add("minecraft:" + normalized);
      if (normalized.contains("_")) {
        candidates.add("minecraft:" + normalized.replace('_', '.'));
      }
      if (normalized.contains(".")) {
        candidates.add("minecraft:" + normalized.replace('.', '_'));
      }
    }
    var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE);
    for (String candidate : candidates) {
      NamespacedKey key = NamespacedKey.fromString(candidate);
      if (key == null) {
        continue;
      }
      Attribute attr = registry.get(key);
      if (attr != null) {
        return attr;
      }
    }
    String needle = normalized;
    if (needle.contains(":")) {
      String[] parts = needle.split(":", 2);
      needle = parts.length == 2 ? parts[1] : needle;
    }
    String needleUnderscore = needle.replace('.', '_');
    String needleDot = needle.replace('_', '.');
    String needleNoGeneric = needle;
    if (needleNoGeneric.startsWith("generic.")) {
      needleNoGeneric = needleNoGeneric.substring("generic.".length());
    } else if (needleNoGeneric.startsWith("generic_")) {
      needleNoGeneric = needleNoGeneric.substring("generic_".length());
    }
    if (registry != null) {
      for (Attribute attr : registry) {
        NamespacedKey key = attr.getKey();
        if (key == null) {
          continue;
        }
        String keyPath = key.getKey().toLowerCase(Locale.ROOT);
        String keyUnderscore = keyPath.replace('.', '_');
        String keyDot = keyPath.replace('_', '.');
        String keyNoGeneric = keyPath;
        if (keyNoGeneric.startsWith("generic.")) {
          keyNoGeneric = keyNoGeneric.substring("generic.".length());
        } else if (keyNoGeneric.startsWith("generic_")) {
          keyNoGeneric = keyNoGeneric.substring("generic_".length());
        }
        if (keyPath.equals(needle) || keyPath.equals(needleUnderscore) || keyPath.equals(needleDot)
            || keyUnderscore.equals(needle) || keyUnderscore.equals(needleUnderscore) || keyUnderscore.equals(needleDot)
            || keyDot.equals(needle) || keyDot.equals(needleUnderscore) || keyDot.equals(needleDot)
            || keyNoGeneric.equals(needleNoGeneric) || keyNoGeneric.equals(needleUnderscore)
            || keyNoGeneric.equals(needleDot)) {
          return attr;
        }
      }
    }
    errors.add(path + ": unknown attribute " + raw);
    return null;
  }

  private AttributeModifier.Operation parseOperation(String raw, String path, List<String> errors) {
    String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    return switch (normalized) {
      case "ADD_NUMBER", "ADD" -> AttributeModifier.Operation.ADD_NUMBER;
      case "ADD_SCALAR", "ADD_MULTIPLIER", "ADD_MULT" -> AttributeModifier.Operation.ADD_SCALAR;
      case "MULTIPLY_SCALAR_1", "MULTIPLY" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
      default -> {
        errors.add(path + ": invalid operation=" + raw);
        yield null;
      }
    };
  }

  private EquipmentSlotGroup parseSlot(String raw, String path, List<String> errors) {
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    normalized = normalized.replace(" ", "").replace("_", "").replace("-", "");
    EquipmentSlotGroup slot = EquipmentSlotGroup.getByName(normalized);
    if (slot == null) {
      errors.add(path + ": invalid slot=" + raw);
      return null;
    }
    return slot;
  }

  private Enchantment parseEnchantment(String raw, String path, List<String> errors) {
    String normalized = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    List<String> candidates = new ArrayList<>();
    if (normalized.contains(":")) {
      candidates.add(normalized);
    } else {
      candidates.add("minecraft:" + normalized);
    }
    var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
    for (String candidate : candidates) {
      NamespacedKey key = NamespacedKey.fromString(candidate);
      Enchantment enchant = key == null ? null : registry.get(key);
      if (enchant != null) {
        return enchant;
      }
    }
    String needle = normalized;
    if (needle.contains(":")) {
      String[] parts = needle.split(":", 2);
      needle = parts.length == 2 ? parts[1] : needle;
    }
    String needleUnderscore = needle.replace('.', '_');
    String needleDot = needle.replace('_', '.');
    if (registry != null) {
      for (Enchantment enchant : registry) {
        NamespacedKey key = enchant.getKey();
        if (key == null) {
          continue;
        }
        String keyPath = key.getKey().toLowerCase(Locale.ROOT);
        String keyUnderscore = keyPath.replace('.', '_');
        String keyDot = keyPath.replace('_', '.');
        if (keyPath.equals(needle) || keyPath.equals(needleUnderscore) || keyPath.equals(needleDot)
            || keyUnderscore.equals(needle) || keyUnderscore.equals(needleUnderscore) || keyUnderscore.equals(needleDot)
            || keyDot.equals(needle) || keyDot.equals(needleUnderscore) || keyDot.equals(needleDot)) {
          return enchant;
        }
      }
    }
    errors.add(path + ": unknown enchant=" + raw);
    return null;
  }

  private PotionEffectType parsePotionEffect(String raw, String path, List<String> errors) {
    String normalized = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    List<String> candidates = new ArrayList<>();
    if (normalized.contains(":")) {
      candidates.add(normalized);
    } else {
      candidates.add("minecraft:" + normalized);
    }
    var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT);
    for (String candidate : candidates) {
      NamespacedKey key = NamespacedKey.fromString(candidate);
      PotionEffectType type = key == null ? null : registry.get(key);
      if (type != null) {
        return type;
      }
    }
    String needle = normalized;
    if (needle.contains(":")) {
      String[] parts = needle.split(":", 2);
      needle = parts.length == 2 ? parts[1] : needle;
    }
    String needleUnderscore = needle.replace('.', '_');
    String needleDot = needle.replace('_', '.');
    if (registry != null) {
      for (PotionEffectType type : registry) {
        NamespacedKey key = type.getKey();
        if (key == null) {
          continue;
        }
        String keyPath = key.getKey().toLowerCase(Locale.ROOT);
        String keyUnderscore = keyPath.replace('.', '_');
        String keyDot = keyPath.replace('_', '.');
        if (keyPath.equals(needle) || keyPath.equals(needleUnderscore) || keyPath.equals(needleDot)
            || keyUnderscore.equals(needle) || keyUnderscore.equals(needleUnderscore) || keyUnderscore.equals(needleDot)
            || keyDot.equals(needle) || keyDot.equals(needleUnderscore) || keyDot.equals(needleDot)) {
          return type;
        }
      }
    }
    errors.add(path + ": unknown effect " + raw);
    return null;
  }

  private static Map<String, Object> castMap(Object raw, String path, List<String> errors) {
    if (raw instanceof ConfigurationSection section) {
      return section.getValues(false);
    }
    if (raw instanceof Map<?, ?> map) {
      Map<String, Object> out = new LinkedHashMap<>();
      for (var e : map.entrySet()) {
        if (e.getKey() != null) {
          out.put(String.valueOf(e.getKey()), e.getValue());
        }
      }
      return out;
    }
    errors.add(path + ": expected object");
    return null;
  }

  private static String normalizeId(String raw, String path) {
    try {
      return Ids.normalize(raw);
    } catch (Exception ex) {
      throw new IllegalArgumentException(path + ": invalid id (" + ex.getMessage() + ")");
    }
  }

}
