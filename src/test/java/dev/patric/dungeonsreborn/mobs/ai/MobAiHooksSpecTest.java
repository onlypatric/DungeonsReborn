package dev.patric.dungeonsreborn.mobs.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MobAiHooksSpecTest {
  @Test
  void trimsValuesAndResolvesStates() {
    MobAiHooksSpec hooks = new MobAiHooksSpec("  idle_a ", "engage_a", "retreat_a", "rage_a");
    assertEquals("idle_a", hooks.forState("idle"));
    assertEquals("engage_a", hooks.forState("ENGAGE"));
    assertEquals("retreat_a", hooks.forState("retreat"));
    assertEquals("rage_a", hooks.forState("RAGE"));
  }

  @Test
  void unknownStateReturnsNull() {
    MobAiHooksSpec hooks = new MobAiHooksSpec("idle_a", "engage_a", "retreat_a", "rage_a");
    assertNull(hooks.forState("panic"));
    assertNull(hooks.forState(null));
  }

  @Test
  void emptyFactoryReturnsEmptySpec() {
    assertTrue(MobAiHooksSpec.empty().isEmpty());
  }
}
