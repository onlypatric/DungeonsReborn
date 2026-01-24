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
  private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

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

  public long cooldownRemaining(UUID playerId, String recipeId) {
    if (playerId == null || recipeId == null) {
      return 0L;
    }
    Map<String, Long> entries = cooldowns.get(playerId);
    if (entries == null) {
      return 0L;
    }
    Long until = entries.get(recipeId);
    if (until == null) {
      return 0L;
    }
    long remaining = until - System.currentTimeMillis();
    return Math.max(0L, remaining);
  }

  public void setCooldown(UUID playerId, String recipeId, long untilMillis) {
    if (playerId == null || recipeId == null) {
      return;
    }
    cooldowns.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>()).put(recipeId, untilMillis);
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    CraftingGuiSession session = sessions.remove(player.getUniqueId());
    cooldowns.remove(player.getUniqueId());
    if (session != null) {
      session.onDisconnect(player);
    }
  }
}
