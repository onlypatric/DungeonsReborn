package dev.patric.dungeonsreborn.effects.mana;

import java.util.Objects;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record ManaUiConfig(Actionbar actionbar, Warnings warnings, boolean scoreboardEnabled) {
  public record Actionbar(boolean enabled, String template) {
  }

  public record Warnings(boolean enabled, double thresholdPercent, long cooldownTicks, String messageKey) {
  }

  public static ManaUiConfig defaults() {
    return new ManaUiConfig(
        new Actionbar(true, "<aqua>{resource}</aqua> <white>{current}</white>/<gray>{max}</gray> <dark_gray>({percent}%)</dark_gray>"),
        new Warnings(true, 25.0, 100L, "messages.mana.low"),
        true);
  }

  public static ManaUiConfig fromConfig(FileConfiguration config) {
    ManaUiConfig defaults = defaults();
    if (config == null) {
      return defaults;
    }
    ConfigurationSection ui = config.getConfigurationSection("mana.ui");
    if (ui == null) {
      return defaults;
    }
    ConfigurationSection actionbarSection = ui.getConfigurationSection("actionbar");
    boolean actionbarEnabled = actionbarSection == null
        ? defaults.actionbar.enabled()
        : actionbarSection.getBoolean("enabled", defaults.actionbar.enabled());
    String template = actionbarSection == null
        ? defaults.actionbar.template()
        : actionbarSection.getString("template", defaults.actionbar.template());

    ConfigurationSection warningSection = ui.getConfigurationSection("warnings");
    boolean warningsEnabled = warningSection == null
        ? defaults.warnings.enabled()
        : warningSection.getBoolean("enabled", defaults.warnings.enabled());
    double threshold = warningSection == null
        ? defaults.warnings.thresholdPercent()
        : warningSection.getDouble("thresholdPercent", defaults.warnings.thresholdPercent());
    long cooldown = warningSection == null
        ? defaults.warnings.cooldownTicks()
        : warningSection.getLong("cooldownTicks", defaults.warnings.cooldownTicks());
    String messageKey = warningSection == null
        ? defaults.warnings.messageKey()
        : warningSection.getString("messageKey", defaults.warnings.messageKey());

    ConfigurationSection scoreboard = ui.getConfigurationSection("scoreboard");
    boolean scoreboardEnabled = scoreboard == null
        ? defaults.scoreboardEnabled()
        : scoreboard.getBoolean("enabled", defaults.scoreboardEnabled());

    return new ManaUiConfig(
        new Actionbar(actionbarEnabled, Objects.requireNonNullElse(template, defaults.actionbar.template())),
        new Warnings(warningsEnabled, threshold, cooldown, Objects.requireNonNullElse(messageKey, defaults.warnings.messageKey())),
        scoreboardEnabled);
  }
}
