package dev.patric.dungeonsreborn.quests;

import org.bukkit.Location;

public record QuestRegion(String world, double x, double y, double z, double radius) {
  public boolean contains(Location location) {
    if (location == null || world == null) {
      return false;
    }
    if (!world.equals(location.getWorld().getName()) && !world.equals(location.getWorld().getKey().toString())) {
      return false;
    }
    double dx = location.getX() - x;
    double dy = location.getY() - y;
    double dz = location.getZ() - z;
    return dx * dx + dy * dy + dz * dz <= radius * radius;
  }
}
