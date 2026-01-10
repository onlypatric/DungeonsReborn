package dev.patric.dungeonsreborn.mobs;

import java.util.Collection;

import org.bukkit.Location;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class MobTargeting {
  private MobTargeting() {
  }

  public static LivingEntity nearestPlayer(LivingEntity origin, double radius) {
    if (origin == null || origin.getWorld() == null) {
      return null;
    }
    Location loc = origin.getLocation();
    double best = Double.MAX_VALUE;
    Player bestPlayer = null;
    for (Player player : origin.getWorld().getPlayers()) {
      if (!player.isValid() || player.isDead()) {
        continue;
      }
      double dist = player.getLocation().distanceSquared(loc);
      if (radius > 0 && dist > radius * radius) {
        continue;
      }
      if (dist < best) {
        best = dist;
        bestPlayer = player;
      }
    }
    return bestPlayer;
  }

  public static LivingEntity nearestHostile(LivingEntity origin, double radius) {
    if (origin == null || origin.getWorld() == null) {
      return null;
    }
    Location loc = origin.getLocation();
    double best = Double.MAX_VALUE;
    LivingEntity bestEntity = null;
    Collection<LivingEntity> nearby = origin.getWorld().getNearbyLivingEntities(loc, radius, radius, radius);
    for (LivingEntity entity : nearby) {
      if (entity == origin || !entity.isValid() || entity.isDead()) {
        continue;
      }
      if (!(entity instanceof Enemy)) {
        continue;
      }
      double dist = entity.getLocation().distanceSquared(loc);
      if (dist < best) {
        best = dist;
        bestEntity = entity;
      }
    }
    return bestEntity;
  }
}
