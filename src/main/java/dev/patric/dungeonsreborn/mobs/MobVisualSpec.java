package dev.patric.dungeonsreborn.mobs;

import java.util.Locale;
import java.util.Objects;

import org.bukkit.Material;

public record MobVisualSpec(String texturePath, String modelKey, Slot slot, Material material) {
  public enum Slot {
    HEAD,
    MAIN_HAND,
    OFF_HAND;

    public static Slot parse(String raw, String path) {
      if (raw == null || raw.isBlank()) {
        return HEAD;
      }
      String key = raw.trim().toLowerCase(Locale.ROOT);
      return switch (key) {
        case "head", "helmet" -> HEAD;
        case "mainhand", "main_hand", "main-hand", "hand", "main" -> MAIN_HAND;
        case "offhand", "off_hand", "off-hand", "off" -> OFF_HAND;
        default -> throw new IllegalArgumentException(path + ": invalid visual slot " + raw);
      };
    }
  }

  public MobVisualSpec {
    texturePath = Objects.requireNonNull(texturePath, "texturePath");
    modelKey = Objects.requireNonNull(modelKey, "modelKey");
    slot = Objects.requireNonNull(slot, "slot");
    material = Objects.requireNonNull(material, "material");
  }
}
