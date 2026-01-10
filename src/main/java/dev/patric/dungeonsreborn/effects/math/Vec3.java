package dev.patric.dungeonsreborn.effects.math;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

public record Vec3(double x, double y, double z) {
  public static Vec3 of(double x, double y, double z) {
    return new Vec3(x, y, z);
  }

  public static Vec3 from(Vector v) {
    Objects.requireNonNull(v, "v");
    return new Vec3(v.getX(), v.getY(), v.getZ());
  }

  public static Vec3 from(Location loc) {
    Objects.requireNonNull(loc, "loc");
    return new Vec3(loc.getX(), loc.getY(), loc.getZ());
  }

  public Vector toVector() {
    return new Vector(x, y, z);
  }

  public Location toLocation(World world) {
    Objects.requireNonNull(world, "world");
    return new Location(world, x, y, z);
  }

  public Vec3 add(Vec3 o) {
    Objects.requireNonNull(o, "o");
    return new Vec3(x + o.x, y + o.y, z + o.z);
  }

  public Vec3 sub(Vec3 o) {
    Objects.requireNonNull(o, "o");
    return new Vec3(x - o.x, y - o.y, z - o.z);
  }

  public Vec3 mul(double s) {
    return new Vec3(x * s, y * s, z * s);
  }

  public double dot(Vec3 o) {
    Objects.requireNonNull(o, "o");
    return x * o.x + y * o.y + z * o.z;
  }

  public Vec3 cross(Vec3 o) {
    Objects.requireNonNull(o, "o");
    return new Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x);
  }

  public double lengthSquared() {
    return x * x + y * y + z * z;
  }

  public double length() {
    return Math.sqrt(lengthSquared());
  }

  public Vec3 normalized() {
    double len = length();
    if (len < 1e-12) {
      return new Vec3(0, 0, 0);
    }
    return mul(1.0 / len);
  }
}

