package dev.patric.dungeonsreborn.effects.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

public final class YamlReaders {
  private YamlReaders() {
  }

  public static Object require(Map<String, Object> node, String key, String path) {
    Object v = node.get(key);
    if (v == null) {
      throw new IllegalArgumentException(YamlErrors.missingKeyMessage(node, key, path));
    }
    return v;
  }

  public static Map<String, Object> castMap(Object raw, String path) {
    if (raw instanceof ConfigurationSection sec) {
      return normalizeMap(sec.getValues(false));
    }
    if (raw instanceof Map<?, ?> map) {
      return normalizeMap(map);
    }
    throw new IllegalArgumentException(path + ": expected object");
  }

  public static Map<String, Object> mapNode(Object raw, String path) {
    if (raw == null) {
      return null;
    }
    return castMap(raw, path);
  }

  public static List<?> mapList(Map<String, Object> node, String key, String path) {
    Object v = node.get(key);
    if (v == null) {
      return List.of();
    }
    if (!(v instanceof List<?> list)) {
      throw new IllegalArgumentException(path + ": expected list");
    }
    for (Object o : list) {
      if (!(o instanceof Map<?, ?>) && !(o instanceof ConfigurationSection)) {
        throw new IllegalArgumentException(path + ": list elements must be objects");
      }
    }
    return list;
  }

  public static Map<String, Object> normalizeMap(Map<?, ?> raw) {
    java.util.HashMap<String, Object> out = new java.util.HashMap<>();
    for (var e : raw.entrySet()) {
      String key = String.valueOf(e.getKey());
      out.put(key, normalizeValue(e.getValue()));
    }
    return out;
  }

  public static Object normalizeValue(Object v) {
    if (v instanceof ConfigurationSection sec) {
      return normalizeMap(sec.getValues(false));
    }
    if (v instanceof Map<?, ?> map) {
      return normalizeMap(map);
    }
    if (v instanceof List<?> list) {
      ArrayList<Object> out = new ArrayList<>(list.size());
      for (Object o : list) {
        out.add(normalizeValue(o));
      }
      return out;
    }
    return v;
  }

  public static String requireString(Map<String, Object> node, String key, String path) {
    Object v = node.get(key);
    if (v == null) {
      throw new IllegalArgumentException(YamlErrors.missingKeyMessage(node, key, path));
    }
    String s = String.valueOf(v);
    if (s.isBlank()) {
      throw new IllegalArgumentException(path + ": " + key + " is blank");
    }
    return s;
  }

  public static String string(Map<String, Object> node, String key, String def) {
    Object v = node.get(key);
    if (v == null) {
      return def;
    }
    return String.valueOf(v);
  }

  public static boolean bool(Map<String, Object> node, String key, boolean def) {
    Object v = node.get(key);
    if (v == null) {
      return def;
    }
    if (v instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(String.valueOf(v));
  }

  public static int intValue(Map<String, Object> node, String key, int def) {
    Object v = node.get(key);
    if (v == null) {
      return def;
    }
    if (v instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(v));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(key + ": expected number");
    }
  }

  public static long longValue(Map<String, Object> node, String key, long def) {
    Object v = node.get(key);
    if (v == null) {
      return def;
    }
    if (v instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(v));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(key + ": expected number");
    }
  }

  public static double doubleValue(Map<String, Object> node, String key, double def) {
    Object v = node.get(key);
    if (v == null) {
      return def;
    }
    if (v instanceof Number n) {
      return n.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(v));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(key + ": expected number");
    }
  }

  public static double requireDouble(Map<String, Object> node, String key, String path) {
    Object v = node.get(key);
    if (v == null) {
      throw new IllegalArgumentException(YamlErrors.missingKeyMessage(node, key, path));
    }
    if (v instanceof Number n) {
      return n.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(v));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(path + ": " + key + " must be a number");
    }
  }

  public static Object pick(Map<String, Object> node, String... keys) {
    for (String key : keys) {
      if (node.containsKey(key)) {
        return node.get(key);
      }
    }
    return null;
  }

  public static java.util.Set<String> parseStringSet(Object primary, Object secondary, String path) {
    Object raw = primary != null ? primary : secondary;
    if (raw == null) {
      return java.util.Set.of();
    }
    java.util.Set<String> out = new java.util.HashSet<>();
    if (raw instanceof java.util.List<?> list) {
      for (Object entry : list) {
        if (entry != null && !String.valueOf(entry).isBlank()) {
          out.add(String.valueOf(entry).trim());
        }
      }
    } else {
      String value = String.valueOf(raw).trim();
      if (!value.isBlank()) {
        out.add(value);
      }
    }
    return out;
  }

  public static java.util.Set<String> parseStringSet(Object raw, String path) {
    java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
    if (raw instanceof java.util.List<?> list) {
      for (Object entry : list) {
        String value = String.valueOf(entry).trim();
        if (!value.isEmpty()) {
          out.add(value);
        }
      }
    } else {
      String text = String.valueOf(raw);
      if (text.contains(",")) {
        for (String part : text.split(",")) {
          String value = part.trim();
          if (!value.isEmpty()) {
            out.add(value);
          }
        }
      } else if (!text.trim().isEmpty()) {
        out.add(text.trim());
      }
    }
    if (out.isEmpty()) {
      return java.util.Set.of();
    }
    return java.util.Set.copyOf(out);
  }
}
