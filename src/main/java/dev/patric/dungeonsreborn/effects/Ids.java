package dev.patric.dungeonsreborn.effects;

import java.util.Objects;

public final class Ids {
  private Ids() {
  }

  /**
   * Normalizes ids used by the effects engine.
   * <p>
   * Allowed characters: {@code [a-z0-9_.:-]}.
   */
  public static String normalize(String id) {
    String normalized = Objects.requireNonNull(id, "id").trim().toLowerCase();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("id is empty");
    }
    for (int i = 0; i < normalized.length(); i++) {
      char c = normalized.charAt(i);
      boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == ':';
      if (!ok) {
        throw new IllegalArgumentException("Invalid id: " + id);
      }
    }
    return normalized;
  }
}

