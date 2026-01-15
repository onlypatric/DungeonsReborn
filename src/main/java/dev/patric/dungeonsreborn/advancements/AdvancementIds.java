package dev.patric.dungeonsreborn.advancements;

import java.util.Locale;

public final class AdvancementIds {
  private static final String NAMESPACE = "dungeonsreborn";

  private AdvancementIds() {
  }

  public static String key(String id) {
    if (id == null || id.isBlank()) {
      return NAMESPACE + ":unknown";
    }
    String normalized = id.toLowerCase(Locale.ROOT).trim();
    if (normalized.startsWith(NAMESPACE + ":")) {
      return normalized;
    }
    return NAMESPACE + ":" + normalized;
  }
}
