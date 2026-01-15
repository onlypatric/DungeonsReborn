package dev.patric.dungeonsreborn.shops;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.logging.ServiceLogger;
import dev.patric.dungeonsreborn.system.SystemStatusStore;
import dev.patric.dungeonsreborn.util.YamlValues;


public final class ShopYamlRegistry {
  public record ReloadResult(int loaded, List<String> errors) {
  }

  private final JavaPlugin plugin;
  private final ServiceLogger logger;
  private final Function<String, ItemStack> itemResolver;
  private final Map<String, ShopSpec> shops = new LinkedHashMap<>();
  private ShopTokenSpec tokenSpec = new ShopTokenSpec(null, ShopTokenSpec.DEFAULT_MARKER);
  private Map<String, ShopTokenTierSpec> tokenTiers = new LinkedHashMap<>();
  private ShopValueTable valueTable = ShopValueTable.empty();
  private List<String> lastErrors = List.of();

  public ShopYamlRegistry(JavaPlugin plugin, ServiceLogger logger, Function<String, ItemStack> itemResolver) {
    this.plugin = plugin;
    this.logger = logger;
    this.itemResolver = itemResolver;
  }

  public File file() {
    return new File(plugin.getDataFolder(), "shops.yml");
  }

  public ShopTokenSpec tokenSpec() {
    return tokenSpec;
  }

  public Map<String, ShopTokenTierSpec> tokenTiers() {
    return Map.copyOf(tokenTiers);
  }

  public ShopTokenTierSpec tokenTier(String id) {
    if (id == null) {
      return null;
    }
    String normalized;
    try {
      normalized = Ids.normalize(id);
    } catch (IllegalArgumentException ex) {
      return null;
    }
    ShopTokenTierSpec tier = tokenTiers.get(normalized);
    if (tier != null) {
      return tier;
    }
    if (normalized.startsWith("token_")) {
      tier = tokenTiers.get(normalized.substring("token_".length()));
      if (tier != null) {
        return tier;
      }
    } else {
      tier = tokenTiers.get("token_" + normalized);
      if (tier != null) {
        return tier;
      }
    }
    return null;
  }

  public ItemStack resolveTokenItem(String id) {
    if (id == null) {
      return null;
    }
    String normalized;
    try {
      normalized = Ids.normalize(id);
    } catch (IllegalArgumentException ex) {
      return null;
    }
    if ("token".equals(normalized)) {
      return tokenSpec == null || tokenSpec.item() == null ? null : tokenSpec.item().clone();
    }
    ShopTokenTierSpec tier = tokenTier(normalized);
    return tier == null || tier.item() == null ? null : tier.item().clone();
  }

  public Function<String, ItemStack> itemResolver() {
    return itemResolver;
  }

  public ShopValueTable valueTable() {
    return valueTable;
  }

  public Map<String, ShopSpec> shops() {
    return Map.copyOf(shops);
  }

  public ShopSpec shop(String id) {
    if (id == null) {
      return null;
    }
    return shops.get(Ids.normalize(id));
  }

  public List<String> lastErrors() {
    return lastErrors;
  }

  public ReloadResult reload() {
    ensureFile();
    List<String> errors = new ArrayList<>();
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file());
    ShopTokenSpec nextToken = parseToken(cfg, errors);
    Map<String, ShopTokenTierSpec> nextTiers = parseTokenTiers(cfg, nextToken, errors);
    ShopValueTable nextValues = parseValues(cfg, errors);
    Map<String, ShopSpec> nextShops = parseShops(cfg, errors);
    if (errors.isEmpty()) {
      tokenSpec = nextToken;
      tokenTiers = nextTiers;
      valueTable = nextValues;
      shops.clear();
      shops.putAll(nextShops);
    }
    lastErrors = List.copyOf(errors);
    if (!errors.isEmpty()) {
      logger.warn("[Shops] YAML reload had " + errors.size() + " errors");
      for (String error : errors) {
        logger.warn("[Shops] YAML: " + error);
      }
      notifyAdmins(errors);
    } else {
      logger.info("[Shops] YAML loaded " + shops.size() + " shops");
    }
    SystemStatusStore.get().record(
        "shops",
        "Shops",
        file().getPath(),
        "shops=" + (errors.isEmpty() ? nextShops.size() : shops.size()),
        errors);
    return new ReloadResult(errors.isEmpty() ? nextShops.size() : shops.size(), errors);
  }

  private void notifyAdmins(List<String> errors) {
    if (errors == null || errors.isEmpty()) {
      return;
    }
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (!player.hasPermission("dungeonsreborn.shop.admin")) {
        continue;
      }
      player.sendMessage("§c[Shops] Reload had " + errors.size() + " errors. Check console for details.");
    }
  }

  private void ensureFile() {
    File file = file();
    if (file.exists()) {
      return;
    }
    plugin.saveResource("shops.yml", false);
  }

  private ShopTokenSpec parseToken(YamlConfiguration cfg, List<String> errors) {
    ConfigurationSection token = cfg.getConfigurationSection("token");
    NamespacedKey markerKey = ShopTokenSpec.DEFAULT_MARKER;
    ItemStack item = null;
    if (token != null) {
      String markerRaw = YamlValues.string(token, "markerKey", markerKey.asString());
      NamespacedKey parsed = NamespacedKey.fromString(markerRaw);
      if (parsed == null) {
        errors.add("token.markerKey: invalid namespaced key");
      } else {
        markerKey = parsed;
      }
      item = token.getItemStack("item");
      if (item == null) {
        String materialRaw = YamlValues.string(token, "material", null);
        if (materialRaw != null) {
          Material material = parseMaterial(materialRaw, "token.material", errors);
          if (material != null) {
            item = new ItemStack(material, 1);
          }
        }
      }
      if (item != null) {
        applyTokenMeta(item, token);
      }
    }
    if (item == null) {
      item = defaultToken();
    }
    ItemMarkers.set(item, markerKey, true);
    return new ShopTokenSpec(item, markerKey);
  }

  private ItemStack defaultToken() {
    ItemStack item = new ItemStack(Material.SUNFLOWER, 1);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.displayName(GuiMini.mm("<gold><bold>Token</bold></gold>"));
      meta.lore(GuiMini.loreMm(List.of(
          "<yellow>Official trade currency</yellow>",
          "<gray>Value:</gray> <white>1 Token</white>",
          "<gray>64 Tokens</gray> <white>= 1 Compressed</white>",
          "<gray>64 Compressed</gray> <white>= 1 Pallet</white>"
      )));
      item.setItemMeta(meta);
    }
    return item;
  }

  private void applyTokenMeta(ItemStack item, ConfigurationSection token) {
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return;
    }
    String name = token.getString("name");
    if (name != null && !name.isBlank()) {
      meta.displayName(GuiMini.mm(name));
    }
    List<String> loreRaw = token.getStringList("lore");
    if (!loreRaw.isEmpty()) {
      meta.lore(GuiMini.loreMm(loreRaw));
    }
    item.setItemMeta(meta);
  }

  private Map<String, ShopTokenTierSpec> parseTokenTiers(YamlConfiguration cfg, ShopTokenSpec baseToken, List<String> errors) {
    Map<String, ShopTokenTierSpec> tiers = new LinkedHashMap<>();
    ConfigurationSection tiersSec = cfg.getConfigurationSection("tokenTiers");
    if (tiersSec != null) {
      for (String rawId : tiersSec.getKeys(false)) {
        String base = "tokenTiers." + rawId;
        ConfigurationSection node = tiersSec.getConfigurationSection(rawId);
        if (node == null) {
          errors.add(base + ": must be an object");
          continue;
        }
        String id = normalizeTierId(rawId, base + ".id", errors);
        if (id == null) {
          continue;
        }
        if (tiers.containsKey(id)) {
          errors.add(base + ": duplicate tier id=" + id);
          continue;
        }
        ItemStack fallback = defaultTierItem(id, baseToken);
        ShopTokenTierSpec spec = parseTokenTier(id, node, baseToken, fallback, base, errors);
        if (spec != null) {
          tiers.put(id, spec);
        }
      }
    }
    ensureTier(tiers, "compressed", baseToken, defaultCompressedToken(baseToken));
    ensureTier(tiers, "pallet", baseToken, defaultPalletToken(baseToken));
    return tiers;
  }

  private ShopTokenTierSpec parseTokenTier(String id, ConfigurationSection node, ShopTokenSpec baseToken,
                                           ItemStack fallback, String base, List<String> errors) {
    NamespacedKey defaultMarker = defaultTokenTierMarker(id);
    String markerRaw = YamlValues.string(node, "markerKey", defaultMarker.asString());
    NamespacedKey markerKey = NamespacedKey.fromString(markerRaw);
    if (markerKey == null) {
      errors.add(base + ".markerKey: invalid namespaced key");
      markerKey = defaultMarker;
    }
    ItemStack item = node.getItemStack("item");
    if (item == null) {
      String materialRaw = YamlValues.string(node, "material", null);
      if (materialRaw != null) {
        Material material = parseMaterial(materialRaw, base + ".material", errors);
        if (material != null) {
          item = new ItemStack(material, 1);
        }
      }
    }
    if (item == null && fallback != null) {
      item = fallback.clone();
    }
    if (item == null) {
      errors.add(base + ": missing item/material");
      return null;
    }
    stripBaseTokenMarker(item, baseToken);
    applyTokenMeta(item, node);
    item.setAmount(1);
    ItemMarkers.set(item, markerKey, true);
    return new ShopTokenTierSpec(id, item, markerKey);
  }

  private void ensureTier(Map<String, ShopTokenTierSpec> tiers, String id,
                          ShopTokenSpec baseToken, ItemStack fallback) {
    if (hasTier(tiers, id)) {
      return;
    }
    ItemStack item = fallback == null ? null : fallback.clone();
    if (item == null) {
      return;
    }
    stripBaseTokenMarker(item, baseToken);
    NamespacedKey markerKey = defaultTokenTierMarker(id);
    ItemMarkers.set(item, markerKey, true);
    tiers.put(id, new ShopTokenTierSpec(id, item, markerKey));
  }

  private boolean hasTier(Map<String, ShopTokenTierSpec> tiers, String id) {
    if (tiers.containsKey(id) || tiers.containsKey("token_" + id)) {
      return true;
    }
    return tiers.containsKey(id.startsWith("token_") ? id.substring("token_".length()) : id);
  }

  private String normalizeTierId(String raw, String path, List<String> errors) {
    if (raw == null) {
      errors.add(path + ": missing id");
      return null;
    }
    try {
      return Ids.normalize(raw);
    } catch (IllegalArgumentException ex) {
      errors.add(path + ": " + ex.getMessage());
      return null;
    }
  }

  private NamespacedKey defaultTokenTierMarker(String id) {
    String normalized = Ids.normalize(id);
    if (normalized.startsWith("token_")) {
      normalized = normalized.substring("token_".length());
    }
    return new NamespacedKey("dungeonsreborn", "shop_token_" + normalized);
  }

  private ItemStack defaultTierItem(String id, ShopTokenSpec baseToken) {
    if (id == null) {
      return null;
    }
    String normalized = Ids.normalize(id);
    if ("compressed".equals(normalized) || "token_compressed".equals(normalized)) {
      return defaultCompressedToken(baseToken);
    }
    if ("pallet".equals(normalized) || "token_pallet".equals(normalized)) {
      return defaultPalletToken(baseToken);
    }
    return null;
  }

  private ItemStack defaultCompressedToken(ShopTokenSpec baseToken) {
    ItemStack item = baseTierItem(baseToken);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.displayName(GuiMini.mm("<yellow><bold>Compressed Token</bold></yellow>"));
      meta.lore(GuiMini.loreMm(List.of(
          "<gray>Value:</gray> <white>64 Tokens</white>",
          "<gray>64 Compressed</gray> <white>= 1 Pallet</white>"
      )));
      item.setItemMeta(meta);
    }
    return item;
  }

  private ItemStack defaultPalletToken(ShopTokenSpec baseToken) {
    ItemStack item = baseTierItem(baseToken);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.displayName(GuiMini.mm("<gold><bold>Token Pallet</bold></gold>"));
      meta.lore(GuiMini.loreMm(List.of(
          "<gray>Value:</gray> <white>4096 Tokens</white>",
          "<gray>64 Compressed</gray> <white>= 1 Pallet</white>"
      )));
      item.setItemMeta(meta);
    }
    return item;
  }

  private ItemStack baseTierItem(ShopTokenSpec baseToken) {
    ItemStack item = baseToken != null && baseToken.item() != null ? baseToken.item().clone() : null;
    if (item == null) {
      item = new ItemStack(Material.SUNFLOWER, 1);
    }
    item.setAmount(1);
    return item;
  }

  private void stripBaseTokenMarker(ItemStack item, ShopTokenSpec baseToken) {
    if (item == null || baseToken == null || baseToken.markerKey() == null) {
      return;
    }
    ItemMarkers.set(item, baseToken.markerKey(), false);
  }

  private ShopValueTable parseValues(YamlConfiguration cfg, List<String> errors) {
    List<Map<?, ?>> valuesRaw = cfg.getMapList("values");
    if (valuesRaw == null || valuesRaw.isEmpty()) {
      return ShopValueTable.empty();
    }
    List<ShopValueSpec> values = new ArrayList<>();
    for (int i = 0; i < valuesRaw.size(); i++) {
      Map<?, ?> entry = valuesRaw.get(i);
      String base = "values[" + i + "]";
      if (entry == null) {
        errors.add(base + ": must be an object");
        continue;
      }
      try {
        ShopIngredientSpec ingredient = parseIngredient(entry, base + ".ingredient", errors, false);
        int value = YamlValues.intValue(entry.get("value"), 0);
        if (value <= 0) {
          errors.add(base + ".value: must be > 0");
          continue;
        }
        values.add(new ShopValueSpec(ingredient, value));
      } catch (Exception ex) {
        errors.add(base + ": " + ex.getMessage());
      }
    }
    return ShopValueTable.of(values);
  }

  private Map<String, ShopSpec> parseShops(YamlConfiguration cfg, List<String> errors) {
    ConfigurationSection shopsSec = cfg.getConfigurationSection("shops");
    if (shopsSec == null) {
      return Map.of();
    }
    Map<String, ShopSpec> out = new LinkedHashMap<>();
    for (String rawId : shopsSec.getKeys(false)) {
      String base = "shops." + rawId;
      ConfigurationSection node = shopsSec.getConfigurationSection(rawId);
      if (node == null) {
        errors.add(base + ": must be an object");
        continue;
      }
      try {
        String id = Ids.normalize(rawId);
        String title = YamlValues.string(node, "title", id);
        boolean enabled = node.getBoolean("enabled", true);
        String permission = YamlValues.string(node, "permission", null);
        double cooldownSeconds = node.getDouble("cooldownSeconds", node.getDouble("cooldown", 0.0));
        long cooldownTicks = Math.round(Math.max(0.0, cooldownSeconds) * 20.0);
        List<String> worldsRaw = node.getStringList("worlds");
        Set<String> worlds = worldsRaw.isEmpty() ? Set.of() : Set.copyOf(worldsRaw);
        ShopIngredientSpec icon = parseIngredient(node.get("icon"), base + ".icon", errors, false);
        ShopStockSpec stock = parseStock(node.getConfigurationSection("stock"), base + ".stock", errors);
        List<Map<?, ?>> tradesRaw = node.getMapList("trades");
        List<ShopTradeSpec> trades = new ArrayList<>();
        for (int i = 0; i < tradesRaw.size(); i++) {
          Map<?, ?> tradeMap = tradesRaw.get(i);
          String tradePath = base + ".trades[" + i + "]";
          trades.add(parseTrade(tradeMap, tradePath, errors));
        }
        out.put(id, new ShopSpec(id, title, enabled, icon, permission, cooldownTicks, worlds, stock,
            List.copyOf(trades)));
      } catch (Exception ex) {
        errors.add(base + ": " + ex.getMessage());
      }
    }
    return out;
  }

  private ShopStockSpec parseStock(ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return null;
    }
    try {
      int min = section.getInt("min", section.getInt("minStock", 0));
      int max = section.getInt("max", section.getInt("maxStock", 0));
      long restockSeconds = Math.max(0L, Math.round(section.getDouble("restockSeconds", 3600.0)));
      return new ShopStockSpec(min, max, restockSeconds);
    } catch (Exception ex) {
      errors.add(path + ": " + ex.getMessage());
      return null;
    }
  }

  private ShopTradeSpec parseTrade(Map<?, ?> trade, String path, List<String> errors) {
    if (trade == null) {
      throw new IllegalArgumentException(path + ": must be an object");
    }
    ShopIngredientSpec buyA = parseIngredient(trade.get("buyA"), path + ".buyA", errors, true);
    ShopIngredientSpec buyB = parseIngredient(trade.get("buyB"), path + ".buyB", errors, true);
    ShopIngredientSpec sell = parseIngredient(trade.get("sell"), path + ".sell", errors, true);
    if (buyA == null) {
      throw new IllegalArgumentException(path + ".buyA: missing ingredient");
    }
    if (sell == null) {
      throw new IllegalArgumentException(path + ".sell: missing ingredient");
    }
    int maxUses = YamlValues.intValue(trade.get("maxUses"), 0);
    boolean experienceReward = YamlValues.bool(trade.get("experienceReward"), false);
    float priceMultiplier = (float) YamlValues.doubleValue(trade.get("priceMultiplier"), 0.0);
    List<String> previewLore = parsePreviewLore(trade.get("previewLore"), path + ".previewLore", errors);
    ShopDynamicPriceSpec dynamicPrice = parseDynamicPrice(trade.get("dynamicPrice"), path + ".dynamicPrice", errors);
    return new ShopTradeSpec(buyA, buyB, sell, maxUses, experienceReward, priceMultiplier, previewLore, dynamicPrice);
  }

  private List<String> parsePreviewLore(Object raw, String path, List<String> errors) {
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

  private ShopDynamicPriceSpec parseDynamicPrice(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof ConfigurationSection sec) {
      raw = sec.getValues(false);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      errors.add(path + ": must be an object");
      return null;
    }
    try {
      String modeRaw = YamlValues.string(map, "mode", "stock");
      ShopDynamicPriceMode mode = ShopDynamicPriceMode.parse(modeRaw, path + ".mode");
      double min = YamlValues.doubleValue(map.get("minMultiplier"), YamlValues.doubleValue(map.get("min"), 0.0));
      double max = YamlValues.doubleValue(map.get("maxMultiplier"), YamlValues.doubleValue(map.get("max"), min));
      long periodSeconds = YamlValues.longValue(map.get("periodSeconds"), 3600L);
      return new ShopDynamicPriceSpec(mode, min, max, periodSeconds);
    } catch (Exception ex) {
      errors.add(path + ": " + ex.getMessage());
      return null;
    }
  }

  private ShopIngredientSpec parseIngredient(Object raw, String path, List<String> errors, boolean allowToken) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof ItemStack stack) {
      return new ShopIngredientSpec(ShopIngredientType.ITEMSTACK, null, null, stack, stack.getAmount());
    }
    if (raw instanceof ConfigurationSection sec) {
      raw = sec.getValues(false);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException(path + ": must be an object");
    }
    String typeRaw = YamlValues.string(map, "type", null);
    if (typeRaw == null) {
      if (map.containsKey("token")) {
        typeRaw = "token";
      } else if (map.containsKey("itemId") || map.containsKey("id")) {
        typeRaw = "itemId";
      } else if (map.containsKey("material")) {
        typeRaw = "material";
      } else if (map.containsKey("item")) {
        typeRaw = "item";
      }
    }
    ShopIngredientType type = ShopIngredientType.parse(typeRaw, path + ".type");
    if (type == ShopIngredientType.TOKEN && !allowToken) {
      throw new IllegalArgumentException(path + ": token not allowed here");
    }
    int amount = YamlValues.intValue(map.get("amount"), YamlValues.intValue(map.get("count"), 1));
    return switch (type) {
      case TOKEN -> new ShopIngredientSpec(type, null, null, null, amount);
      case ITEM_ID -> {
        String itemId = YamlValues.string(map, "itemId", YamlValues.string(map, "id", null));
        if (itemId != null && !itemId.isBlank()) {
          itemId = Ids.normalize(itemId);
        }
        yield new ShopIngredientSpec(type, itemId, null, null, amount);
      }
      case MATERIAL -> {
        String materialRaw = YamlValues.string(map, "material", null);
        Material material = parseMaterial(materialRaw, path + ".material", errors);
        yield new ShopIngredientSpec(type, null, material, null, amount);
      }
      case ITEMSTACK -> {
        Object itemRaw = map.get("item");
        ItemStack item = parseItem(itemRaw, path + ".item", errors);
        yield new ShopIngredientSpec(type, null, null, item, amount);
      }
    };
  }

  private ItemStack parseItem(Object raw, String path, List<String> errors) {
    if (raw == null) {
      errors.add(path + ": missing item");
      return null;
    }
    if (raw instanceof ItemStack stack) {
      return stack.clone();
    }
    if (raw instanceof ConfigurationSection sec) {
      return parseItem(sec.getValues(false), path, errors);
    }
    if (raw instanceof Map<?, ?> map) {
      Map<String, Object> copy = new LinkedHashMap<>();
      for (var entry : map.entrySet()) {
        if (entry.getKey() == null) {
          continue;
        }
        copy.put(String.valueOf(entry.getKey()), entry.getValue());
      }
      try {
        return ItemStack.deserialize(copy);
      } catch (Exception ex) {
        errors.add(path + ": invalid item stack (" + ex.getMessage() + ")");
        return null;
      }
    }
    errors.add(path + ": invalid item stack");
    return null;
  }

  private Material parseMaterial(String raw, String path, List<String> errors) {
    if (raw == null || raw.isBlank()) {
      errors.add(path + ": missing material");
      return null;
    }
    Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
    if (material == null) {
      errors.add(path + ": invalid material " + raw);
    }
    return material;
  }
}
