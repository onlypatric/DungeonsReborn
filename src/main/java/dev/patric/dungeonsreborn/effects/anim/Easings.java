package dev.patric.dungeonsreborn.effects.anim;

public final class Easings {
  private Easings() {
  }

  public static double linear(double t) {
    return clamp01(t);
  }

  public static double inOutCubic(double t) {
    t = clamp01(t);
    return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
  }

  public static double outQuad(double t) {
    t = clamp01(t);
    return 1 - (1 - t) * (1 - t);
  }

  private static double clamp01(double t) {
    if (t <= 0) {
      return 0;
    }
    if (t >= 1) {
      return 1;
    }
    return t;
  }
}

