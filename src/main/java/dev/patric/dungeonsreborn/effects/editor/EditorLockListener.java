package dev.patric.dungeonsreborn.effects.editor;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class EditorLockListener implements Listener {
  private final EditorLockManager locks;

  public EditorLockListener(EditorLockManager locks) {
    this.locks = Objects.requireNonNull(locks, "locks");
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    locks.releaseAll(event.getPlayer().getUniqueId());
  }
}
