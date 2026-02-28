package dev.patric.dungeonsreborn.effects.items;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ItemStatBlock {
  private final Map<String, Double> values;

  public ItemStatBlock(Map<String, Double> values) {
    this.values = values == null ? Map.of() : Map.copyOf(values);
  }

  public Map<String, Double> values() {
    return values;
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public ItemStatBlock scale(double scale) {
    if (!Double.isFinite(scale) || Math.abs(scale - 1.0) < 1e-9 || values.isEmpty()) {
      return this;
    }
    Map<String, Double> out = new LinkedHashMap<>();
    for (var entry : values.entrySet()) {
      out.put(entry.getKey(), entry.getValue() * scale);
    }
    return new ItemStatBlock(out);
  }

  public ItemStatBlock applyCaps(ItemStatCaps caps) {
    if (caps == null || values.isEmpty()) {
      return this;
    }
    Map<String, Double> out = new LinkedHashMap<>();
    for (var entry : values.entrySet()) {
      out.put(entry.getKey(), caps.apply(entry.getKey(), entry.getValue()));
    }
    return new ItemStatBlock(out);
  }

  public ItemStatBlock merge(ItemStatBlock other) {
    if (other == null || other.values.isEmpty()) {
      return this;
    }
    Map<String, Double> out = new LinkedHashMap<>(values);
    for (var entry : other.values.entrySet()) {
      out.merge(
          entry.getKey(),
          entry.getValue(),
          (left, right) -> (left == null ? 0.0 : left) + (right == null ? 0.0 : right));
    }
    return new ItemStatBlock(out);
  }

  public static ItemStatBlock parse(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return new ItemStatBlock(Map.of());
    }
    if (raw instanceof ConfigurationSection section) {
      Map<String, Double> out = new LinkedHashMap<>();
      for (String key : section.getKeys(false)) {
        if (key == null || key.isBlank()) {
          continue;
        }
        Double parsed = parseNumber(section.get(key));
        if (parsed == null) {
          errors.add(path + "." + key + ": expected number");
          continue;
        }
        out.put(key, parsed);
      }
      return new ItemStatBlock(out);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      errors.add(path + ": expected map of stat -> number");
      return new ItemStatBlock(Map.of());
    }
    Map<String, Double> out = new LinkedHashMap<>();
    for (var entry : map.entrySet()) {
      String key = entry.getKey() == null ? null : String.valueOf(entry.getKey()).trim();
      if (key == null || key.isEmpty()) {
        continue;
      }
      Object value = entry.getValue();
      Double parsed = parseNumber(value);
      if (parsed == null) {
        errors.add(path + "." + key + ": expected number");
        continue;
      }
      out.put(key, parsed);
    }
    return new ItemStatBlock(out);
  }

  private static Double parseNumber(Object raw) {
    if (raw instanceof Number num) {
      return num.doubleValue();
    }
    if (raw == null) {
      return null;
    }
    try {
      return Double.parseDouble(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  public static ItemStatBlock empty() {
    return new ItemStatBlock(Collections.emptyMap());
  }
}
