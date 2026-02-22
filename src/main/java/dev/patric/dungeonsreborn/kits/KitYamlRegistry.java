package dev.patric.dungeonsreborn.kits;

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
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.system.SystemStatusStore;
import dev.patric.dungeonsreborn.util.PluginResources;
import dev.patric.dungeonsreborn.util.YamlValues;

public final class KitYamlRegistry {
  public record ReloadResult(int loaded, List<String> errors) {
  }

  private final JavaPlugin plugin;
  private final Logger logger;
  private final Map<String, KitSpec> kits = new LinkedHashMap<>();
  private List<String> lastErrors = List.of();

  public KitYamlRegistry(JavaPlugin plugin, Logger logger) {
    this.plugin = plugin;
    this.logger = logger;
  }

  public File file() {
    return new File(plugin.getDataFolder(), "kits.yml");
  }

  public Map<String, KitSpec> kits() {
    return Map.copyOf(kits);
  }

  public KitSpec kit(String id) {
    if (id == null) {
      return null;
    }
    return kits.get(Ids.normalize(id));
  }

  public List<String> lastErrors() {
    return lastErrors;
  }

  public ReloadResult reload() {
    ensureFile();
    List<String> errors = new ArrayList<>();
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file());
    Map<String, KitSpec> next = parseKits(cfg, errors);
    if (errors.isEmpty()) {
      kits.clear();
      kits.putAll(next);
    }
    lastErrors = List.copyOf(errors);
    if (!errors.isEmpty()) {
      logger.warning("[Kits] YAML reload had " + errors.size() + " errors");
      for (String error : errors) {
        logger.warning("[Kits] YAML: " + error);
      }
    } else {
      logger.info("[Kits] YAML loaded " + next.size() + " kits");
    }
    SystemStatusStore.get().record(
        "kits",
        "Kits",
        file().getPath(),
        "kits=" + (errors.isEmpty() ? next.size() : kits.size()),
        errors);
    return new ReloadResult(errors.isEmpty() ? next.size() : kits.size(), errors);
  }

  private void ensureFile() {
    PluginResources.ensureYamlFile(plugin, file(), "kits.yml", cfg -> cfg.createSection("kits"), logger, "Kits");
  }

  private Map<String, KitSpec> parseKits(YamlConfiguration cfg, List<String> errors) {
    ConfigurationSection kitsSec = cfg.getConfigurationSection("kits");
    if (kitsSec == null) {
      return Map.of();
    }
    Map<String, KitSpec> out = new LinkedHashMap<>();
    for (String rawId : kitsSec.getKeys(false)) {
      String base = "kits." + rawId;
      ConfigurationSection node = kitsSec.getConfigurationSection(rawId);
      if (node == null) {
        errors.add(base + ": must be an object");
        continue;
      }
      try {
        String id = Ids.normalize(rawId);
        String title = YamlValues.string(node, "title", id);
        String permission = YamlValues.string(node, "permission", null);
        boolean oneTime = node.getBoolean("oneTime", true);
        long cooldownSeconds = Math.max(0L, node.getLong("cooldownSeconds", 0L));
        List<Map<?, ?>> itemsRaw = node.getMapList("items");
        List<KitItemSpec> items = new ArrayList<>();
        for (int i = 0; i < itemsRaw.size(); i++) {
          Map<?, ?> itemMap = itemsRaw.get(i);
          String itemPath = base + ".items[" + i + "]";
          try {
            items.add(parseItem(itemMap, itemPath, errors));
          } catch (Exception ex) {
            errors.add(itemPath + ": " + ex.getMessage());
          }
        }
        KitRewards rewards = parseRewards(node.getConfigurationSection("rewards"), base + ".rewards", errors);
        out.put(id, new KitSpec(id, title, permission, oneTime, cooldownSeconds, items, rewards));
      } catch (Exception ex) {
        errors.add(base + ": " + ex.getMessage());
      }
    }
    return out;
  }

  private KitRewards parseRewards(ConfigurationSection section, String base, List<String> errors) {
    if (section == null) {
      return KitRewards.none();
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
    return new KitRewards(xp, tokens, compressed, pallet);
  }

  private KitItemSpec parseItem(Map<?, ?> map, String path, List<String> errors) {
    if (map == null) {
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
    KitItemType type = KitItemType.parse(typeRaw, path + ".type");
    int amount = YamlValues.intValue(map.get("amount"), YamlValues.intValue(map.get("count"), 1));
    return switch (type) {
      case ITEM_ID -> {
        String itemId = YamlValues.string(map, "itemId", YamlValues.string(map, "id", null));
        if (itemId != null && !itemId.isBlank()) {
          itemId = Ids.normalize(itemId);
        }
        yield new KitItemSpec(type, itemId, null, null, amount);
      }
      case MATERIAL -> {
        String materialRaw = YamlValues.string(map, "material", null);
        Material material = parseMaterial(materialRaw, path + ".material", errors);
        yield new KitItemSpec(type, null, material, null, amount);
      }
      case ITEMSTACK -> {
        Object itemRaw = map.get("item");
        ItemStack item = parseItemStack(itemRaw, path + ".item", errors);
        yield new KitItemSpec(type, null, null, item, amount);
      }
    };
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
        meta.displayName(GuiMini.mm(name));
      }
      List<String> loreRaw = listOf(map.get("lore"), path + ".lore", errors);
      if (!loreRaw.isEmpty()) {
        meta.lore(GuiMini.loreMm(loreRaw));
      }
      stack.setItemMeta(meta);
    }
    return stack;
  }

  private List<String> listOf(Object raw, String path, List<String> errors) {
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
      return List.copyOf(out);
    }
    if (raw instanceof String text) {
      if (text.isBlank()) {
        return List.of();
      }
      return List.of(text);
    }
    errors.add(path + ": must be a string list");
    return List.of();
  }

  private Material parseMaterial(String raw, String path, List<String> errors) {
    if (raw == null || raw.isBlank()) {
      errors.add(path + ": material is required");
      return null;
    }
    String key = raw.trim().toUpperCase(Locale.ROOT);
    Material material = Material.matchMaterial(key);
    if (material == null) {
      errors.add(path + ": invalid material " + raw);
      return null;
    }
    return material;
  }
}
