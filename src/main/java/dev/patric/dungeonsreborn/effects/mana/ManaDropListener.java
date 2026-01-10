package dev.patric.dungeonsreborn.effects.mana;

import java.util.Objects;
import java.util.Random;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.mobs.MobMarkers;

public final class ManaDropListener implements Listener {
  private final EffectsEngine engine;
  private final Random rng = new Random();

  public ManaDropListener(EffectsEngine engine) {
    this.engine = Objects.requireNonNull(engine, "engine");
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDeath(EntityDeathEvent event) {
    LivingEntity entity = event.getEntity();
    if (entity instanceof Player) {
      return;
    }
    if (MobMarkers.getMobId(entity) != null) {
      return;
    }
    Player killer = entity.getKiller();
    if (killer == null) {
      return;
    }
    ManaProvider provider = engine.manaProvider();
    if (provider == null) {
      return;
    }
    double max = maxHealth(entity);
    if (!Double.isFinite(max) || max <= 0.0) {
      return;
    }
    double base = Math.log(max);
    if (!Double.isFinite(base) || base <= 0.0) {
      return;
    }
    double amount = rng.nextDouble() * base;
    addMana(provider, killer, amount);
  }

  private static double maxHealth(LivingEntity entity) {
    AttributeInstance attr = entity.getAttribute(Attribute.MAX_HEALTH);
    return attr == null ? entity.getHealth() : attr.getValue();
  }

  private static void addMana(ManaProvider provider, Player player, double amount) {
    if (provider == null || player == null || !Double.isFinite(amount) || amount <= 0.0) {
      return;
    }
    double max = provider.getMax(player);
    if (max <= 0.0) {
      return;
    }
    double current = provider.get(player);
    provider.set(player, Math.min(max, current + amount));
  }
}
