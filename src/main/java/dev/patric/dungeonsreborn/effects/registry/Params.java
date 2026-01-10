package dev.patric.dungeonsreborn.effects.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class Params {
  private final Map<String, Object> values;

  private Params(Map<String, Object> values) {
    this.values = values;
  }

  public static Params empty() {
    return new Params(Map.of());
  }

  public static Params of(Object... keyValues) {
    Objects.requireNonNull(keyValues, "keyValues");
    if (keyValues.length == 0) {
      return empty();
    }
    if ((keyValues.length % 2) != 0) {
      throw new IllegalArgumentException("Params.of requires an even number of arguments (key/value pairs)");
    }

    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      Object k = keyValues[i];
      Object v = keyValues[i + 1];
      if (!(k instanceof String key) || key.isBlank()) {
        throw new IllegalArgumentException("Param key must be a non-blank String (at index " + i + ")");
      }
      map.put(key, v);
    }
    return new Params(Collections.unmodifiableMap(map));
  }

  public boolean has(String key) {
    Objects.requireNonNull(key, "key");
    return values.containsKey(key);
  }

  public Set<String> keys() {
    return values.keySet();
  }

  public Object raw(String key) {
    Objects.requireNonNull(key, "key");
    return values.get(key);
  }

  public String string(String key, String defaultValue) {
    Object v = raw(key);
    if (v == null) {
      return defaultValue;
    }
    if (v instanceof String s) {
      return s;
    }
    return String.valueOf(v);
  }

  public String requireString(String key) {
    Object v = raw(key);
    if (v == null) {
      throw missing(key, "string");
    }
    if (v instanceof String s && !s.isBlank()) {
      return s;
    }
    if (v instanceof String) {
      throw invalid(key, "non-blank string", v);
    }
    return String.valueOf(v);
  }

  public boolean bool(String key, boolean defaultValue) {
    Object v = raw(key);
    if (v == null) {
      return defaultValue;
    }
    if (v instanceof Boolean b) {
      return b;
    }
    if (v instanceof String s) {
      if (s.equalsIgnoreCase("true")) {
        return true;
      }
      if (s.equalsIgnoreCase("false")) {
        return false;
      }
    }
    throw invalid(key, "boolean", v);
  }

  public int integer(String key, int defaultValue) {
    Object v = raw(key);
    if (v == null) {
      return defaultValue;
    }
    if (v instanceof Number n) {
      return n.intValue();
    }
    if (v instanceof String s) {
      try {
        return Integer.parseInt(s.trim());
      } catch (NumberFormatException ex) {
        throw invalid(key, "integer", v);
      }
    }
    throw invalid(key, "integer", v);
  }

  public int requireInt(String key) {
    Object v = raw(key);
    if (v == null) {
      throw missing(key, "integer");
    }
    return integer(key, 0);
  }

  public double dbl(String key, double defaultValue) {
    Object v = raw(key);
    if (v == null) {
      return defaultValue;
    }
    if (v instanceof Number n) {
      return n.doubleValue();
    }
    if (v instanceof String s) {
      try {
        return Double.parseDouble(s.trim());
      } catch (NumberFormatException ex) {
        throw invalid(key, "double", v);
      }
    }
    throw invalid(key, "double", v);
  }

  public double requireDouble(String key) {
    Object v = raw(key);
    if (v == null) {
      throw missing(key, "double");
    }
    return dbl(key, 0.0);
  }

  public <E extends Enum<E>> E enumValue(String key, Class<E> enumClass, E defaultValue) {
    Objects.requireNonNull(enumClass, "enumClass");
    Object v = raw(key);
    if (v == null) {
      return defaultValue;
    }
    if (enumClass.isInstance(v)) {
      return enumClass.cast(v);
    }
    if (v instanceof String s) {
      try {
        return Enum.valueOf(enumClass, s.trim().toUpperCase());
      } catch (IllegalArgumentException ex) {
        throw invalid(key, enumClass.getSimpleName(), v);
      }
    }
    throw invalid(key, enumClass.getSimpleName(), v);
  }

  private static IllegalArgumentException missing(String key, String expected) {
    return new IllegalArgumentException("Missing required param '" + key + "' (" + expected + ")");
  }

  private static IllegalArgumentException invalid(String key, String expected, Object got) {
    String type = got == null ? "null" : got.getClass().getSimpleName();
    return new IllegalArgumentException("Invalid param '" + key + "': expected " + expected + " but got " + type + " (" + got + ")");
  }
}

