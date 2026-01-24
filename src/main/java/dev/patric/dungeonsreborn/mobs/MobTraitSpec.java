package dev.patric.dungeonsreborn.mobs;

import java.util.Map;

import dev.patric.dungeonsreborn.effects.damage.DamageType;

public record MobTraitSpec(
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
    Map<DamageType, Double> resistances) {
}
