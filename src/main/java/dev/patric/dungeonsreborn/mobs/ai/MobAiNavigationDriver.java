package dev.patric.dungeonsreborn.mobs.ai;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

public interface MobAiNavigationDriver {
  String id();

  boolean supports(LivingEntity entity);

  boolean moveToward(LivingEntity entity, Location target, double speed);

  boolean moveAway(LivingEntity entity, LivingEntity target, double speed);

  void stop(LivingEntity entity);
}
