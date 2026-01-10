package dev.patric.dungeonsreborn.mobs;

import org.bukkit.Location;

public record MobSpawnSpec(
    String id,
    String mobId,
    String worldName,
    Location location,
    int count,
    int maxAlive,
    long respawnTicks,
    double radius,
    boolean enabled) {
}
