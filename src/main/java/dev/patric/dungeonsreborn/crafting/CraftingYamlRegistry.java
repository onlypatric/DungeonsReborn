package dev.patric.dungeonsreborn.crafting;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.logging.ServiceLogger;
import dev.patric.dungeonsreborn.system.SystemStatusStore;
import dev.patric.dungeonsreborn.util.YamlValues;

public final class CraftingYamlRegistry {
  public record ReloadResult(int loaded, List<String> errors) {
  }

  private final JavaPlugin plugin;
  private final ServiceLogger logger;
  private final Function<String, ItemStack> itemResolver;
  private final Map<String, CraftingRecipeTemplate> recipes = new LinkedHashMap<>();

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

    List<CraftingRecipeVariant> variants = parseVariants(cfg, base, errors);
    List<CraftingOutputSpec> outputs = parseOutputs(cfg, base, errors);
    if (outputs.isEmpty()) {
      errors.add(base + ": output is missing or invalid");
      return null;
    }
    if (variants.isEmpty()) {
      errors.add(base + ": inputs/variants must not be empty");
      return null;
    }
    return new CraftingRecipeSpec(id, name, description, permissions, cooldownSeconds, variants, outputs,
        scriptSpec.file, scriptSpec.inline);
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
        List<CraftingIngredientSpec> inputs = parseInputs(map.get("inputs"), path + ".inputs", errors);
        if (!inputs.isEmpty()) {
          out.add(new CraftingRecipeVariant(inputs));
        }
      }
      return out;
    }
    List<CraftingIngredientSpec> inputs = parseInputs(cfg.get("inputs"), base + ".inputs", errors);
    if (!inputs.isEmpty()) {
      out.add(new CraftingRecipeVariant(inputs));
    }
    return out;
  }

  private List<CraftingIngredientSpec> parseInputs(Object raw, String path, List<String> errors) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<CraftingIngredientSpec> out = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      String entryPath = path + "[" + i + "]";
      Map<String, Object> map = castMap(list.get(i), entryPath, errors);
      if (map == null) {
        continue;
      }
      CraftingIngredientSpec spec = parseIngredient(map, entryPath, errors);
      if (spec != null) {
        out.add(spec);
      }
    }
    return out;
  }

  private CraftingIngredientSpec parseIngredient(Map<String, Object> map, String path, List<String> errors) {
    String typeRaw = YamlValues.string(map.get("type"), null);
    String itemIdRaw = YamlValues.string(map.get("item"), YamlValues.string(map.get("itemId"), YamlValues.string(map.get("id"), null)));
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
      if (itemIdRaw != null) {
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
    return new CraftingIngredientSpec(type, itemId, tag, material, category, amount);
  }

  private CraftingOutputSpec parseOutput(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof ItemStack stack) {
      ItemStack copy = stack.clone();
      return new CraftingOutputSpec(null, copy, copy.getAmount());
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
    String itemIdRaw = YamlValues.string(map.get("itemId"), YamlValues.string(map.get("item"), null));
    Object itemRaw = map.get("itemStack");
    if (itemRaw == null) {
      itemRaw = map.get("stack");
    }
    int amount = YamlValues.intValue(map.get("amount"), 1);
    if (itemRaw instanceof ItemStack stack) {
      ItemStack copy = stack.clone();
      copy.setAmount(Math.max(1, amount));
      return new CraftingOutputSpec(null, copy, copy.getAmount());
    }
    ItemStack built = materialStack(map.get("material"), amount);
    if (built != null) {
      return new CraftingOutputSpec(null, built, built.getAmount());
    }
    if (itemIdRaw != null) {
      String normalized = normalizeId(itemIdRaw, path + ".itemId");
      return new CraftingOutputSpec(normalized, null, Math.max(1, amount));
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
      item.setAmount(output.amount());
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
    resolved.setAmount(output.amount());
    return resolved;
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

  private record ScriptSpec(String file, String inline) {
    private static final ScriptSpec EMPTY = new ScriptSpec(null, null);
  }
}
