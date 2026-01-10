package dev.patric.dungeonsreborn.effects.damage;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.actions.EntityActions;
import dev.patric.dungeonsreborn.effects.relations.Relation;

public final class DamageMechanicsListener implements Listener {
  private final EffectsEngine engine;
  private final Set<java.util.UUID> reflectingVictims = new HashSet<>();

  public DamageMechanicsListener(EffectsEngine engine) {
    this.engine = engine;
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onDamage(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof LivingEntity victim)) {
      return;
    }
    if (reflectingVictims.contains(victim.getUniqueId())) {
      return;
    }
    LivingEntity attacker = resolveAttacker(event.getDamager());
    if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
      return;
    }
    EffectsEngine.ReflectSpec spec = engine.reflectSpec(victim.getUniqueId());
    if (spec == null) {
      return;
    }
    if (!allowReflect(spec.policy(), victim, attacker)) {
      return;
    }
    double base = spec.flat() + event.getFinalDamage() * spec.ratio();
    if (!(base > 0.0)) {
      return;
    }
    double multiplier = 1.0;
    DamageType type = spec.type();
    if (type != null && !spec.ignoreResistance()) {
      multiplier = engine.resistanceMultiplier(attacker.getUniqueId(), type);
    }
    double dmg = base * multiplier;
    if (!(dmg > 0.0)) {
      return;
    }

    reflectingVictims.add(victim.getUniqueId());
    try {
      attacker.damage(dmg, victim);
    } finally {
      reflectingVictims.remove(victim.getUniqueId());
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    engine.clearResistances(event.getPlayer().getUniqueId());
    engine.clearReflect(event.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onDeath(EntityDeathEvent event) {
    LivingEntity entity = event.getEntity();
    engine.clearResistances(entity.getUniqueId());
    engine.clearReflect(entity.getUniqueId());
  }

  private LivingEntity resolveAttacker(Entity damager) {
    if (damager instanceof LivingEntity living) {
      return living;
    }
    if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity living) {
      return living;
    }
    return null;
  }

  private boolean allowReflect(EntityActions.DamagePolicy policy, LivingEntity caster, LivingEntity target) {
    if (!policy.allowSelf() && target.getUniqueId().equals(caster.getUniqueId())) {
      return false;
    }
    if (target instanceof org.bukkit.entity.Player) {
      if (!policy.allowPlayers()) {
        return false;
      }
    } else {
      if (!policy.allowMobs()) {
        return false;
      }
    }
    Relation rel = engine.relation(caster, target);
    if (!policy.allowAllies() && rel == Relation.ALLY) {
      return false;
    }
    return true;
  }
}
