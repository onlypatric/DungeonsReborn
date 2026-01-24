package dev.patric.dungeonsreborn.locale;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.logging.ServiceLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class LocaleService {
  public record ReloadResult(int locales, List<String> errors) {
  }

  public record CoverageResult(int totalKeys, Map<String, List<String>> missingByLocale) {
    public boolean hasMissing() {
      for (List<String> keys : missingByLocale.values()) {
        if (!keys.isEmpty()) {
          return true;
        }
      }
      return false;
    }
  }

  private final JavaPlugin plugin;
  private final ServiceLogger logger;
  private final MiniMessage miniMessage = MiniMessage.miniMessage();
  private final Map<String, Map<String, String>> bundles = new HashMap<>();
  private final Set<String> missingLogged = ConcurrentHashMap.newKeySet();
  private static final List<String> DEFAULT_LOCALE_FILES = List.of(
      "advancements.yml",
      "bindings.yml",
      "cancel.yml",
      "labels.yml",
      "messages/advancements.yml",
      "messages/bindings.yml",
      "messages/classes.yml",
      "messages/command.yml",
      "messages/common.yml",
      "messages/crafting.yml",
      "messages/dungeons.yml",
      "messages/effects.yml",
      "messages/hud.yml",
      "messages/input.yml",
      "messages/items.yml",
      "messages/kits.yml",
      "messages/locale.yml",
      "messages/mana.yml",
      "messages/mobs.yml",
      "messages/noPermission.yml",
      "messages/party.yml",
      "messages/quests.yml",
      "messages/shop.yml",
      "messages/shops.yml",
      "messages/system.yml",
      "messages/upgrades.yml",
      "tokens.yml"
  );
  private static final Pattern EMPTY_KEY_LINE = Pattern.compile("^\\s*(?:''|\"\"|):\\s*.*$");
  private String defaultLocale = "en";
  private Set<String> enabledLocales = Set.of("en");
  private boolean allowPlayerOverrides = false;
  private Map<UUID, String> playerOverrides = new HashMap<>();

  public LocaleService(JavaPlugin plugin, ServiceLogger logger) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  public ReloadResult reload() {
    plugin.reloadConfig();
    List<String> errors = new ArrayList<>();
    ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("locales");
    String configuredDefault = cfg == null ? "en" : cfg.getString("default", "en");
    defaultLocale = normalizeLocale(configuredDefault);
    allowPlayerOverrides = cfg != null && cfg.getBoolean("allowPlayerOverrides", false);
    List<String> enabled = cfg == null ? List.of() : cfg.getStringList("enabled");
    if (enabled.isEmpty()) {
      enabledLocales = new HashSet<>(List.of(defaultLocale));
    } else {
      Set<String> parsed = new HashSet<>();
      for (String locale : enabled) {
        String normalized = normalizeLocale(locale);
        if (!normalized.isEmpty()) {
          parsed.add(normalized);
        }
      }
      if (parsed.isEmpty()) {
        parsed.add(defaultLocale);
      }
      enabledLocales = parsed;
    }

    Map<UUID, String> overrides = new HashMap<>();
    ConfigurationSection overrideSec = cfg == null ? null : cfg.getConfigurationSection("playerOverrides");
    if (overrideSec != null) {
      for (String key : overrideSec.getKeys(false)) {
        String raw = overrideSec.getString(key);
        if (raw == null || raw.isBlank()) {
          continue;
        }
        try {
          UUID uuid = UUID.fromString(key);
          overrides.put(uuid, normalizeLocale(raw));
        } catch (IllegalArgumentException ex) {
          errors.add("locales.playerOverrides." + key + ": invalid UUID");
        }
      }
    }
    playerOverrides = overrides;

    File folder = new File(plugin.getDataFolder(), "locales");
    folder.mkdirs();
    bundles.clear();
    for (String locale : enabledLocales) {
      File localeDir = new File(folder, locale);
      Map<String, String> entries = new HashMap<>();
      if (!localeDir.isDirectory()) {
        saveDefaultLocale(locale, folder);
      }
      if (localeDir.isDirectory()) {
        List<File> localeFiles = listYamlFiles(localeDir);
        if (localeFiles.isEmpty()) {
          errors.add("locales/" + locale + ": no locale files found");
          continue;
        }
        for (File localeFile : localeFiles) {
          String source = "locales/" + locale + "/" + relativePath(localeDir, localeFile);
          YamlConfiguration yaml = loadYaml(localeFile, errors, source);
          if (yaml == null) {
            continue;
          }
          flatten(yaml, "", entries, errors, source);
        }
      } else {
        File file = new File(folder, locale + ".yml");
        if (!file.exists()) {
          errors.add("locales/" + locale + ".yml: missing locale file");
          continue;
        }
        String source = "locales/" + locale + ".yml";
        YamlConfiguration yaml = loadYaml(file, errors, source);
        if (yaml == null) {
          continue;
        }
        flatten(yaml, "", entries, errors, source);
      }
      fillEmptyStrings(entries, errors, "locales/" + locale);
      bundles.put(locale, entries);
    }
    if (!bundles.containsKey(defaultLocale)) {
      errors.add("locales.default: " + defaultLocale + " not loaded");
    }
    int seeded = seedMissingDefaultLocaleKeys();
    if (seeded > 0) {
      logger.warn("[Locales] default locale missing " + seeded + " keys (seeded)");
    }
    if (!errors.isEmpty()) {
      logger.warn("[Locales] reload had " + errors.size() + " errors");
      for (String error : errors) {
        logger.warn("[Locales] " + error);
      }
    } else {
      logger.info("[Locales] loaded " + bundles.size() + " locales");
    }
    return new ReloadResult(bundles.size(), errors);
  }

  public String defaultLocale() {
    return defaultLocale;
  }

  public boolean allowPlayerOverrides() {
    return allowPlayerOverrides;
  }

  public CoverageResult validateCoverage() {
    Set<String> allKeys = new HashSet<>();
    for (Map<String, String> bundle : bundles.values()) {
      allKeys.addAll(bundle.keySet());
    }
    Map<String, List<String>> missingByLocale = new HashMap<>();
    for (Map.Entry<String, Map<String, String>> entry : bundles.entrySet()) {
      List<String> missing = new ArrayList<>();
      for (String key : allKeys) {
        if (!entry.getValue().containsKey(key)) {
          missing.add(key);
        }
      }
      missing.sort(String::compareTo);
      missingByLocale.put(entry.getKey(), missing);
    }
    return new CoverageResult(allKeys.size(), missingByLocale);
  }

  public Set<String> enabledLocales() {
    return Collections.unmodifiableSet(enabledLocales);
  }

  public String overrideFor(UUID playerId) {
    if (playerId == null) {
      return null;
    }
    return playerOverrides.get(playerId);
  }

  public boolean setPlayerOverride(UUID playerId, String locale) {
    if (playerId == null || !allowPlayerOverrides) {
      return false;
    }
    String normalized = normalizeLocale(locale);
    if (normalized.isBlank() || !enabledLocales.contains(normalized)) {
      return false;
    }
    playerOverrides.put(playerId, normalized);
    savePlayerOverrides();
    return true;
  }

  public boolean clearPlayerOverride(UUID playerId) {
    if (playerId == null || !allowPlayerOverrides) {
      return false;
    }
    if (playerOverrides.remove(playerId) == null) {
      return false;
    }
    savePlayerOverrides();
    return true;
  }

  public String localeFor(Player player) {
    if (player == null) {
      return defaultLocale;
    }
    if (!allowPlayerOverrides) {
      return defaultLocale;
    }
    String override = playerOverrides.get(player.getUniqueId());
    return override == null || override.isBlank() ? defaultLocale : override;
  }

  public String text(Player player, String key) {
    return text(localeFor(player), key, Map.of());
  }

  public String text(Player player, String key, Map<String, String> placeholders) {
    return text(localeFor(player), key, placeholders);
  }

  public String text(String locale, String key, Map<String, String> placeholders) {
    String raw = lookup(locale, key);
    return applyPlaceholders(raw, placeholders);
  }

  public Component component(Player player, String key) {
    return component(localeFor(player), key, Map.of());
  }

  public Component component(Player player, String key, Map<String, String> placeholders) {
    return component(localeFor(player), key, placeholders);
  }

  public Component component(String locale, String key, Map<String, String> placeholders) {
    String raw = text(locale, key, placeholders);
    return dev.patric.dungeonsreborn.util.TextStyles.noItalic(miniMessage.deserialize(raw));
  }

  private String lookup(String locale, String key) {
    if (key == null || key.isBlank()) {
      return "";
    }
    String normalizedLocale = normalizeLocale(locale);
    String text = lookupInLocale(normalizedLocale, key);
    if (text != null) {
      return text;
    }
    if (normalizedLocale.contains("-")) {
      String baseLocale = normalizedLocale.substring(0, normalizedLocale.indexOf('-'));
      text = lookupInLocale(baseLocale, key);
      if (text != null) {
        return text;
      }
    }
    if (!normalizedLocale.equals(defaultLocale)) {
      text = lookupInLocale(defaultLocale, key);
      if (text != null) {
        return text;
      }
    }
    logMissing(normalizedLocale, key);
    return key;
  }

  private String lookupInLocale(String locale, String key) {
    Map<String, String> bundle = bundles.get(locale);
    if (bundle == null) {
      return null;
    }
    return bundle.get(key);
  }

  private void logMissing(String locale, String key) {
    String id = locale + ":" + key;
    if (!missingLogged.add(id)) {
      return;
    }
    logger.warn("[Locales] missing key=" + key + " locale=" + locale);
  }

  private static String normalizeLocale(String raw) {
    if (raw == null) {
      return "";
    }
    return raw.trim().toLowerCase(Locale.ROOT);
  }

  private static String applyPlaceholders(String raw, Map<String, String> placeholders) {
    if (raw == null || raw.isEmpty() || placeholders.isEmpty()) {
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

  private static void flatten(ConfigurationSection section, String prefix, Map<String, String> out, List<String> errors,
                              String source) {
    for (String key : section.getKeys(false)) {
      String path = prefix.isEmpty() ? key : prefix + "." + key;
      Object value = section.get(key);
      if (value instanceof ConfigurationSection child) {
        flatten(child, path, out, errors, source);
      } else if (value instanceof String text) {
        String resolved = text == null ? "" : text;
        if (resolved.isBlank()) {
          resolved = humanizeKey(path);
          errors.add(source + ": empty value at " + path + " (filled)");
        }
        if (out.put(path, resolved) != null) {
          errors.add(source + ": duplicate key " + path);
        }
      } else if (value instanceof List<?> list) {
        String resolved = joinLines(list);
        if (resolved.isBlank()) {
          resolved = humanizeKey(path);
          errors.add(source + ": empty value at " + path + " (filled)");
        }
        if (out.put(path, resolved) != null) {
          errors.add(source + ": duplicate key " + path);
        }
      }
    }
  }

  private void saveDefaultLocale(String locale, File folder) {
    File localeDir = new File(folder, locale);
    localeDir.mkdirs();
    for (String file : DEFAULT_LOCALE_FILES) {
      String resource = "locales/" + locale + "/" + file;
      File target = new File(localeDir, file);
      if (target.exists()) {
        continue;
      }
      try {
        plugin.saveResource(resource, false);
      } catch (IllegalArgumentException ignored) {
      }
    }
  }

  private static List<File> listYamlFiles(File root) {
    List<File> files = new ArrayList<>();
    File[] entries = root.listFiles();
    if (entries == null) {
      return files;
    }
    for (File entry : entries) {
      if (entry.isDirectory()) {
        files.addAll(listYamlFiles(entry));
      } else if (entry.getName().toLowerCase(Locale.ROOT).endsWith(".yml")) {
        files.add(entry);
      }
    }
    files.sort(Comparator.comparing(File::getPath, String.CASE_INSENSITIVE_ORDER));
    return files;
  }

  private static String relativePath(File root, File file) {
    String rootPath = root.getPath();
    String filePath = file.getPath();
    if (filePath.startsWith(rootPath)) {
      String suffix = filePath.substring(rootPath.length());
      if (suffix.startsWith(File.separator)) {
        return suffix.substring(1);
      }
      return suffix;
    }
    return file.getName();
  }

  private static String joinLines(List<?> list) {
    if (list.isEmpty()) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    for (Object entry : list) {
      if (out.length() > 0) {
        out.append('\n');
      }
      out.append(entry == null ? "" : entry.toString());
    }
    return out.toString();
  }

  private static YamlConfiguration loadYaml(File file, List<String> errors, String source) {
    try {
      String raw = Files.readString(file.toPath());
      String sanitized = sanitizeLocaleYaml(raw);
      if (!sanitized.equals(raw)) {
        errors.add(source + ": removed empty keys");
      }
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.loadFromString(sanitized);
      return yaml;
    } catch (IOException ex) {
      errors.add(source + ": failed to read (" + ex.getMessage() + ")");
      return null;
    } catch (Exception ex) {
      errors.add(source + ": failed to parse (" + ex.getMessage() + ")");
      return null;
    }
  }

  private static String sanitizeLocaleYaml(String raw) {
    if (raw == null || raw.isEmpty()) {
      return "";
    }
    String[] lines = raw.split("\\R", -1);
    StringBuilder out = new StringBuilder(raw.length());
    for (String line : lines) {
      if (EMPTY_KEY_LINE.matcher(line).matches()) {
        continue;
      }
      out.append(line).append('\n');
    }
    if (out.length() > 0) {
      out.setLength(out.length() - 1);
    }
    return out.toString();
  }

  private static void fillEmptyStrings(Map<String, String> entries, List<String> errors, String source) {
    for (Map.Entry<String, String> entry : entries.entrySet()) {
      String value = entry.getValue();
      if (value != null && !value.isBlank()) {
        continue;
      }
      entry.setValue(humanizeKey(entry.getKey()));
      errors.add(source + ": empty value at " + entry.getKey() + " (filled)");
    }
  }

  private static String humanizeKey(String key) {
    if (key == null || key.isBlank()) {
      return "Missing text";
    }
    String spaced = key.replace('.', ' ').replace('_', ' ').replace('-', ' ');
    String[] parts = spaced.split("\\s+");
    StringBuilder out = new StringBuilder();
    for (String part : parts) {
      if (part.isBlank()) {
        continue;
      }
      if (out.length() > 0) {
        out.append(' ');
      }
      out.append(Character.toUpperCase(part.charAt(0)));
      if (part.length() > 1) {
        out.append(part.substring(1));
      }
    }
    return out.toString();
  }

  private int seedMissingDefaultLocaleKeys() {
    Map<String, String> defaultBundle = bundles.get(defaultLocale);
    if (defaultBundle == null || bundles.isEmpty()) {
      return 0;
    }
    Set<String> allKeys = new HashSet<>();
    for (Map<String, String> bundle : bundles.values()) {
      allKeys.addAll(bundle.keySet());
    }
    int added = 0;
    for (String key : allKeys) {
      if (!defaultBundle.containsKey(key)) {
        defaultBundle.put(key, humanizeKey(key));
        added++;
      }
    }
    return added;
  }

  private void savePlayerOverrides() {
    ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("locales");
    if (cfg == null) {
      cfg = plugin.getConfig().createSection("locales");
    }
    ConfigurationSection overrides = cfg.getConfigurationSection("playerOverrides");
    if (overrides == null) {
      overrides = cfg.createSection("playerOverrides");
    } else {
      for (String key : overrides.getKeys(false)) {
        overrides.set(key, null);
      }
    }
    for (Map.Entry<UUID, String> entry : playerOverrides.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank()) {
        continue;
      }
      overrides.set(entry.getKey().toString(), entry.getValue());
    }
    plugin.saveConfig();
  }
}
