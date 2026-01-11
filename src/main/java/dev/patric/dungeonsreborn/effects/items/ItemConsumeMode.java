package dev.patric.dungeonsreborn.effects.items;

import java.util.Locale;

public enum ItemConsumeMode {
  NONE,
  STACK,
  DURABILITY;

  public static ItemConsumeMode parse(String raw) {
    if (raw == null) {
      return NONE;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    return switch (normalized) {
      case "", "none", "off", "false" -> NONE;
      case "stack", "item", "item_stack", "itemstack" -> STACK;
      case "durability", "damage", "durable" -> DURABILITY;
      default -> NONE;
    };
  }
}
