package dev.patric.dungeonsreborn.mobs.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import dev.patric.dungeonsreborn.mobs.MobAiSpec;

class MobAiPresetCatalogTest {
  @Test
  void appliesAggressivePreset() {
    MobAiSpec.Builder builder = MobAiSpec.builder();
    MobAiPresetCatalog.apply(builder, MobAiProfile.AGGRESSIVE);
    MobAiSpec spec = builder.build();
    assertEquals(16.0, spec.aggroRadius(), 1e-9);
    assertEquals(0.32, spec.chaseSpeed(), 1e-9);
    assertEquals(0.0, spec.fleeHealthRatio(), 1e-9);
  }

  @Test
  void appliesPassivePreset() {
    MobAiSpec.Builder builder = MobAiSpec.builder();
    MobAiPresetCatalog.apply(builder, MobAiProfile.PASSIVE);
    MobAiSpec spec = builder.build();
    assertEquals(0.0, spec.aggroRadius(), 1e-9);
    assertEquals(0.18, spec.chaseSpeed(), 1e-9);
    assertEquals(0.35, spec.fleeHealthRatio(), 1e-9);
    assertEquals(0.30, spec.fleeSpeed(), 1e-9);
  }
}
