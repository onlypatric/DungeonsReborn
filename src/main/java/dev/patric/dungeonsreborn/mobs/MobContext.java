package dev.patric.dungeonsreborn.mobs;

import java.util.UUID;

import org.bukkit.entity.LivingEntity;

public record MobContext(MobSpec spec, LivingEntity entity, UUID ownerId) {
}
