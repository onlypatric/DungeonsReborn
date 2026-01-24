package dev.patric.dungeonsreborn.effects.mana;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.quests.QuestRegion;

public final class ResourceRuleSet {
  private final Map<String, ResourceRules> baseRules;
  private final Map<String, Map<String, ResourceRules>> worldOverrides;
  private final List<RegionOverride> regionOverrides;

  private ResourceRuleSet(Map<String, ResourceRules> baseRules,
      Map<String, Map<String, ResourceRules>> worldOverrides,
      List<RegionOverride> regionOverrides) {
    this.baseRules = Collections.unmodifiableMap(new HashMap<>(baseRules));
    this.worldOverrides = Collections.unmodifiableMap(new HashMap<>(worldOverrides));
    this.regionOverrides = List.copyOf(regionOverrides);
  }

  public static ResourceRuleSet fromConfig(Configuration config, double fallbackManaBase) {
    Map<String, ResourceRules> base = new HashMap<>();
    Map<String, Map<String, ResourceRules>> overrides = new HashMap<>();
    List<RegionOverride> regionOverrides = new ArrayList<>();
    if (config != null) {
      ConfigurationSection resources = config.getConfigurationSection("resources");
      if (resources != null) {
        for (String key : resources.getKeys(false)) {
          if ("worldOverrides".equalsIgnoreCase(key)) {
            continue;
          }
          ConfigurationSection section = resources.getConfigurationSection(key);
          if (section == null) {
            continue;
          }
          base.put(normalizeId(key), parseRules(section, fallbackManaBase));
        }
        ConfigurationSection worldOverrides = resources.getConfigurationSection("worldOverrides");
        if (worldOverrides != null) {
          for (String worldKey : worldOverrides.getKeys(false)) {
            ConfigurationSection worldSection = worldOverrides.getConfigurationSection(worldKey);
            if (worldSection == null) {
              continue;
            }
            Map<String, ResourceRules> worldRules = new HashMap<>();
            for (String resourceKey : worldSection.getKeys(false)) {
              ConfigurationSection resourceSection = worldSection.getConfigurationSection(resourceKey);
              if (resourceSection == null) {
                continue;
              }
              String id = normalizeId(resourceKey);
              ResourceRules baseRule = base.getOrDefault(id, ResourceRules.defaults(fallbackManaBase));
              worldRules.put(id, mergeRules(baseRule, resourceSection));
            }
            overrides.put(worldKey, worldRules);
          }
        }
        for (var entry : resources.getMapList("regionOverrides")) {
          if (!(entry.get("resource") instanceof String resourceId)) {
            continue;
          }
          String world = entry.get("world") instanceof String value ? value : null;
          double x = entry.get("x") instanceof Number number ? number.doubleValue() : 0.0;
          double y = entry.get("y") instanceof Number number ? number.doubleValue() : 0.0;
          double z = entry.get("z") instanceof Number number ? number.doubleValue() : 0.0;
          double radius = entry.get("radius") instanceof Number number ? number.doubleValue() : 0.0;
          if (world == null || radius <= 0.0) {
            continue;
          }
          org.bukkit.configuration.file.YamlConfiguration temp = new org.bukkit.configuration.file.YamlConfiguration();
          temp.createSection("override", entry);
          ConfigurationSection overrideSection = temp.getConfigurationSection("override");
          if (overrideSection == null) {
            continue;
          }
          ResourceRules baseRule = base.getOrDefault(normalizeId(resourceId), ResourceRules.defaults(fallbackManaBase));
          ResourceRules overrideRules = mergeRules(baseRule, overrideSection);
          regionOverrides.add(new RegionOverride(normalizeId(resourceId), new QuestRegion(world, x, y, z, radius),
              overrideRules));
        }
      }
    }
    if (base.isEmpty()) {
      base.put(ManaProvider.DEFAULT_RESOURCE, ResourceRules.defaults(fallbackManaBase));
    }
    return new ResourceRuleSet(base, overrides, regionOverrides);
  }

  public ResourceRules rulesFor(Player player, String resourceId) {
    String id = normalizeId(resourceId);
    ResourceRules result = baseRules.getOrDefault(id, ResourceRules.defaults(100.0));
    if (player != null) {
      World world = player.getWorld();
      Map<String, ResourceRules> overridesForWorld = worldOverrides.get(world.getKey().asString());
      if (overridesForWorld != null) {
        ResourceRules override = overridesForWorld.get(id);
        if (override != null) {
          result = override;
        }
      }
      for (RegionOverride override : regionOverrides) {
        if (override.resourceId.equals(id) && override.region.contains(player.getLocation())) {
          result = override.rules;
        }
      }
    }
    return result;
  }

  public Set<String> resourceIds() {
    return baseRules.keySet();
  }

  private static String normalizeId(String id) {
    return id == null ? ManaProvider.DEFAULT_RESOURCE : id.trim().toLowerCase(java.util.Locale.ROOT);
  }

  private static ResourceRules parseRules(ConfigurationSection section, double fallbackBase) {
    double base = section.getDouble("base", fallbackBase);
    double hardCap = section.getDouble("hardCap", ResourceRules.NO_CAP);
    double softCap = section.getDouble("softCap", ResourceRules.NO_CAP);
    double overflowDecay = section.getDouble("overflowDecay", 0.0);
    ResourceRules.RegenMode regenMode = parseRegenMode(section.getString("regenMode", "flat"));
    double regenFlat = section.getDouble("regenFlat", 0.0);
    double regenPercent = section.getDouble("regenPercent", 0.0);
    double regenMultiplier = section.getDouble("regenMultiplier", ResourceRules.DEFAULT_REGEN_MULTIPLIER);
    double costMultiplier = section.getDouble("costMultiplier", ResourceRules.DEFAULT_COST_MULTIPLIER);
    Map<String, Double> conversions = new HashMap<>();
    ConfigurationSection conversionsSection = section.getConfigurationSection("conversions");
    if (conversionsSection != null) {
      for (String target : conversionsSection.getKeys(false)) {
        double ratio = conversionsSection.getDouble(target, 0.0);
        if (Double.isFinite(ratio) && ratio > 0.0) {
          conversions.put(normalizeId(target), ratio);
        }
      }
    }
    return new ResourceRules(base, hardCap, softCap, overflowDecay, regenMode, regenFlat, regenPercent, regenMultiplier,
        costMultiplier, conversions);
  }

  private static ResourceRules mergeRules(ResourceRules base, ConfigurationSection override) {
    Objects.requireNonNull(base, "base");
    double baseMax = override.contains("base") ? override.getDouble("base") : base.baseMax();
    double hardCap = override.contains("hardCap") ? override.getDouble("hardCap") : base.hardCap();
    double softCap = override.contains("softCap") ? override.getDouble("softCap") : base.softCap();
    double overflowDecay = override.contains("overflowDecay") ? override.getDouble("overflowDecay") : base.overflowDecay();
    ResourceRules.RegenMode regenMode = override.contains("regenMode")
        ? parseRegenMode(override.getString("regenMode"))
        : base.regenMode();
    double regenFlat = override.contains("regenFlat") ? override.getDouble("regenFlat") : base.regenFlat();
    double regenPercent = override.contains("regenPercent") ? override.getDouble("regenPercent") : base.regenPercent();
    double regenMultiplier = override.contains("regenMultiplier") ? override.getDouble("regenMultiplier")
        : base.regenMultiplier();
    double costMultiplier = override.contains("costMultiplier") ? override.getDouble("costMultiplier")
        : base.costMultiplier();
    Map<String, Double> conversions = base.conversions();
    ConfigurationSection conversionsSection = override.getConfigurationSection("conversions");
    if (conversionsSection != null) {
      conversions = new HashMap<>();
      for (String target : conversionsSection.getKeys(false)) {
        double ratio = conversionsSection.getDouble(target, 0.0);
        if (Double.isFinite(ratio) && ratio > 0.0) {
          conversions.put(normalizeId(target), ratio);
        }
      }
    }
    return new ResourceRules(baseMax, hardCap, softCap, overflowDecay, regenMode, regenFlat, regenPercent,
        regenMultiplier, costMultiplier, conversions);
  }

  private static ResourceRules.RegenMode parseRegenMode(String raw) {
    if (raw == null) {
      return ResourceRules.RegenMode.FLAT;
    }
    return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
      case "percent", "percentage" -> ResourceRules.RegenMode.PERCENT;
      case "hybrid" -> ResourceRules.RegenMode.HYBRID;
      default -> ResourceRules.RegenMode.FLAT;
    };
  }

  private record RegionOverride(String resourceId, QuestRegion region, ResourceRules rules) {
  }
}
