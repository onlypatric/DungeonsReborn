package dev.patric.dungeonsreborn.locale;

import java.io.File;
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

  private final JavaPlugin plugin;
  private final ServiceLogger logger;
  private final MiniMessage miniMessage = MiniMessage.miniMessage();
  private final Map<String, Map<String, String>> bundles = new HashMap<>();
  private final Set<String> missingLogged = ConcurrentHashMap.newKeySet();
  private static final List<String> DEFAULT_LOCALE_FILES = List.of(
      "gui.yml",
      "labels.yml",
      "messages.yml",
      "meta.yml",
      "tokens.yml"
  );
  private String defaultLocale = "en";
  private Set<String> enabledLocales = Set.of("en");
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
          YamlConfiguration yaml = YamlConfiguration.loadConfiguration(localeFile);
          flatten(yaml, "", entries, errors, "locales/" + locale + "/" + relativePath(localeDir, localeFile));
        }
      } else {
        File file = new File(folder, locale + ".yml");
        if (!file.exists()) {
          errors.add("locales/" + locale + ".yml: missing locale file");
          continue;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        flatten(yaml, "", entries, errors, "locales/" + locale + ".yml");
      }
      bundles.put(locale, entries);
    }
    if (!bundles.containsKey(defaultLocale)) {
      errors.add("locales.default: " + defaultLocale + " not loaded");
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

  public Set<String> enabledLocales() {
    return Collections.unmodifiableSet(enabledLocales);
  }

  public String localeFor(Player player) {
    return defaultLocale;
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
    return miniMessage.deserialize(raw);
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
        if (out.put(path, text) != null) {
          errors.add(source + ": duplicate key " + path);
        }
      } else if (value instanceof List<?> list) {
        if (out.put(path, joinLines(list)) != null) {
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
}
