package dev.patric.dungeonsreborn.effects.items;

import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

public record ItemTierSpec(String id, double scale, ItemStatCaps caps) {
  public ItemStatBlock apply(ItemStatBlock base) {
    if (base == null) {
      return ItemStatBlock.empty();
    }
    ItemStatBlock scaled = base.scale(scale);
    return caps == null ? scaled : scaled.applyCaps(caps);
  }

  public static ItemTierSpec parse(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    Map<?, ?> map = null;
    if (raw instanceof ConfigurationSection section) {
      map = section.getValues(false);
    } else if (raw instanceof Map<?, ?> rawMap) {
      map = rawMap;
    }
    if (map == null) {
      errors.add(path + ": expected map");
      return null;
    }
    String id = map.containsKey("id") ? String.valueOf(map.get("id")) : null;
    double scale = map.containsKey("scale") ? parseDouble(map.get("scale"), 1.0) : 1.0;
    Map<String, Double> soft = ItemStatCaps.parseMap(map.get("softCaps"));
    Map<String, Double> hard = ItemStatCaps.parseMap(map.get("hardCaps"));
    ItemStatCaps caps = (soft.isEmpty() && hard.isEmpty()) ? null : new ItemStatCaps(soft, hard);
    return new ItemTierSpec(id, scale, caps);
  }

  private static double parseDouble(Object raw, double def) {
    if (raw instanceof Number num) {
      return num.doubleValue();
    }
    if (raw == null) {
      return def;
    }
    try {
      return Double.parseDouble(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      return def;
    }
  }
}
