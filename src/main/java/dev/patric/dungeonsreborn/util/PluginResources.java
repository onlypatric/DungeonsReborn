package dev.patric.dungeonsreborn.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class PluginResources {
  private PluginResources() {
  }

  public static boolean saveResourceIfPresent(JavaPlugin plugin, String resourcePath, boolean replace) {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(resourcePath, "resourcePath");
    if (plugin.getResource(resourcePath) == null) {
      return false;
    }
    plugin.saveResource(resourcePath, replace);
    return true;
  }

  public static void ensureYamlFile(JavaPlugin plugin, File target, String bundledResource,
      Consumer<YamlConfiguration> defaultsWriter, Logger logger, String label) {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(target, "target");
    if (target.exists()) {
      return;
    }
    File parent = target.getParentFile();
    if (parent != null && !parent.exists()) {
      parent.mkdirs();
    }
    if (bundledResource != null && saveResourceIfPresent(plugin, bundledResource, false)) {
      return;
    }
    YamlConfiguration yaml = new YamlConfiguration();
    if (defaultsWriter != null) {
      defaultsWriter.accept(yaml);
    }
    try {
      yaml.save(target);
      if (logger != null) {
        logger.info("[" + label + "] Created default " + target.getName() + " (bundled resource not present)");
      }
    } catch (Exception ex) {
      if (logger != null) {
        logger.warning("[" + label + "] Failed to create default " + target.getName() + ": " + ex.getMessage());
      }
    }
  }
}
