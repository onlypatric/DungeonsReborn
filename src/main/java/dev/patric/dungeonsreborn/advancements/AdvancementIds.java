package dev.patric.dungeonsreborn.advancements;

import java.util.Locale;

public final class AdvancementIds {
  private static final String NAMESPACE = "dungeonsreborn";
  public static final int CURRENT_SCHEMA_VERSION = 1;

  private AdvancementIds() {
  }

  public static String key(String id) {
    if (id == null || id.isBlank()) {
      return "unknown";
    }
    String normalized = id.toLowerCase(Locale.ROOT).trim();
    if (normalized.startsWith(NAMESPACE + ":")) {
      normalized = normalized.substring(NAMESPACE.length() + 1);
    } else if (normalized.contains(":")) {
      normalized = normalized.substring(normalized.indexOf(':') + 1);
    }
    return normalized;
  }
}
