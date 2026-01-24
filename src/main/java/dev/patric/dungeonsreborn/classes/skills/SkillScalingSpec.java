package dev.patric.dungeonsreborn.classes.skills;

public record SkillScalingSpec(SkillScalingMode mode, SkillCurve curve, double scale, double offset) {
  public static SkillScalingSpec flat() {
    return new SkillScalingSpec(SkillScalingMode.FLAT, SkillCurve.LINEAR, 1.0, 0.0);
  }

  public double multiplier(int rank) {
    if (rank <= 0) {
      return 0.0;
    }
    return switch (mode == null ? SkillScalingMode.FLAT : mode) {
      case PER_RANK -> rank;
      case PERCENT -> rank / 100.0;
      case CURVE -> (offset + (scale * (curve == null ? SkillCurve.LINEAR : curve).value(rank)));
      case FLAT -> 1.0;
    };
  }
}
