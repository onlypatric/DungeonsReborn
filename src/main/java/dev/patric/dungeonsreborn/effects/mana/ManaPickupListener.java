package dev.patric.dungeonsreborn.effects.mana;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.EffectsEngine;

public final class ManaPickupListener implements Listener {
  private final EffectsEngine engine;
  private final ManaSourcesConfig.PickupSource source;

  public ManaPickupListener(EffectsEngine engine, ManaSourcesConfig.PickupSource source) {
    this.engine = Objects.requireNonNull(engine, "engine");
    this.source = Objects.requireNonNull(source, "source");
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPickup(EntityPickupItemEvent event) {
    if (!source.enabled()) {
      return;
    }
    if (!(event.getEntity() instanceof Player player)) {
      return;
    }
    ManaProvider provider = engine.manaProvider();
    if (provider == null) {
      return;
    }
    ItemStack stack = event.getItem().getItemStack();
    double amount = source.amountFor(stack);
    if (amount <= 0.0) {
      return;
    }
    addResource(provider, player, source.resourceId(), amount);
    if (source.consume()) {
      event.setCancelled(true);
      event.getItem().remove();
    }
  }

  private static void addResource(ManaProvider provider, Player player, String resourceId, double amount) {
    if (provider == null || player == null || resourceId == null || resourceId.isBlank()) {
      return;
    }
    if (!Double.isFinite(amount) || amount <= 0.0) {
      return;
    }
    double max = provider.getMax(player, resourceId);
    if (max <= 0.0) {
      return;
    }
    double current = provider.get(player, resourceId);
    provider.set(player, resourceId, Math.min(max, current + amount));
  }
}
