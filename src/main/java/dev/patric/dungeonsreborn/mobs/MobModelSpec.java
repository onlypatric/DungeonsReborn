package dev.patric.dungeonsreborn.mobs;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record MobModelSpec(
    Provider provider,
    String modelId,
    boolean replaceVisual,
    boolean hideBaseEntity,
    String animationId,
    double animationSpeed,
    Map<String, String> animations) {
  public enum Provider {
    MODEL_ENGINE;

    public static Provider parse(String raw) {
      String key = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
      if (key.isBlank()) {
        return MODEL_ENGINE;
      }
      return switch (key) {
        case "MODEL_ENGINE", "MODELENGINE", "ME" -> MODEL_ENGINE;
        default -> throw new IllegalArgumentException("unknown provider " + raw);
      };
    }
  }

  public MobModelSpec {
    provider = provider == null ? Provider.MODEL_ENGINE : provider;
    modelId = modelId == null ? null : modelId.trim();
    if (modelId == null || modelId.isBlank()) {
      throw new IllegalArgumentException("modelId must be set");
    }
    if (!Double.isFinite(animationSpeed) || animationSpeed <= 0.0) {
      throw new IllegalArgumentException("animationSpeed must be > 0");
    }
    modelId = Objects.requireNonNull(modelId, "modelId").trim();
    if (animationId != null && animationId.isBlank()) {
      animationId = null;
    }
    Map<String, String> normalized = new LinkedHashMap<>();
    if (animations != null) {
      for (Map.Entry<String, String> entry : animations.entrySet()) {
        if (entry == null) {
          continue;
        }
        String key = entry.getKey();
        String value = entry.getValue();
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
          continue;
        }
        normalized.put(key.trim().toLowerCase(Locale.ROOT), value.trim());
      }
    }
    animations = Map.copyOf(normalized);
  }

  public MobModelSpec(String modelId, String animationId, double animationSpeed) {
    this(Provider.MODEL_ENGINE, modelId, false, false, animationId, animationSpeed, Map.of());
  }

  public String animationFor(String key) {
    if (key != null && !key.isBlank()) {
      String mapped = animations.get(key.trim().toLowerCase(Locale.ROOT));
      if (mapped != null && !mapped.isBlank()) {
        return mapped;
      }
    }
    return animationId;
  }
}
