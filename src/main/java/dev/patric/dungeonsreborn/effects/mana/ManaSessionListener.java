package dev.patric.dungeonsreborn.effects.mana;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ManaSessionListener implements Listener {
  private final SessionManaProvider provider;

  public ManaSessionListener(SessionManaProvider provider) {
    this.provider = Objects.requireNonNull(provider, "provider");
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    // Reset each session as requested.
    provider.reset(event.getPlayer());
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    provider.clear(event.getPlayer().getUniqueId());
  }
}

