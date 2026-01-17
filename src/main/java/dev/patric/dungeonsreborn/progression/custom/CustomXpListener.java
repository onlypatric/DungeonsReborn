package dev.patric.dungeonsreborn.progression.custom;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class CustomXpListener implements Listener {
  private final CustomXpService service;

  public CustomXpListener(CustomXpService service) {
    this.service = service;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    service.load(event.getPlayer());
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    service.flush(event.getPlayer());
  }

  @EventHandler(ignoreCancelled = true)
  public void onDeath(PlayerDeathEvent event) {
    var player = event.getEntity();
    if (player == null) {
      return;
    }
    if (!service.isWorldAllowed(player.getWorld())) {
      return;
    }
    CustomXpProfile profile = service.getOrCreate(player.getUniqueId());
    long points = profile.points();
    if (points <= 0) {
      return;
    }
    int loss = (int) Math.ceil(points * 0.05);
    if (loss <= 0) {
      return;
    }
    service.removeXp(player, loss);
  }
}
