package dev.patric.dungeonsreborn.effects.config;

import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

public final class YamlValueParsers {
  private YamlValueParsers() {
  }

  public static <E extends Enum<E>> E enumValue(Map<String, Object> node, String key, Class<E> enumType, String path) {
    String raw = YamlReaders.requireString(node, key, path);
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    try {
      return Enum.valueOf(enumType, normalized);
    } catch (IllegalArgumentException ex) {
      String suggestion = YamlErrors.suggestEnumValue(raw, enumType);
      String msg = path + ": invalid " + key + "=" + raw;
      if (suggestion != null) {
        msg += " (did you mean " + suggestion + "?)";
      }
      throw new IllegalArgumentException(msg);
    }
  }

  public static ItemStack parseParticleItem(Object raw, String path) {
    if (raw instanceof ItemStack stack) {
      return stack.clone();
    }
    if (raw instanceof ConfigurationSection sec) {
      return parseParticleItem(sec.getValues(false), path);
    }
    if (raw instanceof Map<?, ?> map) {
      Map<String, Object> node = YamlReaders.normalizeMap(map);
      Object nested = YamlReaders.pick(node, "item", "stack", "value");
      if (nested != null && nested != raw) {
        return parseParticleItem(nested, path + ".item");
      }
      Map<String, Object> copy = new java.util.HashMap<>(node);
      if (!copy.containsKey("type") && copy.containsKey("material")) {
        copy.put("type", copy.get("material"));
      }
      if (copy.containsKey("type")) {
        try {
          return ItemStack.deserialize(copy);
        } catch (IllegalArgumentException ignored) {
          Material material = materialValue(copy.get("type"), path + ".type");
          int amount = Math.max(1, YamlReaders.intValue(copy, "amount", 1));
          return new ItemStack(material, amount);
        }
      }
      if (copy.containsKey("material")) {
        Material material = materialValue(copy.get("material"), path + ".material");
        int amount = Math.max(1, YamlReaders.intValue(copy, "amount", 1));
        return new ItemStack(material, amount);
      }
    }
    if (raw instanceof String s) {
      Material material = materialValue(s, path);
      return new ItemStack(material);
    }
    throw new IllegalArgumentException(path + ": expected itemstack or material");
  }

  public static BlockData parseBlockData(Object raw, String path) {
    if (raw instanceof BlockData blockData) {
      return blockData;
    }
    if (raw instanceof ConfigurationSection sec) {
      return parseBlockData(sec.getValues(false), path);
    }
    if (raw instanceof Map<?, ?> map) {
      Map<String, Object> node = YamlReaders.normalizeMap(map);
      Object nested = YamlReaders.pick(node, "data", "blockData", "block", "value");
      if (nested != null && nested != raw) {
        return parseBlockData(nested, path + ".data");
      }
      Object materialRaw = YamlReaders.pick(node, "material", "type");
      if (materialRaw != null) {
        Material material = materialValue(materialRaw, path + ".material");
        if (!material.isBlock()) {
          throw new IllegalArgumentException(path + ": material is not a block: " + material);
        }
        Object statesRaw = node.get("states");
        if (statesRaw instanceof Map<?, ?> states) {
          return parseBlockDataFromStates(material, YamlReaders.normalizeMap(states), path + ".states");
        }
        return material.createBlockData();
      }
    }
    if (raw instanceof Material material) {
      if (!material.isBlock()) {
        throw new IllegalArgumentException(path + ": material is not a block: " + material);
      }
      return material.createBlockData();
    }
    if (raw instanceof String s) {
      return parseBlockDataString(s, path);
    }
    throw new IllegalArgumentException(path + ": expected block data");
  }

  public static BlockData parseBlockDataFromStates(Material material, Map<String, Object> states, String path) {
    if (states.isEmpty()) {
      return material.createBlockData();
    }
    StringBuilder out = new StringBuilder(material.getKey().toString()).append('[');
    int index = 0;
    for (Map.Entry<String, Object> entry : states.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        throw new IllegalArgumentException(path + ": state keys/values cannot be null");
      }
      if (index++ > 0) {
        out.append(',');
      }
      out.append(entry.getKey()).append('=').append(String.valueOf(entry.getValue()));
    }
    out.append(']');
    try {
      return Bukkit.createBlockData(out.toString());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(path + ": invalid block states for " + material, ex);
    }
  }

  public static BlockData parseBlockDataString(String value, String path) {
    String trimmed = value.trim();
    if (trimmed.isBlank()) {
      throw new IllegalArgumentException(path + ": block data is blank");
    }
    try {
      return Bukkit.createBlockData(trimmed);
    } catch (IllegalArgumentException ex) {
      Material material = materialValue(trimmed, path);
      if (!material.isBlock()) {
        throw new IllegalArgumentException(path + ": material is not a block: " + trimmed);
      }
      return material.createBlockData();
    }
  }

  public static Material materialValue(Object raw, String path) {
    if (raw instanceof Material material) {
      return material;
    }
    if (raw == null) {
      throw new IllegalArgumentException(path + ": missing material");
    }
    String name = String.valueOf(raw).trim();
    if (name.isBlank()) {
      throw new IllegalArgumentException(path + ": material is blank");
    }
    Material material = Material.matchMaterial(name);
    if (material == null) {
      material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
    }
    if (material == null) {
      throw new IllegalArgumentException(path + ": unknown material: " + raw);
    }
    return material;
  }

  public static Particle.DustOptions parseDustOptions(Object raw, String path) {
    Map<String, Object> data = YamlReaders.castMap(raw, path);
    Object colorRaw = YamlReaders.pick(data, "color", "colour");
    Color color = parseColor(colorRaw != null ? colorRaw : data, path + ".color");
    double size = YamlReaders.doubleValue(data, "size", 1.0);
    if (!Double.isFinite(size) || size <= 0.0) {
      throw new IllegalArgumentException(path + ".size: must be > 0");
    }
    return new Particle.DustOptions(color, (float) size);
  }

  public static Particle.DustTransition parseDustTransition(Object raw, String path) {
    Map<String, Object> data = YamlReaders.castMap(raw, path);
    Object fromRaw = YamlReaders.pick(data, "color", "from", "fromColor", "from_colour");
    Object toRaw = YamlReaders.pick(data, "toColor", "colorTo", "to", "end", "endColor", "to_colour");
    if (fromRaw == null) {
      fromRaw = data;
    }
    if (toRaw == null) {
      throw new IllegalArgumentException(path + ".toColor: missing target color");
    }
    Color from = parseColor(fromRaw, path + ".color");
    Color to = parseColor(toRaw, path + ".toColor");
    double size = YamlReaders.doubleValue(data, "size", 1.0);
    if (!Double.isFinite(size) || size <= 0.0) {
      throw new IllegalArgumentException(path + ".size: must be > 0");
    }
    return new Particle.DustTransition(from, to, (float) size);
  }

  public static Color parseColor(Object raw, String path) {
    if (raw == null) {
      throw new IllegalArgumentException(path + ": missing color");
    }
    if (raw instanceof Color color) {
      return color;
    }
    if (raw instanceof Map<?, ?> map) {
      Map<String, Object> node = YamlReaders.normalizeMap(map);
      Object nested = YamlReaders.pick(node, "color", "colour", "hex", "value");
      if (nested != null && nested != raw) {
        return parseColor(nested, path);
      }
      if (hasColorKeys(node)) {
        int r = parseColorComponent(YamlReaders.pick(node, "r", "red"), path + ".r");
        int g = parseColorComponent(YamlReaders.pick(node, "g", "green"), path + ".g");
        int b = parseColorComponent(YamlReaders.pick(node, "b", "blue"), path + ".b");
        return Color.fromRGB(r, g, b);
      }
    }
    String trimmed = String.valueOf(raw).trim();
    if (trimmed.startsWith("#") || trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
      String hex = trimmed;
      if (hex.startsWith("#")) {
        hex = hex.substring(1);
      } else if (hex.startsWith("0x") || hex.startsWith("0X")) {
        hex = hex.substring(2);
      }
      if (hex.matches("[0-9a-fA-F]{6}")) {
        return Color.fromRGB(Integer.parseInt(hex, 16));
      }
    }
    String[] parts = trimmed.split("[,\\s]+");
    if (parts.length >= 3) {
      int r = parseColorComponent(parts[0], path + ".r");
      int g = parseColorComponent(parts[1], path + ".g");
      int b = parseColorComponent(parts[2], path + ".b");
      return Color.fromRGB(r, g, b);
    }
    throw new IllegalArgumentException(path + ": invalid color value");
  }

  public static boolean hasColorKeys(Map<String, Object> node) {
    return node.containsKey("r") || node.containsKey("red")
        || node.containsKey("g") || node.containsKey("green")
        || node.containsKey("b") || node.containsKey("blue");
  }

  public static int parseColorComponent(Object raw, String path) {
    if (raw == null) {
      throw new IllegalArgumentException(path + ": missing color component");
    }
    int value = parseInt(raw, path);
    if (value < 0 || value > 255) {
      throw new IllegalArgumentException(path + ": must be in [0, 255]");
    }
    return value;
  }

  public static Float parseFloatData(Object raw, String path) {
    Object value = raw;
    if (raw instanceof Map<?, ?> map) {
      Map<String, Object> node = YamlReaders.normalizeMap(map);
      value = YamlReaders.pick(node, "value", "data", "scale");
      if (value == null) {
        throw new IllegalArgumentException(path + ": missing value");
      }
    }
    double d = parseDouble(value, path);
    if (!Double.isFinite(d)) {
      throw new IllegalArgumentException(path + ": value must be finite");
    }
    return (float) d;
  }

  public static Integer parseIntData(Object raw, String path) {
    Object value = raw;
    if (raw instanceof Map<?, ?> map) {
      Map<String, Object> node = YamlReaders.normalizeMap(map);
      value = YamlReaders.pick(node, "value", "data");
      if (value == null) {
        throw new IllegalArgumentException(path + ": missing value");
      }
    }
    return parseInt(value, path);
  }

  public static double parseDouble(Object raw, String path) {
    if (raw instanceof Number n) {
      return n.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(path + ": expected number");
    }
  }

  public static int parseInt(Object raw, String path) {
    if (raw instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(path + ": expected integer");
    }
  }
}
