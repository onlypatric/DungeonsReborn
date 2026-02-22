package dev.patric.dungeonsreborn.mobs.model;

import java.util.Map;
import java.util.Objects;

import dev.patric.dungeonsreborn.mobs.MobModelSpec;

public record ModelRuntimeSpec(
    MobModelSpec.Provider provider,
    String modelId,
    boolean replaceVisual,
    boolean hideBaseEntity,
    String animationId,
    double animationSpeed,
    Map<String, String> animations) {
  public ModelRuntimeSpec {
    provider = provider == null ? MobModelSpec.Provider.MODEL_ENGINE : provider;
    modelId = Objects.requireNonNullElse(modelId, "").trim();
    if (!Double.isFinite(animationSpeed) || animationSpeed <= 0.0) {
      animationSpeed = 1.0;
    }
    animations = animations == null ? Map.of() : Map.copyOf(animations);
  }

  public static ModelRuntimeSpec from(MobModelSpec spec) {
    if (spec == null) {
      return null;
    }
    return new ModelRuntimeSpec(
        spec.provider(),
        spec.modelId(),
        spec.replaceVisual(),
        spec.hideBaseEntity(),
        spec.animationId(),
        spec.animationSpeed(),
        spec.animations());
  }

  public String resolveAnimation(String key) {
    if (key != null && !key.isBlank()) {
      String normalized = key.trim().toLowerCase(java.util.Locale.ROOT);
      String mapped = animations.get(normalized);
      if (mapped != null && !mapped.isBlank()) {
        return mapped;
      }
      if (animationId == null || animationId.isBlank()) {
        return key.trim();
      }
    }
    return animationId;
  }
}
