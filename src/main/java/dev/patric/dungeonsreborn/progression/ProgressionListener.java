package dev.patric.dungeonsreborn.progression;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ProgressionListener implements Listener {
  private final ProgressionService service;

  public ProgressionListener(ProgressionService service) {
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
}
