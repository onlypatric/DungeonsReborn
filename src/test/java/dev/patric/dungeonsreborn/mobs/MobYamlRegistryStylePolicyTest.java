package dev.patric.dungeonsreborn.mobs;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import sun.misc.Unsafe;

class MobYamlRegistryStylePolicyTest {

  @Test
  void tierDoesNotFallbackToStylePresetWhenLegacyFlagDisabled() throws Exception {
    MobYamlRegistry registry = newRegistry(false);
    MobStyleSpec resolved = resolve(registry, new LinkedHashMap<>(), "mobs.mob_p01_test", "P01");
    assertNull(resolved);
  }

  @Test
  void tierFallbackToStylePresetWorksWhenLegacyFlagEnabled() throws Exception {
    MobYamlRegistry registry = newRegistry(true);
    MobStyleSpec resolved = resolve(registry, new LinkedHashMap<>(), "mobs.mob_p01_test", "P01");
    assertNotNull(resolved);
  }

  @Test
  void explicitStylePresetAlwaysResolvesIndependentlyFromTierPolicy() throws Exception {
    MobYamlRegistry registry = newRegistry(false);
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("stylePreset", "P01");
    MobStyleSpec resolved = resolve(registry, raw, "mobs.mob_p01_test", "IGNORED_TIER");
    assertNotNull(resolved);
  }

  private static MobYamlRegistry newRegistry(boolean useTierAsPreset) throws Exception {
    Unsafe unsafe = unsafe();
    MobYamlRegistry registry = (MobYamlRegistry) unsafe.allocateInstance(MobYamlRegistry.class);

    Field useTierField = MobYamlRegistry.class.getDeclaredField("useTierAsPreset");
    useTierField.setAccessible(true);
    useTierField.setBoolean(registry, useTierAsPreset);

    Field stylePresetsField = MobYamlRegistry.class.getDeclaredField("stylePresets");
    stylePresetsField.setAccessible(true);
    Map<String, MobStyleSpec> stylePresets = new HashMap<>();
    stylePresets.put("p01", new MobStyleSpec(null, Boolean.TRUE, null));
    stylePresetsField.set(registry, stylePresets);

    return registry;
  }

  @SuppressWarnings("removal")
  private static Unsafe unsafe() throws Exception {
    Field f = Unsafe.class.getDeclaredField("theUnsafe");
    f.setAccessible(true);
    return (Unsafe) f.get(null);
  }

  @SuppressWarnings("unchecked")
  private static MobStyleSpec resolve(MobYamlRegistry registry, Map<String, Object> raw, String base, String tier)
      throws Exception {
    Method method = MobYamlRegistry.class.getDeclaredMethod("resolveStyleSpec", Map.class, String.class, String.class);
    method.setAccessible(true);
    return (MobStyleSpec) method.invoke(registry, raw, base, tier);
  }
}
