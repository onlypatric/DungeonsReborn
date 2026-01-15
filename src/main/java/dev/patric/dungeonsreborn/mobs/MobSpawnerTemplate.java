package dev.patric.dungeonsreborn.mobs;

public record MobSpawnerTemplate(
    Integer count,
    Integer maxAlive,
    String groupId,
    Integer groupMaxAlive,
    Long respawnTicks,
    Long respawnJitterTicks,
    Double radius,
    Boolean allowBlockDamage,
    Double activationRadius,
    Boolean respectDifficulty,
    Boolean respectGameRules,
    Double attackRadius,
    Boolean attackIgnoreOutsideRadius,
    Boolean attackIgnorePlayers,
    Double tetherRadius,
    MobSpawnTetherAction tetherAction,
    Double tetherPullSpeed,
    Long tetherDespawnTicks,
    Boolean hologramEnabled,
    Double hologramOffsetY,
    String hologramFormat,
    Boolean enabled) {
}
