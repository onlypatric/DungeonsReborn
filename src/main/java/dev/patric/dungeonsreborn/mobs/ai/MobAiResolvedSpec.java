package dev.patric.dungeonsreborn.mobs.ai;

import java.util.Objects;

import dev.patric.dungeonsreborn.mobs.MobAiSpec;

public record MobAiResolvedSpec(MobAiSpec spec, String source) {
  public MobAiResolvedSpec {
    spec = Objects.requireNonNull(spec, "spec");
    source = source == null || source.isBlank() ? "base" : source;
  }
}
