package dev.patric.dungeonsreborn.mobs.ai;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

public final class VelocityNavigationDriver implements MobAiNavigationDriver {
  @Override
  public String id() {
    return "velocity";
  }

  @Override
  public boolean supports(LivingEntity entity) {
    return entity != null && entity.isValid();
  }

  @Override
  public boolean moveToward(LivingEntity entity, Location target, double speed) {
    if (entity == null || target == null || !entity.isValid()) {
      return false;
    }
    Vector dir = target.toVector().subtract(entity.getLocation().toVector());
    if (dir.lengthSquared() <= 1e-9) {
      return false;
    }
    entity.setVelocity(dir.normalize().multiply(Math.max(0.0, speed)));
    return true;
  }

  @Override
  public boolean moveAway(LivingEntity entity, LivingEntity target, double speed) {
    if (entity == null || target == null || !entity.isValid() || !target.isValid()) {
      return false;
    }
    Vector dir = entity.getLocation().toVector().subtract(target.getLocation().toVector());
    if (dir.lengthSquared() <= 1e-9) {
      return false;
    }
    entity.setVelocity(dir.normalize().multiply(Math.max(0.0, speed)));
    return true;
  }

  @Override
  public void stop(LivingEntity entity) {
    if (entity == null || !entity.isValid()) {
      return;
    }
    entity.setVelocity(new Vector(0, 0, 0));
  }
}
