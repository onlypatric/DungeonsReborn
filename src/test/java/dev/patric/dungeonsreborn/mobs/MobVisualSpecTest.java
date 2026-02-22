package dev.patric.dungeonsreborn.mobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MobVisualSpecTest {
  @Test
  void parsesSupportedSlots() {
    assertEquals(MobVisualSpec.Slot.HEAD, MobVisualSpec.Slot.parse("head", "test.slot"));
    assertEquals(MobVisualSpec.Slot.MAIN_HAND, MobVisualSpec.Slot.parse("mainHand", "test.slot"));
    assertEquals(MobVisualSpec.Slot.OFF_HAND, MobVisualSpec.Slot.parse("off_hand", "test.slot"));
  }

  @Test
  void rejectsUnknownSlots() {
    assertThrows(IllegalArgumentException.class, () -> MobVisualSpec.Slot.parse("chest", "test.slot"));
  }
}
