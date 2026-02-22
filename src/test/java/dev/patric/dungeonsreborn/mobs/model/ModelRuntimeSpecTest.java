package dev.patric.dungeonsreborn.mobs.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.patric.dungeonsreborn.mobs.MobModelSpec;

class ModelRuntimeSpecTest {
  @Test
  void resolvesMappedAndFallbackAnimations() {
    MobModelSpec spec = new MobModelSpec(
        MobModelSpec.Provider.MODEL_ENGINE,
        "dr_test",
        true,
        true,
        "idle_anim",
        1.25,
        Map.of("attack", "attack_anim", "hurt", "hurt_anim"));
    ModelRuntimeSpec runtime = ModelRuntimeSpec.from(spec);
    assertEquals("attack_anim", runtime.resolveAnimation("attack"));
    assertEquals("idle_anim", runtime.resolveAnimation("walk"));
  }
}
