package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.Locale;

public enum UpgradeModifierType {
  DAMAGE_ADD("damage_add", "Damage", false),
  DAMAGE_MULT("damage_mult", "Damage", true),
  COOLDOWN_ADD("cooldown_add", "Cooldown", false),
  COOLDOWN_MULT("cooldown_mult", "Cooldown", true),
  MANA_ADD("mana_add", "Mana Cost", false),
  MANA_MULT("mana_mult", "Mana Cost", true),
  RADIUS_ADD("radius_add", "Radius", false),
  RADIUS_MULT("radius_mult", "Radius", true),
  DURATION_ADD("duration_add", "Duration", false),
  DURATION_MULT("duration_mult", "Duration", true),
  LOOT_ADD("loot_add", "Loot Chance", false),
  LOOT_MULT("loot_mult", "Loot Chance", true);

  private final String key;
  private final String label;
  private final boolean multiplier;

  UpgradeModifierType(String key, String label, boolean multiplier) {
    this.key = key;
    this.label = label;
    this.multiplier = multiplier;
  }

  public String key() {
    return key;
  }

  public String label() {
    return label;
  }

  public boolean isMultiplier() {
    return multiplier;
  }

  public double defaultValue() {
    return multiplier ? 1.0 : 0.0;
  }

  public static UpgradeModifierType parse(String raw) {
    if (raw == null) {
      return null;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    normalized = normalized.replace(' ', '_').replace('-', '_');
    return switch (normalized) {
      case "damage_add", "damage_bonus", "damage_plus" -> DAMAGE_ADD;
      case "damage_mult", "damage_multiplier", "damage_scale" -> DAMAGE_MULT;
      case "cooldown_add", "cooldown_bonus", "cooldown_plus" -> COOLDOWN_ADD;
      case "cooldown_mult", "cooldown_multiplier", "cooldown_scale" -> COOLDOWN_MULT;
      case "mana_add", "mana_cost_add", "mana_bonus" -> MANA_ADD;
      case "mana_mult", "mana_cost_mult", "mana_scale" -> MANA_MULT;
      case "radius_add", "size_add", "radius_bonus" -> RADIUS_ADD;
      case "radius_mult", "size_mult", "radius_scale" -> RADIUS_MULT;
      case "duration_add", "duration_bonus" -> DURATION_ADD;
      case "duration_mult", "duration_scale" -> DURATION_MULT;
      case "loot_add", "magic_find_add", "magicfind_add", "luck_add" -> LOOT_ADD;
      case "loot_mult", "magic_find", "magicfind", "luck_mult", "luck_multiplier" -> LOOT_MULT;
      default -> null;
    };
  }

  public static UpgradeModifierType fromKey(String key) {
    if (key == null) {
      return null;
    }
    for (UpgradeModifierType type : values()) {
      if (type.key.equalsIgnoreCase(key.trim())) {
        return type;
      }
    }
    return null;
  }
}
