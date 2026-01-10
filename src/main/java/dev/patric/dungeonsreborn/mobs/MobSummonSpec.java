package dev.patric.dungeonsreborn.mobs;

public record MobSummonSpec(
    boolean enabled,
    boolean despawnWhenOwnerOffline,
    double despawnDistance,
    double teleportDistance) {
}
