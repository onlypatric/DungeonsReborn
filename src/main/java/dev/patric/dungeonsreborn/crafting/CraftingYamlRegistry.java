package dev.patric.dungeonsreborn.crafting;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.logging.ServiceLogger;
import dev.patric.dungeonsreborn.system.SystemStatusStore;
import dev.patric.dungeonsreborn.util.YamlValues;
import dev.patric.dungeonsreborn.effects.items.ItemTemplateCompiler;
import dev.patric.dungeonsreborn.effects.items.ItemTemplateCompiler.CompiledTemplate;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

public final class CraftingYamlRegistry {
  public record ReloadResult(int loaded, List<String> errors) {
  }

  private final JavaPlugin plugin;
  private final ServiceLogger logger;
  private final Function<String, ItemStack> itemResolver;
  private final Map<String, CraftingRecipeTemplate> recipes = new LinkedHashMap<>();
  private final CraftingRecipeIndex index = new CraftingRecipeIndex();

  public CraftingYamlRegistry(JavaPlugin plugin, ServiceLogger logger, Function<String, ItemStack> itemResolver) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.itemResolver = Objects.requireNonNull(itemResolver, "itemResolver");
  }

  public ServiceLogger logger() {
    return logger;
  }

  public Map<String, CraftingRecipeTemplate> recipes() {
    return java.util.Collections.unmodifiableMap(recipes);
  }

  public java.util.Collection<CraftingRecipeTemplate> candidates(ItemStack[] inputs) {
    return index.candidates(inputs);
  }

  public CraftingRecipeTemplate recipeTemplate(String id) {
    if (id == null) {
      return null;
    }
    return recipes.get(Ids.normalize(id));
  }

  public CraftingRecipeSpec recipe(String id) {
    CraftingRecipeTemplate template = recipeTemplate(id);
    return template == null ? null : template.spec();
  }

  public ItemStack resolveItemTemplate(String itemId) {
    if (itemId == null) {
      return null;
    }
    ItemStack resolved = itemResolver.apply(itemId);
    return resolved == null ? null : resolved.clone();
  }

  public File recipesDir() {
    return new File(plugin.getDataFolder(), "recipes");
  }

  public ReloadResult reload() {
    List<String> errors = new ArrayList<>();
    File dir = recipesDir();
    if (!dir.exists()) {
      dir.mkdirs();
    }
    Map<String, CraftingRecipeTemplate> next = new LinkedHashMap<>();
    File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml")
        || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
    if (files == null) {
      files = new File[0];
    }
    java.util.Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
    for (File file : files) {
      String base = file.getPath();
      try {
        CraftingRecipeSpec spec = loadRecipe(file, base, errors);
        if (spec != null) {
          List<ItemStack> outputTemplates = resolveOutputs(spec.outputs(), base, errors);
          if (outputTemplates == null || outputTemplates.isEmpty()) {
            errors.add(base + ": output templates could not be resolved");
            continue;
          }
          next.put(spec.id(), new CraftingRecipeTemplate(spec, outputTemplates));
        }
      } catch (Exception ex) {
        errors.add(base + ": " + ex.getMessage());
      }
    }
    if (errors.isEmpty()) {
      recipes.clear();
      recipes.putAll(next);
      index.clear();
      for (CraftingRecipeTemplate recipe : next.values()) {
        index.register(recipe);
      }
    }
    if (!errors.isEmpty()) {
      logger.warn("[Crafting] YAML reload had " + errors.size() + " errors");
      for (String e : errors) {
        logger.warn("[Crafting] YAML: " + e);
      }
    } else {
      logger.info("[Crafting] YAML loaded " + next.size() + " recipes");
    }
    SystemStatusStore.get().record(
        "crafting",
        "Crafting",
        dir.getPath(),
        "recipes=" + (errors.isEmpty() ? next.size() : recipes.size()),
        errors);
    return new ReloadResult(errors.isEmpty() ? next.size() : recipes.size(), errors);
  }

  public List<String> validate() {
    List<String> errors = new ArrayList<>();
    File dir = recipesDir();
    if (!dir.exists()) {
      dir.mkdirs();
    }
    File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml")
        || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
    if (files == null) {
      files = new File[0];
    }
    java.util.Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
    for (File file : files) {
      String base = file.getPath();
      try {
        CraftingRecipeSpec spec = loadRecipe(file, base, errors);
        if (spec != null) {
          List<ItemStack> outputTemplates = resolveOutputs(spec.outputs(), base, errors);
          if (outputTemplates == null || outputTemplates.isEmpty()) {
            errors.add(base + ": output templates could not be resolved");
          }
        }
      } catch (Exception ex) {
        errors.add(base + ": " + ex.getMessage());
      }
    }
    return errors;
  }

  private CraftingRecipeSpec loadRecipe(File file, String base, List<String> errors) {
    String filename = file.getName();
    int dot = filename.lastIndexOf('.');
    if (dot <= 0) {
      errors.add(base + ": filename must be <recipeId>.yml");
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
    if (recipes.containsKey(id)) {
      errors.add(base + ": duplicate recipe id=" + id);
      return null;
    }
    String name = YamlValues.string(cfg.get("name"), "");
    String description = YamlValues.string(cfg.get("description"), "");
    List<String> permissions = readPermissions(cfg, base);
    double cooldownSeconds = YamlValues.doubleValue(cfg.get("cooldownSeconds"), YamlValues.doubleValue(cfg.get("cooldown"), 0.0));

    ScriptSpec scriptSpec = parseScript(cfg.get("script"), base + ".script", errors);
    CraftingHookSpec hooks = parseHooks(cfg.get("hooks"), base + ".hooks", scriptSpec, errors);
    CraftingDiscoverySpec discovery = parseDiscovery(cfg.get("discovery"), base + ".discovery", errors);

    List<CraftingRecipeVariant> variants = parseVariants(cfg, base, errors);
    List<CraftingOutputSpec> outputs = parseOutputs(cfg, base, errors);
    List<CraftingRequirementSpec> requirements = parseRequirements(cfg.get("requirements"), base + ".requirements", errors);
    List<CraftingCostSpec> costs = parseCosts(cfg.get("costs"), base + ".costs", errors);
    if (outputs.isEmpty()) {
      errors.add(base + ": output is missing or invalid");
      return null;
    }
    if (variants.isEmpty()) {
      errors.add(base + ": inputs/variants must not be empty");
      return null;
    }
    return new CraftingRecipeSpec(id, name, description, permissions, cooldownSeconds, requirements, costs, variants, outputs,
        hooks, discovery, scriptSpec.file, scriptSpec.inline);
  }

  private List<CraftingRecipeVariant> parseVariants(YamlConfiguration cfg, String base, List<String> errors) {
    List<CraftingRecipeVariant> out = new ArrayList<>();
    Object rawVariants = cfg.get("variants");
    if (rawVariants instanceof List<?> list && !list.isEmpty()) {
      for (int i = 0; i < list.size(); i++) {
        String path = base + ".variants[" + i + "]";
        Map<String, Object> map = castMap(list.get(i), path, errors);
        if (map == null) {
          continue;
        }
        CraftingRecipeVariant variant = parseVariant(map, path, errors);
        if (variant != null) {
          out.add(variant);
        }
      }
      return out;
    }
    Map<String, Object> root = new LinkedHashMap<>();
    for (String key : cfg.getKeys(false)) {
      root.put(key, cfg.get(key));
    }
    CraftingRecipeVariant variant = parseVariant(root, base, errors);
    if (variant != null) {
      out.add(variant);
    }
    return out;
  }

  private CraftingRecipeVariant parseVariant(Map<String, Object> map, String path, List<String> errors) {
    int priority = YamlValues.intValue(map.get("priority"), 0);
    boolean strict = YamlValues.bool(map.get("strict"), false);
    boolean allowOverflow = YamlValues.bool(map.get("allowOverflow"), !strict);
    if (strict) {
      allowOverflow = false;
    }
    Object gridRaw = map.get("grid");
    Object slotsRaw = map.get("slots");
    CraftingGridSpec grid = null;
    List<CraftingSlotIngredientSpec> slots = new ArrayList<>();
    if (gridRaw != null) {
      GridParseResult gridResult = parseGrid(gridRaw, path + ".grid", errors);
      if (gridResult != null) {
        grid = gridResult.grid;
        slots = gridResult.slots;
      }
    }
    if (slotsRaw != null) {
      slots.addAll(parseSlotConstraints(slotsRaw, path + ".slots", errors));
    }
    VariantInputs inputs = parseInputs(map.get("inputs"), path + ".inputs", errors);
    slots.addAll(inputs.slots);
    boolean hasInputs = !inputs.inputs.isEmpty();
    boolean hasSlots = !slots.isEmpty();
    if (!hasInputs && !hasSlots) {
      return null;
    }
    return new CraftingRecipeVariant(inputs.inputs, slots, grid, strict, allowOverflow, priority);
  }

  private record GridParseResult(CraftingGridSpec grid, List<CraftingSlotIngredientSpec> slots) {}

  private GridParseResult parseGrid(Object raw, String path, List<String> errors) {
    Map<String, Object> map = castMap(raw, path, errors);
    if (map == null) {
      return null;
    }
    List<String> pattern = readStringList(map.get("pattern"));
    if (pattern.isEmpty()) {
      errors.add(path + ".pattern: required");
      return null;
    }
    int height = pattern.size();
    int width = pattern.get(0).length();
    for (String row : pattern) {
      if (row.length() != width) {
        errors.add(path + ".pattern: all rows must have the same length");
        return null;
      }
    }
    int configWidth = YamlValues.intValue(map.get("width"), width);
    int configHeight = YamlValues.intValue(map.get("height"), height);
    if (configWidth != width || configHeight != height) {
      errors.add(path + ".pattern: width/height must match pattern size");
      return null;
    }
    boolean mirror = YamlValues.bool(map.get("mirror"), false);
    boolean rotate = YamlValues.bool(map.get("rotate"), false);
    Map<String, Object> keyMap = castMap(map.get("key"), path + ".key", errors);
    if (keyMap == null) {
      keyMap = castMap(map.get("keys"), path + ".keys", errors);
    }
    if (keyMap == null) {
      errors.add(path + ".key: required for grid patterns");
      return null;
    }
    Map<Character, CraftingIngredientSpec> ingredients = new HashMap<>();
    for (Map.Entry<String, Object> entry : keyMap.entrySet()) {
      String symbol = entry.getKey();
      if (symbol == null || symbol.length() != 1) {
        errors.add(path + ".key: symbol must be a single character");
        continue;
      }
      Map<String, Object> ingredientMap = castMap(entry.getValue(), path + ".key." + symbol, errors);
      if (ingredientMap == null) {
        continue;
      }
      IngredientParseResult parsed = parseIngredient(ingredientMap, path + ".key." + symbol, errors);
      if (parsed != null) {
        ingredients.put(symbol.charAt(0), parsed.spec);
      }
    }
    List<CraftingSlotIngredientSpec> slots = new ArrayList<>();
    for (int y = 0; y < height; y++) {
      String row = pattern.get(y);
      for (int x = 0; x < width; x++) {
        char symbol = row.charAt(x);
        if (symbol == ' ' || symbol == '.') {
          continue;
        }
        CraftingIngredientSpec ingredient = ingredients.get(symbol);
        if (ingredient == null) {
          errors.add(path + ".pattern: unknown symbol=" + symbol);
          continue;
        }
        int slot = y * width + x;
        slots.add(new CraftingSlotIngredientSpec(slot, ingredient));
      }
    }
    CraftingGridSpec grid = new CraftingGridSpec(width, height, mirror, rotate);
    return new GridParseResult(grid, slots);
  }

  private List<CraftingSlotIngredientSpec> parseSlotConstraints(Object raw, String path, List<String> errors) {
    List<CraftingSlotIngredientSpec> slots = new ArrayList<>();
    if (!(raw instanceof List<?> list)) {
      return slots;
    }
    for (int i = 0; i < list.size(); i++) {
      String entryPath = path + "[" + i + "]";
      Map<String, Object> map = castMap(list.get(i), entryPath, errors);
      if (map == null) {
        continue;
      }
      Integer slot = map.containsKey("slot") ? YamlValues.intValue(map.get("slot"), -1) : null;
      if (slot == null || slot < 0) {
        errors.add(entryPath + ".slot: required");
        continue;
      }
      IngredientParseResult parsed = parseIngredient(map, entryPath, errors);
      if (parsed != null) {
        slots.add(new CraftingSlotIngredientSpec(slot, parsed.spec));
      }
    }
    return slots;
  }

  private record VariantInputs(List<CraftingIngredientSpec> inputs, List<CraftingSlotIngredientSpec> slots) {}

  private VariantInputs parseInputs(Object raw, String path, List<String> errors) {
    if (!(raw instanceof List<?> list)) {
      return new VariantInputs(List.of(), List.of());
    }
    List<CraftingIngredientSpec> out = new ArrayList<>();
    List<CraftingSlotIngredientSpec> slots = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      String entryPath = path + "[" + i + "]";
      Map<String, Object> map = castMap(list.get(i), entryPath, errors);
      if (map == null) {
        continue;
      }
      Integer slot = map.containsKey("slot") ? YamlValues.intValue(map.get("slot"), -1) : null;
      IngredientParseResult parsed = parseIngredient(map, entryPath, errors);
      if (parsed != null) {
        if (slot != null && slot >= 0) {
          slots.add(new CraftingSlotIngredientSpec(slot, parsed.spec));
        } else {
          out.add(parsed.spec);
        }
      }
    }
    return new VariantInputs(out, slots);
  }

  private record IngredientParseResult(CraftingIngredientSpec spec) {}

  private IngredientParseResult parseIngredient(Map<String, Object> map, String path, List<String> errors) {
    String typeRaw = YamlValues.string(map.get("type"), null);
    String itemIdRaw = YamlValues.string(map.get("item"), YamlValues.string(map.get("itemId"), YamlValues.string(map.get("id"), null)));
    String upgradeIdRaw = YamlValues.string(map.get("upgradeId"), YamlValues.string(map.get("upgrade"), null));
    String tagRaw = YamlValues.string(map.get("tag"), null);
    String materialRaw = YamlValues.string(map.get("material"), null);
    String categoryRaw = YamlValues.string(map.get("category"), null);
    int amount = YamlValues.intValue(map.get("amount"), 1);
    if (amount <= 0) {
      errors.add(path + ".amount: must be > 0");
      return null;
    }

    CraftingMatchType type = CraftingMatchType.parse(typeRaw);
    if (typeRaw == null) {
      if (upgradeIdRaw != null) {
        type = CraftingMatchType.UPGRADE_ID;
      } else if (itemIdRaw != null) {
        type = CraftingMatchType.ITEM_ID;
      } else if (tagRaw != null) {
        type = CraftingMatchType.TAG;
      } else if (materialRaw != null) {
        type = CraftingMatchType.MATERIAL;
      } else if (categoryRaw != null) {
        type = CraftingMatchType.CATEGORY;
      }
    }

    String itemId = null;
    NamespacedKey tag = null;
    Material material = null;
    CraftingItemCategory category = null;

    switch (type) {
      case ITEM_ID -> {
        if (itemIdRaw == null || itemIdRaw.isBlank()) {
          errors.add(path + ".item: required for type=item_id");
          return null;
        }
        itemId = normalizeId(itemIdRaw, path + ".item");
      }
      case UPGRADE_ID -> {
        if (upgradeIdRaw == null || upgradeIdRaw.isBlank()) {
          errors.add(path + ".upgradeId: required for type=upgrade_id");
          return null;
        }
        itemId = normalizeId(upgradeIdRaw, path + ".upgradeId");
      }
      case TAG -> {
        if (tagRaw == null || tagRaw.isBlank()) {
          errors.add(path + ".tag: required for type=tag");
          return null;
        }
        tag = NamespacedKey.fromString(tagRaw);
        if (tag == null) {
          errors.add(path + ".tag: invalid namespaced key");
          return null;
        }
      }
      case MATERIAL -> {
        if (materialRaw == null || materialRaw.isBlank()) {
          errors.add(path + ".material: required for type=material");
          return null;
        }
        material = Material.matchMaterial(materialRaw);
        if (material == null) {
          errors.add(path + ".material: unknown material=" + materialRaw);
          return null;
        }
      }
      case CATEGORY -> {
        if (categoryRaw == null || categoryRaw.isBlank()) {
          errors.add(path + ".category: required for type=category");
          return null;
        }
        category = CraftingItemCategory.parse(categoryRaw);
      }
      case ANY -> {
      }
    }
    CraftingItemPredicate predicate = parsePredicate(map, path, errors);
    ReturnSpec returnSpec = parseReturnSpec(map.get("return"), path + ".return", errors);
    CraftingIngredientSpec spec = new CraftingIngredientSpec(type, itemId, tag, material, category, amount,
        predicate,
        returnSpec == null ? null : returnSpec.item,
        returnSpec == null ? 1 : returnSpec.amount);
    return new IngredientParseResult(spec);
  }

  private record ReturnSpec(ItemStack item, int amount) {}

  private ReturnSpec parseReturnSpec(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof String str) {
      ItemStack material = materialStack(str, 1);
      if (material != null) {
        return new ReturnSpec(material, 1);
      }
      String normalized = normalizeId(str, path);
      ItemStack resolved = resolveItemTemplate(normalized);
      if (resolved == null) {
        errors.add(path + ": unknown return item=" + str);
        return null;
      }
      resolved.setAmount(1);
      return new ReturnSpec(resolved, 1);
    }
    Map<String, Object> map = castMap(raw, path, errors);
    if (map == null) {
      return null;
    }
    int amount = Math.max(1, YamlValues.intValue(map.get("amount"), 1));
    String itemIdRaw = YamlValues.string(map.get("itemId"), YamlValues.string(map.get("item"), null));
    ItemStack material = materialStack(map.get("material"), 1);
    if (material != null) {
      return new ReturnSpec(material, amount);
    }
    if (itemIdRaw != null) {
      String normalized = normalizeId(itemIdRaw, path + ".itemId");
      ItemStack resolved = resolveItemTemplate(normalized);
      if (resolved == null) {
        errors.add(path + ".itemId: unknown item=" + itemIdRaw);
        return null;
      }
      resolved.setAmount(1);
      return new ReturnSpec(resolved, amount);
    }
    errors.add(path + ": return must include material or itemId");
    return null;
  }

  private CraftingOutputSpec parseOutput(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof ItemStack stack) {
      ItemStack copy = stack.clone();
      return new CraftingOutputSpec(null, copy, copy.getAmount(), null, null, 1.0, null, 1, false, List.of(), null);
    }
    if (raw instanceof ConfigurationSection section) {
      return parseOutput(section.getValues(false), path, errors);
    }
    if (!(raw instanceof Map<?, ?> mapRaw)) {
      errors.add(path + ": expected map or ItemStack");
      return null;
    }
    Map<String, Object> map = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : mapRaw.entrySet()) {
      map.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    double chance = map.containsKey("chance") ? YamlValues.doubleValue(map.get("chance"), 1.0) : 1.0;
    String pool = YamlValues.string(map.get("pool"), null);
    int weight = YamlValues.intValue(map.get("weight"), 1);
    boolean byproduct = YamlValues.bool(map.get("byproduct"), false);
    List<CraftingOutputSpec.OutputScaleRule> scaleRules = parseOutputScaleRules(map.get("scale"),
        path + ".scale", errors);
    CraftingOutputSpec.OutputMutation mutation = parseOutputMutation(map.get("mutation"),
        path + ".mutation", errors);
    if (mutation == null) {
      mutation = parseOutputMutation(map.get("mutate"), path + ".mutate", errors);
    }
    String itemIdRaw = YamlValues.string(map.get("itemId"), YamlValues.string(map.get("item"), null));
    String upgradeIdRaw = YamlValues.string(map.get("upgradeId"), YamlValues.string(map.get("upgrade"), null));
    if ((itemIdRaw == null || itemIdRaw.isBlank()) && upgradeIdRaw != null && !upgradeIdRaw.isBlank()) {
      itemIdRaw = upgradeIdRaw;
    }
    Object itemRaw = map.get("itemStack");
    if (itemRaw == null) {
      itemRaw = map.get("stack");
    }
    int amount = 1;
    Integer minAmount = null;
    Integer maxAmount = null;
    Object amountRaw = map.get("amount");
    if (amountRaw instanceof Map<?, ?> amountMapRaw) {
      Map<String, Object> amountMap = castMap(amountMapRaw, path + ".amount", errors);
      if (amountMap != null) {
        minAmount = amountMap.containsKey("min") ? YamlValues.intValue(amountMap.get("min"), 1) : null;
        maxAmount = amountMap.containsKey("max") ? YamlValues.intValue(amountMap.get("max"), 1) : null;
      }
    } else if (amountRaw != null) {
      amount = YamlValues.intValue(amountRaw, 1);
    }
    if (map.containsKey("min_amount")) {
      minAmount = YamlValues.intValue(map.get("min_amount"), 1);
    }
    if (map.containsKey("max_amount")) {
      maxAmount = YamlValues.intValue(map.get("max_amount"), 1);
    }
    if (minAmount != null && minAmount <= 0) {
      minAmount = 1;
    }
    if (maxAmount != null && maxAmount <= 0) {
      maxAmount = minAmount == null ? 1 : minAmount;
    }
    if (minAmount != null && maxAmount != null && maxAmount < minAmount) {
      int swap = minAmount;
      minAmount = maxAmount;
      maxAmount = swap;
    }
    if (itemRaw instanceof ItemStack stack) {
      ItemStack copy = stack.clone();
      copy.setAmount(Math.max(1, amount));
      return new CraftingOutputSpec(null, copy, copy.getAmount(), minAmount, maxAmount, chance, pool, weight, byproduct,
          scaleRules, mutation);
    }
    Object templateRaw = map.get("template");
    if (templateRaw == null) {
      templateRaw = map.get("item_template");
    }
    if (templateRaw instanceof Map<?, ?> templateMapRaw) {
      ItemStack built = compileItemTemplate(castMap(templateMapRaw, path + ".template", errors),
          path + ".template", errors);
      if (built != null) {
        built.setAmount(Math.max(1, amount));
        return new CraftingOutputSpec(null, built, built.getAmount(), minAmount, maxAmount, chance, pool, weight,
            byproduct, scaleRules, mutation);
      }
    }
    ItemStack built = materialStack(map.get("material"), amount);
    if (built != null) {
      return new CraftingOutputSpec(null, built, built.getAmount(), minAmount, maxAmount, chance, pool, weight, byproduct,
          scaleRules, mutation);
    }
    if (itemIdRaw != null) {
      String normalized = normalizeId(itemIdRaw, path + ".itemId");
      return new CraftingOutputSpec(normalized, null, Math.max(1, amount), minAmount, maxAmount, chance, pool, weight,
          byproduct, scaleRules, mutation);
    }
    errors.add(path + ": output must include itemId, itemStack, or material");
    return null;
  }

  private List<CraftingOutputSpec> parseOutputs(YamlConfiguration cfg, String base, List<String> errors) {
    List<CraftingOutputSpec> outputs = new ArrayList<>();
    Object rawOutputs = cfg.get("outputs");
    if (rawOutputs instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        CraftingOutputSpec spec = parseOutput(list.get(i), base + ".outputs[" + i + "]", errors);
        if (spec != null && spec.isDefined()) {
          outputs.add(spec);
        }
      }
    } else if (rawOutputs != null) {
      CraftingOutputSpec spec = parseOutput(rawOutputs, base + ".outputs", errors);
      if (spec != null && spec.isDefined()) {
        outputs.add(spec);
      }
    }
    if (outputs.isEmpty()) {
      Object raw = cfg.get("output");
      if (raw instanceof List<?> list) {
        for (int i = 0; i < list.size(); i++) {
          CraftingOutputSpec spec = parseOutput(list.get(i), base + ".output[" + i + "]", errors);
          if (spec != null && spec.isDefined()) {
            outputs.add(spec);
          }
        }
      } else {
        CraftingOutputSpec spec = parseOutput(raw, base + ".output", errors);
        if (spec != null && spec.isDefined()) {
          outputs.add(spec);
        }
      }
    }
    Object rawByproducts = cfg.get("byproducts");
    if (rawByproducts instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        CraftingOutputSpec spec = parseOutput(list.get(i), base + ".byproducts[" + i + "]", errors);
        if (spec != null && spec.isDefined()) {
          outputs.add(spec.byproduct() ? spec : spec.withByproduct(true));
        }
      }
    } else if (rawByproducts != null) {
      CraftingOutputSpec spec = parseOutput(rawByproducts, base + ".byproducts", errors);
      if (spec != null && spec.isDefined()) {
        outputs.add(spec.byproduct() ? spec : spec.withByproduct(true));
      }
    }
    return outputs;
  }

  private List<ItemStack> resolveOutputs(List<CraftingOutputSpec> outputs, String base, List<String> errors) {
    List<ItemStack> items = new ArrayList<>();
    for (int i = 0; i < outputs.size(); i++) {
      CraftingOutputSpec output = outputs.get(i);
      if (output == null) {
        continue;
      }
      ItemStack item = resolveOutput(output, base + ".outputs[" + i + "]", errors);
      if (item == null || item.getType().isAir()) {
        return null;
      }
      items.add(item);
    }
    return items;
  }

  private ItemStack resolveOutput(CraftingOutputSpec output, String path, List<String> errors) {
    if (output == null) {
      return null;
    }
    ItemStack item = output.item();
    if (item != null) {
      item.setAmount(output.previewAmount());
      return item;
    }
    String itemId = output.itemId();
    if (itemId == null) {
      return null;
    }
    ItemStack resolved = itemResolver.apply(itemId);
    if (resolved == null || resolved.getType().isAir()) {
      errors.add(path + ": output.itemId not found: " + itemId);
      return null;
    }
    resolved.setAmount(output.previewAmount());
    return resolved;
  }

  private List<CraftingOutputSpec.OutputScaleRule> parseOutputScaleRules(Object raw, String path, List<String> errors) {
    List<CraftingOutputSpec.OutputScaleRule> rules = new ArrayList<>();
    if (raw instanceof Map<?, ?> mapRaw) {
      Map<String, Object> map = castMap(mapRaw, path, errors);
      if (map == null) {
        return rules;
      }
      Object perPerm = map.get("per_permission");
      if (perPerm instanceof Map<?, ?> permRaw) {
        for (Map.Entry<?, ?> entry : permRaw.entrySet()) {
          String permission = String.valueOf(entry.getKey());
          double mult = YamlValues.doubleValue(entry.getValue(), 1.0);
          rules.add(new CraftingOutputSpec.OutputScaleRule(permission, mult, 0));
        }
      }
      String permission = YamlValues.string(map.get("permission"), null);
      if (permission != null) {
        double mult = YamlValues.doubleValue(map.get("multiplier"), 1.0);
        int add = YamlValues.intValue(map.get("add"), 0);
        rules.add(new CraftingOutputSpec.OutputScaleRule(permission, mult, add));
      } else if (map.containsKey("multiplier") || map.containsKey("add")) {
        double mult = YamlValues.doubleValue(map.get("multiplier"), 1.0);
        int add = YamlValues.intValue(map.get("add"), 0);
        rules.add(new CraftingOutputSpec.OutputScaleRule(null, mult, add));
      }
      return rules;
    }
    if (raw instanceof List<?> list) {
      int index = 0;
      for (Object entry : list) {
        String entryPath = path + "[" + index + "]";
        Map<String, Object> map = castMap(entry, entryPath, errors);
        if (map == null) {
          index++;
          continue;
        }
        String permission = YamlValues.string(map.get("permission"), null);
        double mult = YamlValues.doubleValue(map.get("multiplier"), 1.0);
        int add = YamlValues.intValue(map.get("add"), 0);
        rules.add(new CraftingOutputSpec.OutputScaleRule(permission, mult, add));
        index++;
      }
    }
    return rules;
  }

  private CraftingOutputSpec.OutputMutation parseOutputMutation(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    Map<String, Object> map = castMap(raw, path, errors);
    if (map == null) {
      return null;
    }
    String name = YamlValues.string(map.get("name"), YamlValues.string(map.get("display"), null));
    List<String> loreSet = readStringList(map.get("lore"));
    List<String> loreAdd = readStringList(map.get("lore_add"));
    if (name == null && loreSet.isEmpty() && loreAdd.isEmpty()) {
      return null;
    }
    return new CraftingOutputSpec.OutputMutation(name, loreSet, loreAdd);
  }

  private ItemStack compileItemTemplate(Map<String, Object> map, String path, List<String> errors) {
    if (map == null) {
      return null;
    }
    YamlConfiguration config = new YamlConfiguration();
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      config.set(entry.getKey(), entry.getValue());
    }
    CompiledTemplate compiled = ItemTemplateCompiler.compile(config, path, errors);
    if (compiled == null || compiled.item() == null) {
      return null;
    }
    return compiled.item().clone();
  }

  private List<String> readPermissions(YamlConfiguration cfg, String base) {
    List<String> out = new ArrayList<>();
    Object raw = cfg.get("permissions");
    if (raw instanceof List<?> list) {
      for (Object entry : list) {
        String s = YamlValues.string(entry, null);
        if (s != null && !s.isBlank()) {
          out.add(s);
        }
      }
    }
    Object reqs = cfg.get("requirements");
    if (reqs instanceof List<?> list) {
      for (Object entry : list) {
        if (entry instanceof String str) {
          out.add(str);
          continue;
        }
        if (entry instanceof Map<?, ?> map) {
          Object permission = map.get("permission");
          String s = YamlValues.string(permission, null);
          if (s != null && !s.isBlank()) {
            out.add(s);
          }
        }
      }
    }
    return out;
  }

  private List<CraftingRequirementSpec> parseRequirements(Object raw, String path, List<String> errors) {
    List<CraftingRequirementSpec> out = new ArrayList<>();
    if (raw == null) {
      return out;
    }
    if (raw instanceof String permission) {
      if (!permission.isBlank()) {
        out.add(CraftingRequirementSpec.permission(permission, null));
      }
      return out;
    }
    if (!(raw instanceof List<?> list)) {
      errors.add(path + ": requirements must be a list");
      return out;
    }
    for (int i = 0; i < list.size(); i++) {
      String entryPath = path + "[" + i + "]";
      Object entry = list.get(i);
      if (entry instanceof String permission) {
        if (!permission.isBlank()) {
          out.add(CraftingRequirementSpec.permission(permission, null));
        }
        continue;
      }
      Map<String, Object> map = castMap(entry, entryPath, errors);
      if (map == null) {
        continue;
      }
      CraftingRequirementSpec req = parseRequirement(map, entryPath, errors);
      if (req != null) {
        out.add(req);
      }
    }
    return out;
  }

  private CraftingRequirementSpec parseRequirement(Map<String, Object> map, String path, List<String> errors) {
    String typeRaw = YamlValues.string(map.get("type"), null);
    String message = YamlValues.string(map.get("message"), null);
    if (typeRaw == null) {
      if (map.containsKey("permission")) {
        typeRaw = "permission";
      } else if (map.containsKey("min") || map.containsKey("minLevel") || map.containsKey("level")) {
        typeRaw = "level";
      } else if (map.containsKey("customXp") || map.containsKey("custom_xp") || map.containsKey("minPoints")
          || map.containsKey("points")) {
        typeRaw = "custom_xp";
      } else if (map.containsKey("quest") || map.containsKey("questId") || map.containsKey("status")) {
        typeRaw = "quest";
      } else if (map.containsKey("class") || map.containsKey("classId") || map.containsKey("classes")) {
        typeRaw = "class";
      } else if (map.containsKey("region") || map.containsKey("regions")) {
        typeRaw = "region";
      }
    }
    if (typeRaw == null) {
      errors.add(path + ".type: required");
      return null;
    }
    switch (typeRaw.toLowerCase(Locale.ROOT)) {
      case "permission" -> {
        String permission = YamlValues.string(map.get("permission"), null);
        if (permission == null || permission.isBlank()) {
          errors.add(path + ".permission: required");
          return null;
        }
        return CraftingRequirementSpec.permission(permission, message);
      }
      case "level" -> {
        int minLevel = YamlValues.intValue(map.get("min"), YamlValues.intValue(map.get("minLevel"),
            YamlValues.intValue(map.get("level"), 0)));
        return CraftingRequirementSpec.level(minLevel, message);
      }
      case "custom_xp", "customxp" -> {
        int minLevel = YamlValues.intValue(map.get("minLevel"), YamlValues.intValue(map.get("level"), 0));
        long minPoints = YamlValues.longValue(map.get("minPoints"), YamlValues.longValue(map.get("points"), 0L));
        return CraftingRequirementSpec.customXp(minLevel, minPoints, message);
      }
      case "quest" -> {
        String questIdRaw = YamlValues.string(map.get("quest"), YamlValues.string(map.get("questId"),
            YamlValues.string(map.get("id"), null)));
        if (questIdRaw == null || questIdRaw.isBlank()) {
          errors.add(path + ".quest: required");
          return null;
        }
        String statusRaw = YamlValues.string(map.get("status"), "completed");
        CraftingRequirementSpec.QuestStatus status;
        try {
          status = CraftingRequirementSpec.QuestStatus.valueOf(statusRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
          errors.add(path + ".status: invalid quest status");
          return null;
        }
        return CraftingRequirementSpec.quest(normalizeId(questIdRaw, path + ".quest"), status, message);
      }
      case "class" -> {
        List<String> classIds = new ArrayList<>();
        Object classRaw = map.get("class");
        if (classRaw == null) {
          classRaw = map.get("classId");
        }
        if (classRaw != null) {
          String id = YamlValues.string(classRaw, null);
          if (id != null && !id.isBlank()) {
            classIds.add(normalizeId(id, path + ".class"));
          }
        }
        classIds.addAll(readNormalizedIdList(map.get("classes")));
        if (classIds.isEmpty()) {
          errors.add(path + ".class: required");
          return null;
        }
        return CraftingRequirementSpec.classes(classIds, message);
      }
      case "region" -> {
        List<dev.patric.dungeonsreborn.quests.QuestRegion> regions = parseRegions(map, path + ".region", errors);
        if (regions.isEmpty()) {
          errors.add(path + ".region: required");
          return null;
        }
        return CraftingRequirementSpec.region(regions, message);
      }
      default -> {
        errors.add(path + ".type: unknown requirement type " + typeRaw);
        return null;
      }
    }
  }

  private List<dev.patric.dungeonsreborn.quests.QuestRegion> parseRegions(Map<String, Object> map, String path,
      List<String> errors) {
    List<dev.patric.dungeonsreborn.quests.QuestRegion> out = new ArrayList<>();
    Object raw = map.get("regions");
    if (raw == null) {
      raw = map.get("region");
    }
    if (raw == null) {
      return out;
    }
    if (raw instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        Map<String, Object> entry = castMap(list.get(i), path + "[" + i + "]", errors);
        if (entry == null) {
          continue;
        }
        dev.patric.dungeonsreborn.quests.QuestRegion region = parseRegion(entry, path + "[" + i + "]", errors);
        if (region != null) {
          out.add(region);
        }
      }
      return out;
    }
    Map<String, Object> single = castMap(raw, path, errors);
    if (single != null) {
      dev.patric.dungeonsreborn.quests.QuestRegion region = parseRegion(single, path, errors);
      if (region != null) {
        out.add(region);
      }
    }
    return out;
  }

  private dev.patric.dungeonsreborn.quests.QuestRegion parseRegion(Map<String, Object> map, String path,
      List<String> errors) {
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
    return new dev.patric.dungeonsreborn.quests.QuestRegion(world, x, y, z, radius);
  }

  private List<String> readNormalizedIdList(Object raw) {
    List<String> out = new ArrayList<>();
    if (!(raw instanceof List<?> list)) {
      return out;
    }
    for (Object entry : list) {
      String value = YamlValues.string(entry, null);
      if (value != null && !value.isBlank()) {
        out.add(Ids.normalize(value));
      }
    }
    return out;
  }

  private List<CraftingCostSpec> parseCosts(Object raw, String path, List<String> errors) {
    List<CraftingCostSpec> out = new ArrayList<>();
    if (raw == null) {
      return out;
    }
    if (!(raw instanceof List<?> list)) {
      errors.add(path + ": costs must be a list");
      return out;
    }
    for (int i = 0; i < list.size(); i++) {
      String entryPath = path + "[" + i + "]";
      Map<String, Object> map = castMap(list.get(i), entryPath, errors);
      if (map == null) {
        continue;
      }
      CraftingCostSpec cost = parseCost(map, entryPath, errors);
      if (cost != null) {
        out.add(cost);
      }
    }
    return out;
  }

  private CraftingCostSpec parseCost(Map<String, Object> map, String path, List<String> errors) {
    String typeRaw = YamlValues.string(map.get("type"), null);
    String message = YamlValues.string(map.get("message"), null);
    if (typeRaw == null) {
      if (map.containsKey("mana")) {
        typeRaw = "mana";
      } else if (map.containsKey("resource")) {
        typeRaw = "resource";
      } else if (map.containsKey("tokens") || map.containsKey("tokenTier")) {
        typeRaw = "tokens";
      } else if (map.containsKey("durability") || map.containsKey("damage")) {
        typeRaw = "durability";
      } else if (map.containsKey("item") || map.containsKey("material") || map.containsKey("category")
          || map.containsKey("tag") || map.containsKey("upgradeId")) {
        typeRaw = "item";
      }
    }
    if (typeRaw == null) {
      errors.add(path + ".type: required");
      return null;
    }
    switch (typeRaw.toLowerCase(Locale.ROOT)) {
      case "mana" -> {
        double amount = YamlValues.doubleValue(map.get("amount"), YamlValues.doubleValue(map.get("mana"), 0.0));
        return CraftingCostSpec.mana(amount, message);
      }
      case "resource" -> {
        String resourceId = YamlValues.string(map.get("resource"), null);
        double amount = YamlValues.doubleValue(map.get("amount"), 0.0);
        if (resourceId == null || resourceId.isBlank()) {
          errors.add(path + ".resource: required");
          return null;
        }
        return CraftingCostSpec.resource(resourceId, amount, message);
      }
      case "tokens" -> {
        String tokenTier = YamlValues.string(map.get("tokenTier"), YamlValues.string(map.get("tier"), null));
        double amount = YamlValues.doubleValue(map.get("amount"), YamlValues.doubleValue(map.get("tokens"), 0.0));
        return CraftingCostSpec.tokens(tokenTier, amount, message);
      }
      case "durability" -> {
        double amount = YamlValues.doubleValue(map.get("amount"), YamlValues.doubleValue(map.get("damage"), 0.0));
        boolean allowBreak = YamlValues.bool(map.get("allowBreak"), false);
        return CraftingCostSpec.durability(amount, allowBreak, message);
      }
      case "item" -> {
        Object rawItem = map.get("item");
        Map<String, Object> itemMap = rawItem instanceof Map<?, ?> itemRaw ? castMap(itemRaw, path + ".item", errors) : map;
        if (itemMap == null) {
          return null;
        }
        IngredientParseResult parsed = parseIngredient(itemMap, path + ".item", errors);
        if (parsed == null) {
          return null;
        }
        return CraftingCostSpec.item(parsed.spec, message);
      }
      default -> {
        errors.add(path + ".type: unknown cost type " + typeRaw);
        return null;
      }
    }
  }

  private ScriptSpec parseScript(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return ScriptSpec.EMPTY;
    }
    if (raw instanceof String text) {
      return new ScriptSpec(null, text);
    }
    if (raw instanceof Map<?, ?> mapRaw) {
      String file = YamlValues.string(mapRaw.get("file"), null);
      if (file == null) {
        file = YamlValues.string(mapRaw.get("path"), null);
      }
      String inline = YamlValues.string(mapRaw.get("code"), YamlValues.string(mapRaw.get("inline"), null));
      if (file == null && inline == null) {
        errors.add(path + ": script requires file or code");
      }
      return new ScriptSpec(file, inline);
    }
    errors.add(path + ": script must be a string or object");
    return ScriptSpec.EMPTY;
  }

  private CraftingHookSpec parseHooks(Object raw, String path, ScriptSpec fallbackScript, List<String> errors) {
    CraftingHookSpec.Hook pre = null;
    CraftingHookSpec.Hook post = null;
    CraftingHookSpec.Hook preview = null;
    if (raw instanceof Map<?, ?> mapRaw) {
      Map<String, Object> map = castMap(mapRaw, path, errors);
      if (map != null) {
        pre = parseHookEntry(map.get("pre"), path + ".pre", errors);
        post = parseHookEntry(map.get("post"), path + ".post", errors);
        preview = parseHookEntry(map.get("preview"), path + ".preview", errors);
      }
    }
    if (post == null && fallbackScript != null && (fallbackScript.file != null || fallbackScript.inline != null)) {
      post = new CraftingHookSpec.Hook(fallbackScript.file, fallbackScript.inline, List.of(), false, null);
    }
    if (pre == null && post == null && preview == null) {
      return null;
    }
    return new CraftingHookSpec(pre, post, preview);
  }

  private CraftingHookSpec.Hook parseHookEntry(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof String inline) {
      return new CraftingHookSpec.Hook(null, inline, List.of(), false, null);
    }
    Map<String, Object> map = castMap(raw, path, errors);
    if (map == null) {
      return null;
    }
    String file = YamlValues.string(map.get("file"), YamlValues.string(map.get("path"), null));
    String inline = YamlValues.string(map.get("code"), YamlValues.string(map.get("inline"), null));
    List<String> abilities = readNormalizedIdList(map.get("abilities"));
    String ability = YamlValues.string(map.get("ability"), YamlValues.string(map.get("abilityId"), null));
    if (ability != null && !ability.isBlank()) {
      abilities = new ArrayList<>(abilities);
      abilities.add(normalizeId(ability, path + ".ability"));
    }
    boolean deny = YamlValues.bool(map.get("deny"), false);
    String message = YamlValues.string(map.get("message"), null);
    if (file == null && inline == null && abilities.isEmpty() && !deny) {
      return null;
    }
    return new CraftingHookSpec.Hook(file, inline, abilities, deny, message);
  }

  private CraftingItemPredicate parsePredicate(Map<String, Object> map, String path, List<String> errors) {
    List<CraftingItemPredicate> direct = parseDirectPredicates(map, path, errors);
    List<CraftingItemPredicate> anyOf = parsePredicateList(map.get("any_of"), path + ".any_of", errors);
    List<CraftingItemPredicate> allOf = parsePredicateList(map.get("all_of"), path + ".all_of", errors);
    CraftingItemPredicate not = parsePredicateEntry(map.get("not"), path + ".not", errors);
    if (!allOf.isEmpty()) {
      direct.add(CraftingItemPredicates.allOf(allOf));
    }
    if (!anyOf.isEmpty()) {
      direct.add(CraftingItemPredicates.anyOf(anyOf));
    }
    if (not != null) {
      direct.add(CraftingItemPredicates.not(not));
    }
    if (direct.isEmpty()) {
      return null;
    }
    return CraftingItemPredicates.allOf(direct);
  }

  private List<CraftingItemPredicate> parsePredicateList(Object raw, String path, List<String> errors) {
    List<CraftingItemPredicate> out = new ArrayList<>();
    if (!(raw instanceof List<?> list)) {
      return out;
    }
    for (int i = 0; i < list.size(); i++) {
      CraftingItemPredicate predicate = parsePredicateEntry(list.get(i), path + "[" + i + "]", errors);
      if (predicate != null) {
        out.add(predicate);
      }
    }
    return out;
  }

  private CraftingItemPredicate parsePredicateEntry(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    Map<String, Object> map = castMap(raw, path, errors);
    if (map == null) {
      return null;
    }
    CraftingItemPredicate predicate = parsePredicate(map, path, errors);
    if (predicate == null) {
      errors.add(path + ": predicate requires matching fields");
    }
    return predicate;
  }

  private List<CraftingItemPredicate> parseDirectPredicates(Map<String, Object> map, String path, List<String> errors) {
    List<CraftingItemPredicate> out = new ArrayList<>();

    Integer cmdExact = null;
    Integer cmdMin = null;
    Integer cmdMax = null;
    Object cmdRaw = map.get("custom_model_data");
    if (cmdRaw == null) {
      cmdRaw = map.get("customModelData");
    }
    if (cmdRaw == null) {
      cmdRaw = map.get("model_data");
    }
    if (cmdRaw instanceof Map<?, ?> cmdMapRaw) {
      Map<String, Object> cmdMap = castMap(cmdMapRaw, path + ".custom_model_data", errors);
      if (cmdMap != null) {
        cmdExact = cmdMap.containsKey("exact") ? YamlValues.intValue(cmdMap.get("exact"), 0) : null;
        cmdMin = cmdMap.containsKey("min") ? YamlValues.intValue(cmdMap.get("min"), 0) : null;
        cmdMax = cmdMap.containsKey("max") ? YamlValues.intValue(cmdMap.get("max"), 0) : null;
      }
    } else if (cmdRaw != null) {
      cmdExact = YamlValues.intValue(cmdRaw, 0);
    }
    if (cmdExact != null || cmdMin != null || cmdMax != null) {
      out.add(CraftingItemPredicates.customModelData(cmdExact, cmdMin, cmdMax));
    }

    String namePlain = YamlValues.string(map.get("name"), null);
    if (namePlain == null) {
      namePlain = YamlValues.string(map.get("display_name"), null);
    }
    if (namePlain != null) {
      out.add(CraftingItemPredicates.namePlain(namePlain));
    }
    String nameMm = YamlValues.string(map.get("name_mm"), YamlValues.string(map.get("nameMini"), null));
    if (nameMm != null) {
      out.add(CraftingItemPredicates.nameMini(nameMm));
    }

    Object loreContainsRaw = map.get("lore_contains");
    if (loreContainsRaw != null) {
      List<String> needles = readStringList(loreContainsRaw);
      for (String needle : needles) {
        out.add(CraftingItemPredicates.loreContains(needle));
      }
    }
    Object loreExactRaw = map.get("lore_exact");
    if (loreExactRaw != null) {
      List<String> lines = readStringList(loreExactRaw);
      out.add(CraftingItemPredicates.loreExact(lines));
    }

    Object enchRaw = map.containsKey("enchantments") ? map.get("enchantments") : map.get("enchants");
    if (enchRaw != null) {
      Map<Enchantment, Integer> req = parseEnchantments(enchRaw, path + ".enchantments", errors);
      if (!req.isEmpty()) {
        out.add(CraftingItemPredicates.enchantAll(req));
      }
    }
    Object enchAnyRaw = map.containsKey("enchantments_any") ? map.get("enchantments_any") : map.get("enchants_any");
    if (enchAnyRaw != null) {
      Map<Enchantment, Integer> req = parseEnchantments(enchAnyRaw, path + ".enchantments_any", errors);
      if (!req.isEmpty()) {
        out.add(CraftingItemPredicates.enchantAny(req));
      }
    }

    Object attrsRaw = map.get("attributes");
    if (attrsRaw instanceof List<?> list) {
      List<CraftingItemPredicates.AttributeRequirement> reqs = parseAttributes(list, path + ".attributes", errors);
      if (!reqs.isEmpty()) {
        out.add(CraftingItemPredicates.attributes(reqs));
      }
    }

    Object durabilityRaw = map.get("durability");
    if (durabilityRaw instanceof Map<?, ?> durMapRaw) {
      CraftingItemPredicates.DurabilityRequirement req =
          parseDurability(castMap(durMapRaw, path + ".durability", errors));
      if (req != null) {
        out.add(CraftingItemPredicates.durability(req));
      }
    }

    Object potionRaw = map.get("potion");
    if (potionRaw instanceof Map<?, ?> potionMapRaw) {
      CraftingItemPredicates.PotionRequirement req =
          parsePotionRequirement(castMap(potionMapRaw, path + ".potion", errors), path + ".potion", errors);
      if (req != null) {
        out.add(CraftingItemPredicates.potion(req));
      }
    }

    Object pdcRaw = map.containsKey("pdc") ? map.get("pdc") : map.get("nbt");
    if (pdcRaw != null) {
      List<CraftingItemPredicates.PdcRequirement> reqs = parsePdcRequirements(pdcRaw, path + ".pdc", errors);
      if (!reqs.isEmpty()) {
        out.add(CraftingItemPredicates.pdc(reqs));
      }
    }

    return out;
  }

  private Map<Enchantment, Integer> parseEnchantments(Object raw, String path, List<String> errors) {
    Map<Enchantment, Integer> out = new LinkedHashMap<>();
    if (raw instanceof Map<?, ?> mapRaw) {
      for (Map.Entry<?, ?> entry : mapRaw.entrySet()) {
        String key = String.valueOf(entry.getKey());
        Enchantment enchant = parseEnchantment(key);
        if (enchant == null) {
          errors.add(path + ": unknown enchantment=" + key);
          continue;
        }
        int level = YamlValues.intValue(entry.getValue(), 1);
        out.put(enchant, Math.max(1, level));
      }
      return out;
    }
    if (raw instanceof List<?> list) {
      int index = 0;
      for (Object entry : list) {
        String entryPath = path + "[" + index + "]";
        Map<String, Object> map = castMap(entry, entryPath, errors);
        if (map != null) {
          String id = YamlValues.string(map.get("id"), YamlValues.string(map.get("enchant"), null));
          if (id == null) {
            errors.add(entryPath + ": missing enchant id");
          } else {
            Enchantment enchant = parseEnchantment(id);
            if (enchant == null) {
              errors.add(entryPath + ": unknown enchantment=" + id);
            } else {
              int level = YamlValues.intValue(map.get("min"), YamlValues.intValue(map.get("level"), 1));
              out.put(enchant, Math.max(1, level));
            }
          }
        }
        index++;
      }
    }
    return out;
  }

  private List<CraftingItemPredicates.AttributeRequirement> parseAttributes(List<?> list, String path, List<String> errors) {
    List<CraftingItemPredicates.AttributeRequirement> out = new ArrayList<>();
    int index = 0;
    for (Object entry : list) {
      String entryPath = path + "[" + index + "]";
      Map<String, Object> map = castMap(entry, entryPath, errors);
      if (map == null) {
        index++;
        continue;
      }
      String attrRaw = YamlValues.string(map.get("attribute"), null);
      Attribute attribute = parseAttribute(attrRaw);
      if (attribute == null) {
        errors.add(entryPath + ": invalid attribute=" + attrRaw);
        index++;
        continue;
      }
      Double min = map.containsKey("min") ? YamlValues.doubleValue(map.get("min"), 0.0) : null;
      Double max = map.containsKey("max") ? YamlValues.doubleValue(map.get("max"), 0.0) : null;
      String opRaw = YamlValues.string(map.get("operation"), null);
      AttributeModifier.Operation op = opRaw == null ? null : parseAttributeOperation(opRaw);
      EquipmentSlotGroup group = null;
      String slotGroup = YamlValues.string(map.get("slotGroup"), YamlValues.string(map.get("slot_group"), null));
      if (slotGroup != null) {
        group = EquipmentSlotGroup.getByName(slotGroup.toLowerCase(Locale.ROOT));
      }
      String slotRaw = YamlValues.string(map.get("slot"), null);
      if (group == null && slotRaw != null) {
        try {
          EquipmentSlot slot = EquipmentSlot.valueOf(slotRaw.trim().toUpperCase(Locale.ROOT));
          group = slotToGroup(slot);
        } catch (IllegalArgumentException ignored) {
          errors.add(entryPath + ": invalid slot=" + slotRaw);
        }
      }
      out.add(new CraftingItemPredicates.AttributeRequirement(attribute, min, max, op, group));
      index++;
    }
    return out;
  }

  private CraftingItemPredicates.DurabilityRequirement parseDurability(Map<String, Object> map) {
    if (map == null) {
      return null;
    }
    Integer minRemaining = map.containsKey("min_remaining") ? YamlValues.intValue(map.get("min_remaining"), 0) : null;
    Integer maxRemaining = map.containsKey("max_remaining") ? YamlValues.intValue(map.get("max_remaining"), 0) : null;
    Integer minDamage = map.containsKey("min_damage") ? YamlValues.intValue(map.get("min_damage"), 0) : null;
    Integer maxDamage = map.containsKey("max_damage") ? YamlValues.intValue(map.get("max_damage"), 0) : null;
    Double minPercent = map.containsKey("min_percent") ? YamlValues.doubleValue(map.get("min_percent"), 0.0) : null;
    Double maxPercent = map.containsKey("max_percent") ? YamlValues.doubleValue(map.get("max_percent"), 0.0) : null;
    return new CraftingItemPredicates.DurabilityRequirement(minRemaining, maxRemaining, minPercent, maxPercent, minDamage, maxDamage);
  }

  private CraftingItemPredicates.PotionRequirement parsePotionRequirement(Map<String, Object> map, String path,
      List<String> errors) {
    if (map == null) {
      return null;
    }
    PotionType baseType = parsePotionType(YamlValues.string(map.get("type"), null));
    List<CraftingItemPredicates.PotionEffectRequirement> effects = new ArrayList<>();
    Object effectsRaw = map.get("effects");
    if (effectsRaw instanceof List<?> list) {
      int index = 0;
      for (Object entry : list) {
        String entryPath = path + ".effects[" + index + "]";
        Map<String, Object> effectMap = castMap(entry, entryPath, errors);
        if (effectMap == null) {
          index++;
          continue;
        }
        String typeRaw = YamlValues.string(effectMap.get("type"), null);
        PotionEffectType type = parsePotionEffectType(typeRaw);
        if (type == null) {
          errors.add(entryPath + ": invalid effect type=" + typeRaw);
          index++;
          continue;
        }
        Integer minAmplifier = effectMap.containsKey("minAmplifier") ? YamlValues.intValue(effectMap.get("minAmplifier"), 0) : null;
        Integer maxAmplifier = effectMap.containsKey("maxAmplifier") ? YamlValues.intValue(effectMap.get("maxAmplifier"), 0) : null;
        Integer minDuration = effectMap.containsKey("minDuration") ? YamlValues.intValue(effectMap.get("minDuration"), 0) : null;
        Integer maxDuration = effectMap.containsKey("maxDuration") ? YamlValues.intValue(effectMap.get("maxDuration"), 0) : null;
        effects.add(new CraftingItemPredicates.PotionEffectRequirement(type, minAmplifier, maxAmplifier, minDuration, maxDuration));
        index++;
      }
    }
    return new CraftingItemPredicates.PotionRequirement(baseType, effects);
  }

  private List<CraftingItemPredicates.PdcRequirement> parsePdcRequirements(Object raw, String path, List<String> errors) {
    List<CraftingItemPredicates.PdcRequirement> out = new ArrayList<>();
    if (raw instanceof Map<?, ?> mapRaw) {
      for (Map.Entry<?, ?> entry : mapRaw.entrySet()) {
        String keyRaw = String.valueOf(entry.getKey());
        NamespacedKey key = NamespacedKey.fromString(keyRaw);
        if (key == null) {
          errors.add(path + ": invalid key=" + keyRaw);
          continue;
        }
        Object value = entry.getValue();
        CraftingItemPredicates.PdcRequirement req = parsePdcValue(key, value, path + "." + keyRaw, errors);
        if (req != null) {
          out.add(req);
        }
      }
      return out;
    }
    if (raw instanceof List<?> list) {
      int index = 0;
      for (Object entry : list) {
        String entryPath = path + "[" + index + "]";
        Map<String, Object> map = castMap(entry, entryPath, errors);
        if (map == null) {
          index++;
          continue;
        }
        String keyRaw = YamlValues.string(map.get("key"), null);
        NamespacedKey key = keyRaw == null ? null : NamespacedKey.fromString(keyRaw);
        if (key == null) {
          errors.add(entryPath + ": invalid key");
          index++;
          continue;
        }
        String typeRaw = YamlValues.string(map.get("type"), null);
        Object value = map.get("value");
        CraftingItemPredicates.PdcRequirement req = parsePdcValue(key, typeRaw, value, entryPath, errors);
        if (req != null) {
          out.add(req);
        }
        index++;
      }
    }
    return out;
  }

  private CraftingItemPredicates.PdcRequirement parsePdcValue(NamespacedKey key, Object value, String path,
      List<String> errors) {
    if (value instanceof Map<?, ?> mapRaw) {
      Map<String, Object> map = castMap(mapRaw, path, errors);
      if (map == null) {
        return null;
      }
      String typeRaw = YamlValues.string(map.get("type"), null);
      Object rawValue = map.get("value");
      return parsePdcValue(key, typeRaw, rawValue, path, errors);
    }
    return parsePdcValue(key, null, value, path, errors);
  }

  private CraftingItemPredicates.PdcRequirement parsePdcValue(NamespacedKey key, String typeRaw, Object value,
      String path, List<String> errors) {
    PersistentDataType<?, ?> type = parsePdcType(typeRaw, value);
    if (type == null) {
      errors.add(path + ": unsupported pdc type");
      return null;
    }
    Object converted = convertPdcValue(type, value);
    return new CraftingItemPredicates.PdcRequirement(key, type, converted);
  }

  private PersistentDataType<?, ?> parsePdcType(String raw, Object value) {
    if (raw != null) {
      String normalized = raw.trim().toLowerCase(Locale.ROOT);
      return switch (normalized) {
        case "string" -> PersistentDataType.STRING;
        case "int", "integer" -> PersistentDataType.INTEGER;
        case "long" -> PersistentDataType.LONG;
        case "double" -> PersistentDataType.DOUBLE;
        case "float" -> PersistentDataType.FLOAT;
        case "byte" -> PersistentDataType.BYTE;
        case "short" -> PersistentDataType.SHORT;
        case "byte_array" -> PersistentDataType.BYTE_ARRAY;
        case "int_array" -> PersistentDataType.INTEGER_ARRAY;
        case "long_array" -> PersistentDataType.LONG_ARRAY;
        case "boolean" -> PersistentDataType.BYTE;
        default -> null;
      };
    }
    if (value instanceof Integer) {
      return PersistentDataType.INTEGER;
    }
    if (value instanceof Long) {
      return PersistentDataType.LONG;
    }
    if (value instanceof Double) {
      return PersistentDataType.DOUBLE;
    }
    if (value instanceof Float) {
      return PersistentDataType.FLOAT;
    }
    if (value instanceof Byte) {
      return PersistentDataType.BYTE;
    }
    if (value instanceof Short) {
      return PersistentDataType.SHORT;
    }
    if (value instanceof String) {
      return PersistentDataType.STRING;
    }
    if (value instanceof byte[]) {
      return PersistentDataType.BYTE_ARRAY;
    }
    if (value instanceof int[]) {
      return PersistentDataType.INTEGER_ARRAY;
    }
    if (value instanceof long[]) {
      return PersistentDataType.LONG_ARRAY;
    }
    if (value instanceof Boolean) {
      return PersistentDataType.BYTE;
    }
    return null;
  }

  private Object convertPdcValue(PersistentDataType<?, ?> type, Object value) {
    if (type == PersistentDataType.STRING) {
      return value == null ? "" : String.valueOf(value);
    }
    if (type == PersistentDataType.INTEGER) {
      return YamlValues.intValue(value, 0);
    }
    if (type == PersistentDataType.LONG) {
      return YamlValues.longValue(value, 0L);
    }
    if (type == PersistentDataType.DOUBLE) {
      return YamlValues.doubleValue(value, 0.0);
    }
    if (type == PersistentDataType.FLOAT) {
      return (float) YamlValues.doubleValue(value, 0.0);
    }
    if (type == PersistentDataType.BYTE) {
      if (value instanceof Boolean bool) {
        return (byte) (bool ? 1 : 0);
      }
      return (byte) YamlValues.intValue(value, 0);
    }
    if (type == PersistentDataType.SHORT) {
      return (short) YamlValues.intValue(value, 0);
    }
    if (type == PersistentDataType.BYTE_ARRAY && value instanceof byte[] bytes) {
      return bytes;
    }
    if (type == PersistentDataType.INTEGER_ARRAY && value instanceof int[] ints) {
      return ints;
    }
    if (type == PersistentDataType.LONG_ARRAY && value instanceof long[] longs) {
      return longs;
    }
    return value;
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
      return null;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    return switch (normalized) {
      case "ADD", "ADD_NUMBER" -> AttributeModifier.Operation.ADD_NUMBER;
      case "ADD_SCALAR", "ADD_MULTIPLIER", "ADD_MULT" -> AttributeModifier.Operation.ADD_SCALAR;
      case "MULTIPLY_SCALAR_1", "MULTIPLY" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
      default -> null;
    };
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

  private static Map<String, Object> castMap(Object raw, String path, List<String> errors) {
    if (!(raw instanceof Map<?, ?> mapRaw)) {
      errors.add(path + ": expected object");
      return null;
    }
    Map<String, Object> map = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : mapRaw.entrySet()) {
      map.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return map;
  }

  private static String normalizeId(String raw, String path) {
    try {
      return Ids.normalize(raw);
    } catch (Exception ex) {
      throw new IllegalArgumentException(path + ": invalid id (" + ex.getMessage() + ")");
    }
  }

  private static ItemStack materialStack(Object raw, int amount) {
    if (raw == null) {
      return null;
    }
    String materialRaw = String.valueOf(raw);
    if (materialRaw.isBlank()) {
      return null;
    }
    Material material = Material.matchMaterial(materialRaw);
    if (material == null) {
      return null;
    }
    ItemStack stack = new ItemStack(material);
    stack.setAmount(Math.max(1, amount));
    return stack;
  }

  private static List<String> readStringList(Object raw) {
    if (raw == null) {
      return List.of();
    }
    if (raw instanceof List<?> list) {
      List<String> out = new ArrayList<>();
      for (Object entry : list) {
        if (entry == null) {
          continue;
        }
        String value = String.valueOf(entry);
        if (!value.isBlank()) {
          out.add(value);
        }
      }
      return out;
    }
    String value = String.valueOf(raw);
    return value.isBlank() ? List.of() : List.of(value);
  }

  private record ScriptSpec(String file, String inline) {
    private static final ScriptSpec EMPTY = new ScriptSpec(null, null);
  }

  private CraftingDiscoverySpec parseDiscovery(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return CraftingDiscoverySpec.empty();
    }
    Map<String, Object> map = raw instanceof ConfigurationSection section
        ? section.getValues(false)
        : castMap(raw, path, errors);
    if (map == null) {
      return CraftingDiscoverySpec.empty();
    }
    boolean hidden = YamlValues.bool(map.get("hidden"), false);
    List<String> requires = readStringList(map.get("requires"));
    List<String> grants = readStringList(map.get("grants"));
    boolean unlockOnCraft = YamlValues.bool(map.get("unlockOnCraft"), false);
    boolean showInBook = YamlValues.bool(map.get("showInBook"), YamlValues.bool(map.get("showInRecipeBook"), false));
    int researchSeconds = YamlValues.intValue(map.get("researchSeconds"), 0);

    List<String> questUnlocks = List.of();
    List<String> dropItemIds = List.of();
    List<String> dropMaterials = List.of();
    Object unlockRaw = map.get("unlock");
    if (unlockRaw != null) {
      Map<String, Object> unlock = unlockRaw instanceof ConfigurationSection section
          ? section.getValues(false)
          : castMap(unlockRaw, path + ".unlock", errors);
      if (unlock != null) {
        questUnlocks = readStringList(unlock.get("quests"));
        Object dropRaw = unlock.get("drops");
        if (dropRaw != null) {
          Map<String, Object> drops = dropRaw instanceof ConfigurationSection section
              ? section.getValues(false)
              : castMap(dropRaw, path + ".unlock.drops", errors);
          if (drops != null) {
            dropItemIds = readStringList(drops.get("itemIds"));
            dropMaterials = readStringList(drops.get("materials"));
          }
        }
      }
    }
    if (researchSeconds < 0) {
      errors.add(path + ".researchSeconds: must be >= 0");
      researchSeconds = 0;
    }
    return new CraftingDiscoverySpec(
        hidden,
        requires,
        grants,
        questUnlocks,
        dropItemIds,
        dropMaterials,
        unlockOnCraft,
        showInBook,
        researchSeconds);
  }
}
