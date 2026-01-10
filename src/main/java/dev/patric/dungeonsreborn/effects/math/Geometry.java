package dev.patric.dungeonsreborn.effects.math;

import org.bukkit.util.Vector;

public final class Geometry {
  private Geometry() {
  }

  /**
   * Squared distance from point {@code p} to segment {@code [a,b]} (in 3D).
   */
  public static double distanceSquaredPointToSegment(Vector p, Vector a, Vector b) {
    Vector ab = b.clone().subtract(a);
    double abLenSq = ab.lengthSquared();
    if (abLenSq < 1e-9) {
      return p.clone().subtract(a).lengthSquared();
    }

    double t = p.clone().subtract(a).dot(ab) / abLenSq;
    t = Math.max(0.0, Math.min(1.0, t));

    Vector closest = a.clone().add(ab.multiply(t));
    return p.clone().subtract(closest).lengthSquared();
  }
}

