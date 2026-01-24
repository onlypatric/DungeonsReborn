package dev.patric.dungeonsreborn.mobs;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.party.Party;
import dev.patric.dungeonsreborn.party.PartyService;

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

  public static LivingEntity weightedByDistance(LivingEntity origin, double radius) {
    if (origin == null || origin.getWorld() == null) {
      return null;
    }
    Location loc = origin.getLocation();
    double limit = radius > 0 ? radius * radius : -1.0;
    double total = 0.0;
    Player chosen = null;
    for (Player player : origin.getWorld().getPlayers()) {
      if (!player.isValid() || player.isDead()) {
        continue;
      }
      double dist = player.getLocation().distanceSquared(loc);
      if (limit > 0 && dist > limit) {
        continue;
      }
      double weight = 1.0 / Math.max(1.0, Math.sqrt(dist));
      total += weight;
      if (chosen == null) {
        chosen = player;
      } else if (ThreadLocalRandom.current().nextDouble(total) < weight) {
        chosen = player;
      }
    }
    return chosen;
  }

  public static LivingEntity weightedByThreat(LivingEntity origin, double radius, Map<UUID, Double> threat) {
    if (origin == null || origin.getWorld() == null || threat == null || threat.isEmpty()) {
      return null;
    }
    double limit = radius > 0 ? radius * radius : -1.0;
    double total = 0.0;
    LivingEntity chosen = null;
    for (Map.Entry<UUID, Double> entry : threat.entrySet()) {
      if (entry.getValue() == null || entry.getValue() <= 0.0) {
        continue;
      }
      LivingEntity entity = resolveLiving(origin, entry.getKey());
      if (entity == null || !entity.isValid() || entity.isDead()) {
        continue;
      }
      if (limit > 0 && entity.getLocation().distanceSquared(origin.getLocation()) > limit) {
        continue;
      }
      double weight = entry.getValue();
      total += weight;
      if (chosen == null) {
        chosen = entity;
      } else if (ThreadLocalRandom.current().nextDouble(total) < weight) {
        chosen = entity;
      }
    }
    return chosen;
  }

  public static LivingEntity nearestPartyLeader(LivingEntity origin, PartyService parties, double radius) {
    Objects.requireNonNull(parties, "parties");
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
      Party party = parties.partyOf(player);
      if (party == null || !party.leader().equals(player.getUniqueId())) {
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

  private static LivingEntity resolveLiving(LivingEntity origin, UUID id) {
    if (id == null) {
      return null;
    }
    if (origin == null || origin.getWorld() == null) {
      return null;
    }
    var entity = origin.getWorld().getEntity(id);
    return entity instanceof LivingEntity living ? living : null;
  }
}
