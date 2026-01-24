package dev.patric.dungeonsreborn.mobs;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

public interface MobAiContext {
  MobSpec spec();

  LivingEntity entity();

  UUID ownerId();

  long tick();

  Location home();

  LivingEntity currentTarget();

  void setCurrentTarget(LivingEntity target);

  void clearTarget();

  MobBehaviorState behaviorState();

  void setBehaviorState(MobBehaviorState state);

  void moveToward(Location target, double speed);

  void moveAwayFrom(LivingEntity target, double speed);

  void teleportHome();
}
