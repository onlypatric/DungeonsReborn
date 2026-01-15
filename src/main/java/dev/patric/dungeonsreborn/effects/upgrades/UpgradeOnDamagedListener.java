package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class UpgradeOnDamagedListener implements Listener {
  private final UpgradeService upgrades;

  public UpgradeOnDamagedListener(UpgradeService upgrades) {
    this.upgrades = Objects.requireNonNull(upgrades, "upgrades");
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDamage(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Player player)) {
      return;
    }
    upgrades.handleOnDamaged(player);
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    upgrades.clearOnDamagedState(event.getPlayer().getUniqueId());
  }
}
