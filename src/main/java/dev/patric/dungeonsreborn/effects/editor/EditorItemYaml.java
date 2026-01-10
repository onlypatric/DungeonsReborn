package dev.patric.dungeonsreborn.effects.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;

public final class EditorItemYaml {
  private EditorItemYaml() {
  }

  public static List<Map<String, Object>> bindings(YamlConfiguration yaml) {
    return castList(yaml.getMapList("bindings"));
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
}
