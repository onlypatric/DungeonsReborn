package dev.patric.dungeonsreborn.effects.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class EffectsYamlAbilitiesHelpersTest {
  @Test
  void parseStringSetHandlesListAndSingle() throws Exception {
    Set<String> listResult = YamlReaders.parseStringSet(List.of("one", "two"), null, "test.list");
    assertEquals(2, listResult.size());
    assertTrue(listResult.contains("one"));
    assertTrue(listResult.contains("two"));

    Set<String> singleResult = YamlReaders.parseStringSet(null, "alpha", "test.single");
    assertEquals(Set.of("alpha"), singleResult);
  }

  @Test
  void easingIdDefaultsWhenUnknown() throws Exception {
    Method method = EffectsYamlAbilities.class.getDeclaredMethod("easingId", Map.class, String.class);
    method.setAccessible(true);

    Object easing = method.invoke(null, Map.of("easing", "linear"), "test.easing");
    assertEquals("LINEAR", String.valueOf(easing));

    Object fallback = method.invoke(null, Map.of("easing", "not_real"), "test.easing");
    assertEquals("IN_OUT_CUBIC", String.valueOf(fallback));
  }
}
