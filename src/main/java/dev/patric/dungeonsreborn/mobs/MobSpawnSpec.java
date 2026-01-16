package dev.patric.dungeonsreborn.mobs;

import org.bukkit.Location;

public record MobSpawnSpec(
    String id,
    String mobId,
    String worldName,
    Location location,
    int count,
    int maxAlive,
    String groupId,
    int groupMaxAlive,
    java.util.List<MobSpawnGroupSpec> groups,
    long respawnTicks,
    long respawnJitterTicks,
    double radius,
    boolean allowBlockDamage,
    double activationRadius,
    boolean beamEnabled,
    org.bukkit.Particle beamParticle,
    double beamStep,
    boolean respectDifficulty,
    boolean respectGameRules,
    double attackRadius,
    boolean attackIgnoreOutsideRadius,
    boolean attackIgnorePlayers,
    double tetherRadius,
    MobSpawnTetherAction tetherAction,
    double tetherPullSpeed,
    long tetherDespawnTicks,
    boolean hologramEnabled,
    double hologramOffsetY,
    String hologramFormat,
    boolean enabled) {
}
