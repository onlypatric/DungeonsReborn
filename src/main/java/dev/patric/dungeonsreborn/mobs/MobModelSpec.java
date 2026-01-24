package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

public record MobModelSpec(String modelId, String animationId, double animationSpeed) {
  public MobModelSpec {
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
  }
}
