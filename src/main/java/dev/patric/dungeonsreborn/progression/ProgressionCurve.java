package dev.patric.dungeonsreborn.progression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.util.YamlValues;

public final class ProgressionCurve {
  public enum Type {
    VANILLA,
    CUSTOM_THRESHOLDS
  }

  private final Type type;
  private final List<Integer> thresholds;
  private final int softCapStart;
  private final double softCapMultiplier;

  private ProgressionCurve(Type type, List<Integer> thresholds, int softCapStart, double softCapMultiplier) {
    this.type = Objects.requireNonNull(type, "type");
    this.thresholds = Collections.unmodifiableList(new ArrayList<>(thresholds));
    this.softCapStart = Math.max(0, softCapStart);
    if (!Double.isFinite(softCapMultiplier) || softCapMultiplier < 0.0) {
      this.softCapMultiplier = 1.0;
    } else {
      this.softCapMultiplier = softCapMultiplier;
    }
  }

  public static ProgressionCurve fromConfig(ConfigurationSection section) {
    if (section == null) {
      return new ProgressionCurve(Type.VANILLA, List.of(), 0, 1.0);
    }
    String rawType = YamlValues.string(section, "type", "VANILLA");
    Type type;
    try {
      type = Type.valueOf(rawType.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      type = Type.VANILLA;
    }
    List<Integer> thresholds = section.getIntegerList("thresholds");
    if (thresholds != null && thresholds.size() > 1) {
      thresholds = new ArrayList<>(thresholds);
      thresholds.sort(Integer::compareTo);
    }
    int softCapStart = section.getInt("softCapStart", 0);
    double softCapMultiplier = section.getDouble("softCapMultiplier", 1.0);
    return new ProgressionCurve(type, thresholds == null ? List.of() : thresholds, softCapStart, softCapMultiplier);
  }

  public int levelFor(Player player, int totalXp) {
    if (type == Type.CUSTOM_THRESHOLDS && !thresholds.isEmpty()) {
      int level = 1;
      for (int i = 0; i < thresholds.size(); i++) {
        if (totalXp >= thresholds.get(i)) {
          level = i + 1;
        }
      }
      return level;
    }
    if (player != null) {
      return Math.max(1, player.getLevel());
    }
    return 1;
  }

  public int applySoftCap(int totalXp, int award) {
    if (award <= 0) {
      return 0;
    }
    if (softCapStart > 0 && totalXp >= softCapStart) {
      double scaled = award * softCapMultiplier;
      if (!Double.isFinite(scaled)) {
        return award;
      }
      return Math.max(0, (int) Math.round(scaled));
    }
    return award;
  }
}
