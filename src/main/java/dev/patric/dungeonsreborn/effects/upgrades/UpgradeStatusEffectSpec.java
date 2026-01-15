package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffectType;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

public record UpgradeStatusEffectSpec(
    PotionEffectType type,
    int durationTicks,
    int amplifier,
    double chance,
    boolean ambient,
    boolean particles,
    boolean icon
) {
  public UpgradeStatusEffectSpec {
    Objects.requireNonNull(type, "type");
    if (durationTicks <= 0) {
      throw new IllegalArgumentException("durationTicks must be > 0");
    }
    if (!Double.isFinite(chance) || chance < 0.0) {
      throw new IllegalArgumentException("chance must be >= 0");
    }
  }

  public String toRecord() {
    String key = type.getKey().toString().toLowerCase(Locale.ROOT);
    return key + "|" + durationTicks + "|" + amplifier + "|" + chance + "|" + ambient + "|" + particles + "|" + icon;
  }

  public static UpgradeStatusEffectSpec fromRecord(String record) {
    if (record == null || record.isBlank()) {
      return null;
    }
    String[] parts = record.split("\\|");
    if (parts.length < 4) {
      return null;
    }
    NamespacedKey key = NamespacedKey.fromString(parts[0].trim().toLowerCase(Locale.ROOT));
    if (key == null) {
      return null;
    }
    PotionEffectType type = RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT).get(key);
    if (type == null) {
      return null;
    }
    try {
      int duration = Integer.parseInt(parts[1].trim());
      int amplifier = Integer.parseInt(parts[2].trim());
      double chance = Double.parseDouble(parts[3].trim());
      boolean ambient = parts.length > 4 && Boolean.parseBoolean(parts[4].trim());
      boolean particles = parts.length > 5 ? Boolean.parseBoolean(parts[5].trim()) : true;
      boolean icon = parts.length > 6 ? Boolean.parseBoolean(parts[6].trim()) : true;
      if (duration <= 0) {
        return null;
      }
      return new UpgradeStatusEffectSpec(type, duration, amplifier, chance, ambient, particles, icon);
    } catch (Exception ignored) {
      return null;
    }
  }

  public static List<UpgradeStatusEffectSpec> parseRecords(List<String> records) {
    if (records == null || records.isEmpty()) {
      return List.of();
    }
    List<UpgradeStatusEffectSpec> out = new ArrayList<>();
    for (String record : records) {
      UpgradeStatusEffectSpec spec = fromRecord(record);
      if (spec != null) {
        out.add(spec);
      }
    }
    return out;
  }
}
