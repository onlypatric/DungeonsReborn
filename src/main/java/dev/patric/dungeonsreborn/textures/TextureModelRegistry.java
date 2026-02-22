package dev.patric.dungeonsreborn.textures;

import java.util.Locale;

public final class TextureModelRegistry {
  public record ModelKeyParts(String namespace, String path) {
  }

  private TextureModelRegistry() {
  }

  public static ModelKeyParts resolve(
      String configuredNamespace,
      String defaultPath,
      String explicitModelKey) {
    String namespace = normalizeNamespace(configuredNamespace);
    String path = TexturePathNormalizer.normalizeModelPath(defaultPath);
    if (explicitModelKey == null || explicitModelKey.isBlank()) {
      return new ModelKeyParts(namespace, path);
    }
    String raw = explicitModelKey.trim();
    int colon = raw.indexOf(':');
    if (colon >= 0) {
      String explicitNamespace = raw.substring(0, colon).trim();
      String explicitPath = raw.substring(colon + 1).trim();
      return new ModelKeyParts(
          normalizeNamespace(explicitNamespace),
          TexturePathNormalizer.normalizeModelPath(explicitPath));
    }
    return new ModelKeyParts(namespace, TexturePathNormalizer.normalizeModelPath(raw));
  }

  public static String namespacedKey(ModelKeyParts key) {
    return key.namespace() + ":" + key.path();
  }

  public static String normalizeNamespace(String raw) {
    String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    if (value.isEmpty()) {
      value = "dungeonsreborn";
    }
    if (!value.matches("[a-z0-9._-]+")) {
      throw new IllegalArgumentException("invalid namespace: " + raw);
    }
    return value;
  }
}
