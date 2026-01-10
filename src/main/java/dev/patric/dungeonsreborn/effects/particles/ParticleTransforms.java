package dev.patric.dungeonsreborn.effects.particles;

import java.util.Objects;

import org.bukkit.util.Vector;

public final class ParticleTransforms {
  private ParticleTransforms() {
  }

  /**
   * Rotates {@code v} around {@code axis} by {@code angleRad} (Rodrigues' rotation formula).
   */
  public static Vector rotateAroundAxis(Vector v, Vector axis, double angleRad) {
    Objects.requireNonNull(v, "v");
    Objects.requireNonNull(axis, "axis");
    Vector k = axis.clone();
    if (k.lengthSquared() < 1e-9) {
      return v.clone();
    }
    k.normalize();

    double cos = Math.cos(angleRad);
    double sin = Math.sin(angleRad);

    Vector term1 = v.clone().multiply(cos);
    Vector term2 = k.clone().crossProduct(v).multiply(sin);
    Vector term3 = k.clone().multiply(k.dot(v) * (1.0 - cos));
    return term1.add(term2).add(term3);
  }

  public static Vector rotateAroundY(Vector v, double angleRad) {
    Objects.requireNonNull(v, "v");
    double cos = Math.cos(angleRad);
    double sin = Math.sin(angleRad);
    double x = v.getX() * cos - v.getZ() * sin;
    double z = v.getX() * sin + v.getZ() * cos;
    return new Vector(x, v.getY(), z);
  }

  public static Vector scale(Vector v, double scale) {
    Objects.requireNonNull(v, "v");
    return v.clone().multiply(scale);
  }
}

