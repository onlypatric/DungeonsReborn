package dev.patric.dungeonsreborn.textures;

import java.io.File;
import java.util.Objects;

public record TextureAssetRef(
    String category,
    String requestedPath,
    String normalizedPath,
    String modelPath,
    String namespacedModelKey,
    File pngFile,
    File mcmetaFile) {

  public TextureAssetRef {
    category = Objects.requireNonNull(category, "category");
    requestedPath = Objects.requireNonNull(requestedPath, "requestedPath");
    normalizedPath = Objects.requireNonNull(normalizedPath, "normalizedPath");
    modelPath = Objects.requireNonNull(modelPath, "modelPath");
    namespacedModelKey = Objects.requireNonNull(namespacedModelKey, "namespacedModelKey");
    pngFile = Objects.requireNonNull(pngFile, "pngFile");
  }
}
