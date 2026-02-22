package dev.patric.dungeonsreborn.mobs.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MobAiPhaseMergeModeTest {
  @Test
  void defaultsToPatchWhenBlank() {
    assertEquals(MobAiPhaseMergeMode.PATCH, MobAiPhaseMergeMode.parse(null));
    assertEquals(MobAiPhaseMergeMode.PATCH, MobAiPhaseMergeMode.parse("   "));
  }

  @Test
  void parsesKnownModes() {
    assertEquals(MobAiPhaseMergeMode.PATCH, MobAiPhaseMergeMode.parse("patch"));
    assertEquals(MobAiPhaseMergeMode.REPLACE, MobAiPhaseMergeMode.parse("REPLACE"));
  }

  @Test
  void rejectsInvalidModes() {
    assertThrows(IllegalArgumentException.class, () -> MobAiPhaseMergeMode.parse("overwrite"));
  }
}
