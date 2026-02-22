package dev.patric.dungeonsreborn.textures;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TexturePathNormalizer {
  private TexturePathNormalizer() {
  }

  public static String normalizeTexturePath(String raw, String expectedRoot) {
    String normalized = normalizePath(raw, false);
    String root = normalizePath(expectedRoot, true);
    if (root.isBlank()) {
      return normalized;
    }
    if (normalized.equals(root) || normalized.startsWith(root + "/")) {
      return normalized;
    }
    return root + "/" + normalized;
  }

  public static String normalizeModelPath(String raw) {
    return normalizePath(raw, false);
  }

  public static String stripPngExtension(String path) {
    if (path == null) {
      return "";
    }
    String out = path;
    if (out.toLowerCase(Locale.ROOT).endsWith(".png")) {
      out = out.substring(0, out.length() - 4);
    }
    return out;
  }

  public static String ensurePngExtension(String pathNoExt) {
    String base = stripPngExtension(pathNoExt);
    return base + ".png";
  }

  private static String normalizePath(String raw, boolean allowEmpty) {
    if (raw == null) {
      if (allowEmpty) {
        return "";
      }
      throw new IllegalArgumentException("path is missing");
    }
    String value = raw.trim().replace('\\', '/');
    while (value.startsWith("./")) {
      value = value.substring(2);
    }
    while (value.startsWith("/")) {
      value = value.substring(1);
    }
    value = stripPngExtension(value);
    String[] parts = value.split("/");
    List<String> out = new ArrayList<>();
    for (String part : parts) {
      String p = part == null ? "" : part.trim();
      if (p.isEmpty() || ".".equals(p)) {
        continue;
      }
      if ("..".equals(p)) {
        throw new IllegalArgumentException("path traversal is not allowed: " + raw);
      }
      if (!p.matches("[A-Za-z0-9._-]+")) {
        throw new IllegalArgumentException("invalid path segment: " + p);
      }
      out.add(p.toLowerCase(Locale.ROOT));
    }
    if (out.isEmpty()) {
      if (allowEmpty) {
        return "";
      }
      throw new IllegalArgumentException("path is empty");
    }
    return String.join("/", out);
  }
}
