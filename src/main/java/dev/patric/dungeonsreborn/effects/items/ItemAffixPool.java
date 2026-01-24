package dev.patric.dungeonsreborn.effects.items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public record ItemAffixPool(List<ItemAffixSpec> affixes, int rolls, boolean allowDuplicates) {
  public List<ItemAffixRoll> roll(Random random) {
    if (affixes.isEmpty() || rolls <= 0) {
      return List.of();
    }
    List<ItemAffixRoll> result = new ArrayList<>();
    Set<String> used = new HashSet<>();
    for (int i = 0; i < rolls; i++) {
      ItemAffixSpec chosen = pick(random, used);
      if (chosen == null) {
        break;
      }
      result.add(chosen.roll(random));
      if (!allowDuplicates) {
        used.add(chosen.id());
      }
    }
    return result;
  }

  private ItemAffixSpec pick(Random random, Set<String> used) {
    double total = 0.0;
    for (ItemAffixSpec spec : affixes) {
      if (!allowDuplicates && used.contains(spec.id())) {
        continue;
      }
      total += Math.max(0.0, spec.weight());
    }
    if (total <= 0.0) {
      return null;
    }
    double roll = random.nextDouble() * total;
    for (ItemAffixSpec spec : affixes) {
      if (!allowDuplicates && used.contains(spec.id())) {
        continue;
      }
      roll -= Math.max(0.0, spec.weight());
      if (roll <= 0.0) {
        return spec;
      }
    }
    return affixes.get(0);
  }

  public static ItemAffixPool parse(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof List<?> list) {
      return parseList(list, path, errors, 0, false);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      errors.add(path + ": expected list or map");
      return null;
    }
    int rolls = map.containsKey("rolls") ? parseInt(map.get("rolls"), 0) : 0;
    boolean allowDuplicates = map.containsKey("allowDuplicates") && Boolean.parseBoolean(String.valueOf(map.get("allowDuplicates")));
    Object poolRaw = map.get("pool");
    if (!(poolRaw instanceof List<?> list)) {
      errors.add(path + ".pool: expected list of affixes");
      return null;
    }
    return parseList(list, path + ".pool", errors, rolls, allowDuplicates);
  }

  private static ItemAffixPool parseList(List<?> list, String path, List<String> errors, int rolls, boolean allowDuplicates) {
    List<ItemAffixSpec> specs = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      ItemAffixSpec spec = ItemAffixSpec.parse(list.get(i), path + "[" + i + "]", errors);
      if (spec != null) {
        specs.add(spec);
      }
    }
    if (specs.isEmpty()) {
      return null;
    }
    return new ItemAffixPool(specs, rolls, allowDuplicates);
  }

  private static int parseInt(Object raw, int def) {
    if (raw instanceof Number num) {
      return num.intValue();
    }
    if (raw == null) {
      return def;
    }
    try {
      return Integer.parseInt(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      return def;
    }
  }
}
