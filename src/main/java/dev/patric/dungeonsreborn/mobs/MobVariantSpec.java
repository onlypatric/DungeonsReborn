package dev.patric.dungeonsreborn.mobs;

public record MobVariantSpec(
    String id,
    double weight,
    String name,
    String namePrefix,
    String nameSuffix,
    double healthMultiplier,
    double damageMultiplier,
    double speedMultiplier,
    double followRangeMultiplier,
    double scaleMultiplier,
    Boolean collidable) {
}
