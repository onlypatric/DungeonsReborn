package dev.patric.dungeonsreborn.crafting;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class CraftingGuiSessionManager implements Listener {
  private final Map<UUID, CraftingGuiSession> sessions = new ConcurrentHashMap<>();

  public void register(Player player, CraftingGuiSession session) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(session, "session");
    sessions.put(player.getUniqueId(), session);
  }

  public void unregister(Player player, CraftingGuiSession session) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(session, "session");
    sessions.remove(player.getUniqueId(), session);
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    CraftingGuiSession session = sessions.remove(player.getUniqueId());
    if (session != null) {
      session.onDisconnect(player);
    }
  }
}
