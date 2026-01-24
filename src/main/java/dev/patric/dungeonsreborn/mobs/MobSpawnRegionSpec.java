package dev.patric.dungeonsreborn.mobs;

import org.bukkit.Location;

public record MobSpawnRegionSpec(
    double minX,
    double minY,
    double minZ,
    double maxX,
    double maxY,
    double maxZ) {

  public static MobSpawnRegionSpec normalized(double minX, double minY, double minZ,
      double maxX, double maxY, double maxZ) {
    double loX = Math.min(minX, maxX);
    double hiX = Math.max(minX, maxX);
    double loY = Math.min(minY, maxY);
    double hiY = Math.max(minY, maxY);
    double loZ = Math.min(minZ, maxZ);
    double hiZ = Math.max(minZ, maxZ);
    return new MobSpawnRegionSpec(loX, loY, loZ, hiX, hiY, hiZ);
  }

  public boolean contains(Location location) {
    if (location == null) {
      return false;
    }
    double x = location.getX();
    double y = location.getY();
    double z = location.getZ();
    return x >= minX && x <= maxX
        && y >= minY && y <= maxY
        && z >= minZ && z <= maxZ;
  }
}
