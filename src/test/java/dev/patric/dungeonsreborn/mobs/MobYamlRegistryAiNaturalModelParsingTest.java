package dev.patric.dungeonsreborn.mobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import sun.misc.Unsafe;

class MobYamlRegistryAiNaturalModelParsingTest {

  @Test
  void parseAiSpecAcceptsNaturalModelTargetSources() throws Exception {
    MobYamlRegistry registry = newRegistry();
    Method parse = MobYamlRegistry.class.getDeclaredMethod("parseAiSpec", Map.class, String.class, MobAiSpec.class);
    parse.setAccessible(true);

    Map<String, Object> source = new LinkedHashMap<>();
    source.put("type", "LAST_ATTACKER");
    source.put("memoryTicks", 80);
    source.put("priority", 10);

    Map<String, Object> targeting = new LinkedHashMap<>();
    targeting.put("sources", List.of(source));

    Map<String, Object> control = new LinkedHashMap<>();
    control.put("mode", "FULL_OVERRIDE");

    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("version", "V4");
    raw.put("engine", "V3");
    raw.put("control", control);
    raw.put("runtimeModel", "NATURAL_V1");
    raw.put("movementPolicy", "PATHFINDER_FIRST");
    raw.put("targeting", targeting);

    MobAiSpec spec = (MobAiSpec) parse.invoke(registry, raw, "mobs.passive.ai", null);
    assertEquals(MobAiSchemaVersion.V4, spec.schemaVersion());
    assertEquals(MobAiControlMode.FULL_OVERRIDE, spec.controlMode());
    assertEquals(MobAiRuntimeModel.NATURAL_V1, spec.runtimeModel());
    assertEquals(MobAiMovementPolicy.PATHFINDER_FIRST, spec.movementPolicy());
    assertEquals(1, spec.targetSources().size());
    assertEquals(MobAiTargetSourceType.LAST_ATTACKER, spec.targetSources().get(0).type());
    assertEquals(80L, spec.targetSources().get(0).memoryTicks());
  }

  @Test
  void parseAiSpecRejectsInvalidTargetSourceTypeWithPath() throws Exception {
    MobYamlRegistry registry = newRegistry();
    Method parse = MobYamlRegistry.class.getDeclaredMethod("parseAiSpec", Map.class, String.class, MobAiSpec.class);
    parse.setAccessible(true);

    Map<String, Object> source = new LinkedHashMap<>();
    source.put("type", "BAD_SOURCE");
    Map<String, Object> targeting = new LinkedHashMap<>();
    targeting.put("sources", List.of(source));
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("version", "V4");
    raw.put("targeting", targeting);

    InvocationTargetException ex = assertThrows(InvocationTargetException.class,
        () -> parse.invoke(registry, raw, "mobs.passive.ai", null));
    Throwable cause = assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    assertTrue(cause.getMessage().contains("mobs.passive.ai.targeting.sources[0].type"));
  }

  private static MobYamlRegistry newRegistry() throws Exception {
    return (MobYamlRegistry) unsafe().allocateInstance(MobYamlRegistry.class);
  }

  @SuppressWarnings("removal")
  private static Unsafe unsafe() throws Exception {
    Field f = Unsafe.class.getDeclaredField("theUnsafe");
    f.setAccessible(true);
    return (Unsafe) f.get(null);
  }
}
