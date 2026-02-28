package dev.patric.dungeonsreborn.mobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.patric.dungeonsreborn.mobs.ai.MobAiEngineMode;

class MobAiSpecV4ValidationTest {

  @Test
  void fullOverrideRequiresV4Schema() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
        MobAiSpec.builder()
            .schemaVersion(MobAiSchemaVersion.LEGACY)
            .controlMode(MobAiControlMode.FULL_OVERRIDE)
            .build());
    assertTrue(ex.getMessage().contains("FULL_OVERRIDE requires ai.version V4"));
  }

  @Test
  void fullOverrideBuildsWithV4Schema() {
    MobAiSpec spec = MobAiSpec.builder()
        .schemaVersion(MobAiSchemaVersion.V4)
        .engineMode(MobAiEngineMode.V3)
        .controlMode(MobAiControlMode.FULL_OVERRIDE)
        .combatAuthority(MobAiCombatAuthority.ABILITY_DRIVEN)
        .addSelector(
            new MobAiSelectorSpec(
                "chase",
                10,
                MobAiConditionSpec.hasTarget(true),
                new MobAiIntentSpec(MobAiIntentType.CHASE, 0.5, 0.0, 0.0, 0.0, 0L, null, 0L, true)))
        .build();

    assertEquals(MobAiSchemaVersion.V4, spec.schemaVersion());
    assertEquals(MobAiControlMode.FULL_OVERRIDE, spec.controlMode());
    assertEquals(MobAiCombatAuthority.ABILITY_DRIVEN, spec.combatAuthority());
    assertTrue(spec.isFullOverride());
    assertEquals(1, spec.selectors().size());
  }

  @Test
  void legacySchemaWithoutOverrideRemainsValid() {
    MobAiSpec spec = MobAiSpec.builder()
        .schemaVersion(MobAiSchemaVersion.LEGACY)
        .controlMode(MobAiControlMode.DEFAULT)
        .build();
    assertEquals(MobAiSchemaVersion.LEGACY, spec.schemaVersion());
    assertEquals(MobAiControlMode.DEFAULT, spec.controlMode());
    assertTrue(!spec.isFullOverride());
  }
}
