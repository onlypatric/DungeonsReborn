package dev.patric.dungeonsreborn.effects.math;

import org.bukkit.util.Vector;

public final class Curves {
  private Curves() {
  }

  public static Vector cubicBezier(Vector p0, Vector p1, Vector p2, Vector p3, double t) {
    double u = 1.0 - t;
    double tt = t * t;
    double uu = u * u;
    double uuu = uu * u;
    double ttt = tt * t;

    Vector out = p0.clone().multiply(uuu);
    out.add(p1.clone().multiply(3.0 * uu * t));
    out.add(p2.clone().multiply(3.0 * u * tt));
    out.add(p3.clone().multiply(ttt));
    return out;
  }

  /**
   * Approximates curve length by sampling straight segments.
   */
  public static double approximateLengthCubicBezier(Vector p0, Vector p1, Vector p2, Vector p3, int samples) {
    if (samples < 2) {
      throw new IllegalArgumentException("samples must be >= 2");
    }
    double length = 0.0;
    Vector prev = cubicBezier(p0, p1, p2, p3, 0.0);
    for (int i = 1; i < samples; i++) {
      double t = i / (double) (samples - 1);
      Vector next = cubicBezier(p0, p1, p2, p3, t);
      length += next.distance(prev);
      prev = next;
    }
    return length;
  }

  /**
   * Catmull-Rom spline segment between {@code p1} and {@code p2}.
   * <p>
   * Standard uniform Catmull-Rom (tension=0.5).
   */
  public static Vector catmullRom(Vector p0, Vector p1, Vector p2, Vector p3, double t) {
    double tt = t * t;
    double ttt = tt * t;

    Vector out = p1.clone().multiply(2.0);
    out.add(p2.clone().subtract(p0).multiply(t));
    out.add(p0.clone().multiply(2.0).subtract(p1.clone().multiply(5.0)).add(p2.clone().multiply(4.0)).subtract(p3).multiply(tt));
    out.add(p3.clone().subtract(p0).add(p1.clone().multiply(3.0)).subtract(p2.clone().multiply(3.0)).multiply(ttt));
    out.multiply(0.5);
    return out;
  }

  public static double approximateLengthCatmullRom(Vector p0, Vector p1, Vector p2, Vector p3, int samples) {
    if (samples < 2) {
      throw new IllegalArgumentException("samples must be >= 2");
    }
    double length = 0.0;
    Vector prev = catmullRom(p0, p1, p2, p3, 0.0);
    for (int i = 1; i < samples; i++) {
      double t = i / (double) (samples - 1);
      Vector next = catmullRom(p0, p1, p2, p3, t);
      length += next.distance(prev);
      prev = next;
    }
    return length;
  }
}
