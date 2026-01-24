package dev.patric.dungeonsreborn.effects.mana;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ManaSessionListener implements Listener {
  private final SessionManaProvider provider;
  private final ManaStorageService storage;
  private final boolean persistenceEnabled;

  public ManaSessionListener(SessionManaProvider provider, ManaStorageService storage, boolean persistenceEnabled) {
    this.provider = Objects.requireNonNull(provider, "provider");
    this.storage = Objects.requireNonNull(storage, "storage");
    this.persistenceEnabled = persistenceEnabled;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    if (!persistenceEnabled) {
      provider.reset(event.getPlayer());
      return;
    }
    storage.get(event.getPlayer().getUniqueId())
        .ifPresentOrElse(
            snapshot -> {
              try {
                snapshot.apply(provider, event.getPlayer());
              } catch (Exception ex) {
                provider.reset(event.getPlayer());
              }
            },
            () -> provider.reset(event.getPlayer()));
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    if (persistenceEnabled) {
      try {
        storage.set(event.getPlayer().getUniqueId(), ManaSnapshot.from(provider, event.getPlayer()));
        storage.saveNow();
      } catch (Exception ignored) {
        // ignore storage errors on quit to avoid blocking cleanup
      }
    }
    provider.clear(event.getPlayer().getUniqueId());
  }
}
