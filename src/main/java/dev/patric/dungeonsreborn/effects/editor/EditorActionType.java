package dev.patric.dungeonsreborn.effects.editor;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;

public enum EditorActionType {
  SEQUENCE("sequence", "Sequence", Material.REPEATER, "Run actions in order", "actions"),
  DELAY("delay", "Delay", Material.CLOCK, "Wait then run", "then"),
  REPEAT_TICKS("repeat_ticks", "Repeat", Material.COMPARATOR, "Repeat every period", "action"),
  WHEN("when", "When", Material.REDSTONE_TORCH, "Run if condition passes", "then"),
  FOR_EACH_TARGET("for_each_target", "For Each Target", Material.TARGET, "Run for each target", "then"),
  MESSAGE("message", "Message", Material.PAPER, "Send chat message", null),
  SOUND("sound", "Sound", Material.NOTE_BLOCK, "Play a sound", null),
  PARTICLES_POINT("particles_point", "Particles (Point)", Material.FIREWORK_STAR, "Spawn a point particle", null),
  DAMAGE("damage", "Damage", Material.IRON_SWORD, "Deal damage", null),
  ACTION_BAR("action_bar", "Action Bar", Material.FEATHER, "Send action bar", null),
  TITLE("title", "Title", Material.NAME_TAG, "Show title", null);

  private final String id;
  private final String label;
  private final Material icon;
  private final String hint;
  private final String childKey;

  EditorActionType(String id, String label, Material icon, String hint, String childKey) {
    this.id = id;
    this.label = label;
    this.icon = icon;
    this.hint = hint;
    this.childKey = childKey;
  }

  public String id() {
    return id;
  }

  public String label() {
    return label;
  }

  public Material icon() {
    return icon;
  }

  public String hint() {
    return hint;
  }

  public String childKey() {
    return childKey;
  }

  public boolean supportsChildren() {
    return childKey != null;
  }

  public static EditorActionType fromType(String raw) {
    if (raw == null) {
      return SEQUENCE;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    for (EditorActionType type : values()) {
      if (type.id.equals(normalized)) {
        return type;
      }
    }
    return null;
  }

  public Map<String, Object> create() {
    Map<String, Object> node = new LinkedHashMap<>();
    node.put("type", id);
    switch (this) {
      case SEQUENCE -> node.put("actions", new java.util.ArrayList<>());
      case DELAY -> {
        node.put("ticks", 20);
        node.put("then", EditorActionTree.sequenceNode());
      }
      case REPEAT_TICKS -> {
        node.put("delayTicks", 0);
        node.put("periodTicks", 20);
        node.put("times", 5);
        node.put("action", EditorActionTree.sequenceNode());
      }
      case WHEN -> {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("type", "always");
        node.put("condition", condition);
        node.put("then", EditorActionTree.sequenceNode());
      }
      case FOR_EACH_TARGET -> {
        Map<String, Object> targeter = new LinkedHashMap<>();
        targeter.put("type", "self");
        node.put("targeter", targeter);
        node.put("mode", "each");
        node.put("maxTargets", 0);
        node.put("originAt", "origin");
        node.put("then", EditorActionTree.sequenceNode());
      }
      case MESSAGE -> node.put("text", "<gray>Message</gray>");
      case SOUND -> {
        node.put("sound", "ENTITY_PLAYER_LEVELUP");
        node.put("volume", 1.0);
        node.put("pitch", 1.0);
      }
      case PARTICLES_POINT -> {
        node.put("particle", "CRIT");
        node.put("count", 6);
        node.put("offset", 0.1);
        node.put("extra", 0.0);
      }
      case DAMAGE -> {
        node.put("amount", 1.0);
        node.put("policy", "hostile_default");
      }
      case ACTION_BAR -> node.put("text", "<gray>Action bar</gray>");
      case TITLE -> {
        node.put("title", "<gold>Title</gold>");
        node.put("subtitle", "<gray>Subtitle</gray>");
        node.put("fadeInTicks", 10);
        node.put("stayTicks", 40);
        node.put("fadeOutTicks", 10);
      }
      default -> {
      }
    }
    return node;
  }
}
