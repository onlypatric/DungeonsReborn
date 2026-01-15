package dev.patric.dungeonsreborn.util;

import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

public final class YamlValues {
  private YamlValues() {
  }

  public static String string(Object raw, String def) {
    if (raw == null) {
      return def;
    }
    String value = String.valueOf(raw);
    return value.isBlank() ? def : value;
  }

  public static String string(Map<?, ?> map, String key, String def) {
    return string(map.get(key), def);
  }

  public static String string(ConfigurationSection sec, String key, String def) {
    String value = sec.getString(key);
    return value == null || value.isBlank() ? def : value;
  }

  public static String requireString(ConfigurationSection sec, String key, String path) {
    String value = sec.getString(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(path + ": missing " + key);
    }
    return value;
  }

  public static boolean bool(Object raw, boolean def) {
    if (raw == null) {
      return def;
    }
    if (raw instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(String.valueOf(raw));
  }

  public static boolean bool(Map<?, ?> map, String key, boolean def) {
    return bool(map.get(key), def);
  }

  public static int intValue(Object raw, int def) {
    if (raw == null) {
      return def;
    }
    if (raw instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(raw));
    } catch (Exception ex) {
      return def;
    }
  }

  public static long longValue(Object raw, long def) {
    if (raw == null) {
      return def;
    }
    if (raw instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(raw));
    } catch (Exception ex) {
      return def;
    }
  }

  public static double doubleValue(Object raw, double def) {
    if (raw == null) {
      return def;
    }
    if (raw instanceof Number number) {
      return number.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(raw));
    } catch (Exception ex) {
      return def;
    }
  }

  public static int intValueStrict(Map<?, ?> map, String key, int def) {
    Object raw = map.get(key);
    if (raw == null) {
      return def;
    }
    if (raw instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(key + ": expected integer");
    }
  }

  public static long longValueStrict(Map<?, ?> map, String key, long def) {
    Object raw = map.get(key);
    if (raw == null) {
      return def;
    }
    if (raw instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(key + ": expected number");
    }
  }

  public static double doubleValueStrict(Map<?, ?> map, String key, double def) {
    Object raw = map.get(key);
    if (raw == null) {
      return def;
    }
    if (raw instanceof Number n) {
      return n.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(key + ": expected number");
    }
  }
}
