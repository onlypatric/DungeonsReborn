package dev.patric.dungeonsreborn.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.quests.QuestRegion;

public record UpgradeScalingConfig(
    boolean enabled,
    double defaultMultiplier,
    Map<String, Double> worldMultipliers,
    List<RegionScale> regions,
    LevelScale levelScale,
    double presetMultiplier
) {
  public record RegionScale(QuestRegion region, double multiplier) {
    public RegionScale {
      Objects.requireNonNull(region, "region");
    }
  }

  public record LevelScale(boolean enabled, int baseLevel, double perLevel, double minMultiplier, double maxMultiplier) {
  }

  public static UpgradeScalingConfig from(ConfigurationSection root) {
    if (root == null) {
      return new UpgradeScalingConfig(false, 1.0, Map.of(), List.of(), new LevelScale(false, 1, 0.0, 0.5, 2.0), 1.0);
    }
    ConfigurationSection upgrades = root.getConfigurationSection("upgrades");
    ConfigurationSection scaling = upgrades == null ? null : upgrades.getConfigurationSection("scaling");
    boolean enabled = scaling != null && scaling.getBoolean("enabled", false);
    double defaultMultiplier = scaling == null ? 1.0 : scaling.getDouble("defaultMultiplier", 1.0);
    Map<String, Double> worldMultipliers = parseWorldMultipliers(scaling == null ? null
        : scaling.getConfigurationSection("worlds"));
    List<RegionScale> regions = parseRegions(scaling == null ? null : scaling.get("regions"));
    LevelScale levelScale = parseLevelScale(scaling == null ? null : scaling.getConfigurationSection("level"));
    double presetMultiplier = parsePresetMultiplier(upgrades == null ? null : upgrades.getConfigurationSection("tuningPresets"));
    return new UpgradeScalingConfig(enabled, defaultMultiplier, worldMultipliers, regions, levelScale, presetMultiplier);
  }

  public double resolve(Player player, ToIntFunction<Player> levelResolver) {
    if (!enabled || player == null) {
      return 1.0;
    }
    double multiplier = defaultMultiplier;
    String world = player.getWorld() == null ? "" : player.getWorld().getName().toLowerCase(Locale.ROOT);
    Double worldMultiplier = worldMultipliers.get(world);
    if (worldMultiplier != null) {
      multiplier *= worldMultiplier;
    }
    Location location = player.getLocation();
    for (RegionScale region : regions) {
      if (region.region().contains(location)) {
        multiplier *= region.multiplier();
        break;
      }
    }
    LevelScale level = levelScale;
    if (level != null && level.enabled() && levelResolver != null) {
      int playerLevel = levelResolver.applyAsInt(player);
      int delta = playerLevel - Math.max(1, level.baseLevel());
      double levelMultiplier = 1.0 + (level.perLevel() * delta);
      levelMultiplier = clamp(levelMultiplier, level.minMultiplier(), level.maxMultiplier());
      multiplier *= levelMultiplier;
    }
    multiplier *= presetMultiplier;
    return clamp(multiplier, 0.01, 100.0);
  }

  private static Map<String, Double> parseWorldMultipliers(ConfigurationSection section) {
    if (section == null) {
      return Map.of();
    }
    Map<String, Double> out = new LinkedHashMap<>();
    for (String key : section.getKeys(false)) {
      String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
      double value = section.getDouble(key, 1.0);
      if (!Double.isFinite(value) || value <= 0.0) {
        value = 1.0;
      }
      out.put(normalized, value);
    }
    return Map.copyOf(out);
  }

  private static List<RegionScale> parseRegions(Object raw) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<RegionScale> regions = new ArrayList<>();
    for (Object entry : list) {
      if (!(entry instanceof java.util.Map<?, ?> map)) {
        continue;
      }
      Object worldRaw = map.get("world");
      String world = worldRaw == null ? "" : worldRaw.toString();
      double x = parseDouble(map.get("x"), 0.0);
      double y = parseDouble(map.get("y"), 0.0);
      double z = parseDouble(map.get("z"), 0.0);
      double radius = Math.max(0.0, parseDouble(map.get("radius"), 0.0));
      double multiplier = parseDouble(map.get("multiplier"), 1.0);
      if (!Double.isFinite(multiplier) || multiplier <= 0.0) {
        multiplier = 1.0;
      }
      regions.add(new RegionScale(new QuestRegion(world, x, y, z, radius), multiplier));
    }
    return List.copyOf(regions);
  }

  private static LevelScale parseLevelScale(ConfigurationSection section) {
    if (section == null) {
      return new LevelScale(false, 1, 0.0, 0.5, 2.0);
    }
    boolean enabled = section.getBoolean("enabled", false);
    int base = Math.max(1, section.getInt("baseLevel", 1));
    double perLevel = section.getDouble("perLevel", 0.0);
    double minMultiplier = section.getDouble("minMultiplier", 0.5);
    double maxMultiplier = section.getDouble("maxMultiplier", 2.0);
    if (!Double.isFinite(minMultiplier) || minMultiplier <= 0.0) {
      minMultiplier = 0.5;
    }
    if (!Double.isFinite(maxMultiplier) || maxMultiplier <= 0.0) {
      maxMultiplier = 2.0;
    }
    return new LevelScale(enabled, base, perLevel, minMultiplier, maxMultiplier);
  }

  private static double parsePresetMultiplier(ConfigurationSection section) {
    if (section == null) {
      return 1.0;
    }
    String active = section.getString("active", "medium");
    ConfigurationSection presets = section.getConfigurationSection("presets");
    if (presets == null) {
      return 1.0;
    }
    double multiplier = presets.getDouble(active, 1.0);
    if (!Double.isFinite(multiplier) || multiplier <= 0.0) {
      return 1.0;
    }
    return multiplier;
  }

  private static double parseDouble(Object raw, double fallback) {
    if (raw instanceof Number number) {
      return number.doubleValue();
    }
    if (raw instanceof String str) {
      try {
        return Double.parseDouble(str);
      } catch (NumberFormatException ignored) {
        return fallback;
      }
    }
    return fallback;
  }

  private static double clamp(double value, double min, double max) {
    if (value < min) {
      return min;
    }
    if (value > max) {
      return max;
    }
    return value;
  }
}
