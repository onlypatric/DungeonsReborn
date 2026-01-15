package dev.patric.dungeonsreborn.logging;

import java.util.EnumMap;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public final class ServiceLogManager {
  private final Logger logger;
  private final EnumMap<ServiceLogCategory, ServiceLogLevel> levels;

  private ServiceLogManager(Logger logger, EnumMap<ServiceLogCategory, ServiceLogLevel> levels) {
    this.logger = Objects.requireNonNull(logger, "logger");
    this.levels = new EnumMap<>(Objects.requireNonNull(levels, "levels"));
  }

  public static ServiceLogManager fromConfig(JavaPlugin plugin) {
    Objects.requireNonNull(plugin, "plugin");
    EnumMap<ServiceLogCategory, ServiceLogLevel> out = new EnumMap<>(ServiceLogCategory.class);
    ConfigurationSection section = plugin.getConfig().getConfigurationSection("logging");
    populateLevels(out, section);
    return new ServiceLogManager(plugin.getLogger(), out);
  }

  public ServiceLogger gui() {
    return new ServiceLogger(this, ServiceLogCategory.GUI);
  }

  public ServiceLogger effects() {
    return new ServiceLogger(this, ServiceLogCategory.EFFECTS);
  }

  public ServiceLogger mobs() {
    return new ServiceLogger(this, ServiceLogCategory.MOBS);
  }

  public ServiceLogger bindings() {
    return new ServiceLogger(this, ServiceLogCategory.BINDINGS);
  }

  public ServiceLogger upgrades() {
    return new ServiceLogger(this, ServiceLogCategory.UPGRADES);
  }

  public ServiceLogger shops() {
    return new ServiceLogger(this, ServiceLogCategory.SHOPS);
  }

  public ServiceLogger dungeons() {
    return new ServiceLogger(this, ServiceLogCategory.DUNGEONS);
  }

  public ServiceLogger locales() {
    return new ServiceLogger(this, ServiceLogCategory.LOCALES);
  }

  public void reloadFromConfig(ConfigurationSection section) {
    populateLevels(this.levels, section);
  }

  public void reloadFromConfig(JavaPlugin plugin) {
    Objects.requireNonNull(plugin, "plugin");
    reloadFromConfig(plugin.getConfig().getConfigurationSection("logging"));
  }

  void log(ServiceLogCategory category, ServiceLogLevel level, String message, Throwable throwable) {
    ServiceLogLevel min = levels.getOrDefault(category, ServiceLogLevel.INFO);
    if (!min.allows(level)) {
      return;
    }
    String text = message == null ? "" : message;
    if (throwable == null) {
      logger.log(toJavaLevel(level), text);
    } else {
      logger.log(toJavaLevel(level), text, throwable);
    }
  }

  private static Level toJavaLevel(ServiceLogLevel level) {
    return switch (level) {
      case DEBUG -> Level.INFO;
      case INFO -> Level.INFO;
      case WARNING -> Level.WARNING;
      case ERROR -> Level.SEVERE;
    };
  }

  private static void populateLevels(EnumMap<ServiceLogCategory, ServiceLogLevel> target, ConfigurationSection section) {
    for (ServiceLogCategory category : ServiceLogCategory.values()) {
      String raw = section == null ? null : section.getString(category.configKey());
      target.put(category, ServiceLogLevel.parse(raw, ServiceLogLevel.INFO));
    }
  }
}
