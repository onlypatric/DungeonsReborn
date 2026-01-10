package dev.patric.dungeonsreborn.effects.math;

import org.bukkit.util.Vector;

public final class Mathf {
  private Mathf() {
  }

  public static double clamp(double v, double min, double max) {
    return Math.max(min, Math.min(max, v));
  }

  public static double lerp(double a, double b, double t) {
    return a + (b - a) * t;
  }

  public static double invLerp(double a, double b, double v) {
    if (Math.abs(b - a) < 1e-12) {
      return 0.0;
    }
    return (v - a) / (b - a);
  }

  public static double smoothstep(double t) {
    t = clamp(t, 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
  }

  /**
   * Spherical interpolation between two direction vectors.
   */
  public static Vector slerpDirection(Vector a, Vector b, double t) {
    if (a == null || b == null) {
      return new Vector(0, 0, 0);
    }
    Vector na = a.clone();
    Vector nb = b.clone();
    if (na.lengthSquared() < 1e-12 || nb.lengthSquared() < 1e-12) {
      return new Vector(0, 0, 0);
    }
    na.normalize();
    nb.normalize();

    double dot = clamp(na.dot(nb), -1.0, 1.0);
    double omega = Math.acos(dot);
    if (omega < 1e-6) {
      return na.multiply(1.0 - t).add(nb.multiply(t)).normalize();
    }
    double sinOmega = Math.sin(omega);
    double s0 = Math.sin((1.0 - t) * omega) / sinOmega;
    double s1 = Math.sin(t * omega) / sinOmega;
    return na.multiply(s0).add(nb.multiply(s1));
  }
}

