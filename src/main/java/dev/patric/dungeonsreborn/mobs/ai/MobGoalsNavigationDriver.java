package dev.patric.dungeonsreborn.mobs.ai;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

public final class MobGoalsNavigationDriver implements MobAiNavigationDriver {
  @Override
  public String id() {
    return "mob_goals_pathfinder";
  }

  @Override
  public boolean supports(LivingEntity entity) {
    return entity instanceof Mob mob && mob.isValid();
  }

  @Override
  public boolean moveToward(LivingEntity entity, Location target, double speed) {
    if (!(entity instanceof Mob mob) || target == null || !mob.isValid()) {
      return false;
    }
    try {
      mob.getPathfinder().moveTo(target, Math.max(0.0, speed));
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  @Override
  public boolean moveAway(LivingEntity entity, LivingEntity target, double speed) {
    if (!(entity instanceof Mob mob) || target == null || !mob.isValid() || !target.isValid()) {
      return false;
    }
    Location from = mob.getLocation();
    Vector away = from.toVector().subtract(target.getLocation().toVector());
    if (away.lengthSquared() <= 1e-9) {
      return false;
    }
    Location next = from.clone().add(away.normalize().multiply(3.0));
    try {
      mob.getPathfinder().moveTo(next, Math.max(0.0, speed));
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  @Override
  public void stop(LivingEntity entity) {
    if (!(entity instanceof Mob mob) || !mob.isValid()) {
      return;
    }
    try {
      mob.getPathfinder().stopPathfinding();
    } catch (Throwable ignored) {
    }
  }
}
