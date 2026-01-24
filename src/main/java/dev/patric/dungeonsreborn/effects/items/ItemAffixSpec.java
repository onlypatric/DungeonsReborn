package dev.patric.dungeonsreborn.effects.items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public record ItemAffixSpec(String id, String type, double weight, Map<String, Range> statRanges) {
  public record Range(double min, double max) {
    public double roll(Random random) {
      if (min >= max) {
        return min;
      }
      return min + (max - min) * random.nextDouble();
    }
  }

  public ItemAffixRoll roll(Random random) {
    Map<String, Double> rolled = new LinkedHashMap<>();
    for (var entry : statRanges.entrySet()) {
      rolled.put(entry.getKey(), entry.getValue().roll(random));
    }
    return new ItemAffixRoll(id, type, new ItemStatBlock(rolled));
  }

  public static ItemAffixSpec parse(Object raw, String path, List<String> errors) {
    if (!(raw instanceof Map<?, ?> map)) {
      errors.add(path + ": expected map");
      return null;
    }
    Object idRaw = map.get("id");
    if (idRaw == null || String.valueOf(idRaw).isBlank()) {
      errors.add(path + ".id: missing affix id");
      return null;
    }
    String id = String.valueOf(idRaw).trim();
    String type = map.containsKey("type") ? String.valueOf(map.get("type")).trim() : null;
    double weight = map.containsKey("weight") ? parseDouble(map.get("weight"), 1.0) : 1.0;

    Object statsRaw = map.get("stats");
    if (!(statsRaw instanceof Map<?, ?> statsMap)) {
      errors.add(path + ".stats: expected map of stat -> number/min/max");
      return null;
    }
    Map<String, Range> ranges = new LinkedHashMap<>();
    for (var entry : statsMap.entrySet()) {
      String statKey = entry.getKey() == null ? null : String.valueOf(entry.getKey()).trim();
      if (statKey == null || statKey.isEmpty()) {
        continue;
      }
      Object value = entry.getValue();
      Range range = parseRange(value);
      if (range == null) {
        errors.add(path + ".stats." + statKey + ": expected number or {min,max}");
        continue;
      }
      ranges.put(statKey, range);
    }
    if (ranges.isEmpty()) {
      errors.add(path + ".stats: empty");
      return null;
    }
    return new ItemAffixSpec(id, type, weight, ranges);
  }

  private static Range parseRange(Object raw) {
    if (raw instanceof Number num) {
      return new Range(num.doubleValue(), num.doubleValue());
    }
    if (raw instanceof Map<?, ?> map) {
      double min = parseDouble(map.get("min"), 0.0);
      double max = map.containsKey("max") ? parseDouble(map.get("max"), min) : min;
      return new Range(min, max);
    }
    if (raw == null) {
      return null;
    }
    try {
      double value = Double.parseDouble(String.valueOf(raw));
      return new Range(value, value);
    } catch (NumberFormatException ex) {
      return null;
    }
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
