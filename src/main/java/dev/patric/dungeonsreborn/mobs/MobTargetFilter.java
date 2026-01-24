package dev.patric.dungeonsreborn.mobs;

import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public enum MobTargetFilter {
  ANY,
  PLAYERS,
  MOBS,
  HOSTILE;

  public boolean matches(LivingEntity entity) {
    return switch (this) {
      case ANY -> true;
      case PLAYERS -> entity instanceof Player;
      case MOBS -> !(entity instanceof Player);
      case HOSTILE -> entity instanceof Enemy;
    };
  }
}
