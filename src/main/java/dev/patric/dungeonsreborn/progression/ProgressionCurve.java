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
    CUSTOM_THRESHOLDS,
    LINEAR
  }

  private final Type type;
  private final List<Integer> thresholds;
  private final int pointsPerLevel;
  private final int softCapStart;
  private final double softCapMultiplier;

  private ProgressionCurve(Type type, List<Integer> thresholds, int pointsPerLevel, int softCapStart,
                           double softCapMultiplier) {
    this.type = Objects.requireNonNull(type, "type");
    this.thresholds = Collections.unmodifiableList(new ArrayList<>(thresholds));
    this.pointsPerLevel = Math.max(1, pointsPerLevel);
    this.softCapStart = Math.max(0, softCapStart);
    if (!Double.isFinite(softCapMultiplier) || softCapMultiplier < 0.0) {
      this.softCapMultiplier = 1.0;
    } else {
      this.softCapMultiplier = softCapMultiplier;
    }
  }

  public static ProgressionCurve fromConfig(ConfigurationSection section) {
    if (section == null) {
      return new ProgressionCurve(Type.VANILLA, List.of(), 1, 0, 1.0);
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
    int pointsPerLevel = section.getInt("pointsPerLevel", 100);
    int softCapStart = section.getInt("softCapStart", 0);
    double softCapMultiplier = section.getDouble("softCapMultiplier", 1.0);
    return new ProgressionCurve(type, thresholds == null ? List.of() : thresholds, pointsPerLevel, softCapStart,
        softCapMultiplier);
  }

  public int levelFor(Player player, int totalXp) {
    if (type == Type.LINEAR) {
      return levelForLinear(totalXp);
    }
    if (type == Type.CUSTOM_THRESHOLDS && !thresholds.isEmpty()) {
      return levelForThresholds(totalXp);
    }
    if (player != null) {
      return Math.max(1, player.getLevel());
    }
    return 1;
  }

  public int levelForTotal(int totalXp) {
    if (type == Type.LINEAR) {
      return levelForLinear(totalXp);
    }
    if (type == Type.CUSTOM_THRESHOLDS && !thresholds.isEmpty()) {
      return levelForThresholds(totalXp);
    }
    return 1;
  }

  public double progressForTotal(int totalXp) {
    if (type == Type.LINEAR) {
      if (pointsPerLevel <= 0) {
        return 0.0;
      }
      int progress = Math.floorMod(totalXp, pointsPerLevel);
      return Math.max(0.0, Math.min(1.0, progress / (double) pointsPerLevel));
    }
    if (type != Type.CUSTOM_THRESHOLDS || thresholds.isEmpty()) {
      return 0.0;
    }
    int levelIndex = 0;
    for (int i = 0; i < thresholds.size(); i++) {
      if (totalXp >= thresholds.get(i)) {
        levelIndex = i;
      } else {
        break;
      }
    }
    int start = thresholds.get(levelIndex);
    int end = levelIndex + 1 < thresholds.size() ? thresholds.get(levelIndex + 1) : start;
    if (end <= start) {
      return 1.0;
    }
    double fraction = (totalXp - start) / (double) (end - start);
    if (fraction < 0.0) {
      return 0.0;
    }
    if (fraction > 1.0) {
      return 1.0;
    }
    return fraction;
  }

  public int pointsForProgress(int totalXp, double fraction) {
    if (type == Type.LINEAR) {
      if (!Double.isFinite(fraction) || fraction <= 0.0) {
        return 0;
      }
      if (fraction > 1.0) {
        fraction = 1.0;
      }
      return (int) Math.round(pointsPerLevel * fraction);
    }
    if (type != Type.CUSTOM_THRESHOLDS || thresholds.isEmpty()) {
      return 0;
    }
    if (!Double.isFinite(fraction) || fraction <= 0.0) {
      return 0;
    }
    if (fraction > 1.0) {
      fraction = 1.0;
    }
    int levelIndex = 0;
    for (int i = 0; i < thresholds.size(); i++) {
      if (totalXp >= thresholds.get(i)) {
        levelIndex = i;
      } else {
        break;
      }
    }
    int start = thresholds.get(levelIndex);
    int end = levelIndex + 1 < thresholds.size() ? thresholds.get(levelIndex + 1) : start;
    if (end <= start) {
      return 0;
    }
    return (int) Math.round((end - start) * fraction);
  }

  public int totalForLevel(int level) {
    if (type == Type.LINEAR) {
      int safeLevel = Math.max(1, level);
      return Math.max(0, (safeLevel - 1) * pointsPerLevel);
    }
    if (type != Type.CUSTOM_THRESHOLDS || thresholds.isEmpty()) {
      return 0;
    }
    int index = Math.max(1, level) - 1;
    if (index >= thresholds.size()) {
      index = thresholds.size() - 1;
    }
    return thresholds.get(index);
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

  private int levelForLinear(int totalXp) {
    if (pointsPerLevel <= 0) {
      return 1;
    }
    int level = (int) Math.floor(totalXp / (double) pointsPerLevel) + 1;
    return Math.max(1, level);
  }

  private int levelForThresholds(int totalXp) {
    int level = 1;
    for (int i = 0; i < thresholds.size(); i++) {
      if (totalXp >= thresholds.get(i)) {
        level = i + 1;
      }
    }
    return level;
  }
}
