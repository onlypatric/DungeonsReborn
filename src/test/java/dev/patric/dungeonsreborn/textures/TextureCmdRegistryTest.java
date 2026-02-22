package dev.patric.dungeonsreborn.textures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextureCmdRegistryTest {
  @Test
  void persistsAssignedMappings(@TempDir Path tempDir) {
    File file = tempDir.resolve("texture-cmd-registry.yml").toFile();
    TextureCmdRegistry first = new TextureCmdRegistry(file, 10000);
    first.load();

    int sword = first.assign("dungeonsreborn:items/custom/sword");
    int wand = first.assign("dungeonsreborn:items/custom/wand");
    assertTrue(sword >= 10000);
    assertEquals(sword, first.assign("dungeonsreborn:items/custom/sword"));

    TextureCmdRegistry second = new TextureCmdRegistry(file, 10000);
    second.load();
    assertEquals(sword, second.assign("dungeonsreborn:items/custom/sword"));
    assertEquals(wand, second.assign("dungeonsreborn:items/custom/wand"));
  }
}
