package dev.patric.dungeonsreborn.effects.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

public final class EditorActionTree {
  private EditorActionTree() {
  }

  public static Map<String, Object> root(EditorAbilityDraft draft) {
    ConfigurationSection ability = draft.abilitySection();
    Object raw = ability.get("action");
    Map<String, Object> node = mapFrom(raw);
    if (node == null) {
      node = sequenceNode();
      ability.set("action", node);
    }
    return node;
  }

  public static void setRoot(EditorAbilityDraft draft, Map<String, Object> root) {
    draft.abilitySection().set("action", root);
  }

  public static List<Map<String, Object>> rootActions(EditorAbilityDraft draft) {
    Map<String, Object> root = root(draft);
    Map<String, Object> seq = ensureSequence(root);
    if (seq != root) {
      setRoot(draft, seq);
    }
    return ensureActionsList(seq);
  }

  public static List<Map<String, Object>> ensureChildList(Map<String, Object> node, String key) {
    Object raw = node.get(key);
    Map<String, Object> child = mapFrom(raw);
    if (child == null) {
      child = sequenceNode();
      node.put(key, child);
      return ensureActionsList(child);
    }
    Map<String, Object> seq = ensureSequence(child);
    if (seq != child) {
      node.put(key, seq);
    }
    return ensureActionsList(seq);
  }

  public static Map<String, Object> ensureSequence(Map<String, Object> node) {
    String type = typeOf(node);
    if ("sequence".equals(type)) {
      return node;
    }
    Map<String, Object> seq = sequenceNode();
    List<Map<String, Object>> actions = ensureActionsList(seq);
    if (!node.isEmpty()) {
      actions.add(node);
    }
    return seq;
  }

  public static String typeOf(Map<String, Object> node) {
    Object raw = node.get("type");
    if (raw == null) {
      return "sequence";
    }
    return raw.toString().trim().toLowerCase(Locale.ROOT);
  }

  public static Map<String, Object> sequenceNode() {
    Map<String, Object> seq = new LinkedHashMap<>();
    seq.put("type", "sequence");
    seq.put("actions", new ArrayList<>());
    return seq;
  }

  public static List<Map<String, Object>> ensureActionsList(Map<String, Object> sequence) {
    Object raw = sequence.get("actions");
    List<Map<String, Object>> list = castList(raw);
    if (raw == null || raw instanceof List<?>) {
      sequence.put("actions", list);
    } else {
      sequence.put("actions", list);
    }
    return list;
  }

  public static Map<String, Object> mapFrom(Object raw) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof Map<?, ?> map) {
      Map<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        out.put(String.valueOf(entry.getKey()), entry.getValue());
      }
      return out;
    }
    if (raw instanceof ConfigurationSection section) {
      return new LinkedHashMap<>(section.getValues(false));
    }
    return null;
  }

  public static List<Map<String, Object>> castList(Object raw) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (!(raw instanceof List<?> list)) {
      return out;
    }
    for (Object entry : list) {
      Map<String, Object> map = mapFrom(entry);
      if (map != null) {
        out.add(map);
      }
    }
    return out;
  }
}
