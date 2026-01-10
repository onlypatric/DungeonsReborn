package dev.patric.dungeonsreborn.mobs;

import java.util.UUID;

import org.bukkit.entity.LivingEntity;

public record MobCastContext(MobSpec spec, MobAttackSpec attack, LivingEntity caster, LivingEntity target, UUID ownerId) {
}
