package dev.patric.dungeonsreborn.textures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TexturePathNormalizerTest {
  @Test
  void normalizesAndPrefixesExpectedRoot() {
    assertEquals(
        "items/custom/sword",
        TexturePathNormalizer.normalizeTexturePath("custom/sword.png", "items"));
    assertEquals(
        "mobs/boss/stag",
        TexturePathNormalizer.normalizeTexturePath("mobs/boss/stag", "mobs"));
  }

  @Test
  void rejectsTraversal() {
    assertThrows(
        IllegalArgumentException.class,
        () -> TexturePathNormalizer.normalizeTexturePath("../secrets.png", "items"));
    assertThrows(
        IllegalArgumentException.class,
        () -> TexturePathNormalizer.normalizeModelPath("../../../hack"));
  }
}
