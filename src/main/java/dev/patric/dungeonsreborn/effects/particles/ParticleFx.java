package dev.patric.dungeonsreborn.effects.particles;

import java.util.Objects;

import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.util.Vector;

public final class ParticleFx {
  private ParticleFx() {
  }

  public static void line(World world, Location origin, Vector direction, double length, double step,
      Particle particle, int count, double offset, double extra) {
    Objects.requireNonNull(world, "world");
    Objects.requireNonNull(origin, "origin");
    Objects.requireNonNull(direction, "direction");
    Objects.requireNonNull(particle, "particle");

    Vector dir = direction.clone();
    if (dir.lengthSquared() < 1e-9) {
      dir.setX(0).setY(0).setZ(1);
    }
    dir.normalize();

    Location pos = origin.clone();
    for (double d = 0.0; d <= length + 1e-9; d += step) {
      world.spawnParticle(particle, pos, count, offset, offset, offset, extra);
      pos.add(dir.getX() * step, dir.getY() * step, dir.getZ() * step);
    }
  }

  /**
   * Renders a ring oriented perpendicular to {@code normal}. If {@code normal} is near-zero, Y-up is used.
   */
  public static void ring(World world, Location center, Vector normal, double radius, int points,
      Particle particle, int count, double offset, double extra) {
    Objects.requireNonNull(world, "world");
    Objects.requireNonNull(center, "center");
    Objects.requireNonNull(normal, "normal");
    Objects.requireNonNull(particle, "particle");

    Vector n = normal.clone();
    if (n.lengthSquared() < 1e-9) {
      n.setX(0).setY(1).setZ(0);
    }
    n.normalize();

    // Pick a vector not parallel to n to build an orthonormal basis.
    Vector a = Math.abs(n.getY()) < 0.99 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
    Vector u = n.clone().crossProduct(a);
    if (u.lengthSquared() < 1e-9) {
      a = new Vector(0, 0, 1);
      u = n.clone().crossProduct(a);
    }
    u.normalize();
    Vector v = n.clone().crossProduct(u).normalize();

    double step = (Math.PI * 2.0) / points;
    Location tmp = center.clone();
    for (int i = 0; i < points; i++) {
      double t = i * step;
      double x = (u.getX() * Math.cos(t) + v.getX() * Math.sin(t)) * radius;
      double y = (u.getY() * Math.cos(t) + v.getY() * Math.sin(t)) * radius;
      double z = (u.getZ() * Math.cos(t) + v.getZ() * Math.sin(t)) * radius;
      tmp.set(center.getX() + x, center.getY() + y, center.getZ() + z);
      world.spawnParticle(particle, tmp, count, offset, offset, offset, extra);
    }
  }
}

