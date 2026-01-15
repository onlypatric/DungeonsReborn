package dev.patric.dungeonsreborn.dungeons;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import dev.patric.dungeonsreborn.locale.Locales;

public final class DungeonSessionListener implements Listener {
  private final DungeonSessionManager sessions;

  public DungeonSessionListener(DungeonSessionManager sessions) {
    this.sessions = Objects.requireNonNull(sessions, "sessions");
  }

  @EventHandler
  public void onDeath(PlayerDeathEvent event) {
    if (event == null || event.getEntity() == null) {
      return;
    }
    sessions.markDeath(event.getEntity());
    if (!sessions.isCheckpointEnabled(event.getEntity())) {
      sessions.abortActive(Locales.component(event.getEntity(), "messages.dungeons.session.fail.death"));
    }
  }

  @EventHandler
  public void onRespawn(PlayerRespawnEvent event) {
    if (event == null || event.getPlayer() == null) {
      return;
    }
    var checkpoint = sessions.checkpointLocation(event.getPlayer());
    if (checkpoint != null) {
      event.setRespawnLocation(checkpoint);
    }
  }
}
