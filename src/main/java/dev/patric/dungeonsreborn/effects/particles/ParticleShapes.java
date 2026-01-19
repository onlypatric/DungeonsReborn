package dev.patric.dungeonsreborn.effects.particles;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import dev.patric.dungeonsreborn.effects.math.Curves;

public final class ParticleShapes {
  private ParticleShapes() {
  }

  public static void ring(Location center, Vector normal, double radius, int points, java.util.function.Consumer<Location> pointConsumer) {
    Objects.requireNonNull(center, "center");
    Objects.requireNonNull(normal, "normal");
    Objects.requireNonNull(pointConsumer, "pointConsumer");
    if (points <= 0) {
      throw new IllegalArgumentException("points must be > 0");
    }

    Vector n = normal.clone();
    if (n.lengthSquared() < 1e-9) {
      n.setX(0).setY(1).setZ(0);
    }
    n.normalize();

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
      pointConsumer.accept(tmp);
    }
  }

  public static void arc(Location center, Vector normal, double radius, double angleDegrees, int points,
      java.util.function.Consumer<Location> pointConsumer) {
    Objects.requireNonNull(center, "center");
    Objects.requireNonNull(normal, "normal");
    Objects.requireNonNull(pointConsumer, "pointConsumer");
    if (points <= 0) {
      throw new IllegalArgumentException("points must be > 0");
    }
    if (angleDegrees <= 0 || angleDegrees > 360) {
      throw new IllegalArgumentException("angleDegrees must be in (0, 360]");
    }

    double start = -Math.toRadians(angleDegrees) / 2.0;
    double step = Math.toRadians(angleDegrees) / Math.max(1, (points - 1));

    Vector n = normal.clone();
    if (n.lengthSquared() < 1e-9) {
      n.setX(0).setY(1).setZ(0);
    }
    n.normalize();

    Vector a = Math.abs(n.getY()) < 0.99 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
    Vector u = n.clone().crossProduct(a);
    if (u.lengthSquared() < 1e-9) {
      a = new Vector(0, 0, 1);
      u = n.clone().crossProduct(a);
    }
    u.normalize();
    Vector v = n.clone().crossProduct(u).normalize();

    Location tmp = center.clone();
    for (int i = 0; i < points; i++) {
      double t = start + (i * step);
      double x = (u.getX() * Math.cos(t) + v.getX() * Math.sin(t)) * radius;
      double y = (u.getY() * Math.cos(t) + v.getY() * Math.sin(t)) * radius;
      double z = (u.getZ() * Math.cos(t) + v.getZ() * Math.sin(t)) * radius;
      tmp.set(center.getX() + x, center.getY() + y, center.getZ() + z);
      pointConsumer.accept(tmp);
    }
  }

  public static void disk(Location center, Vector normal, double radius, int rings, int pointsPerRing,
      java.util.function.Consumer<Location> pointConsumer) {
    Objects.requireNonNull(center, "center");
    Objects.requireNonNull(normal, "normal");
    Objects.requireNonNull(pointConsumer, "pointConsumer");
    if (rings <= 0) {
      throw new IllegalArgumentException("rings must be > 0");
    }
    if (pointsPerRing <= 0) {
      throw new IllegalArgumentException("pointsPerRing must be > 0");
    }

    for (int i = 1; i <= rings; i++) {
      double r = radius * (i / (double) rings);
      int points = Math.max(6, (int) Math.round(pointsPerRing * (i / (double) rings)));
      ring(center, normal, r, points, pointConsumer);
    }
    pointConsumer.accept(center);
  }

  /**
   * Sphere shell points using a Fibonacci sphere distribution.
   */
  public static void sphereShell(Location center, double radius, int points, java.util.function.Consumer<Location> pointConsumer) {
    Objects.requireNonNull(center, "center");
    Objects.requireNonNull(pointConsumer, "pointConsumer");
    if (points <= 0) {
      throw new IllegalArgumentException("points must be > 0");
    }

    final double goldenAngle = Math.PI * (3 - Math.sqrt(5));
    Location tmp = center.clone();
    for (int i = 0; i < points; i++) {
      double t = i / (double) points;
      double y = 1 - 2 * t;
      double r = Math.sqrt(Math.max(0, 1 - y * y));
      double theta = goldenAngle * i;
      double x = Math.cos(theta) * r;
      double z = Math.sin(theta) * r;
      tmp.set(center.getX() + x * radius, center.getY() + y * radius, center.getZ() + z * radius);
      pointConsumer.accept(tmp);
    }
  }

  /**
   * Filled sphere points using a low-discrepancy sequence (deterministic).
   */
  public static void sphereFilled(Location center, double radius, int points, java.util.function.Consumer<Location> pointConsumer) {
    Objects.requireNonNull(center, "center");
    Objects.requireNonNull(pointConsumer, "pointConsumer");
    if (points <= 0) {
      throw new IllegalArgumentException("points must be > 0");
    }
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }

    final double goldenAngle = Math.PI * (3 - Math.sqrt(5));
    Location tmp = center.clone();
    for (int i = 0; i < points; i++) {
      double t = (i + 0.5) / points;
      double y = 1 - 2 * t;
      double r = Math.sqrt(Math.max(0, 1 - y * y));
      double theta = goldenAngle * i;
      double u = (i * 0.6180339887498949);
      u = u - Math.floor(u);
      double radial = Math.cbrt(u) * radius;
      double x = Math.cos(theta) * r * radial;
      double z = Math.sin(theta) * r * radial;
      tmp.set(center.getX() + x, center.getY() + y * radial, center.getZ() + z);
      pointConsumer.accept(tmp);
    }
  }

  public static void helix(Location start, Vector axis, double radius, double length, int turns, int points,
      java.util.function.Consumer<Location> pointConsumer) {
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(axis, "axis");
    Objects.requireNonNull(pointConsumer, "pointConsumer");
    if (points <= 0) {
      throw new IllegalArgumentException("points must be > 0");
    }
    if (turns <= 0) {
      throw new IllegalArgumentException("turns must be > 0");
    }

    Vector n = axis.clone();
    if (n.lengthSquared() < 1e-9) {
      n.setX(0).setY(0).setZ(1);
    }
    n.normalize();

    Vector a = Math.abs(n.getY()) < 0.99 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
    Vector u = n.clone().crossProduct(a);
    if (u.lengthSquared() < 1e-9) {
      a = new Vector(0, 0, 1);
      u = n.clone().crossProduct(a);
    }
    u.normalize();
    Vector v = n.clone().crossProduct(u).normalize();

    Location tmp = start.clone();
    for (int i = 0; i < points; i++) {
      double t = i / (double) (points - 1);
      double ang = (Math.PI * 2.0) * turns * t;
      double x = (u.getX() * Math.cos(ang) + v.getX() * Math.sin(ang)) * radius;
      double y = (u.getY() * Math.cos(ang) + v.getY() * Math.sin(ang)) * radius;
      double z = (u.getZ() * Math.cos(ang) + v.getZ() * Math.sin(ang)) * radius;
      Vector along = n.clone().multiply(length * t);
      tmp.set(start.getX() + along.getX() + x, start.getY() + along.getY() + y, start.getZ() + along.getZ() + z);
      pointConsumer.accept(tmp);
    }
  }

  /**
   * Samples a cubic Bezier curve with density controls (points per meter + max points).
   */
  public static void cubicBezier(Location p0, Location p1, Location p2, Location p3,
      double pointsPerMeter, int maxPoints, java.util.function.Consumer<Location> pointConsumer) {
    Objects.requireNonNull(p0, "p0");
    Objects.requireNonNull(p1, "p1");
    Objects.requireNonNull(p2, "p2");
    Objects.requireNonNull(p3, "p3");
    Objects.requireNonNull(pointConsumer, "pointConsumer");
    if (p0.getWorld() == null) {
      return;
    }
    if (pointsPerMeter <= 0) {
      throw new IllegalArgumentException("pointsPerMeter must be > 0");
    }
    if (maxPoints <= 0) {
      throw new IllegalArgumentException("maxPoints must be > 0");
    }

    Vector v0 = p0.toVector();
    Vector v1 = p1.toVector();
    Vector v2 = p2.toVector();
    Vector v3 = p3.toVector();

    double approxLen = Curves.approximateLengthCubicBezier(v0, v1, v2, v3, 24);
    int points = (int) Math.ceil(approxLen * pointsPerMeter) + 1;
    points = Math.max(2, Math.min(maxPoints, points));

    Location tmp = p0.clone();
    for (int i = 0; i < points; i++) {
      double t = points == 1 ? 0.0 : (i / (double) (points - 1));
      Vector pos = Curves.cubicBezier(v0, v1, v2, v3, t);
      tmp.set(pos.getX(), pos.getY(), pos.getZ());
      pointConsumer.accept(tmp);
    }
  }

  /**
   * Samples a Catmull-Rom spline through {@code controlPoints}.
   * <p>
   * Requires at least 2 points; end tangents are approximated by clamping endpoints.
   */
  public static void catmullRomSpline(java.util.List<Location> controlPoints,
      double pointsPerMeter, int maxPoints, java.util.function.Consumer<Location> pointConsumer) {
    Objects.requireNonNull(controlPoints, "controlPoints");
    Objects.requireNonNull(pointConsumer, "pointConsumer");
    if (pointsPerMeter <= 0) {
      throw new IllegalArgumentException("pointsPerMeter must be > 0");
    }
    if (maxPoints <= 0) {
      throw new IllegalArgumentException("maxPoints must be > 0");
    }
    if (controlPoints.size() < 2) {
      return;
    }

    Location first = controlPoints.getFirst();
    if (first == null || first.getWorld() == null) {
      return;
    }

    int n = controlPoints.size();
    Vector[] p = new Vector[n];
    for (int i = 0; i < n; i++) {
      Location loc = controlPoints.get(i);
      if (loc == null) {
        return;
      }
      p[i] = loc.toVector();
    }

    int segments = n - 1;
    double[] segLen = new double[segments];
    double totalLen = 0.0;
    for (int i = 0; i < segments; i++) {
      Vector p0 = p[Math.max(0, i - 1)];
      Vector p1 = p[i];
      Vector p2 = p[i + 1];
      Vector p3 = p[Math.min(n - 1, i + 2)];
      double len = Curves.approximateLengthCatmullRom(p0, p1, p2, p3, 12);
      segLen[i] = len;
      totalLen += len;
    }

    if (totalLen <= 1e-9) {
      // Degenerate: just output endpoints.
      pointConsumer.accept(controlPoints.getFirst());
      pointConsumer.accept(controlPoints.getLast());
      return;
    }

    int idealTotal = (int) Math.ceil(totalLen * pointsPerMeter) + 1;
    idealTotal = Math.max(2, idealTotal);
    int totalPoints = Math.min(maxPoints, idealTotal);

    // Allocate points across segments proportionally.
    int[] pointsPerSegment = new int[segments];
    int allocated = 0;
    for (int i = 0; i < segments; i++) {
      double share = segLen[i] / totalLen;
      int pts = Math.max(2, (int) Math.round(totalPoints * share));
      pointsPerSegment[i] = pts;
      allocated += pts;
    }
    // Adjust allocation to match totalPoints (+1 overlap handling is dealt with below).
    while (allocated > totalPoints && segments > 0) {
      for (int i = 0; i < segments && allocated > totalPoints; i++) {
        if (pointsPerSegment[i] > 2) {
          pointsPerSegment[i]--;
          allocated--;
        }
      }
      if (allocated == totalPoints) {
        break;
      }
      // If we can't reduce further, stop.
      boolean reducible = false;
      for (int i = 0; i < segments; i++) {
        if (pointsPerSegment[i] > 2) {
          reducible = true;
          break;
        }
      }
      if (!reducible) {
        break;
      }
    }
    while (allocated < totalPoints && segments > 0) {
      for (int i = 0; i < segments && allocated < totalPoints; i++) {
        pointsPerSegment[i]++;
        allocated++;
      }
    }

    Location tmp = first.clone();
    for (int i = 0; i < segments; i++) {
      Vector p0 = p[Math.max(0, i - 1)];
      Vector p1 = p[i];
      Vector p2 = p[i + 1];
      Vector p3 = p[Math.min(n - 1, i + 2)];
      int pts = pointsPerSegment[i];
      // Avoid duplicate points at segment boundaries by skipping the first point except for the first segment.
      int start = (i == 0) ? 0 : 1;
      for (int j = start; j < pts; j++) {
        double t = (pts <= 1) ? 0.0 : (j / (double) (pts - 1));
        Vector pos = Curves.catmullRom(p0, p1, p2, p3, t);
        tmp.set(pos.getX(), pos.getY(), pos.getZ());
        pointConsumer.accept(tmp);
      }
    }
  }

  public static void cylinderShell(Location center, Vector axis, double radius, double height, int rings, int pointsPerRing,
      java.util.function.Consumer<Location> pointConsumer) {
    Objects.requireNonNull(center, "center");
    Objects.requireNonNull(axis, "axis");
    Objects.requireNonNull(pointConsumer, "pointConsumer");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (height < 0) {
      throw new IllegalArgumentException("height must be >= 0");
    }
    if (rings <= 0) {
      throw new IllegalArgumentException("rings must be > 0");
    }
    if (pointsPerRing <= 0) {
      throw new IllegalArgumentException("pointsPerRing must be > 0");
    }

    Vector n = axis.clone();
    if (n.lengthSquared() < 1e-9) {
      n.setX(0).setY(1).setZ(0);
    }
    n.normalize();

    Location slice = center.clone();
    for (int i = 0; i <= rings; i++) {
      double t = i / (double) rings;
      double along = (t - 0.5) * height;
      slice.set(center.getX() + n.getX() * along, center.getY() + n.getY() * along, center.getZ() + n.getZ() * along);
      ring(slice, n, radius, pointsPerRing, pointConsumer);
    }
  }

  public static void coneShell(Location apex, Vector axis, double length, double angleDegrees, int rings, int pointsPerRing,
      java.util.function.Consumer<Location> pointConsumer) {
    Objects.requireNonNull(apex, "apex");
    Objects.requireNonNull(axis, "axis");
    Objects.requireNonNull(pointConsumer, "pointConsumer");
    if (length < 0) {
      throw new IllegalArgumentException("length must be >= 0");
    }
    if (angleDegrees <= 0 || angleDegrees >= 180) {
      throw new IllegalArgumentException("angleDegrees must be in (0, 180)");
    }
    if (rings <= 0) {
      throw new IllegalArgumentException("rings must be > 0");
    }
    if (pointsPerRing <= 0) {
      throw new IllegalArgumentException("pointsPerRing must be > 0");
    }

    Vector n = axis.clone();
    if (n.lengthSquared() < 1e-9) {
      n.setX(0).setY(0).setZ(1);
    }
    n.normalize();

    double tan = Math.tan(Math.toRadians(angleDegrees) / 2.0);
    Location slice = apex.clone();
    for (int i = 0; i <= rings; i++) {
      double t = i / (double) rings;
      double along = length * t;
      double r = tan * along;
      slice.set(apex.getX() + n.getX() * along, apex.getY() + n.getY() * along, apex.getZ() + n.getZ() * along);
      if (r <= 1e-9) {
        pointConsumer.accept(slice);
      } else {
        ring(slice, n, r, pointsPerRing, pointConsumer);
      }
    }
  }

  public static void polygonOutline(Location center, Vector normal, double radius, int sides, int pointsPerEdge,
      java.util.function.Consumer<Location> pointConsumer) {
    Objects.requireNonNull(center, "center");
    Objects.requireNonNull(normal, "normal");
    Objects.requireNonNull(pointConsumer, "pointConsumer");
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    if (sides < 3) {
      throw new IllegalArgumentException("sides must be >= 3");
    }
    if (pointsPerEdge <= 0) {
      throw new IllegalArgumentException("pointsPerEdge must be > 0");
    }

    Vector n = normal.clone();
    if (n.lengthSquared() < 1e-9) {
      n.setX(0).setY(1).setZ(0);
    }
    n.normalize();

    Vector a = Math.abs(n.getY()) < 0.99 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
    Vector u = n.clone().crossProduct(a);
    if (u.lengthSquared() < 1e-9) {
      a = new Vector(0, 0, 1);
      u = n.clone().crossProduct(a);
    }
    u.normalize();
    Vector v = n.clone().crossProduct(u).normalize();

    Vector[] verts = new Vector[sides];
    for (int i = 0; i < sides; i++) {
      double ang = (Math.PI * 2.0) * (i / (double) sides);
      double x = (u.getX() * Math.cos(ang) + v.getX() * Math.sin(ang)) * radius;
      double y = (u.getY() * Math.cos(ang) + v.getY() * Math.sin(ang)) * radius;
      double z = (u.getZ() * Math.cos(ang) + v.getZ() * Math.sin(ang)) * radius;
      verts[i] = new Vector(center.getX() + x, center.getY() + y, center.getZ() + z);
    }

    Location tmp = center.clone();
    for (int i = 0; i < sides; i++) {
      Vector a0 = verts[i];
      Vector b0 = verts[(i + 1) % sides];
      for (int p = 0; p <= pointsPerEdge; p++) {
        double t = pointsPerEdge == 0 ? 0.0 : (p / (double) pointsPerEdge);
        double x = a0.getX() + (b0.getX() - a0.getX()) * t;
        double y = a0.getY() + (b0.getY() - a0.getY()) * t;
        double z = a0.getZ() + (b0.getZ() - a0.getZ()) * t;
        tmp.set(x, y, z);
        pointConsumer.accept(tmp);
      }
    }
  }

  public static void boxOutline(Location center, double xRadius, double yRadius, double zRadius, double step,
      java.util.function.Consumer<Location> pointConsumer) {
    Objects.requireNonNull(center, "center");
    Objects.requireNonNull(pointConsumer, "pointConsumer");
    if (xRadius < 0 || yRadius < 0 || zRadius < 0) {
      throw new IllegalArgumentException("radii must be >= 0");
    }
    if (step <= 0) {
      throw new IllegalArgumentException("step must be > 0");
    }

    double minX = center.getX() - xRadius;
    double maxX = center.getX() + xRadius;
    double minY = center.getY() - yRadius;
    double maxY = center.getY() + yRadius;
    double minZ = center.getZ() - zRadius;
    double maxZ = center.getZ() + zRadius;

    Location tmp = center.clone();

    java.util.function.BiConsumer<Location, Location> line = (a0, b0) -> {
      double dx = b0.getX() - a0.getX();
      double dy = b0.getY() - a0.getY();
      double dz = b0.getZ() - a0.getZ();
      double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
      int points = Math.max(1, (int) Math.ceil(len / step));
      for (int i = 0; i <= points; i++) {
        double t = points == 0 ? 0.0 : (i / (double) points);
        tmp.set(a0.getX() + dx * t, a0.getY() + dy * t, a0.getZ() + dz * t);
        pointConsumer.accept(tmp);
      }
    };

    Location c000 = new Location(center.getWorld(), minX, minY, minZ);
    Location c001 = new Location(center.getWorld(), minX, minY, maxZ);
    Location c010 = new Location(center.getWorld(), minX, maxY, minZ);
    Location c011 = new Location(center.getWorld(), minX, maxY, maxZ);
    Location c100 = new Location(center.getWorld(), maxX, minY, minZ);
    Location c101 = new Location(center.getWorld(), maxX, minY, maxZ);
    Location c110 = new Location(center.getWorld(), maxX, maxY, minZ);
    Location c111 = new Location(center.getWorld(), maxX, maxY, maxZ);

    line.accept(c000, c001);
    line.accept(c000, c010);
    line.accept(c001, c011);
    line.accept(c010, c011);

    line.accept(c100, c101);
    line.accept(c100, c110);
    line.accept(c101, c111);
    line.accept(c110, c111);

    line.accept(c000, c100);
    line.accept(c001, c101);
    line.accept(c010, c110);
    line.accept(c011, c111);
  }

  public static void points(java.util.List<Location> points, java.util.function.Consumer<Location> pointConsumer) {
    Objects.requireNonNull(points, "points");
    Objects.requireNonNull(pointConsumer, "pointConsumer");
    for (Location point : points) {
      if (point != null) {
        pointConsumer.accept(point);
      }
    }
  }

  public static void polyline(java.util.List<Location> points, double step,
      java.util.function.Consumer<Location> pointConsumer) {
    Objects.requireNonNull(points, "points");
    Objects.requireNonNull(pointConsumer, "pointConsumer");
    if (step <= 0) {
      throw new IllegalArgumentException("step must be > 0");
    }
    if (points.size() < 2) {
      return;
    }
    Location tmp = points.getFirst().clone();
    for (int i = 0; i < points.size() - 1; i++) {
      Location a0 = points.get(i);
      Location b0 = points.get(i + 1);
      if (a0 == null || b0 == null) {
        continue;
      }
      double dx = b0.getX() - a0.getX();
      double dy = b0.getY() - a0.getY();
      double dz = b0.getZ() - a0.getZ();
      double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
      int segments = Math.max(1, (int) Math.ceil(len / step));
      for (int s = 0; s <= segments; s++) {
        double t = segments == 0 ? 0.0 : (s / (double) segments);
        tmp.set(a0.getX() + dx * t, a0.getY() + dy * t, a0.getZ() + dz * t);
        pointConsumer.accept(tmp);
      }
    }
  }

  public static void mesh(java.util.List<Location[]> triangles, double step,
      java.util.function.Consumer<Location> pointConsumer) {
    Objects.requireNonNull(triangles, "triangles");
    Objects.requireNonNull(pointConsumer, "pointConsumer");
    if (step <= 0) {
      throw new IllegalArgumentException("step must be > 0");
    }
    Location tmp = new Location(null, 0, 0, 0);
    for (Location[] tri : triangles) {
      if (tri == null || tri.length < 3 || tri[0] == null || tri[1] == null || tri[2] == null) {
        continue;
      }
      Location a = tri[0];
      Location b = tri[1];
      Location c = tri[2];
      double ab = a.distance(b);
      double bc = b.distance(c);
      double ca = c.distance(a);
      double max = Math.max(ab, Math.max(bc, ca));
      int div = Math.max(1, (int) Math.ceil(max / step));
      for (int i = 0; i <= div; i++) {
        double u = i / (double) div;
        for (int j = 0; j <= div - i; j++) {
          double v = j / (double) div;
          double w = 1.0 - u - v;
          tmp.set(
              a.getX() * w + b.getX() * u + c.getX() * v,
              a.getY() * w + b.getY() * u + c.getY() * v,
              a.getZ() * w + b.getZ() * u + c.getZ() * v);
          pointConsumer.accept(tmp);
        }
      }
    }
  }
}
