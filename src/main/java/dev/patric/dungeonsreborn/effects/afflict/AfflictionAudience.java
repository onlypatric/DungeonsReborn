package dev.patric.dungeonsreborn.effects.afflict;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public enum AfflictionAudience {
  ANY,
  PVE_ONLY,
  PVP_ONLY;

  public boolean allows(LivingEntity caster, LivingEntity target) {
    if (target == null) {
      return false;
    }
    boolean targetPlayer = target instanceof Player;
    boolean casterPlayer = caster instanceof Player;
    boolean pvp = targetPlayer && casterPlayer;
    return switch (this) {
      case ANY -> true;
      case PVE_ONLY -> !pvp;
      case PVP_ONLY -> pvp;
    };
  }
}
