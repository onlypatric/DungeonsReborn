package dev.patric.dungeonsreborn.classes.skills;

import java.util.Locale;

public enum SkillCurve {
  LINEAR,
  QUAD,
  CUBIC,
  SQRT,
  LOG,
  EXP;

  public static SkillCurve parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return LINEAR;
    }
    return switch (raw.trim().toUpperCase(Locale.ROOT)) {
      case "QUAD", "QUADRATIC" -> QUAD;
      case "CUBIC" -> CUBIC;
      case "SQRT", "SQUARE_ROOT" -> SQRT;
      case "LOG", "LOGARITHMIC" -> LOG;
      case "EXP", "EXPONENTIAL" -> EXP;
      default -> LINEAR;
    };
  }

  public double value(double rank) {
    double r = Math.max(0.0, rank);
    return switch (this) {
      case QUAD -> r * r;
      case CUBIC -> r * r * r;
      case SQRT -> Math.sqrt(r);
      case LOG -> Math.log1p(r);
      case EXP -> Math.expm1(r);
      case LINEAR -> r;
    };
  }
}
