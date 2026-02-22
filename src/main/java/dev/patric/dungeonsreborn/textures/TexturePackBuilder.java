package dev.patric.dungeonsreborn.textures;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Formatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class TexturePackBuilder {
  public record CustomModel(String modelPath, String modelJson) {
  }

  public TextureBuildResult build(TextureService.Config config, Collection<TextureAssetRef> assets) {
    return build(config, assets, List.of());
  }

  public TextureBuildResult build(
      TextureService.Config config,
      Collection<TextureAssetRef> assets,
      Collection<CustomModel> customModels) {
    List<String> warnings = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    if (config == null || !config.enabled()) {
      return TextureBuildResult.disabled();
    }
    File buildDir = config.resolvedBuildDir();
    try {
      resetDirectory(buildDir);
      writePackMeta(buildDir, config);
      int modelsWritten = 0;
      for (TextureAssetRef ref : assets) {
        writeModelFiles(buildDir, config, ref);
        modelsWritten++;
      }
      if (customModels != null) {
        for (CustomModel customModel : customModels) {
          if (customModel == null || customModel.modelPath() == null || customModel.modelPath().isBlank()
              || customModel.modelJson() == null || customModel.modelJson().isBlank()) {
            continue;
          }
          writeCustomModelFiles(buildDir, config.namespace(), customModel);
          modelsWritten++;
        }
      }
      File zip = new File(buildDir, config.zipName());
      zipDirectory(buildDir, zip);
      String sha1 = sha1(zip);
      return new TextureBuildResult(
          true,
          assets.size(),
          modelsWritten,
          buildDir,
          zip,
          sha1,
          warnings,
          errors);
    } catch (Exception ex) {
      errors.add(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
      return new TextureBuildResult(
          false,
          assets.size(),
          0,
          buildDir,
          null,
          "",
          warnings,
          errors);
    }
  }

  private static void writePackMeta(File buildDir, TextureService.Config config) throws IOException {
    File packMeta = new File(buildDir, "pack.mcmeta");
    String escaped = config.packDescription().replace("\"", "\\\"");
    String json = "{\n"
        + "  \"pack\": {\n"
        + "    \"pack_format\": " + config.packFormat() + ",\n"
        + "    \"description\": \"" + escaped + "\"\n"
        + "  }\n"
        + "}\n";
    writeUtf8(packMeta, json);
  }

  private static void writeModelFiles(File buildDir, TextureService.Config config, TextureAssetRef ref)
      throws IOException {
    String namespace = config.namespace();
    String key = ref.modelPath();

    File itemDef = new File(buildDir, "assets/" + namespace + "/items/" + key + ".json");
    String itemDefJson = "{\n"
        + "  \"model\": {\n"
        + "    \"type\": \"minecraft:model\",\n"
        + "    \"model\": \"" + namespace + ":item/" + key + "\"\n"
        + "  }\n"
        + "}\n";
    writeUtf8(itemDef, itemDefJson);

    File modelJson = new File(buildDir, "assets/" + namespace + "/models/item/" + key + ".json");
    String model = "{\n"
        + "  \"parent\": \"minecraft:item/generated\",\n"
        + "  \"textures\": {\n"
        + "    \"layer0\": \"" + namespace + ":item/" + key + "\"\n"
        + "  }\n"
        + "}\n";
    writeUtf8(modelJson, model);

    File textureOut = new File(buildDir, "assets/" + namespace + "/textures/item/" + key + ".png");
    copyFile(ref.pngFile(), textureOut);
    if (ref.mcmetaFile() != null && ref.mcmetaFile().exists()) {
      File mcmetaOut = new File(textureOut.getPath() + ".mcmeta");
      copyFile(ref.mcmetaFile(), mcmetaOut);
    }
  }

  private static void writeCustomModelFiles(File buildDir, String namespace, CustomModel customModel)
      throws IOException {
    String key = TexturePathNormalizer.normalizeModelPath(customModel.modelPath());
    File itemDef = new File(buildDir, "assets/" + namespace + "/items/" + key + ".json");
    String itemDefJson = "{\n"
        + "  \"model\": {\n"
        + "    \"type\": \"minecraft:model\",\n"
        + "    \"model\": \"" + namespace + ":item/" + key + "\"\n"
        + "  }\n"
        + "}\n";
    writeUtf8(itemDef, itemDefJson);
    File modelJson = new File(buildDir, "assets/" + namespace + "/models/item/" + key + ".json");
    writeUtf8(modelJson, customModel.modelJson());
  }

  private static void writeUtf8(File file, String value) throws IOException {
    File parent = file.getParentFile();
    if (parent != null && !parent.exists()) {
      parent.mkdirs();
    }
    Files.writeString(file.toPath(), value, StandardCharsets.UTF_8);
  }

  private static void copyFile(File source, File target) throws IOException {
    File parent = target.getParentFile();
    if (parent != null && !parent.exists()) {
      parent.mkdirs();
    }
    Files.copy(source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  }

  private static void resetDirectory(File dir) throws IOException {
    if (dir.exists()) {
      deleteRecursively(dir);
    }
    dir.mkdirs();
  }

  private static void deleteRecursively(File file) throws IOException {
    if (file.isDirectory()) {
      File[] children = file.listFiles();
      if (children != null) {
        for (File child : children) {
          deleteRecursively(child);
        }
      }
    }
    Files.deleteIfExists(file.toPath());
  }

  private static void zipDirectory(File dir, File zipFile) throws IOException {
    File[] files = dir.listFiles();
    if (files == null) {
      return;
    }
    try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile))) {
      zipChildren(dir, dir, zipFile, out);
    }
  }

  private static void zipChildren(File root, File current, File zipFile, ZipOutputStream out) throws IOException {
    File[] files = current.listFiles();
    if (files == null) {
      return;
    }
    for (File child : files) {
      if (child.equals(zipFile)) {
        continue;
      }
      if (child.isDirectory()) {
        zipChildren(root, child, zipFile, out);
        continue;
      }
      String relative = root.toPath().relativize(child.toPath()).toString().replace('\\', '/');
      out.putNextEntry(new ZipEntry(relative));
      try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(child))) {
        in.transferTo(out);
      }
      out.closeEntry();
    }
  }

  private static String sha1(File file) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-1");
    try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) > 0) {
        digest.update(buffer, 0, read);
      }
    }
    byte[] hash = digest.digest();
    try (Formatter formatter = new Formatter()) {
      for (byte b : hash) {
        formatter.format("%02x", b);
      }
      return formatter.toString();
    }
  }
}
