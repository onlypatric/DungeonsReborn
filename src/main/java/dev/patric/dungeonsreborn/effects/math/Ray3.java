package dev.patric.dungeonsreborn.effects.math;

import java.util.Objects;

public record Ray3(Vec3 origin, Vec3 direction) {
  public Ray3 {
    Objects.requireNonNull(origin, "origin");
    Objects.requireNonNull(direction, "direction");
    direction = direction.normalized();
  }

  public Vec3 at(double t) {
    return origin.add(direction.mul(t));
  }
}

