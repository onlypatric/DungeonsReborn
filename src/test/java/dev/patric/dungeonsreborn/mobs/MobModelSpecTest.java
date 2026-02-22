package dev.patric.dungeonsreborn.mobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class MobModelSpecTest {
  @Test
  void parsesProviderAliases() {
    assertEquals(MobModelSpec.Provider.MODEL_ENGINE, MobModelSpec.Provider.parse("model_engine"));
    assertEquals(MobModelSpec.Provider.MODEL_ENGINE, MobModelSpec.Provider.parse("ModelEngine"));
    assertThrows(IllegalArgumentException.class, () -> MobModelSpec.Provider.parse("unknown"));
  }

  @Test
  void normalizesAnimationKeys() {
    MobModelSpec spec = new MobModelSpec(
        MobModelSpec.Provider.MODEL_ENGINE,
        "dr_test",
        true,
        true,
        "idle",
        1.0,
        Map.of("Attack", "attack_anim"));
    assertEquals("attack_anim", spec.animationFor("attack"));
    assertEquals("idle", spec.animationFor("walk"));
    assertTrue(spec.replaceVisual());
    assertTrue(spec.hideBaseEntity());
  }

  @Test
  void requiresModelId() {
    assertThrows(IllegalArgumentException.class, () -> new MobModelSpec(
        MobModelSpec.Provider.MODEL_ENGINE,
        " ",
        true,
        true,
        null,
        1.0,
        Map.of()));
  }
}
