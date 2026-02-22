package dev.patric.dungeonsreborn.effects.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class EffectsCombatMigratorTest {
  @Test
  void migrateRewritesLegacyTriggerAndDamageKeys() throws Exception {
    Path dir = Files.createTempDirectory("dr-combat-migrate");
    Path effects = dir.resolve("effects.yml");
    Files.writeString(effects, """
        abilities:
          demo:
            triggers:
              - type: on_hit
                ability: demo_proc
                cooldownTicks: 20
            action:
              type: damage
              amount: 5
              armor_pen_flat: 2
        """);

    EffectsCombatMigrator.MigrationReport report = EffectsCombatMigrator.migrate(effects.toFile(), dir.resolve("missing").toFile(), false);
    assertEquals(1, report.filesScanned());
    assertEquals(1, report.filesChanged());
    assertTrue(report.nodesChanged() >= 2);
    assertEquals(0, report.unresolvedNodes());

    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(effects.toFile());
    var trigger = yaml.getMapList("abilities.demo.triggers").get(0);
    assertEquals("event", String.valueOf(trigger.get("type")));
    assertEquals("ON_ATTACK_HIT", String.valueOf(trigger.get("event")));
    Object cooldown = trigger.get("cooldown");
    assertTrue(cooldown instanceof Map<?, ?>);
    assertEquals(20, ((Number) ((Map<?, ?>) cooldown).get("ticks")).intValue());
    assertEquals(2, yaml.getInt("abilities.demo.action.armorPenFlat"));
  }

  @Test
  void migrateMarksOnSprintAsUnresolved() throws Exception {
    Path dir = Files.createTempDirectory("dr-combat-migrate-2");
    Path effects = dir.resolve("effects.yml");
    Files.writeString(effects, """
        abilities:
          demo:
            triggers:
              - type: event
                event: ON_SPRINT
                ability: demo_proc
        """);

    EffectsCombatMigrator.MigrationReport report = EffectsCombatMigrator.migrate(effects.toFile(), dir.resolve("missing").toFile(), false);
    assertEquals(1, report.unresolvedNodes());
    assertTrue(report.details().stream().anyMatch(s -> s.contains("ON_SPRINT")));
  }
}
