package dev.patric.dungeonsreborn.textures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextureModelRegistryTest {
  @Test
  void resolvesDeterministicDefaultModelKey() {
    TextureModelRegistry.ModelKeyParts first =
        TextureModelRegistry.resolve("dungeonsreborn", "items/weapons/staff", null);
    TextureModelRegistry.ModelKeyParts second =
        TextureModelRegistry.resolve("dungeonsreborn", "items/weapons/staff", null);
    assertEquals(first, second);
    assertEquals("dungeonsreborn:items/weapons/staff", TextureModelRegistry.namespacedKey(first));
  }

  @Test
  void honorsExplicitNamespaceAndPath() {
    TextureModelRegistry.ModelKeyParts key =
        TextureModelRegistry.resolve("dungeonsreborn", "items/default", "customns:gear/blade_01");
    assertEquals("customns", key.namespace());
    assertEquals("gear/blade_01", key.path());
  }
}
