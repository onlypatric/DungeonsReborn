package dev.patric.dungeonsreborn.progression;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class ProgressionHudListener implements Listener {
  private final ProgressionHudService hudService;

  public ProgressionHudListener(ProgressionHudService hudService) {
    this.hudService = hudService;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    hudService.refresh(event.getPlayer());
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    hudService.hide(event.getPlayer());
  }

  @EventHandler
  public void onWorldChange(PlayerChangedWorldEvent event) {
    hudService.refresh(event.getPlayer());
  }

  @EventHandler
  public void onRespawn(PlayerRespawnEvent event) {
    hudService.refresh(event.getPlayer());
  }
}
