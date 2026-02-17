package dev.patric.dungeonsreborn.shops;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.meta.SkullMeta;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.integration.ItemMatcher;
import dev.patric.dungeonsreborn.effects.integration.ItemMatchers;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.effects.items.HeadRegistry;
import net.kyori.adventure.text.Component;
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
  private Map<String, ShopCurrencySpec> currencies = new LinkedHashMap<>();
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

  public File shopsDir() {
    return new File(plugin.getDataFolder(), "shops");
  }

  public ShopTokenSpec tokenSpec() {
    return tokenSpec;
  }

  public Map<String, ShopTokenTierSpec> tokenTiers() {
    return Map.copyOf(tokenTiers);
  }

  public Map<String, ShopCurrencySpec> currencies() {
    return Map.copyOf(currencies);
  }

  public ShopCurrencySpec currency(String id) {
    if (id == null) {
      return null;
    }
    String normalized;
    try {
      normalized = Ids.normalize(id);
    } catch (IllegalArgumentException ex) {
      return null;
    }
    ShopCurrencySpec spec = currencies.get(normalized);
    if (spec != null) {
      return spec;
    }
    return currencies.get("currency_" + normalized);
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
    Map<String, ShopCurrencySpec> nextCurrencies = parseCurrencies(cfg, errors);
    ShopValueTable nextValues = parseValues(cfg, errors);
    Map<String, ShopCurrencySpec> previousCurrencies = currencies;
    currencies = nextCurrencies;
    Map<String, ShopSpec> nextShops = new LinkedHashMap<>(parseShops(cfg, errors));
    File[] files = shopsDir().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml")
        || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
    if (files != null) {
      java.util.Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
      for (File shopFile : files) {
        YamlConfiguration shopCfg = YamlConfiguration.loadConfiguration(shopFile);
        mergeShops(nextShops, parseShops(shopCfg, errors), errors, shopFile.getPath());
      }
    }
    if (errors.isEmpty()) {
      tokenSpec = nextToken;
      tokenTiers = nextTiers;
      valueTable = nextValues;
      shops.clear();
      shops.putAll(nextShops);
    } else {
      currencies = previousCurrencies;
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
    Component name = GuiMini.mm("<gold><bold>Token</bold></gold>");
    List<Component> lore = GuiMini.loreMm(List.of(
        "<yellow>Official trade currency</yellow>",
        "<gray>Value:</gray> <white>1 Token</white>",
        "<gray>64 Tokens</gray> <white>= 1 Compressed</white>",
        "<gray>64 Compressed</gray> <white>= 1 Pallet</white>"
    ));
    return headOrFallback("TOKEN", name, lore, Material.SUNFLOWER);
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

  private Map<String, ShopCurrencySpec> parseCurrencies(YamlConfiguration cfg, List<String> errors) {
    Map<String, ShopCurrencySpec> out = new LinkedHashMap<>();
    ConfigurationSection currenciesSec = cfg.getConfigurationSection("currencies");
    if (currenciesSec == null) {
      return out;
    }
    for (String rawId : currenciesSec.getKeys(false)) {
      String base = "currencies." + rawId;
      ConfigurationSection node = currenciesSec.getConfigurationSection(rawId);
      if (node == null) {
        errors.add(base + ": must be an object");
        continue;
      }
      String id;
      try {
        id = Ids.normalize(rawId);
      } catch (IllegalArgumentException ex) {
        errors.add(base + ": invalid currency id");
        continue;
      }
      NamespacedKey markerKey = NamespacedKey.fromString(
          YamlValues.string(node, "markerKey", "dungeonsreborn:shop_currency_" + id));
      if (markerKey == null) {
        errors.add(base + ".markerKey: invalid namespaced key");
        continue;
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
      if (item == null) {
        errors.add(base + ": missing item/material");
        continue;
      }
      applyCurrencyMeta(item, node);
      ItemMarkers.set(item, markerKey, true);
      out.put(id, new ShopCurrencySpec(id, item, markerKey));
    }
    return out;
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
    ItemStack item = headItem("COMPRESSED_TOKEN");
    if (item == null) {
      item = baseTierItem(baseToken);
    }
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
    ItemStack item = headItem("TOKEN_PALLET");
    if (item == null) {
      item = baseTierItem(baseToken);
    }
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
      item = headItem("TOKEN");
      if (item == null) {
        item = new ItemStack(Material.SUNFLOWER, 1);
      }
    }
    item.setAmount(1);
    return item;
  }

  private ItemStack headOrFallback(String headId, Component name, List<Component> lore, Material fallback) {
    ItemStack item = headItem(headId);
    if (item == null) {
      item = new ItemStack(fallback, 1);
    }
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      if (name != null) {
        meta.displayName(name);
      }
      if (lore != null && !lore.isEmpty()) {
        meta.lore(lore);
      }
      item.setItemMeta(meta);
    }
    return item;
  }

  private ItemStack headItem(String headId) {
    HeadRegistry registry = GuiItems.headRegistry();
    HeadRegistry.HeadSpec spec = registry != null ? registry.head(headId) : null;
    if (spec == null) {
      return null;
    }
    ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
    ItemMeta meta = item.getItemMeta();
    if (meta instanceof SkullMeta skull) {
      HeadRegistry.applyTo(skull, spec, null);
      item.setItemMeta(skull);
    } else if (meta != null) {
      item.setItemMeta(meta);
    }
    return item;
  }

  private void stripBaseTokenMarker(ItemStack item, ShopTokenSpec baseToken) {
    if (item == null || baseToken == null || baseToken.markerKey() == null) {
      return;
    }
    ItemMarkers.set(item, baseToken.markerKey(), false);
  }

  private void applyCurrencyMeta(ItemStack item, ConfigurationSection node) {
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return;
    }
    String name = node.getString("name");
    if (name != null && !name.isBlank()) {
      meta.displayName(GuiMini.mm(name));
    }
    List<String> loreRaw = node.getStringList("lore");
    if (!loreRaw.isEmpty()) {
      meta.lore(GuiMini.loreMm(loreRaw));
    }
    item.setItemMeta(meta);
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
        List<ShopRequirementSpec> requirements = parseRequirements(node.get("requirements"),
            base + ".requirements", errors);
        List<ShopRequirementSpec> visibility = parseRequirements(node.get("visibility"),
            base + ".visibility", errors);
        Object availabilityRaw = node.get("availability");
        if (availabilityRaw == null) {
          availabilityRaw = node.get("timeWindows");
        }
        ShopAvailabilitySpec availability = parseAvailability(availabilityRaw, base + ".availability", errors);
        ShopStockSpec stock = parseStock(node.get("stock"), base + ".stock", errors);
        ConfigurationSection pricing = node.getConfigurationSection("pricing");
        if (pricing == null) {
          pricing = node.getConfigurationSection("price");
        }
        double taxRate = parseTaxRate(pricing == null ? node : pricing, base + ".pricing.taxRate", errors);
        Map<String, Double> worldMultipliers = parseWorldMultipliers(
            pricing == null ? node.get("worldMultipliers") : pricing.get("worldMultipliers"),
            base + ".pricing.worldMultipliers", errors);
        List<ShopRegionPriceSpec> regionPrices = parseRegionPrices(
            pricing == null ? node.get("regions") : pricing.get("regions"),
            base + ".pricing.regions", errors);
        ShopPriceModifiers priceModifiers = parsePriceModifiers(node.get("priceModifiers"),
            base + ".priceModifiers", errors);
        List<Map<?, ?>> tradesRaw = node.getMapList("trades");
        List<ShopTradeSpec> trades = new ArrayList<>();
        for (int i = 0; i < tradesRaw.size(); i++) {
          Map<?, ?> tradeMap = tradesRaw.get(i);
          String tradePath = base + ".trades[" + i + "]";
          trades.add(parseTrade(tradeMap, tradePath, errors));
        }
        out.put(id, new ShopSpec(id, title, enabled, icon, permission, requirements, visibility, availability,
            cooldownTicks, worlds, stock, taxRate,
            worldMultipliers, regionPrices, priceModifiers, List.copyOf(trades)));
      } catch (Exception ex) {
        errors.add(base + ": " + ex.getMessage());
      }
    }
    return out;
  }

  private void mergeShops(Map<String, ShopSpec> target, Map<String, ShopSpec> incoming, List<String> errors,
      String source) {
    for (Map.Entry<String, ShopSpec> entry : incoming.entrySet()) {
      if (target.containsKey(entry.getKey())) {
        errors.add(source + ": duplicate shop id=" + entry.getKey());
        continue;
      }
      target.put(entry.getKey(), entry.getValue());
    }
  }

  private void ensureFile() {
    File file = file();
    if (!file.exists()) {
      plugin.saveResource("shops.yml", false);
    }
    File dir = shopsDir();
    if (!dir.exists()) {
      dir.mkdirs();
    }
    copyBundledShops(dir);
  }

  private void copyBundledShops(File dir) {
    List<String> entries = readResourceIndex("shops/index.txt");
    for (String entry : entries) {
      String trimmed = entry.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      if (!trimmed.endsWith(".yml") && !trimmed.endsWith(".yaml")) {
        continue;
      }
      String resourcePath = "shops/" + trimmed;
      File target = new File(dir, trimmed);
      if (target.exists()) {
        continue;
      }
      if (plugin.getResource(resourcePath) == null) {
        logger.warn("[Shops] Missing bundled shop: " + resourcePath + " (skipping copy)");
        continue;
      }
      plugin.saveResource(resourcePath, false);
    }
  }

  private List<String> readResourceIndex(String path) {
    try (InputStream stream = plugin.getResource(path)) {
      if (stream == null) {
        return List.of();
      }
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
          lines.add(line);
        }
        return lines;
      }
    } catch (Exception ex) {
      logger.warn("[Shops] Unable to read " + path + ": " + ex.getMessage());
      return List.of();
    }
  }

  private ShopStockSpec parseStock(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof ConfigurationSection section) {
      raw = section.getValues(false);
    }
    if (!(raw instanceof Map<?, ?> section)) {
      errors.add(path + ": must be an object");
      return null;
    }
    try {
      int min = YamlValues.intValue(section.get("min"), YamlValues.intValue(section.get("minStock"), 0));
      int max = YamlValues.intValue(section.get("max"), YamlValues.intValue(section.get("maxStock"), 0));
      long restockSeconds = Math.max(0L, Math.round(YamlValues.doubleValue(section.get("restockSeconds"), 3600.0)));
      String scopeRaw = YamlValues.string(section, "scope", "global");
      ShopStockScope scope = ShopStockScope.parse(scopeRaw, path + ".scope");
      return new ShopStockSpec(min, max, restockSeconds, scope);
    } catch (Exception ex) {
      errors.add(path + ": " + ex.getMessage());
      return null;
    }
  }

  private ShopTradeSpec parseTrade(Map<?, ?> trade, String path, List<String> errors) {
    if (trade == null) {
      throw new IllegalArgumentException(path + ": must be an object");
    }
    List<ShopIngredientSpec> buys = parseIngredientList(trade.get("buy"), path + ".buy", errors);
    if (buys.isEmpty()) {
      buys = parseIngredientList(trade.get("buys"), path + ".buys", errors);
    }
    if (buys.isEmpty()) {
      buys = parseIngredientList(trade.get("inputs"), path + ".inputs", errors);
    }
    if (buys.isEmpty()) {
      ShopIngredientSpec buyA = parseIngredient(trade.get("buyA"), path + ".buyA", errors, true);
      ShopIngredientSpec buyB = parseIngredient(trade.get("buyB"), path + ".buyB", errors, true);
      if (buyA != null) {
        List<ShopIngredientSpec> legacy = new ArrayList<>();
        legacy.add(buyA);
        if (buyB != null) {
          legacy.add(buyB);
        }
        buys = legacy;
      }
    }
    List<ShopIngredientSpec> sells = parseIngredientList(trade.get("sell"), path + ".sell", errors);
    if (sells.isEmpty()) {
      sells = parseIngredientList(trade.get("sells"), path + ".sells", errors);
    }
    if (sells.isEmpty()) {
      sells = parseIngredientList(trade.get("outputs"), path + ".outputs", errors);
    }
    if (sells.isEmpty()) {
      ShopIngredientSpec sell = parseIngredient(trade.get("sell"), path + ".sell", errors, true);
      if (sell != null) {
        sells = List.of(sell);
      }
    }
    if (buys.isEmpty()) {
      throw new IllegalArgumentException(path + ".buy: missing ingredient");
    }
    if (sells.isEmpty()) {
      throw new IllegalArgumentException(path + ".sell: missing ingredient");
    }
    int maxUses = YamlValues.intValue(trade.get("maxUses"), 0);
    int minLevel = Math.max(0, YamlValues.intValue(trade.get("minLevel"), 0));
    List<ShopRequirementSpec> requirements = parseRequirements(trade.get("requirements"),
        path + ".requirements", errors);
    List<ShopRequirementSpec> visibility = parseRequirements(trade.get("visibility"),
        path + ".visibility", errors);
    Object availabilityRaw = trade.get("availability");
    if (availabilityRaw == null) {
      availabilityRaw = trade.get("timeWindows");
    }
    ShopAvailabilitySpec availability = parseAvailability(availabilityRaw, path + ".availability", errors);
    boolean experienceReward = YamlValues.bool(trade.get("experienceReward"), false);
    float priceMultiplier = (float) YamlValues.doubleValue(trade.get("priceMultiplier"), 0.0);
    List<String> previewLore = parsePreviewLore(trade.get("previewLore"), path + ".previewLore", errors);
    ShopDynamicPriceSpec dynamicPrice = parseDynamicPrice(trade.get("dynamicPrice"), path + ".dynamicPrice", errors);
    ShopPriceModifiers priceModifiers = parsePriceModifiers(trade.get("priceModifiers"), path + ".priceModifiers", errors);
    ShopStockSpec stock = parseStock(trade.get("stock"), path + ".stock", errors);
    boolean buyback = YamlValues.bool(trade.get("buyback"), false);
    return new ShopTradeSpec(buys, sells, maxUses, minLevel, requirements, visibility, availability, experienceReward,
        priceMultiplier, previewLore, dynamicPrice, priceModifiers, stock, buyback);
  }

  private List<ShopRequirementSpec> parseRequirements(Object raw, String path, List<String> errors) {
    List<ShopRequirementSpec> out = new ArrayList<>();
    if (raw == null) {
      return out;
    }
    if (raw instanceof String permission) {
      if (!permission.isBlank()) {
        out.add(ShopRequirementSpec.permission(permission, null));
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
          out.add(ShopRequirementSpec.permission(permission, null));
        }
        continue;
      }
      Map<String, Object> map = castMap(entry, entryPath, errors);
      if (map == null) {
        continue;
      }
      ShopRequirementSpec req = parseRequirement(map, entryPath, errors);
      if (req != null) {
        out.add(req);
      }
    }
    return out;
  }

  private ShopRequirementSpec parseRequirement(Map<String, Object> map, String path, List<String> errors) {
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
      } else if (map.containsKey("faction") || map.containsKey("factionId") || map.containsKey("rank")) {
        typeRaw = "faction";
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
        return ShopRequirementSpec.permission(permission, message);
      }
      case "level" -> {
        int minLevel = YamlValues.intValue(map.get("min"), YamlValues.intValue(map.get("minLevel"),
            YamlValues.intValue(map.get("level"), 0)));
        return ShopRequirementSpec.level(minLevel, message);
      }
      case "custom_xp", "customxp" -> {
        int minLevel = YamlValues.intValue(map.get("minLevel"), YamlValues.intValue(map.get("level"), 0));
        long minPoints = YamlValues.longValue(map.get("minPoints"), YamlValues.longValue(map.get("points"), 0L));
        return ShopRequirementSpec.customXp(minLevel, minPoints, message);
      }
      case "quest" -> {
        String questIdRaw = YamlValues.string(map.get("quest"), YamlValues.string(map.get("questId"),
            YamlValues.string(map.get("id"), null)));
        if (questIdRaw == null || questIdRaw.isBlank()) {
          errors.add(path + ".quest: required");
          return null;
        }
        String statusRaw = YamlValues.string(map.get("status"), "completed");
        ShopRequirementSpec.QuestStatus status;
        try {
          status = ShopRequirementSpec.QuestStatus.valueOf(statusRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
          errors.add(path + ".status: invalid quest status");
          return null;
        }
        return ShopRequirementSpec.quest(Ids.normalize(questIdRaw), status, message);
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
            classIds.add(Ids.normalize(id));
          }
        }
        classIds.addAll(readNormalizedIdList(map.get("classes")));
        if (classIds.isEmpty()) {
          errors.add(path + ".class: required");
          return null;
        }
        return ShopRequirementSpec.classes(classIds, message);
      }
      case "region" -> {
        List<dev.patric.dungeonsreborn.quests.QuestRegion> regions = parseRegions(map, path + ".region", errors);
        if (regions.isEmpty()) {
          errors.add(path + ".region: required");
          return null;
        }
        return ShopRequirementSpec.region(regions, message);
      }
      case "faction" -> {
        String factionId = YamlValues.string(map.get("faction"), YamlValues.string(map.get("factionId"),
            YamlValues.string(map.get("id"), null)));
        int rank = YamlValues.intValue(map.get("rank"), YamlValues.intValue(map.get("minRank"), 0));
        if (factionId == null || factionId.isBlank()) {
          errors.add(path + ".faction: required");
          return null;
        }
        return ShopRequirementSpec.faction(Ids.normalize(factionId), rank, message);
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
    java.util.Set<DayOfWeek> days = new java.util.LinkedHashSet<>();
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

  private double parseTaxRate(ConfigurationSection section, String path, List<String> errors) {
    if (section == null) {
      return 0.0;
    }
    try {
      double rate = section.getDouble("taxRate", section.getDouble("tax", 0.0));
      if (section.contains("taxPercent")) {
        rate = section.getDouble("taxPercent", 0.0) / 100.0;
      }
      if (rate < 0.0) {
        rate = 0.0;
      }
      return rate;
    } catch (Exception ex) {
      errors.add(path + ": " + ex.getMessage());
      return 0.0;
    }
  }

  private Map<String, Double> parseWorldMultipliers(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return Map.of();
    }
    if (raw instanceof ConfigurationSection section) {
      raw = section.getValues(false);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      errors.add(path + ": must be an object");
      return Map.of();
    }
    Map<String, Double> out = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (entry.getKey() == null) {
        continue;
      }
      String key = String.valueOf(entry.getKey()).trim();
      if (key.isBlank()) {
        continue;
      }
      out.put(key, YamlValues.doubleValue(entry.getValue(), 1.0));
    }
    return Map.copyOf(out);
  }

  private List<ShopRegionPriceSpec> parseRegionPrices(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return List.of();
    }
    if (raw instanceof ConfigurationSection section) {
      raw = section.getValues(false);
    }
    if (!(raw instanceof List<?> list)) {
      errors.add(path + ": must be a list");
      return List.of();
    }
    List<ShopRegionPriceSpec> out = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      Object entry = list.get(i);
      String entryPath = path + "[" + i + "]";
      if (entry instanceof ConfigurationSection section) {
        entry = section.getValues(false);
      }
      if (!(entry instanceof Map<?, ?> map)) {
        errors.add(entryPath + ": must be an object");
        continue;
      }
      String world = YamlValues.string(map, "world", "");
      double x = YamlValues.doubleValue(map.get("x"), 0.0);
      double y = YamlValues.doubleValue(map.get("y"), 0.0);
      double z = YamlValues.doubleValue(map.get("z"), 0.0);
      double radius = YamlValues.doubleValue(map.get("radius"), YamlValues.doubleValue(map.get("r"), 0.0));
      if (world == null || world.isBlank()) {
        errors.add(entryPath + ": world is required");
        continue;
      }
      if (radius <= 0.0) {
        errors.add(entryPath + ": radius must be > 0");
        continue;
      }
      double multiplier = YamlValues.doubleValue(map.get("multiplier"), 1.0);
      double taxRate = YamlValues.doubleValue(map.get("taxRate"), YamlValues.doubleValue(map.get("tax"), 0.0));
      if (map.containsKey("taxPercent")) {
        taxRate = YamlValues.doubleValue(map.get("taxPercent"), 0.0) / 100.0;
      }
      try {
        out.add(new ShopRegionPriceSpec(new dev.patric.dungeonsreborn.quests.QuestRegion(world, x, y, z, radius),
            multiplier, taxRate));
      } catch (Exception ex) {
        errors.add(entryPath + ": " + ex.getMessage());
      }
    }
    return List.copyOf(out);
  }

  private List<ShopIngredientSpec> parseIngredientList(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return List.of();
    }
    List<ShopIngredientSpec> out = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        Object entry = list.get(i);
        ShopIngredientSpec spec = parseIngredient(entry, path + "[" + i + "]", errors, true);
        if (spec != null) {
          out.add(spec);
        }
      }
      return List.copyOf(out);
    }
    ShopIngredientSpec single = parseIngredient(raw, path, errors, true);
    if (single != null) {
      out.add(single);
    }
    return List.copyOf(out);
  }

  private ShopPriceModifiers parsePriceModifiers(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return ShopPriceModifiers.empty();
    }
    if (raw instanceof ConfigurationSection section) {
      raw = section.getValues(false);
    }
    if (!(raw instanceof Map<?, ?> mapRaw)) {
      errors.add(path + ": must be an object");
      return ShopPriceModifiers.empty();
    }
    Map<String, Object> map = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : mapRaw.entrySet()) {
      map.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    Map<String, Double> tier = parseMultiplierMap(map.get("tier"), path + ".tier", errors);
    if (tier.isEmpty()) {
      tier = parseMultiplierMap(map.get("tiers"), path + ".tiers", errors);
    }
    Map<String, Double> rarity = parseMultiplierMap(map.get("rarity"), path + ".rarity", errors);
    if (rarity.isEmpty()) {
      rarity = parseMultiplierMap(map.get("rarities"), path + ".rarities", errors);
    }
    double defaultTier = YamlValues.doubleValue(map.get("defaultTierMultiplier"), 1.0);
    double defaultRarity = YamlValues.doubleValue(map.get("defaultRarityMultiplier"), 1.0);
    return new ShopPriceModifiers(Map.copyOf(tier), Map.copyOf(rarity), defaultTier, defaultRarity);
  }

  private Map<String, Double> parseMultiplierMap(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return Map.of();
    }
    if (raw instanceof ConfigurationSection section) {
      raw = section.getValues(false);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      errors.add(path + ": must be an object");
      return Map.of();
    }
    Map<String, Double> out = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String key = entry.getKey() == null ? null : String.valueOf(entry.getKey());
      if (key == null || key.isBlank()) {
        continue;
      }
      String normalized;
      try {
        normalized = Ids.normalize(key);
      } catch (Exception ex) {
        errors.add(path + ": invalid id " + key);
        continue;
      }
      double value = YamlValues.doubleValue(entry.getValue(), 1.0);
      out.put(normalized, value);
    }
    return out;
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
      return new ShopIngredientSpec(ShopIngredientType.ITEMSTACK, null, null, stack, stack.getAmount(), null, null, null, null);
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
      } else if (map.containsKey("upgradeId") || map.containsKey("upgrade")) {
        typeRaw = "itemId";
      } else if (map.containsKey("itemId") || map.containsKey("id")) {
        typeRaw = "itemId";
      } else if (map.containsKey("material")) {
        typeRaw = "material";
      } else if (map.containsKey("tag") || map.containsKey("tags")) {
        typeRaw = "tag";
      } else if (map.containsKey("category")) {
        typeRaw = "category";
      } else if (map.containsKey("currency") || map.containsKey("currencyId") || map.containsKey("currency_id")) {
        typeRaw = "currency";
      } else if (map.containsKey("xp") || map.containsKey("experience")) {
        typeRaw = "xp";
      } else if (map.containsKey("customXp") || map.containsKey("custom_xp") || map.containsKey("custom-xp")) {
        typeRaw = "custom_xp";
      } else if (map.containsKey("item")) {
        typeRaw = "item";
      } else if (map.containsKey("matcher") || map.containsKey("matchers")) {
        typeRaw = "matcher";
      }
    }
    ShopIngredientType type = ShopIngredientType.parse(typeRaw, path + ".type");
    if (type == ShopIngredientType.TOKEN && !allowToken) {
      throw new IllegalArgumentException(path + ": token not allowed here");
    }
    int amount = YamlValues.intValue(map.get("amount"),
        YamlValues.intValue(map.get("count"),
            YamlValues.intValue(map.get("xp"), 1)));
    String label = YamlValues.string(map, "label", null);
    String tag = YamlValues.string(map, "tag", YamlValues.string(map, "tags", null));
    String category = YamlValues.string(map, "category", null);
    return switch (type) {
      case TOKEN -> new ShopIngredientSpec(type, null, null, null, amount, null, null, null, label);
      case ITEM_ID -> {
        String itemId = YamlValues.string(map, "upgradeId",
            YamlValues.string(map, "upgrade",
                YamlValues.string(map, "itemId", YamlValues.string(map, "id", null))));
        if (itemId != null && !itemId.isBlank()) {
          itemId = Ids.normalize(itemId);
        }
        yield new ShopIngredientSpec(type, itemId, null, null, amount, null, null, null, label);
      }
      case MATERIAL -> {
        String materialRaw = YamlValues.string(map, "material", null);
        Material material = parseMaterial(materialRaw, path + ".material", errors);
        yield new ShopIngredientSpec(type, null, material, null, amount, null, null, null, label);
      }
      case ITEMSTACK -> {
        Object itemRaw = map.get("item");
        ItemStack item = parseItem(itemRaw, path + ".item", errors);
        yield new ShopIngredientSpec(type, null, null, item, amount, null, null, null, label);
      }
      case TAG -> {
        if (tag != null && !tag.isBlank()) {
          tag = tag.trim();
        }
        yield new ShopIngredientSpec(type, null, null, null, amount, tag, null, null, label);
      }
      case CATEGORY -> {
        if (category != null && !category.isBlank()) {
          category = Ids.normalize(category);
        }
        yield new ShopIngredientSpec(type, null, null, null, amount, null, category, null, label);
      }
      case CURRENCY -> {
        String currencyId = YamlValues.string(map, "currency",
            YamlValues.string(map, "currencyId", YamlValues.string(map, "currency_id", null)));
        if (currencyId != null && !currencyId.isBlank()) {
          currencyId = Ids.normalize(currencyId);
        }
        ShopCurrencySpec currency = currency(currencyId);
        if (currency == null) {
          errors.add(path + ".currency: unknown currency " + currencyId);
          yield new ShopIngredientSpec(type, currencyId, null, null, amount, null, null, ItemMatchers.anyNonAir(), label);
        }
        ItemStack preview = currency.item();
        ItemMatcher matcher = (player, stack) -> currency.matches(stack);
        yield new ShopIngredientSpec(type, currencyId, null, preview, amount, null, null, matcher,
            label == null || label.isBlank() ? currencyId : label);
      }
      case XP, CUSTOM_XP -> {
        Object iconRaw = map.containsKey("icon") ? map.get("icon") : map.get("item");
        ItemStack icon = iconRaw == null ? null : parseItem(iconRaw, path + ".icon", errors);
        if (icon == null) {
          icon = new ItemStack(type == ShopIngredientType.XP ? Material.EXPERIENCE_BOTTLE : Material.LAPIS_LAZULI, 1);
        }
        String fallbackLabel = type == ShopIngredientType.XP ? "XP" : "Custom XP";
        yield new ShopIngredientSpec(type, null, null, icon, amount, null, null, null,
            label == null || label.isBlank() ? fallbackLabel : label);
      }
      case MATCHER -> {
        Object matcherRaw = map.get("matcher");
        if (matcherRaw == null && map.containsKey("matchers")) {
          matcherRaw = map;
        }
        ItemMatcher matcher = parseItemMatcher(matcherRaw, path + ".matcher", errors);
        Object itemRaw = map.get("item");
        ItemStack preview = itemRaw == null ? null : parseItem(itemRaw, path + ".item", errors);
        String itemId = YamlValues.string(map, "itemId", YamlValues.string(map, "id", null));
        if (itemId != null && !itemId.isBlank()) {
          itemId = Ids.normalize(itemId);
        }
        String materialRaw = YamlValues.string(map, "material", null);
        Material material = parseMaterial(materialRaw, path + ".material", errors);
        yield new ShopIngredientSpec(type, itemId, material, preview, amount, null, null, matcher, label);
      }
    };
  }

  private ItemMatcher parseItemMatcher(Object raw, String path, List<String> errors) {
    if (raw == null) {
      errors.add(path + ": missing matcher");
      return ItemMatchers.anyNonAir();
    }
    if (raw instanceof ConfigurationSection sec) {
      raw = sec.getValues(false);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      errors.add(path + ": must be an object");
      return ItemMatchers.anyNonAir();
    }
    String typeRaw = YamlValues.string(map, "type", null);
    if (typeRaw == null) {
      if (map.containsKey("material")) {
        typeRaw = "material";
      } else if (map.containsKey("itemId") || map.containsKey("id")) {
        typeRaw = "item_id";
      } else if (map.containsKey("tag")) {
        typeRaw = "tag";
      } else if (map.containsKey("category")) {
        typeRaw = "category";
      } else if (map.containsKey("lore") || map.containsKey("text")) {
        typeRaw = "lore_contains";
      } else if (map.containsKey("matchers")) {
        typeRaw = "all";
      }
    }
    String type = typeRaw == null ? "any" : typeRaw.trim().toLowerCase(Locale.ROOT);
    try {
      return switch (type) {
        case "any", "any_non_air", "any-non-air", "any_nonair" -> ItemMatchers.anyNonAir();
        case "material", "mat" -> {
          String materialRaw = YamlValues.string(map, "material", null);
          Material material = parseMaterial(materialRaw, path + ".material", errors);
          yield material == null ? ItemMatchers.anyNonAir() : ItemMatchers.material(material);
        }
        case "itemid", "item_id", "id" -> {
          String itemId = YamlValues.string(map, "itemId", YamlValues.string(map, "id", null));
          if (itemId == null || itemId.isBlank()) {
            errors.add(path + ".itemId: required for matcher type item_id");
            yield ItemMatchers.anyNonAir();
          }
          yield ItemMatchers.itemId(Ids.normalize(itemId));
        }
        case "item", "similar" -> {
          Object itemRaw = map.get("item");
          ItemStack item = parseItem(itemRaw, path + ".item", errors);
          yield item == null ? ItemMatchers.anyNonAir() : ItemMatchers.similar(item);
        }
        case "custom_model_data", "custom-model-data", "cmd" -> {
          Object cmdRaw = map.containsKey("value") ? map.get("value") : map.get("cmd");
          int cmd = YamlValues.intValue(cmdRaw, 0);
          yield ItemMatchers.customModelData(cmd);
        }
        case "lore_contains", "lore-contains", "lore" -> {
          String text = YamlValues.string(map, "text", null);
          if (text == null) {
            errors.add(path + ".text: required for matcher type lore_contains");
            yield ItemMatchers.anyNonAir();
          }
          yield ItemMatchers.loreContains(text);
        }
        case "tag", "tags" -> {
          String tag = YamlValues.string(map, "tag", YamlValues.string(map, "tags", null));
          if (tag == null || tag.isBlank()) {
            errors.add(path + ".tag: required for matcher type tag");
            yield ItemMatchers.anyNonAir();
          }
          yield ItemMatchers.itemTag(tag.trim());
        }
        case "category", "cat" -> {
          String category = YamlValues.string(map, "category", null);
          if (category == null || category.isBlank()) {
            errors.add(path + ".category: required for matcher type category");
            yield ItemMatchers.anyNonAir();
          }
          yield ItemMatchers.itemCategory(Ids.normalize(category));
        }
        case "pdc" -> parsePdcMatcher(map, path, errors);
        case "durability" -> {
          Integer min = map.containsKey("min") ? YamlValues.intValue(map.get("min"), 0) : null;
          Integer max = map.containsKey("max") ? YamlValues.intValue(map.get("max"), 0) : null;
          String modeRaw = YamlValues.string(map, "mode", "remaining");
          boolean remaining = !"damage".equalsIgnoreCase(modeRaw);
          yield ItemMatchers.durabilityRange(min, max, remaining);
        }
        case "attribute" -> parseAttributeMatcher(map, path, errors);
        case "all", "and" -> parseCompositeMatcher(map, path, errors, true);
        case "any_of", "or" -> parseCompositeMatcher(map, path, errors, false);
        case "not" -> {
          Object matcherRaw = map.get("matcher");
          ItemMatcher inner = parseItemMatcher(matcherRaw, path + ".matcher", errors);
          yield ItemMatchers.not(inner);
        }
        default -> {
          errors.add(path + ".type: unknown matcher type " + typeRaw);
          yield ItemMatchers.anyNonAir();
        }
      };
    } catch (Exception ex) {
      errors.add(path + ": " + ex.getMessage());
      return ItemMatchers.anyNonAir();
    }
  }

  private ItemMatcher parseCompositeMatcher(Map<?, ?> map, String path, List<String> errors, boolean all) {
    Object listRaw = map.get("matchers");
    if (listRaw instanceof ConfigurationSection sec) {
      listRaw = sec.getValues(false);
    }
    if (!(listRaw instanceof List<?> list) || list.isEmpty()) {
      errors.add(path + ".matchers: must be a non-empty list");
      return ItemMatchers.anyNonAir();
    }
    ItemMatcher out = null;
    for (int i = 0; i < list.size(); i++) {
      ItemMatcher matcher = parseItemMatcher(list.get(i), path + ".matchers[" + i + "]", errors);
      if (out == null) {
        out = matcher;
      } else {
        out = all ? ItemMatchers.and(out, matcher) : ItemMatchers.or(out, matcher);
      }
    }
    return out == null ? ItemMatchers.anyNonAir() : out;
  }

  private ItemMatcher parsePdcMatcher(Map<?, ?> map, String path, List<String> errors) {
    String keyRaw = YamlValues.string(map, "key", null);
    if (keyRaw == null || keyRaw.isBlank()) {
      errors.add(path + ".key: required for matcher type pdc");
      return ItemMatchers.anyNonAir();
    }
    NamespacedKey key = NamespacedKey.fromString(keyRaw);
    if (key == null) {
      errors.add(path + ".key: invalid namespaced key " + keyRaw);
      return ItemMatchers.anyNonAir();
    }
    String typeRaw = YamlValues.string(map, "dataType", YamlValues.string(map, "pdcType", "string"));
    String normalized = typeRaw == null ? "string" : typeRaw.trim().toLowerCase(Locale.ROOT);
    Object valueRaw = map.get("value");
    return switch (normalized) {
      case "byte", "bool", "boolean" -> {
        Byte value = valueRaw == null ? null : (byte) (YamlValues.bool(valueRaw, false) ? 1 : 0);
        yield ItemMatchers.pdc(key, PersistentDataType.BYTE, value);
      }
      case "short" -> {
        Short value = valueRaw == null ? null : (short) YamlValues.intValue(valueRaw, 0);
        yield ItemMatchers.pdc(key, PersistentDataType.SHORT, value);
      }
      case "int", "integer" -> {
        Integer value = valueRaw == null ? null : YamlValues.intValue(valueRaw, 0);
        yield ItemMatchers.pdc(key, PersistentDataType.INTEGER, value);
      }
      case "long" -> {
        Long value = valueRaw == null ? null : YamlValues.longValue(valueRaw, 0L);
        yield ItemMatchers.pdc(key, PersistentDataType.LONG, value);
      }
      case "float" -> {
        Float value = valueRaw == null ? null : (float) YamlValues.doubleValue(valueRaw, 0.0);
        yield ItemMatchers.pdc(key, PersistentDataType.FLOAT, value);
      }
      case "double" -> {
        Double value = valueRaw == null ? null : YamlValues.doubleValue(valueRaw, 0.0);
        yield ItemMatchers.pdc(key, PersistentDataType.DOUBLE, value);
      }
      case "string" -> {
        String value = valueRaw == null ? null : String.valueOf(valueRaw);
        yield ItemMatchers.pdc(key, PersistentDataType.STRING, value);
      }
      case "byte_array", "bytes" -> {
        yield ItemMatchers.pdc(key, PersistentDataType.BYTE_ARRAY, null);
      }
      case "int_array", "ints" -> {
        yield ItemMatchers.pdc(key, PersistentDataType.INTEGER_ARRAY, null);
      }
      case "long_array", "longs" -> {
        yield ItemMatchers.pdc(key, PersistentDataType.LONG_ARRAY, null);
      }
      default -> {
        errors.add(path + ".dataType: unsupported pdc type " + typeRaw);
        yield ItemMatchers.anyNonAir();
      }
    };
  }

  private ItemMatcher parseAttributeMatcher(Map<?, ?> map, String path, List<String> errors) {
    String attrRaw = YamlValues.string(map, "attribute", YamlValues.string(map, "attr", null));
    if (attrRaw == null || attrRaw.isBlank()) {
      errors.add(path + ".attribute: required for matcher type attribute");
      return ItemMatchers.anyNonAir();
    }
    Attribute attribute = parseAttribute(attrRaw, path + ".attribute", errors);
    if (attribute == null) {
      return ItemMatchers.anyNonAir();
    }
    String opRaw = YamlValues.string(map, "operation", YamlValues.string(map, "op", null));
    AttributeModifier.Operation op = parseAttributeOperation(opRaw);
    Double min = map.containsKey("min") ? YamlValues.doubleValue(map.get("min"), 0.0) : null;
    Double max = map.containsKey("max") ? YamlValues.doubleValue(map.get("max"), 0.0) : null;
    return ItemMatchers.attribute(attribute, op, min, max);
  }

  private Attribute parseAttribute(String raw, String path, List<String> errors) {
    String normalized = raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    if (normalized == null || normalized.isBlank()) {
      errors.add(path + ": invalid attribute " + raw);
      return null;
    }
    NamespacedKey key = NamespacedKey.fromString(normalized.contains(":") ? normalized : "minecraft:" + normalized);
    if (key == null) {
      errors.add(path + ": invalid attribute " + raw);
      return null;
    }
    Attribute attribute = Registry.ATTRIBUTE.get(key);
    if (attribute == null) {
      errors.add(path + ": unknown attribute " + raw);
      return null;
    }
    return attribute;
  }

  private AttributeModifier.Operation parseAttributeOperation(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "add", "add_number" -> AttributeModifier.Operation.ADD_NUMBER;
      case "add_scalar", "multiply", "multiply_scalar" -> AttributeModifier.Operation.ADD_SCALAR;
      case "multiply_base", "multiply_base_scalar" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
      default -> null;
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
