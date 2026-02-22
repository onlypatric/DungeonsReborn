package dev.patric.dungeonsreborn.textures;

import java.io.File;
import java.util.List;

public record TextureBuildResult(
    boolean success,
    int texturesDiscovered,
    int modelsWritten,
    File buildDir,
    File zipFile,
    String zipSha1,
    List<String> warnings,
    List<String> errors) {

  public static TextureBuildResult disabled() {
    return new TextureBuildResult(
        false,
        0,
        0,
        null,
        null,
        "",
        List.of("textures disabled"),
        List.of());
  }

  public int warningCount() {
    return warnings == null ? 0 : warnings.size();
  }

  public int errorCount() {
    return errors == null ? 0 : errors.size();
  }
}
