package dev.patric.dungeonsreborn.party;

public enum PartyLootShareMode {
  NONE,
  LEADER_ONLY,
  DUPLICATE,
  SPLIT;

  public static PartyLootShareMode fromString(String value, PartyLootShareMode fallback) {
    if (value == null) {
      return fallback;
    }
    try {
      return PartyLootShareMode.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return fallback;
    }
  }
}
