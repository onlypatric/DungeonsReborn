package dev.patric.dungeonsreborn.quests.editor.menu;

import java.util.Map;

import dev.patric.dungeonsreborn.quests.QuestObjectiveType;
import dev.patric.dungeonsreborn.quests.editor.QuestEditorYaml;

final class QuestObjectiveSummary {
  private QuestObjectiveSummary() {
  }

  static String describe(QuestEditorYaml.ObjectiveData data) {
    QuestObjectiveType type = data.type();
    Map<String, Object> raw = data.raw();
    return switch (type) {
      case KILL_MOB -> {
        String mob = string(raw, "mob");
        String entity = string(raw, "entity");
        int count = intValue(raw.get("count"), 1);
        if (mob != null) {
          yield "Kill mob " + mob + " x" + count;
        }
        if (entity != null) {
          yield "Kill entity " + entity + " x" + count;
        }
        yield "Kill mobs x" + count;
      }
      case USE_ITEM -> {
        String itemId = string(raw, "itemId");
        String material = string(raw, "material");
        int count = intValue(raw.get("count"), 1);
        if (itemId != null) {
          yield "Use item " + itemId + " x" + count;
        }
        if (material != null) {
          yield "Use material " + material + " x" + count;
        }
        yield "Use items x" + count;
      }
      case VISIT_REGION -> {
        String world = string(raw, "world");
        double radius = doubleValue(raw.get("radius"), 4.0);
        yield "Visit region in " + (world == null ? "world" : world) + " (r=" + radius + ")";
      }
      case CRAFT_ITEM -> {
        String recipeId = string(raw, "recipeId");
        String itemId = string(raw, "itemId");
        String material = string(raw, "material");
        int count = intValue(raw.get("count"), 1);
        if (recipeId != null) {
          yield "Craft recipe " + recipeId + " x" + count;
        }
        if (itemId != null) {
          yield "Craft item " + itemId + " x" + count;
        }
        if (material != null) {
          yield "Craft material " + material + " x" + count;
        }
        yield "Craft items x" + count;
      }
    };
  }

  private static String string(Map<String, Object> map, String key) {
    Object raw = map.get(key);
    if (raw == null) {
      return null;
    }
    String value = raw.toString();
    return value.isBlank() ? null : value;
  }

  private static int intValue(Object raw, int def) {
    if (raw instanceof Number num) {
      return num.intValue();
    }
    if (raw instanceof String str) {
      try {
        return Integer.parseInt(str.trim());
      } catch (NumberFormatException ex) {
        return def;
      }
    }
    return def;
  }

  private static double doubleValue(Object raw, double def) {
    if (raw instanceof Number num) {
      return num.doubleValue();
    }
    if (raw instanceof String str) {
      try {
        return Double.parseDouble(str.trim());
      } catch (NumberFormatException ex) {
        return def;
      }
    }
    return def;
  }
}
