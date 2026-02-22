package dev.patric.dungeonsreborn.textures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TexturePackBuilderTest {
  @Test
  void writesExpectedPackFiles(@TempDir Path tempDir) throws Exception {
    Path sourceRoot = tempDir.resolve("assets/textures");
    Path png = sourceRoot.resolve("items/custom/test.png");
    Files.createDirectories(png.getParent());
    Files.write(png, new byte[] {0, 1, 2, 3});
    Files.writeString(sourceRoot.resolve("items/custom/test.png.mcmeta"), "{\"animation\":{}}\n");

    TextureService.Config config = new TextureService.Config(
        true,
        "dungeonsreborn",
        true,
        "assets/textures",
        "assets/generated/resourcepack",
        "generated-pack.zip",
        "Generated",
        46,
        false,
        "",
        "",
        false,
        "",
        false,
        "0.0.0.0",
        0,
        "",
        0,
        "http",
        "/dungeonsreborn/generated-pack.zip",
        true,
        10000,
        tempDir.toFile());

    TextureAssetRef asset = new TextureAssetRef(
        "items",
        "items/custom/test.png",
        "items/custom/test",
        "items/custom/test",
        "dungeonsreborn:items/custom/test",
        png.toFile(),
        sourceRoot.resolve("items/custom/test.png.mcmeta").toFile());

    TexturePackBuilder builder = new TexturePackBuilder();
    TexturePackBuilder.CustomModel customModel = new TexturePackBuilder.CustomModel(
        "mobs/test/simple",
        "{\n  \"textures\": {\"0\": \"dungeonsreborn:item/items/custom/test\"},\n  \"elements\": []\n}\n");
    TextureBuildResult result = builder.build(config, List.of(asset), List.of(customModel));

    assertTrue(result.success());
    assertEquals(2, result.modelsWritten());
    assertEquals(1, result.texturesDiscovered());
    assertTrue(result.zipFile().exists());
    assertEquals(40, result.zipSha1().length());

    File buildDir = config.resolvedBuildDir();
    assertTrue(new File(buildDir, "pack.mcmeta").exists());
    assertTrue(new File(buildDir, "assets/dungeonsreborn/items/items/custom/test.json").exists());
    assertTrue(new File(buildDir, "assets/dungeonsreborn/models/item/items/custom/test.json").exists());
    assertTrue(new File(buildDir, "assets/dungeonsreborn/textures/item/items/custom/test.png").exists());
    assertTrue(new File(buildDir, "assets/dungeonsreborn/items/mobs/test/simple.json").exists());
    String customModelJson = Files.readString(
        new File(buildDir, "assets/dungeonsreborn/models/item/mobs/test/simple.json").toPath());
    assertTrue(customModelJson.contains("\"elements\""));
  }
}
