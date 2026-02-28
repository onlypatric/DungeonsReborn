package dev.patric.dungeonsreborn.mobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MobYamlRegistryAiV4HelpersTest {

  @Test
  void parseAiIntentRejectsInvalidTypeWithPath() throws Exception {
    Method parseIntent = MobYamlRegistry.class.getDeclaredMethod("parseAiIntent", Object.class, String.class);
    parseIntent.setAccessible(true);

    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("type", "not_a_real_intent");

    InvocationTargetException ex = assertThrows(InvocationTargetException.class,
        () -> parseIntent.invoke(null, raw, "mobs.ghost.ai.selectors[0].intent"));
    Throwable cause = assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    assertEquals("mobs.ghost.ai.selectors[0].intent.type: invalid intent type=not_a_real_intent", cause.getMessage());
  }

  @Test
  void parseAiConditionRejectsUnknownLeafWithPath() throws Exception {
    Method parseCondition = MobYamlRegistry.class.getDeclaredMethod("parseAiCondition", Object.class, String.class);
    parseCondition.setAccessible(true);

    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("foo", 1);

    InvocationTargetException ex = assertThrows(InvocationTargetException.class,
        () -> parseCondition.invoke(null, raw, "mobs.ghost.ai.selectors[0].when"));
    Throwable cause = assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    assertTrue(cause.getMessage().startsWith("mobs.ghost.ai.selectors[0].when: invalid condition"));
  }

  @Test
  void parseAiIntentAcceptsCastOnlyWithAbility() throws Exception {
    Method parseIntent = MobYamlRegistry.class.getDeclaredMethod("parseAiIntent", Object.class, String.class);
    parseIntent.setAccessible(true);

    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("type", "CAST_ONLY");
    raw.put("ability", "ability_ghost_bite");
    raw.put("castCooldownTicks", 12);

    Object parsed = parseIntent.invoke(null, raw, "mobs.ghost.ai.selectors[0].intent");
    MobAiIntentSpec intent = assertInstanceOf(MobAiIntentSpec.class, parsed);
    assertEquals(MobAiIntentType.CAST_ONLY, intent.type());
    assertEquals("ability_ghost_bite", intent.abilityId());
    assertEquals(12L, intent.castCooldownTicks());
  }
}
