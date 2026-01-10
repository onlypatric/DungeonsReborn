package dev.patric.dungeonsreborn.effects.relations;

import org.bukkit.entity.LivingEntity;

@FunctionalInterface
public interface RelationProvider {
  Relation relation(LivingEntity caster, LivingEntity target);
}

