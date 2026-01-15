package dev.patric.dungeonsreborn.party;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PartyListener implements Listener {
  private final PartyService parties;

  public PartyListener(PartyService parties) {
    this.parties = Objects.requireNonNull(parties, "parties");
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    parties.handleQuit(event.getPlayer());
  }

  @EventHandler
  public void onWorldChange(PlayerChangedWorldEvent event) {
    parties.handleWorldChange(event.getPlayer(), event.getFrom(), event.getPlayer().getWorld());
  }
}
