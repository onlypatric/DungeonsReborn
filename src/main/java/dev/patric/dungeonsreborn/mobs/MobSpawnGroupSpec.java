package dev.patric.dungeonsreborn.mobs;

import java.util.List;

public record MobSpawnGroupSpec(
    double chance,
    Integer count,
    List<MobSpawnGroupEntry> mobs,
    MobSpawnRulesSpec rules) {
}
