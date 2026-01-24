package dev.patric.dungeonsreborn.party;

public enum PartyShareMode {
  NONE,
  FULL,
  SPLIT;

  public static PartyShareMode fromString(String value, PartyShareMode fallback) {
    if (value == null) {
      return fallback;
    }
    try {
      return PartyShareMode.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return fallback;
    }
  }
}
