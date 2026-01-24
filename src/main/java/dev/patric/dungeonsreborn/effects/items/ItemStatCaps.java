package dev.patric.dungeonsreborn.effects.items;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ItemStatCaps {
  private final Map<String, Double> softCaps;
  private final Map<String, Double> hardCaps;

  public ItemStatCaps(Map<String, Double> softCaps, Map<String, Double> hardCaps) {
    this.softCaps = softCaps == null ? Map.of() : Map.copyOf(softCaps);
    this.hardCaps = hardCaps == null ? Map.of() : Map.copyOf(hardCaps);
  }

  public Map<String, Double> softCaps() {
    return softCaps;
  }

  public Map<String, Double> hardCaps() {
    return hardCaps;
  }

  public double apply(String stat, double value) {
    Double hard = hardCaps.get(stat);
    if (hard != null && Double.isFinite(hard)) {
      value = Math.min(value, hard);
    }
    Double soft = softCaps.get(stat);
    if (soft != null && Double.isFinite(soft) && value > soft) {
      value = soft + (value - soft) * 0.5;
    }
    return value;
  }

  public static ItemStatCaps empty() {
    return new ItemStatCaps(Collections.emptyMap(), Collections.emptyMap());
  }

  public static Map<String, Double> parseMap(Object raw) {
    if (!(raw instanceof Map<?, ?> map)) {
      return Map.of();
    }
    Map<String, Double> out = new LinkedHashMap<>();
    for (var entry : map.entrySet()) {
      String key = entry.getKey() == null ? null : String.valueOf(entry.getKey()).trim();
      if (key == null || key.isEmpty()) {
        continue;
      }
      Object value = entry.getValue();
      if (value instanceof Number num) {
        out.put(key, num.doubleValue());
      } else if (value != null) {
        try {
          out.put(key, Double.parseDouble(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
        }
      }
    }
    return out;
  }
}
