package dev.patric.dungeonsreborn.effects.items;

import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.util.YamlValues;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TropicalFish;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.AxolotlBucketMeta;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockDataMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.KnowledgeBookMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.meta.MusicInstrumentMeta;
import org.bukkit.inventory.meta.OminousBottleMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.ShieldMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.SpawnEggMeta;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.inventory.meta.TropicalFishBucketMeta;
import org.bukkit.inventory.meta.WritableBookMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.MusicInstrument;
import net.kyori.adventure.text.Component;
import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.potion.SuspiciousEffectEntry;
import org.bukkit.block.data.BlockData;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.inventory.meta.components.JukeboxPlayableComponent;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.bukkit.inventory.meta.components.UseCooldownComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;


public final class ItemTemplateCompiler {
  public record DurabilityRange(int minDamage, int maxDamage) {
    public int roll(Random random) {
      if (minDamage >= maxDamage) {
        return minDamage;
      }
      return minDamage + random.nextInt(maxDamage - minDamage + 1);
    }
  }

  public record CompiledTemplate(ItemStack item, DurabilityRange durabilityRange) {
  }

  private ItemTemplateCompiler() {
  }

  private static volatile HeadRegistry headRegistry;

  public static void setHeadRegistry(HeadRegistry registry) {
    headRegistry = registry;
  }

  public static CompiledTemplate compile(ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return null;
    }
    validateSectionKeys(section, ITEM_KEYS, path, errors);
    String materialKey = YamlValues.string(section, "material", null);
    if (materialKey == null) {
      String typeRaw = YamlValues.string(section, "type", null);
      if (typeRaw != null && !typeRaw.equalsIgnoreCase("material")) {
        materialKey = typeRaw;
      }
    }
    if (materialKey == null || materialKey.isBlank()) {
      errors.add(path + ".material: missing material");
      return null;
    }
    Material material = Material.matchMaterial(materialKey);
    if (material == null) {
      errors.add(path + ".material: unknown material=" + materialKey);
      return null;
    }
    int amount = Math.max(1, section.getInt("amount", 1));
    ItemStack item = new ItemStack(material, amount);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return new CompiledTemplate(item, null);
    }

    ConfigurationSection display = section.getConfigurationSection("display");
    if (display != null) {
      validateSectionKeys(display, DISPLAY_KEYS, path + ".display", errors);
    }
    applyDisplay(meta, display, section, material, amount);

    ConfigurationSection metaSection = section.getConfigurationSection("meta");
    if (metaSection != null) {
      validateSectionKeys(metaSection, META_KEYS, path + ".meta", errors);
    }
    applyMeta(meta, metaSection, path, errors, material, amount);

    if (display != null) {
      Integer cmd = intFrom(display, "custom_model_data", "customModelData");
      if (cmd != null) {
        setCustomModelData(meta, cmd);
      }
    } else if (metaSection != null) {
      Integer cmd = intFrom(metaSection, "custom_model_data", "customModelData");
      if (cmd != null) {
        setCustomModelData(meta, cmd);
      }
    }

    item.setItemMeta(meta);
    DurabilityRange range = parseDurabilityRange(metaSection, path, errors, material);
    return new CompiledTemplate(item, range);
  }

  public static void applyDurabilityRange(ItemStack item, DurabilityRange range, Random random) {
    Objects.requireNonNull(item, "item");
    if (range == null) {
      return;
    }
    ItemMeta meta = item.getItemMeta();
    if (!(meta instanceof Damageable damageable)) {
      return;
    }
    int rolled = range.roll(random);
    damageable.setDamage(Math.max(0, rolled));
    item.setItemMeta(meta);
  }

  private static void applyDisplay(ItemMeta meta, ConfigurationSection display, ConfigurationSection root,
      Material material, int amount) {
    Map<String, String> placeholders = buildPlaceholders(display, root, material, amount);
    String name = localizedString(display, root, placeholders, "name", "nameKey", "name_key");
    if (name != null && !name.isBlank()) {
      meta.displayName(GuiMini.mm(name));
    }

    List<String> lore = localizedList(display, root, placeholders, "lore", "loreKeys", "lore_keys", "loreKey");
    String subtitle = localizedString(display, root, placeholders, "subtitle", "subtitleKey", "subtitle_key");
    String description = localizedString(display, root, placeholders, "description", "descriptionKey", "description_key");
    String rarityLine = localizedString(display, root, placeholders, "rarityLine", "rarity_line",
        "rarityLineKey", "rarity_line_key");
    String flavor = localizedString(display, root, placeholders, "flavor", "flavorKey", "flavor_key");

    List<String> out = new ArrayList<>();
    if (subtitle != null && !subtitle.isBlank()) {
      out.add(subtitle);
    }
    if (description != null && !description.isBlank()) {
      out.add(description);
    }
    out.addAll(lore);
    if (rarityLine != null && !rarityLine.isBlank()) {
      out.add(rarityLine);
    }
    if (flavor != null && !flavor.isBlank()) {
      out.add(flavor);
    }
    if (!out.isEmpty()) {
      meta.lore(GuiMini.loreMm(out));
    }
  }

  private static void applyMeta(ItemMeta meta, ConfigurationSection metaSection, String path, List<String> errors,
      Material material, int amount) {
    if (metaSection == null) {
      return;
    }
    Map<String, String> placeholders = buildPlaceholders(metaSection, metaSection, material, amount);
    String displayName = localizedString(metaSection, metaSection, placeholders, "display-name", "displayName", "name",
        "displayNameKey", "display_name_key", "display-name-key", "nameKey", "name_key");
    if (displayName != null && !displayName.isBlank()) {
      meta.displayName(GuiMini.mm(displayName));
    }
    List<String> loreLines = localizedList(metaSection, metaSection, placeholders, "lore", "loreKeys", "lore_keys", "loreKey");
    if (!loreLines.isEmpty()) {
      meta.lore(GuiMini.loreMm(loreLines));
    }

    if (metaSection.contains("unbreakable")) {
      meta.setUnbreakable(metaSection.getBoolean("unbreakable"));
    }

    if (metaSection.contains("damage") && meta instanceof Damageable damageable) {
      damageable.setDamage(Math.max(0, metaSection.getInt("damage")));
    }
    if (metaSection.contains("max_damage") || metaSection.contains("maxDamage")) {
      if (meta instanceof Damageable damageable) {
        Integer maxDamage = intFrom(metaSection, "max_damage", "maxDamage");
        damageable.setMaxDamage(maxDamage);
      }
    }
    if (metaSection.contains("repair_cost") || metaSection.contains("repairCost")) {
      if (meta instanceof Repairable repairable) {
        Integer cost = intFrom(metaSection, "repair_cost", "repairCost");
        if (cost != null) {
          repairable.setRepairCost(Math.max(0, cost));
        }
      }
    }

    ConfigurationSection enchants = metaSection.getConfigurationSection("enchants");
    if (enchants != null) {
      for (String key : enchants.getKeys(false)) {
        int level = enchants.getInt(key, 1);
        Enchantment enchant = parseEnchantment(key);
        if (enchant == null) {
          errors.add(path + ".meta.enchants: unknown enchantment=" + key);
          continue;
        }
        meta.addEnchant(enchant, Math.max(1, level), true);
      }
    }

    List<Map<?, ?>> enchList = metaSection.getMapList("enchantments");
    if (!enchList.isEmpty()) {
      for (Map<?, ?> entry : enchList) {
        Object idRaw = entry.get("id");
        if (idRaw == null) {
          continue;
        }
        String id = String.valueOf(idRaw);
        int level = entry.containsKey("level") ? Integer.parseInt(String.valueOf(entry.get("level"))) : 1;
        Enchantment enchant = parseEnchantment(id);
        if (enchant == null) {
          errors.add(path + ".meta.enchantments: unknown enchantment=" + id);
          continue;
        }
        meta.addEnchant(enchant, Math.max(1, level), true);
      }
    }

    List<String> flags = metaSection.getStringList("flags");
    for (String raw : flags) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      try {
        meta.addItemFlags(ItemFlag.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException ex) {
        errors.add(path + ".meta.flags: unknown flag=" + raw);
      }
    }

    ConfigurationSection pdc = metaSection.getConfigurationSection("pdc");
    if (pdc != null) {
      applyPdc(meta, pdc, path + ".meta.pdc", errors);
    }
    ConfigurationSection tags = metaSection.getConfigurationSection("tags");
    if (tags != null) {
      applyPdc(meta, tags, path + ".meta.tags", errors);
    }
    ConfigurationSection customTags = metaSection.getConfigurationSection("custom_tags");
    if (customTags != null) {
      applyCustomTags(meta, customTags, path + ".meta.custom_tags", errors);
    }

    List<Map<?, ?>> attrs = metaSection.getMapList("attributes");
    if (!attrs.isEmpty()) {
      applyAttributes(meta, attrs, path + ".meta.attributes", errors);
    }

    applyBookMeta(meta, metaSection.getConfigurationSection("book"), path + ".meta.book", errors);
    applyStoredEnchants(meta, metaSection.getConfigurationSection("stored_enchants"),
        path + ".meta.stored_enchants", errors);
    applyPotionMeta(meta, metaSection.getConfigurationSection("potion"), path + ".meta.potion", errors);
    applySuspiciousStewMeta(meta, metaSection.getConfigurationSection("suspicious_stew"),
        path + ".meta.suspicious_stew", errors);
    applyLeatherArmorMeta(meta, metaSection.getConfigurationSection("leather_armor"),
        path + ".meta.leather_armor", errors);
    applyBannerMeta(meta, metaSection.getConfigurationSection("banner"), path + ".meta.banner", errors);
    applyShieldMeta(meta, metaSection.getConfigurationSection("shield"), path + ".meta.shield", errors);
    applyFireworkMeta(meta, metaSection.getConfigurationSection("firework"), path + ".meta.firework", errors);
    applyFireworkChargeMeta(meta, metaSection.getConfigurationSection("firework_charge"),
        path + ".meta.firework_charge", errors);
    applyMapMeta(meta, metaSection.getConfigurationSection("map"), path + ".meta.map", errors);
    applyCompassMeta(meta, metaSection.getConfigurationSection("compass"), path + ".meta.compass", errors);
    applySkullMeta(meta, metaSection.getConfigurationSection("skull"), path + ".meta.skull", errors);
    applyCrossbowMeta(meta, metaSection.getConfigurationSection("crossbow"), path + ".meta.crossbow", errors);
    applyBundleMeta(meta, metaSection.getConfigurationSection("bundle"), path + ".meta.bundle", errors);
    applySpawnEggMeta(meta, metaSection.getConfigurationSection("spawn_egg"), path + ".meta.spawn_egg", errors);
    applyAxolotlBucketMeta(meta, metaSection.getConfigurationSection("axolotl_bucket"),
        path + ".meta.axolotl_bucket", errors);
    applyTropicalFishBucketMeta(meta, metaSection.getConfigurationSection("tropical_fish_bucket"),
        path + ".meta.tropical_fish_bucket", errors);
    applyMusicInstrumentMeta(meta, metaSection.getConfigurationSection("music_instrument"),
        path + ".meta.music_instrument", errors);
    applyOminousBottleMeta(meta, metaSection.getConfigurationSection("ominous_bottle"),
        path + ".meta.ominous_bottle", errors);
    applyKnowledgeBookMeta(meta, metaSection.getConfigurationSection("knowledge_book"),
        path + ".meta.knowledge_book", errors);
    applyTrimMeta(meta, metaSection.getConfigurationSection("trim"), path + ".meta.trim", errors);
    applyBlockDataMeta(meta, metaSection.getConfigurationSection("block_data"), path + ".meta.block_data", errors);
    applyBlockStateMeta(meta, metaSection.getConfigurationSection("block_state"), path + ".meta.block_state", errors);

    ConfigurationSection components = metaSection.getConfigurationSection("components");
    if (components != null) {
      applyComponents(meta, components, path + ".meta.components", errors);
    }
  }

  private static DurabilityRange parseDurabilityRange(ConfigurationSection metaSection, String path, List<String> errors, Material material) {
    if (metaSection == null) {
      return null;
    }
    ConfigurationSection durability = metaSection.getConfigurationSection("durability");
    if (durability == null && !metaSection.contains("damageMin") && !metaSection.contains("damageMax")) {
      return null;
    }
    int maxAllowed = Math.max(0, material.getMaxDurability());
    int min = 0;
    int max = 0;
    if (durability != null) {
      min = intFrom(durability, "min", "minDamage");
      max = intFrom(durability, "max", "maxDamage");
    } else {
      min = intFrom(metaSection, "damageMin", "minDamage");
      max = intFrom(metaSection, "damageMax", "maxDamage");
    }
    if (min < 0 || max < 0) {
      errors.add(path + ".meta.durability: min/max must be >= 0");
      return null;
    }
    if (maxAllowed > 0) {
      min = Math.min(min, maxAllowed);
      max = Math.min(max, maxAllowed);
    }
    if (min > max) {
      int swap = min;
      min = max;
      max = swap;
    }
    return new DurabilityRange(min, max);
  }

  private static Integer intFrom(ConfigurationSection section, String... keys) {
    if (section == null) {
      return null;
    }
    for (String key : keys) {
      if (section.contains(key)) {
        return section.getInt(key);
      }
    }
    return null;
  }

  private static void setCustomModelData(ItemMeta meta, int value) {
    CustomModelDataComponent component = meta.getCustomModelDataComponent();
    component.setFloats(List.of((float) value));
    meta.setCustomModelDataComponent(component);
  }

  private static void applyBookMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof BookMeta book)) {
      errors.add(path + ": meta is not BookMeta");
      return;
    }
    String title = section.getString("title");
    if (title != null) {
      book.setTitle(title);
    }
    String author = section.getString("author");
    if (author != null) {
      book.setAuthor(author);
    }
    List<String> pages = section.getStringList("pages");
    if (!pages.isEmpty()) {
      List<Component> components = new ArrayList<>(pages.size());
      for (String page : pages) {
        components.add(GuiMini.mm(page));
      }
      book.pages(components);
    }
    String generationRaw = section.getString("generation");
    if (generationRaw != null) {
      try {
        book.setGeneration(BookMeta.Generation.valueOf(generationRaw.trim().toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException ex) {
        errors.add(path + ".generation: invalid generation=" + generationRaw);
      }
    }
    if (section.contains("signed") && meta instanceof WritableBookMeta) {
      boolean signed = section.getBoolean("signed", false);
      if (signed) {
        errors.add(path + ".signed: writable book cannot be signed");
      }
    }
  }

  private static void applyStoredEnchants(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof EnchantmentStorageMeta stored)) {
      errors.add(path + ": meta is not EnchantmentStorageMeta");
      return;
    }
    List<Map<?, ?>> list = section.getMapList("stored_enchantments");
    if (list.isEmpty()) {
      list = section.getMapList("enchants");
    }
    for (Map<?, ?> entry : list) {
      Object idRaw = entry.get("id");
      if (idRaw == null) {
        continue;
      }
      Enchantment enchant = parseEnchantment(String.valueOf(idRaw));
      if (enchant == null) {
        errors.add(path + ": unknown enchantment=" + idRaw);
        continue;
      }
      int level = entry.containsKey("level") ? Integer.parseInt(String.valueOf(entry.get("level"))) : 1;
      stored.addStoredEnchant(enchant, Math.max(1, level), true);
    }
  }

  private static void applyPotionMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof PotionMeta potion)) {
      errors.add(path + ": meta is not PotionMeta");
      return;
    }
    String base = section.getString("base");
    if (base != null) {
      PotionType type = parsePotionType(base);
      if (type == null) {
        errors.add(path + ".base: invalid potion type=" + base);
      } else {
        potion.setBasePotionType(type);
      }
    }
    Color color = parseColorSection(section.getConfigurationSection("color"), path + ".color", errors);
    if (color != null) {
      potion.setColor(color);
    }
    List<Map<?, ?>> effects = section.getMapList("effects");
    for (Map<?, ?> entry : effects) {
      String typeRaw = entry.get("type") == null ? null : String.valueOf(entry.get("type"));
      PotionEffectType type = parsePotionEffectType(typeRaw);
      if (type == null) {
        errors.add(path + ".effects: invalid type=" + typeRaw);
        continue;
      }
      int duration = entry.containsKey("duration") ? Integer.parseInt(String.valueOf(entry.get("duration"))) : 200;
      int amplifier = entry.containsKey("amplifier") ? Integer.parseInt(String.valueOf(entry.get("amplifier"))) : 0;
      boolean ambient = entry.containsKey("ambient") && Boolean.parseBoolean(String.valueOf(entry.get("ambient")));
      boolean particles = !entry.containsKey("particles") || Boolean.parseBoolean(String.valueOf(entry.get("particles")));
      boolean icon = !entry.containsKey("icon") || Boolean.parseBoolean(String.valueOf(entry.get("icon")));
      potion.addCustomEffect(new PotionEffect(type, duration, amplifier, ambient, particles, icon), true);
    }
  }

  private static void applySuspiciousStewMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof SuspiciousStewMeta stew)) {
      errors.add(path + ": meta is not SuspiciousStewMeta");
      return;
    }
    List<Map<?, ?>> effects = section.getMapList("effects");
    for (Map<?, ?> entry : effects) {
      String typeRaw = entry.get("type") == null ? null : String.valueOf(entry.get("type"));
      PotionEffectType type = parsePotionEffectType(typeRaw);
      if (type == null) {
        errors.add(path + ".effects: invalid type=" + typeRaw);
        continue;
      }
      int duration = entry.containsKey("duration") ? Integer.parseInt(String.valueOf(entry.get("duration"))) : 200;
      stew.addCustomEffect(SuspiciousEffectEntry.create(type, duration), true);
    }
  }

  private static void applyLeatherArmorMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof LeatherArmorMeta leather)) {
      errors.add(path + ": meta is not LeatherArmorMeta");
      return;
    }
    Color color = parseColorSection(section.getConfigurationSection("color"), path + ".color", errors);
    if (color != null) {
      leather.setColor(color);
    }
  }

  private static void applyBannerMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof BannerMeta banner)) {
      errors.add(path + ": meta is not BannerMeta");
      return;
    }
    List<Map<?, ?>> patterns = section.getMapList("patterns");
    List<Pattern> out = new ArrayList<>();
    for (Map<?, ?> entry : patterns) {
      String patternRaw = entry.get("pattern") == null ? null : String.valueOf(entry.get("pattern"));
      String colorRaw = entry.get("color") == null ? null : String.valueOf(entry.get("color"));
      PatternType type = parsePatternType(patternRaw);
      DyeColor color = parseDyeColor(colorRaw, path + ".patterns.color", errors);
      if (type == null || color == null) {
        errors.add(path + ".patterns: invalid pattern or color");
        continue;
      }
      out.add(new Pattern(color, type));
    }
    if (!out.isEmpty()) {
      banner.setPatterns(out);
    }
  }

  private static void applyShieldMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof ShieldMeta shield)) {
      errors.add(path + ": meta is not ShieldMeta");
      return;
    }
    DyeColor base = parseDyeColor(section.getString("base_color"), path + ".base_color", errors);
    if (base != null) {
      shield.setBaseColor(base);
    }
    List<Map<?, ?>> patterns = section.getMapList("patterns");
    List<Pattern> out = new ArrayList<>();
    for (Map<?, ?> entry : patterns) {
      String patternRaw = entry.get("pattern") == null ? null : String.valueOf(entry.get("pattern"));
      String colorRaw = entry.get("color") == null ? null : String.valueOf(entry.get("color"));
      PatternType type = parsePatternType(patternRaw);
      DyeColor color = parseDyeColor(colorRaw, path + ".patterns.color", errors);
      if (type == null || color == null) {
        errors.add(path + ".patterns: invalid pattern or color");
        continue;
      }
      out.add(new Pattern(color, type));
    }
    if (!out.isEmpty()) {
      shield.setPatterns(out);
    }
  }

  private static void applyFireworkMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof FireworkMeta firework)) {
      errors.add(path + ": meta is not FireworkMeta");
      return;
    }
    if (section.contains("power")) {
      firework.setPower(section.getInt("power"));
    }
    List<Map<?, ?>> effects = section.getMapList("effects");
    for (Map<?, ?> entry : effects) {
      FireworkEffect effect = parseFireworkEffect(entry, path + ".effects", errors);
      if (effect != null) {
        firework.addEffect(effect);
      }
    }
  }

  private static void applyFireworkChargeMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof FireworkEffectMeta charge)) {
      errors.add(path + ": meta is not FireworkEffectMeta");
      return;
    }
    ConfigurationSection effectSection = section.getConfigurationSection("effect");
    if (effectSection == null) {
      return;
    }
    FireworkEffect effect = parseFireworkEffect(effectSection.getValues(false), path + ".effect", errors);
    if (effect != null) {
      charge.setEffect(effect);
    }
  }

  private static void applyMapMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof MapMeta map)) {
      errors.add(path + ": meta is not MapMeta");
      return;
    }
    Color color = parseColorSection(section.getConfigurationSection("color"), path + ".color", errors);
    if (color != null) {
      map.setColor(color);
    }
    if (section.contains("scale")) {
      map.setScaling(section.getBoolean("scale"));
    }
    String locationName = section.getString("location_name");
    if (locationName != null) {
      map.displayName(GuiMini.mm(locationName));
    }
    if (section.contains("locked")) {
      errors.add(path + ".locked: unsupported in this Paper version");
    }
    if (section.contains("tracking")) {
      errors.add(path + ".tracking: unsupported in this Paper version");
    }
  }

  private static void applyCompassMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof CompassMeta compass)) {
      errors.add(path + ": meta is not CompassMeta");
      return;
    }
    ConfigurationSection lodestone = section.getConfigurationSection("lodestone");
    if (lodestone != null) {
      String worldName = lodestone.getString("world");
      if (worldName != null) {
        var world = Bukkit.getWorld(worldName);
        if (world == null) {
          errors.add(path + ".lodestone.world: unknown world=" + worldName);
        } else {
          double x = lodestone.getDouble("x");
          double y = lodestone.getDouble("y");
          double z = lodestone.getDouble("z");
          compass.setLodestone(new org.bukkit.Location(world, x, y, z));
        }
      }
    }
    if (section.contains("lodestone_tracked")) {
      compass.setLodestoneTracked(section.getBoolean("lodestone_tracked"));
    }
  }

  private static void applySkullMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof SkullMeta skull)) {
      errors.add(path + ": meta is not SkullMeta");
      return;
    }
    String headId = section.getString("head");
    if (headId != null && !headId.isBlank()) {
      HeadRegistry registry = headRegistry;
      HeadRegistry.HeadSpec spec = registry == null ? null : registry.head(headId);
      if (spec == null) {
        errors.add(path + ".head: unknown head id=" + headId);
      } else {
        HeadRegistry.applyTo(skull, spec, errors);
      }
    }
    String texture = section.getString("texture");
    if (texture != null && !texture.isBlank()) {
      if (!HeadRegistry.applyTexture(skull, null, null, texture, errors)) {
        errors.add(path + ".texture: failed to set texture");
      }
    }
    String owner = section.getString("owner");
    if (owner != null && !owner.isBlank()) {
      skull.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
    }
    ConfigurationSection profileSection = section.getConfigurationSection("profile");
    if (profileSection != null) {
      String name = profileSection.getString("name");
      String uuidRaw = profileSection.getString("uuid");
      UUID uuid = null;
      if (uuidRaw != null) {
        try {
          uuid = UUID.fromString(uuidRaw);
        } catch (IllegalArgumentException ex) {
          errors.add(path + ".profile.uuid: invalid uuid=" + uuidRaw);
        }
      }
      String profileTexture = profileSection.getString("texture");
      if (profileTexture != null && !profileTexture.isBlank()) {
        if (!HeadRegistry.applyTexture(skull, uuid, name, profileTexture, errors)) {
          errors.add(path + ".profile.texture: failed to set texture");
        }
      } else {
        PlayerProfile profile = uuid == null ? Bukkit.createProfile(name) : Bukkit.createProfile(uuid, name);
        skull.setPlayerProfile(profile);
      }
    }
  }

  private static void applyCrossbowMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof CrossbowMeta crossbow)) {
      errors.add(path + ": meta is not CrossbowMeta");
      return;
    }
    List<?> charged = section.getList("charged");
    if (charged == null || charged.isEmpty()) {
      return;
    }
    List<ItemStack> projectiles = new ArrayList<>();
    int index = 0;
    for (Object entry : charged) {
      String entryPath = path + ".charged[" + index + "]";
      ItemStack stack = parseItemStackEntry(entry, entryPath, errors);
      if (stack != null) {
        projectiles.add(stack);
      }
      index++;
    }
    if (!projectiles.isEmpty()) {
      crossbow.setChargedProjectiles(projectiles);
    }
  }

  private static void applyBundleMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof BundleMeta bundle)) {
      errors.add(path + ": meta is not BundleMeta");
      return;
    }
    List<?> contents = section.getList("contents");
    if (contents == null || contents.isEmpty()) {
      return;
    }
    List<ItemStack> items = new ArrayList<>();
    int index = 0;
    for (Object entry : contents) {
      String entryPath = path + ".contents[" + index + "]";
      ItemStack stack = parseItemStackEntry(entry, entryPath, errors);
      if (stack != null) {
        items.add(stack);
      }
      index++;
    }
    if (!items.isEmpty()) {
      bundle.setItems(items);
    }
  }

  private static void applySpawnEggMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof @SuppressWarnings("unused") SpawnEggMeta egg)) {
      errors.add(path + ": meta is not SpawnEggMeta");
      return;
    }
    String raw = section.getString("entity");
    if (raw == null) {
      return;
    }
    errors.add(path + ".entity: spawn egg type is derived from material (use " + raw.toUpperCase(Locale.ROOT)
        + "_SPAWN_EGG)");
  }

  private static void applyAxolotlBucketMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof AxolotlBucketMeta axolotl)) {
      errors.add(path + ": meta is not AxolotlBucketMeta");
      return;
    }
    String raw = section.getString("variant");
    if (raw == null) {
      return;
    }
    try {
      axolotl.setVariant(Axolotl.Variant.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException ex) {
      errors.add(path + ".variant: invalid variant=" + raw);
    }
  }

  private static void applyTropicalFishBucketMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof TropicalFishBucketMeta fish)) {
      errors.add(path + ": meta is not TropicalFishBucketMeta");
      return;
    }
    String patternRaw = section.getString("pattern");
    if (patternRaw != null) {
      try {
        fish.setPattern(TropicalFish.Pattern.valueOf(patternRaw.trim().toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException ex) {
        errors.add(path + ".pattern: invalid pattern=" + patternRaw);
      }
    }
    DyeColor body = parseDyeColor(section.getString("body_color"), path + ".body_color", errors);
    if (body != null) {
      fish.setBodyColor(body);
    }
    DyeColor pattern = parseDyeColor(section.getString("pattern_color"), path + ".pattern_color", errors);
    if (pattern != null) {
      fish.setPatternColor(pattern);
    }
  }

  private static void applyMusicInstrumentMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof MusicInstrumentMeta music)) {
      errors.add(path + ": meta is not MusicInstrumentMeta");
      return;
    }
    String raw = section.getString("instrument");
    if (raw == null) {
      return;
    }
    NamespacedKey key = NamespacedKey.fromString(raw.contains(":") ? raw : "minecraft:" + raw.toLowerCase(Locale.ROOT));
    if (key == null) {
      errors.add(path + ".instrument: invalid key=" + raw);
      return;
    }
    var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.INSTRUMENT);
    MusicInstrument instrument = registry == null ? null : registry.get(key);
    if (instrument == null) {
      errors.add(path + ".instrument: unknown instrument=" + raw);
      return;
    }
    music.setInstrument(instrument);
  }

  private static void applyOminousBottleMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof OminousBottleMeta bottle)) {
      errors.add(path + ": meta is not OminousBottleMeta");
      return;
    }
    if (section.contains("amplifier")) {
      bottle.setAmplifier(section.getInt("amplifier"));
    }
  }

  private static void applyKnowledgeBookMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof KnowledgeBookMeta knowledge)) {
      errors.add(path + ": meta is not KnowledgeBookMeta");
      return;
    }
    List<String> recipes = section.getStringList("recipes");
    if (recipes.isEmpty()) {
      return;
    }
    List<NamespacedKey> keys = new ArrayList<>();
    for (String raw : recipes) {
      NamespacedKey key = NamespacedKey.fromString(raw);
      if (key == null) {
        errors.add(path + ".recipes: invalid key=" + raw);
        continue;
      }
      keys.add(key);
    }
    if (!keys.isEmpty()) {
      knowledge.setRecipes(keys);
    }
  }

  private static void applyTrimMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof ArmorMeta armor)) {
      errors.add(path + ": meta is not ArmorMeta");
      return;
    }
    String materialRaw = section.getString("material");
    String patternRaw = section.getString("pattern");
    if (materialRaw == null || patternRaw == null) {
      return;
    }
    TrimMaterial material = null;
    TrimPattern pattern = null;
    var materialRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL);
    var patternRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN);
    NamespacedKey materialKey = NamespacedKey.fromString(materialRaw.contains(":") ? materialRaw : "minecraft:" + materialRaw);
    NamespacedKey patternKey = NamespacedKey.fromString(patternRaw.contains(":") ? patternRaw : "minecraft:" + patternRaw);
    if (materialKey != null && materialRegistry != null) {
      material = materialRegistry.get(materialKey);
    }
    if (patternKey != null && patternRegistry != null) {
      pattern = patternRegistry.get(patternKey);
    }
    if (material == null || pattern == null) {
      errors.add(path + ": invalid trim material or pattern");
      return;
    }
    armor.setTrim(new ArmorTrim(material, pattern));
  }

  private static void applyBlockDataMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof BlockDataMeta dataMeta)) {
      errors.add(path + ": meta is not BlockDataMeta");
      return;
    }
    String data = section.getString("block_data");
    if (data == null) {
      data = section.getString("data");
    }
    if (data == null) {
      return;
    }
    try {
      BlockData blockData = Bukkit.createBlockData(data);
      dataMeta.setBlockData(blockData);
    } catch (IllegalArgumentException ex) {
      errors.add(path + ".block_data: invalid block data=" + data);
    }
  }

  private static void applyBlockStateMeta(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return;
    }
    if (!(meta instanceof BlockStateMeta stateMeta)) {
      errors.add(path + ": meta is not BlockStateMeta");
      return;
    }
    String data = section.getString("block_data");
    if (data == null) {
      data = section.getString("data");
    }
    if (data == null) {
      return;
    }
    BlockState state = stateMeta.getBlockState();
    if (state == null) {
      errors.add(path + ": block state unavailable");
      return;
    }
    try {
      BlockData blockData = Bukkit.createBlockData(data);
      state.setBlockData(blockData);
      stateMeta.setBlockState(state);
    } catch (IllegalArgumentException ex) {
      errors.add(path + ".block_data: invalid block data=" + data);
    }
  }

  private static void applyAttributes(ItemMeta meta, List<Map<?, ?>> list, String path, List<String> errors) {
    int index = 0;
    for (Map<?, ?> entry : list) {
      String entryPath = path + "[" + index + "]";
      Object attrRaw = entry.get("attribute");
      if (attrRaw == null) {
        errors.add(entryPath + ": missing attribute");
        index++;
        continue;
      }
      Attribute attribute = parseAttribute(String.valueOf(attrRaw));
      if (attribute == null) {
        errors.add(entryPath + ": invalid attribute=" + attrRaw);
        index++;
        continue;
      }
      Object amountRaw = entry.get("amount");
      if (amountRaw == null) {
        errors.add(entryPath + ": missing amount");
        index++;
        continue;
      }
      double amount = Double.parseDouble(String.valueOf(amountRaw));
      Object operationRaw = entry.containsKey("operation") ? entry.get("operation") : "add_number";
      AttributeModifier.Operation operation = parseAttributeOperation(String.valueOf(operationRaw));
      if (operation == null) {
        errors.add(entryPath + ": invalid operation");
        index++;
        continue;
      }
      String keyRaw = entry.containsKey("key") ? String.valueOf(entry.get("key")) : null;
      String slotRaw = entry.containsKey("slot") ? String.valueOf(entry.get("slot")) : null;
      String groupRaw = entry.containsKey("slotGroup") ? String.valueOf(entry.get("slotGroup")) : null;
      AttributeModifier modifier;
      NamespacedKey key = keyRaw == null ? NamespacedKey.minecraft("attr_" + index) : NamespacedKey.fromString(keyRaw);
      if (key == null) {
        errors.add(entryPath + ": invalid key=" + keyRaw);
        index++;
        continue;
      }
      if (groupRaw != null && !groupRaw.isBlank()) {
        EquipmentSlotGroup group = EquipmentSlotGroup.getByName(groupRaw.toLowerCase(Locale.ROOT));
        if (group == null) {
          errors.add(entryPath + ": invalid slotGroup=" + groupRaw);
          index++;
          continue;
        }
        modifier = new AttributeModifier(key, amount, operation, group);
      } else if (slotRaw != null && !slotRaw.isBlank()) {
        EquipmentSlot slot;
        try {
          slot = EquipmentSlot.valueOf(slotRaw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
          errors.add(entryPath + ": invalid slot=" + slotRaw);
          index++;
          continue;
        }
        EquipmentSlotGroup group = slotToGroup(slot);
        modifier = new AttributeModifier(key, amount, operation, group);
      } else {
        modifier = new AttributeModifier(key, amount, operation);
      }
      meta.addAttributeModifier(attribute, modifier);
      index++;
    }
  }

  private static EquipmentSlotGroup slotToGroup(EquipmentSlot slot) {
    return switch (slot) {
      case HAND -> EquipmentSlotGroup.HAND;
      case OFF_HAND -> EquipmentSlotGroup.OFFHAND;
      case HEAD -> EquipmentSlotGroup.HEAD;
      case CHEST -> EquipmentSlotGroup.CHEST;
      case LEGS -> EquipmentSlotGroup.LEGS;
      case FEET -> EquipmentSlotGroup.FEET;
      default -> EquipmentSlotGroup.ANY;
    };
  }

  private static Attribute parseAttribute(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    List<String> candidates = new ArrayList<>();
    if (normalized.contains(":")) {
      candidates.add(normalized);
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
    if (registry == null) {
      return null;
    }
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
    return null;
  }

  private static AttributeModifier.Operation parseAttributeOperation(String raw) {
    if (raw == null) {
      return AttributeModifier.Operation.ADD_NUMBER;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    return switch (normalized) {
      case "ADD", "ADD_NUMBER" -> AttributeModifier.Operation.ADD_NUMBER;
      case "ADD_SCALAR", "ADD_MULTIPLIER", "ADD_MULT" -> AttributeModifier.Operation.ADD_SCALAR;
      case "MULTIPLY_SCALAR_1", "MULTIPLY" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
      default -> null;
    };
  }

  private static void applyPdc(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    for (String key : section.getKeys(false)) {
      NamespacedKey namespacedKey = NamespacedKey.fromString(key);
      if (namespacedKey == null) {
        errors.add(path + ": invalid NamespacedKey=" + key);
        continue;
      }
      Object value = section.get(key);
      if (value instanceof Number number) {
        if (value instanceof Integer) {
          meta.getPersistentDataContainer().set(namespacedKey, PersistentDataType.INTEGER, number.intValue());
        } else if (value instanceof Long) {
          meta.getPersistentDataContainer().set(namespacedKey, PersistentDataType.LONG, number.longValue());
        } else {
          meta.getPersistentDataContainer().set(namespacedKey, PersistentDataType.DOUBLE, number.doubleValue());
        }
        continue;
      }
      if (value instanceof Boolean bool) {
        meta.getPersistentDataContainer().set(namespacedKey, PersistentDataType.BYTE, (byte) (bool ? 1 : 0));
        continue;
      }
      if (value instanceof List<?> list) {
        meta.getPersistentDataContainer().set(namespacedKey, PersistentDataType.STRING, list.toString());
        continue;
      }
      meta.getPersistentDataContainer().set(namespacedKey, PersistentDataType.STRING, String.valueOf(value));
    }
  }

  private static void applyCustomTags(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    PersistentDataContainer container = meta.getPersistentDataContainer();
    PersistentDataAdapterContext adapterContext = container.getAdapterContext();
    for (String key : section.getKeys(false)) {
      NamespacedKey namespacedKey = NamespacedKey.fromString(key);
      if (namespacedKey == null) {
        errors.add(path + ": invalid NamespacedKey=" + key);
        continue;
      }
      Object raw = section.get(key);
      ConfigurationSection nested = section.getConfigurationSection(key);
      if (nested != null) {
        raw = nested;
      }
      applyCustomTag(container, adapterContext, namespacedKey, raw, path + "." + key, errors);
    }
  }

  private static void applyCustomTag(PersistentDataContainer container, PersistentDataAdapterContext adapterContext,
      NamespacedKey key, Object raw, String path, List<String> errors) {
    String type = null;
    Object value = raw;
    if (raw instanceof ConfigurationSection section) {
      type = section.getString("type");
      if (section.contains("value")) {
        value = section.get("value");
      }
    } else if (raw instanceof Map<?, ?> map) {
      Object typeRaw = map.get("type");
      if (typeRaw != null) {
        type = String.valueOf(typeRaw);
      }
      if (map.containsKey("value")) {
        value = map.get("value");
      }
    }

    if (type != null) {
      applyTypedCustomTag(container, adapterContext, key, type, value, path, errors);
      return;
    }
    applyInferredCustomTag(container, adapterContext, key, value, path, errors);
  }

  private static void applyTypedCustomTag(PersistentDataContainer container, PersistentDataAdapterContext adapterContext,
      NamespacedKey key, String type, Object value, String path, List<String> errors) {
    String normalized = type.trim().toLowerCase(Locale.ROOT);
    switch (normalized) {
      case "byte" -> container.set(key, PersistentDataType.BYTE, coerceNumber(value, path, errors).byteValue());
      case "short" -> container.set(key, PersistentDataType.SHORT, coerceNumber(value, path, errors).shortValue());
      case "int", "integer" -> container.set(key, PersistentDataType.INTEGER, coerceNumber(value, path, errors).intValue());
      case "long" -> container.set(key, PersistentDataType.LONG, coerceNumber(value, path, errors).longValue());
      case "float" -> container.set(key, PersistentDataType.FLOAT, coerceNumber(value, path, errors).floatValue());
      case "double" -> container.set(key, PersistentDataType.DOUBLE, coerceNumber(value, path, errors).doubleValue());
      case "string" -> container.set(key, PersistentDataType.STRING, String.valueOf(value));
      case "byte_array", "bytes" -> container.set(key, PersistentDataType.BYTE_ARRAY, toByteArray(value, path, errors));
      case "int_array", "integer_array", "ints" -> container.set(key, PersistentDataType.INTEGER_ARRAY, toIntArray(value, path, errors));
      case "long_array", "longs" -> container.set(key, PersistentDataType.LONG_ARRAY, toLongArray(value, path, errors));
      case "container", "tag_container" -> {
        PersistentDataContainer nested = adapterContext.newPersistentDataContainer();
        if (value instanceof ConfigurationSection section) {
          applyCustomTags(nested, adapterContext, section, path, errors);
        } else if (value instanceof Map<?, ?> map) {
          applyCustomTags(nested, adapterContext, map, path, errors);
        } else {
          errors.add(path + ": custom tag container requires a map value");
        }
        container.set(key, PersistentDataType.TAG_CONTAINER, nested);
      }
      default -> errors.add(path + ": unknown custom tag type=" + type);
    }
  }

  private static void applyInferredCustomTag(PersistentDataContainer container, PersistentDataAdapterContext adapterContext,
      NamespacedKey key, Object value, String path, List<String> errors) {
    if (value instanceof Number number) {
      if (value instanceof Integer) {
        container.set(key, PersistentDataType.INTEGER, number.intValue());
      } else if (value instanceof Long) {
        container.set(key, PersistentDataType.LONG, number.longValue());
      } else if (value instanceof Float) {
        container.set(key, PersistentDataType.FLOAT, number.floatValue());
      } else if (value instanceof Double) {
        container.set(key, PersistentDataType.DOUBLE, number.doubleValue());
      } else if (value instanceof Short) {
        container.set(key, PersistentDataType.SHORT, number.shortValue());
      } else if (value instanceof Byte) {
        container.set(key, PersistentDataType.BYTE, number.byteValue());
      } else {
        container.set(key, PersistentDataType.DOUBLE, number.doubleValue());
      }
      return;
    }
    if (value instanceof String string) {
      container.set(key, PersistentDataType.STRING, string);
      return;
    }
    if (value instanceof ConfigurationSection section) {
      PersistentDataContainer nested = adapterContext.newPersistentDataContainer();
      applyCustomTags(nested, adapterContext, section, path, errors);
      container.set(key, PersistentDataType.TAG_CONTAINER, nested);
      return;
    }
    if (value instanceof Map<?, ?> map) {
      PersistentDataContainer nested = adapterContext.newPersistentDataContainer();
      applyCustomTags(nested, adapterContext, map, path, errors);
      container.set(key, PersistentDataType.TAG_CONTAINER, nested);
      return;
    }
    if (value instanceof List<?> list) {
      container.set(key, PersistentDataType.INTEGER_ARRAY, toIntArray(list, path, errors));
      return;
    }
    errors.add(path + ": unsupported custom tag value type=" + value.getClass().getSimpleName());
  }

  private static void applyCustomTags(PersistentDataContainer container, PersistentDataAdapterContext adapterContext,
      ConfigurationSection section, String path, List<String> errors) {
    for (String key : section.getKeys(false)) {
      NamespacedKey namespacedKey = NamespacedKey.fromString(key);
      if (namespacedKey == null) {
        errors.add(path + ": invalid NamespacedKey=" + key);
        continue;
      }
      Object raw = section.get(key);
      ConfigurationSection nested = section.getConfigurationSection(key);
      if (nested != null) {
        raw = nested;
      }
      applyCustomTag(container, adapterContext, namespacedKey, raw, path + "." + key, errors);
    }
  }

  private static void applyCustomTags(PersistentDataContainer container, PersistentDataAdapterContext adapterContext,
      Map<?, ?> map, String path, List<String> errors) {
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String key = String.valueOf(entry.getKey());
      NamespacedKey namespacedKey = NamespacedKey.fromString(key);
      if (namespacedKey == null) {
        errors.add(path + ": invalid NamespacedKey=" + key);
        continue;
      }
      applyCustomTag(container, adapterContext, namespacedKey, entry.getValue(), path + "." + key, errors);
    }
  }

  private static Number coerceNumber(Object value, String path, List<String> errors) {
    if (value instanceof Number number) {
      return number;
    }
    if (value instanceof String raw) {
      try {
        if (raw.contains(".")) {
          return Double.parseDouble(raw);
        }
        return Long.parseLong(raw);
      } catch (NumberFormatException ex) {
        errors.add(path + ": invalid numeric value=" + raw);
      }
    }
    errors.add(path + ": expected numeric value");
    return 0;
  }

  private static byte[] toByteArray(Object value, String path, List<String> errors) {
    if (value instanceof byte[] bytes) {
      return bytes;
    }
    if (!(value instanceof List<?> list)) {
      errors.add(path + ": expected list for byte_array");
      return new byte[0];
    }
    byte[] out = new byte[list.size()];
    for (int i = 0; i < list.size(); i++) {
      Object entry = list.get(i);
      if (!(entry instanceof Number number)) {
        errors.add(path + ": byte_array entry is not a number");
        continue;
      }
      out[i] = number.byteValue();
    }
    return out;
  }

  private static int[] toIntArray(Object value, String path, List<String> errors) {
    if (value instanceof int[] ints) {
      return ints;
    }
    if (!(value instanceof List<?> list)) {
      errors.add(path + ": expected list for int_array");
      return new int[0];
    }
    int[] out = new int[list.size()];
    for (int i = 0; i < list.size(); i++) {
      Object entry = list.get(i);
      if (!(entry instanceof Number number)) {
        errors.add(path + ": int_array entry is not a number");
        continue;
      }
      out[i] = number.intValue();
    }
    return out;
  }

  private static long[] toLongArray(Object value, String path, List<String> errors) {
    if (value instanceof long[] longs) {
      return longs;
    }
    if (!(value instanceof List<?> list)) {
      errors.add(path + ": expected list for long_array");
      return new long[0];
    }
    long[] out = new long[list.size()];
    for (int i = 0; i < list.size(); i++) {
      Object entry = list.get(i);
      if (!(entry instanceof Number number)) {
        errors.add(path + ": long_array entry is not a number");
        continue;
      }
      out[i] = number.longValue();
    }
    return out;
  }

  private static void applyComponents(ItemMeta meta, ConfigurationSection section, String path, List<String> errors) {
    ConfigurationSection cmdSection = section.getConfigurationSection("custom_model_data");
    if (cmdSection == null) {
      cmdSection = section.getConfigurationSection("customModelData");
    }
    if (cmdSection != null) {
      CustomModelDataComponent component = meta.getCustomModelDataComponent();
      if (cmdSection.contains("value")) {
        Number value = coerceNumber(cmdSection.get("value"), path + ".custom_model_data.value", errors);
        component.setFloats(List.of(value.floatValue()));
      }
      if (cmdSection.contains("floats")) {
        List<Float> floats = parseFloatList(cmdSection.getList("floats"), path + ".custom_model_data.floats", errors);
        component.setFloats(floats);
      }
      if (cmdSection.contains("strings")) {
        component.setStrings(cmdSection.getStringList("strings"));
      }
      if (cmdSection.contains("flags")) {
        component.setFlags(parseBooleanList(cmdSection.getList("flags"), path + ".custom_model_data.flags", errors));
      }
      if (cmdSection.contains("colors")) {
        component.setColors(parseColorList(cmdSection.getList("colors"), path + ".custom_model_data.colors", errors));
      }
      meta.setCustomModelDataComponent(component);
    }
    ConfigurationSection foodSection = section.getConfigurationSection("food");
    if (foodSection != null) {
      FoodComponent food = meta.getFood();
      food.setNutrition(foodSection.getInt("nutrition", food.getNutrition()));
      food.setSaturation((float) foodSection.getDouble("saturation", food.getSaturation()));
      if (foodSection.contains("canAlwaysEat")) {
        food.setCanAlwaysEat(foodSection.getBoolean("canAlwaysEat"));
      }
      meta.setFood(food);
    }
    ConfigurationSection cooldownSection = section.getConfigurationSection("use_cooldown");
    if (cooldownSection != null) {
      UseCooldownComponent cooldown = meta.getUseCooldown();
      if (cooldownSection.contains("seconds")) {
        cooldown.setCooldownSeconds((float) cooldownSection.getDouble("seconds"));
      }
      String group = cooldownSection.getString("group");
      if (group != null) {
        NamespacedKey key = NamespacedKey.fromString(group);
        cooldown.setCooldownGroup(key);
      }
      meta.setUseCooldown(cooldown);
    }
    ConfigurationSection toolSection = section.getConfigurationSection("tool");
    if (toolSection != null) {
      ToolComponent tool = meta.getTool();
      if (toolSection.contains("defaultMiningSpeed")) {
        tool.setDefaultMiningSpeed((float) toolSection.getDouble("defaultMiningSpeed"));
      }
      if (toolSection.contains("damagePerBlock")) {
        tool.setDamagePerBlock(Math.max(0, toolSection.getInt("damagePerBlock")));
      }
      meta.setTool(tool);
    }
    ConfigurationSection equippableSection = section.getConfigurationSection("equippable");
    if (equippableSection != null) {
      EquippableComponent equippable = meta.getEquippable();
      String slotRaw = equippableSection.getString("slot");
      if (slotRaw != null) {
        try {
          equippable.setSlot(EquipmentSlot.valueOf(slotRaw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
          errors.add(path + ".equippable.slot: invalid slot=" + slotRaw);
        }
      }
      if (equippableSection.contains("dispensable")) {
        equippable.setDispensable(equippableSection.getBoolean("dispensable"));
      }
      if (equippableSection.contains("swappable")) {
        equippable.setSwappable(equippableSection.getBoolean("swappable"));
      }
      if (equippableSection.contains("equipOnInteract")) {
        equippable.setEquipOnInteract(equippableSection.getBoolean("equipOnInteract"));
      }
      if (equippableSection.contains("damageOnHurt")) {
        equippable.setDamageOnHurt(equippableSection.getBoolean("damageOnHurt"));
      }
      String model = equippableSection.getString("model");
      if (model != null) {
        equippable.setModel(NamespacedKey.fromString(model));
      }
      String overlay = equippableSection.getString("cameraOverlay");
      if (overlay != null) {
        equippable.setCameraOverlay(NamespacedKey.fromString(overlay));
      }
      String soundRaw = equippableSection.getString("equipSound");
      if (soundRaw != null) {
        org.bukkit.Sound sound = parseSound(soundRaw);
        if (sound == null) {
          errors.add(path + ".equippable.equipSound: invalid sound=" + soundRaw);
        } else {
          equippable.setEquipSound(sound);
        }
      }
      List<String> allowed = equippableSection.getStringList("allowedEntities");
      if (!allowed.isEmpty()) {
        List<EntityType> types = new ArrayList<>();
        for (String raw : allowed) {
          try {
            types.add(EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
          } catch (IllegalArgumentException ex) {
            errors.add(path + ".equippable.allowedEntities: invalid entity=" + raw);
          }
        }
        equippable.setAllowedEntities(types);
      }
      meta.setEquippable(equippable);
    }
    ConfigurationSection jukeboxSection = section.getConfigurationSection("jukebox");
    if (jukeboxSection != null) {
      JukeboxPlayableComponent jukebox = meta.getJukeboxPlayable();
      String song = jukeboxSection.getString("song");
      if (song != null) {
        NamespacedKey key = NamespacedKey.fromString(song.contains(":") ? song : "minecraft:" + song.toLowerCase(Locale.ROOT));
        if (key != null) {
          jukebox.setSongKey(key);
        }
      }
      if (jukeboxSection.contains("showInTooltip")) {
        errors.add(path + ".jukebox.showInTooltip: not supported in this Paper version");
      }
      meta.setJukeboxPlayable(jukebox);
    }
  }

  private static List<Float> parseFloatList(List<?> values, String path, List<String> errors) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    List<Float> out = new ArrayList<>(values.size());
    for (Object value : values) {
      if (value instanceof Number number) {
        out.add(number.floatValue());
      } else if (value instanceof String raw) {
        try {
          out.add(Float.parseFloat(raw));
        } catch (NumberFormatException ex) {
          errors.add(path + ": invalid float=" + raw);
        }
      } else {
        errors.add(path + ": invalid float value");
      }
    }
    return out;
  }

  private static List<Boolean> parseBooleanList(List<?> values, String path, List<String> errors) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    List<Boolean> out = new ArrayList<>(values.size());
    for (Object value : values) {
      if (value instanceof Boolean bool) {
        out.add(bool);
      } else if (value instanceof String raw) {
        out.add(Boolean.parseBoolean(raw));
      } else {
        errors.add(path + ": invalid boolean value");
      }
    }
    return out;
  }

  private static Color parseColorSection(ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return null;
    }
    if (section.contains("hex")) {
      return parseColorString(section.getString("hex"), path + ".hex", errors);
    }
    if (section.contains("value")) {
      return parseColorString(section.getString("value"), path + ".value", errors);
    }
    Integer r = intFrom(section, "r", "red");
    Integer g = intFrom(section, "g", "green");
    Integer b = intFrom(section, "b", "blue");
    if (r == null || g == null || b == null) {
      errors.add(path + ": expected color r/g/b or hex");
      return null;
    }
    return Color.fromRGB(clampColor(r), clampColor(g), clampColor(b));
  }

  private static int clampColor(int value) {
    return Math.max(0, Math.min(255, value));
  }

  private static Color parseColorString(String raw, String path, List<String> errors) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String value = raw.trim();
    if (value.startsWith("#")) {
      value = value.substring(1);
    }
    if (value.matches("^[0-9a-fA-F]{6}$")) {
      int rgb = Integer.parseInt(value, 16);
      return Color.fromRGB(rgb);
    }
    DyeColor dye = parseDyeColor(value, path, errors);
    return dye == null ? null : dye.getColor();
  }

  private static DyeColor parseDyeColor(String raw, String path, List<String> errors) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return DyeColor.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      errors.add(path + ": invalid dye color=" + raw);
      return null;
    }
  }

  private static PatternType parsePatternType(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    NamespacedKey key = NamespacedKey.fromString(raw.contains(":") ? raw : "minecraft:" + raw.toLowerCase(Locale.ROOT));
    if (key == null) {
      return null;
    }
    var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BANNER_PATTERN);
    return registry == null ? null : registry.get(key);
  }

  private static org.bukkit.Sound parseSound(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    NamespacedKey key = NamespacedKey.fromString(raw.contains(":") ? raw : "minecraft:" + raw.toLowerCase(Locale.ROOT));
    if (key == null) {
      return null;
    }
    var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT);
    return registry == null ? null : registry.get(key);
  }

  private static FireworkEffect parseFireworkEffect(Map<?, ?> entry, String path, List<String> errors) {
    Object typeRaw = entry.get("type");
    if (typeRaw == null) {
      errors.add(path + ": missing type");
      return null;
    }
    FireworkEffect.Type type;
    try {
      type = FireworkEffect.Type.valueOf(String.valueOf(typeRaw).trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      errors.add(path + ": invalid firework type=" + typeRaw);
      return null;
    }
    List<Color> colors = parseColorList(entry.get("colors"), path + ".colors", errors);
    List<Color> fades = parseColorList(entry.get("fades"), path + ".fades", errors);
    boolean flicker = entry.containsKey("flicker") && Boolean.parseBoolean(String.valueOf(entry.get("flicker")));
    boolean trail = entry.containsKey("trail") && Boolean.parseBoolean(String.valueOf(entry.get("trail")));
    FireworkEffect.Builder builder = FireworkEffect.builder().with(type);
    if (!colors.isEmpty()) {
      builder.withColor(colors);
    }
    if (!fades.isEmpty()) {
      builder.withFade(fades);
    }
    if (flicker) {
      builder.flicker(true);
    }
    if (trail) {
      builder.trail(true);
    }
    return builder.build();
  }

  private static List<Color> parseColorList(Object raw, String path, List<String> errors) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<Color> colors = new ArrayList<>();
    int index = 0;
    for (Object entry : list) {
      String entryPath = path + "[" + index + "]";
      Color color = parseColorString(String.valueOf(entry), entryPath, errors);
      if (color != null) {
        colors.add(color);
      }
      index++;
    }
    return colors;
  }

  private static PotionType parsePotionType(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return PotionType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private static PotionEffectType parsePotionEffectType(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    NamespacedKey key = NamespacedKey.fromString(raw.contains(":") ? raw : "minecraft:" + raw.toLowerCase(Locale.ROOT));
    if (key == null) {
      return null;
    }
    return RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT).get(key);
  }

  private static ItemStack parseItemStackEntry(Object entry, String path, List<String> errors) {
    if (entry == null) {
      return null;
    }
    String materialRaw = null;
    int amount = 1;
    if (entry instanceof String s) {
      materialRaw = s;
    } else if (entry instanceof ConfigurationSection section) {
      materialRaw = section.getString("material", section.getString("type", null));
      amount = section.getInt("amount", 1);
    } else if (entry instanceof Map<?, ?> map) {
      Object material = map.get("material");
      if (material == null) {
        material = map.get("type");
      }
      if (material != null) {
        materialRaw = String.valueOf(material);
      }
      if (map.containsKey("amount")) {
        amount = Integer.parseInt(String.valueOf(map.get("amount")));
      }
    }
    if (materialRaw == null) {
      errors.add(path + ": missing material");
      return null;
    }
    Material material = Material.matchMaterial(materialRaw);
    if (material == null) {
      errors.add(path + ": unknown material=" + materialRaw);
      return null;
    }
    return new ItemStack(material, Math.max(1, amount));
  }

  private static String stringFrom(ConfigurationSection primary, ConfigurationSection fallback, String... keys) {
    if (primary != null) {
      for (String key : keys) {
        String value = primary.getString(key);
        if (value != null) {
          return value;
        }
      }
    }
    if (fallback != null && fallback != primary) {
      for (String key : keys) {
        String value = fallback.getString(key);
        if (value != null) {
          return value;
        }
      }
    }
    return null;
  }

  @SuppressWarnings("unused")
  private static List<String> listFrom(ConfigurationSection primary, ConfigurationSection fallback, String key) {
    if (primary != null && primary.isList(key)) {
      return primary.getStringList(key);
    }
    if (fallback != null && fallback.isList(key)) {
      return fallback.getStringList(key);
    }
    return List.of();
  }

  private static Map<String, String> buildPlaceholders(ConfigurationSection display, ConfigurationSection root,
      Material material, int amount) {
    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("material", material == null ? "unknown" : material.name().toLowerCase(Locale.ROOT));
    placeholders.put("amount", String.valueOf(amount));
    String tier = stringFrom(root, root, "tier", "itemTier");
    if (tier != null) {
      placeholders.put("tier", tier);
    }
    String rarity = stringFrom(root, root, "rarity", "itemRarity");
    if (rarity != null) {
      placeholders.put("rarity", rarity);
    }
    ConfigurationSection placeholderSection = display == null ? null : display.getConfigurationSection("placeholders");
    if (placeholderSection != null) {
      for (String key : placeholderSection.getKeys(false)) {
        Object value = placeholderSection.get(key);
        if (value != null) {
          placeholders.put(key, String.valueOf(value));
        }
      }
    }
    return placeholders;
  }

  private static String localizedString(ConfigurationSection primary, ConfigurationSection fallback,
      Map<String, String> placeholders, String... keys) {
    String localized = localizedKey(primary, fallback, placeholders, keys);
    if (localized != null) {
      return localized;
    }
    String raw = stringFrom(primary, fallback, keys);
    return raw == null ? null : resolveMaybeLocaleKey(raw, placeholders);
  }

  private static List<String> localizedList(ConfigurationSection primary, ConfigurationSection fallback,
      Map<String, String> placeholders, String... keys) {
    List<String> raw = List.of();
    boolean localized = false;
    if (primary != null) {
      for (String key : keys) {
        if (primary.isList(key)) {
          raw = primary.getStringList(key);
          localized = isLocaleKey(key);
          break;
        }
      }
    }
    if (raw.isEmpty() && fallback != null) {
      for (String key : keys) {
        if (fallback.isList(key)) {
          raw = fallback.getStringList(key);
          localized = isLocaleKey(key);
          break;
        }
      }
    }
    if (raw.isEmpty()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String value : raw) {
      if (value == null) {
        continue;
      }
      if (localized || isLocaleValue(value)) {
        out.add(Locales.text(null, stripLocalePrefix(value), placeholders));
      } else {
        out.add(resolveMaybeLocaleKey(value, placeholders));
      }
    }
    return out;
  }

  private static String localizedKey(ConfigurationSection primary, ConfigurationSection fallback,
      Map<String, String> placeholders, String... keys) {
    if (primary != null) {
      for (String key : keys) {
        if (!isLocaleKey(key)) {
          continue;
        }
        String value = primary.getString(key);
        if (value != null) {
          return Locales.text(null, value, placeholders);
        }
      }
    }
    if (fallback != null && fallback != primary) {
      for (String key : keys) {
        if (!isLocaleKey(key)) {
          continue;
        }
        String value = fallback.getString(key);
        if (value != null) {
          return Locales.text(null, value, placeholders);
        }
      }
    }
    return null;
  }

  private static boolean isLocaleKey(String key) {
    if (key == null) {
      return false;
    }
    String normalized = key.toLowerCase(Locale.ROOT);
    return normalized.endsWith("key") || normalized.endsWith("keys");
  }

  private static String applyPlaceholders(String raw, Map<String, String> placeholders) {
    if (raw == null || raw.isEmpty() || placeholders == null || placeholders.isEmpty()) {
      return raw == null ? "" : raw;
    }
    String out = raw;
    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
      String key = entry.getKey();
      if (key == null) {
        continue;
      }
      String value = entry.getValue() == null ? "" : entry.getValue();
      out = out.replace("{" + key + "}", value);
    }
    return out;
  }

  private static String resolveMaybeLocaleKey(String raw, Map<String, String> placeholders) {
    if (raw == null) {
      return null;
    }
    if (isLocaleValue(raw)) {
      return Locales.text(null, stripLocalePrefix(raw), placeholders);
    }
    return applyPlaceholders(raw, placeholders);
  }

  private static boolean isLocaleValue(String raw) {
    if (raw == null) {
      return false;
    }
    String trimmed = raw.trim().toLowerCase(Locale.ROOT);
    return trimmed.startsWith("key:") || trimmed.startsWith("i18n:") || trimmed.startsWith("locale:");
  }

  private static String stripLocalePrefix(String raw) {
    if (raw == null) {
      return "";
    }
    String trimmed = raw.trim();
    String lower = trimmed.toLowerCase(Locale.ROOT);
    if (lower.startsWith("key:")) {
      return trimmed.substring(4).trim();
    }
    if (lower.startsWith("i18n:")) {
      return trimmed.substring(5).trim();
    }
    if (lower.startsWith("locale:")) {
      return trimmed.substring(7).trim();
    }
    return trimmed;
  }

  private static Enchantment parseEnchantment(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String trimmed = raw.trim();
    NamespacedKey key;
    if (trimmed.contains(":")) {
      key = NamespacedKey.fromString(trimmed);
    } else {
      String legacy = switch (trimmed.toUpperCase(Locale.ROOT)) {
        case "DURABILITY" -> "unbreaking";
        default -> trimmed.toLowerCase(Locale.ROOT);
      };
      key = NamespacedKey.minecraft(legacy);
    }
    if (key == null) {
      return null;
    }
    return RegistryAccess.registryAccess()
        .getRegistry(RegistryKey.ENCHANTMENT)
        .get(key);
  }

  private static void validateSectionKeys(ConfigurationSection section, java.util.Set<String> allowed,
      String path, List<String> errors) {
    if (section == null || allowed == null || allowed.isEmpty()) {
      return;
    }
    for (String key : section.getKeys(false)) {
      if (!allowed.contains(key)) {
        errors.add(path + ": unknown key=" + key);
      }
    }
  }

  private static final java.util.Set<String> ITEM_KEYS = java.util.Set.of(
      "material",
      "type",
      "amount",
      "display",
      "meta",
      "custom_model_data",
      "customModelData");

  private static final java.util.Set<String> DISPLAY_KEYS = java.util.Set.of(
      "name",
      "nameKey",
      "name_key",
      "lore",
      "loreKeys",
      "lore_keys",
      "loreKey",
      "subtitle",
      "subtitleKey",
      "subtitle_key",
      "description",
      "descriptionKey",
      "description_key",
      "rarityLine",
      "rarity_line",
      "rarityLineKey",
      "rarity_line_key",
      "flavor",
      "flavorKey",
      "flavor_key",
      "placeholders",
      "custom_model_data",
      "customModelData");

  private static final java.util.Set<String> META_KEYS = java.util.Set.of(
      "display-name",
      "displayName",
      "name",
      "displayNameKey",
      "display_name_key",
      "display-name-key",
      "nameKey",
      "name_key",
      "lore",
      "loreKeys",
      "lore_keys",
      "loreKey",
      "unbreakable",
      "enchants",
      "enchantments",
      "flags",
      "pdc",
      "custom_tags",
      "durability",
      "damageMin",
      "damageMax",
      "damage_min",
      "damage_max",
      "damage",
      "repair_cost",
      "repairCost",
      "max_damage",
      "maxDamage",
      "custom_model_data",
      "customModelData",
      "book",
      "stored_enchants",
      "potion",
      "suspicious_stew",
      "leather_armor",
      "banner",
      "shield",
      "firework",
      "firework_charge",
      "map",
      "compass",
      "skull",
      "crossbow",
      "bundle",
      "spawn_egg",
      "axolotl_bucket",
      "tropical_fish_bucket",
      "music_instrument",
      "ominous_bottle",
      "knowledge_book",
      "trim",
      "block_state",
      "block_data",
      "components",
      "tags");
}
