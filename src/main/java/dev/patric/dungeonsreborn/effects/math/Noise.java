package dev.patric.dungeonsreborn.effects.math;

/**
 * Tiny deterministic value-noise helpers for particles/animation wobble.
 */
public final class Noise {
  private Noise() {
  }

  public static double value2D(int seed, int x, int y) {
    int h = seed;
    h ^= x * 0x9E3779B9;
    h ^= y * 0x85EBCA6B;
    h ^= (h >>> 16);
    h *= 0x7FEB352D;
    h ^= (h >>> 15);
    h *= 0x846CA68B;
    h ^= (h >>> 16);
    // Map to [0,1)
    return (h & 0x7fffffff) / (double) 0x80000000L;
  }

  public static double value2D(int seed, double x, double y) {
    int x0 = (int) Math.floor(x);
    int y0 = (int) Math.floor(y);
    int x1 = x0 + 1;
    int y1 = y0 + 1;

    double tx = x - x0;
    double ty = y - y0;
    double sx = Mathf.smoothstep(tx);
    double sy = Mathf.smoothstep(ty);

    double v00 = value2D(seed, x0, y0);
    double v10 = value2D(seed, x1, y0);
    double v01 = value2D(seed, x0, y1);
    double v11 = value2D(seed, x1, y1);

    double a = Mathf.lerp(v00, v10, sx);
    double b = Mathf.lerp(v01, v11, sx);
    return Mathf.lerp(a, b, sy);
  }
}

