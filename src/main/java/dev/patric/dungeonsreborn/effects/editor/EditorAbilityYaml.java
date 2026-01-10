package dev.patric.dungeonsreborn.effects.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

public final class EditorAbilityYaml {
  private EditorAbilityYaml() {
  }

  public static ConfigurationSection ability(EditorAbilityDraft draft) {
    return draft.abilitySection();
  }

  public static String name(EditorAbilityDraft draft) {
    return ability(draft).getString("name");
  }

  public static void setName(EditorAbilityDraft draft, String name) {
    ability(draft).set("name", normalizeText(name));
  }

  public static String description(EditorAbilityDraft draft) {
    return ability(draft).getString("description");
  }

  public static void setDescription(EditorAbilityDraft draft, String description) {
    ability(draft).set("description", normalizeText(description));
  }

  public static int cooldownTicks(EditorAbilityDraft draft) {
    ConfigurationSection cooldown = ability(draft).getConfigurationSection("cooldown");
    if (cooldown == null) {
      return 0;
    }
    return cooldown.getInt("ticks", 0);
  }

  public static void setCooldownTicks(EditorAbilityDraft draft, int ticks) {
    if (ticks <= 0) {
      ability(draft).set("cooldown", null);
      return;
    }
    ConfigurationSection cooldown = ability(draft).getConfigurationSection("cooldown");
    if (cooldown == null) {
      cooldown = ability(draft).createSection("cooldown");
    }
    cooldown.set("ticks", ticks);
  }

  public static String cooldownKey(EditorAbilityDraft draft) {
    ConfigurationSection cooldown = ability(draft).getConfigurationSection("cooldown");
    if (cooldown == null) {
      return null;
    }
    return cooldown.getString("key");
  }

  public static void setCooldownKey(EditorAbilityDraft draft, String key) {
    ConfigurationSection cooldown = ability(draft).getConfigurationSection("cooldown");
    if (cooldown == null) {
      if (key == null || key.isBlank()) {
        return;
      }
      cooldown = ability(draft).createSection("cooldown");
    }
    if (key == null || key.isBlank()) {
      cooldown.set("key", null);
    } else {
      cooldown.set("key", key.trim());
    }
  }

  public static List<Map<String, Object>> requirements(EditorAbilityDraft draft) {
    return castList(ability(draft).getMapList("requirements"));
  }

  public static List<Map<String, Object>> costs(EditorAbilityDraft draft) {
    return castList(ability(draft).getMapList("costs"));
  }

  public static List<Map<String, Object>> triggers(EditorAbilityDraft draft) {
    return castList(ability(draft).getMapList("triggers"));
  }

  public static Map<String, Object> findByType(List<Map<String, Object>> list, String type) {
    if (type == null) {
      return null;
    }
    String needle = type.toLowerCase(Locale.ROOT);
    for (Map<String, Object> entry : list) {
      Object raw = entry.get("type");
      if (raw == null) {
        continue;
      }
      String current = raw.toString().toLowerCase(Locale.ROOT);
      if (current.equals(needle)) {
        return entry;
      }
    }
    return null;
  }

  public static void replaceByType(List<Map<String, Object>> list, String type, Map<String, Object> replacement) {
    String needle = type == null ? null : type.toLowerCase(Locale.ROOT);
    list.removeIf(entry -> {
      Object raw = entry.get("type");
      return raw != null && needle != null && raw.toString().toLowerCase(Locale.ROOT).equals(needle);
    });
    if (replacement != null) {
      list.add(replacement);
    }
  }

  public static void writeRequirements(EditorAbilityDraft draft, List<Map<String, Object>> list) {
    ability(draft).set("requirements", list.isEmpty() ? null : list);
  }

  public static void writeCosts(EditorAbilityDraft draft, List<Map<String, Object>> list) {
    ability(draft).set("costs", list.isEmpty() ? null : list);
  }

  public static void writeTriggers(EditorAbilityDraft draft, List<Map<String, Object>> list) {
    ability(draft).set("triggers", list.isEmpty() ? null : list);
  }

  private static List<Map<String, Object>> castList(List<?> rawList) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object raw : rawList) {
      if (raw instanceof Map<?, ?> map) {
        Map<String, Object> entry = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> item : map.entrySet()) {
          entry.put(String.valueOf(item.getKey()), item.getValue());
        }
        out.add(entry);
      }
    }
    return out;
  }

  private static String normalizeText(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
