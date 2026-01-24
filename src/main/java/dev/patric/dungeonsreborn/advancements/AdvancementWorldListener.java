package dev.patric.dungeonsreborn.advancements;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import dev.patric.dungeonsreborn.util.WorldAllowlist;

public final class AdvancementWorldListener implements Listener {
  private final AdvancementService advancements;
  private final WorldAllowlist worldAllowlist;

  public AdvancementWorldListener(AdvancementService advancements, WorldAllowlist worldAllowlist) {
    this.advancements = advancements;
    this.worldAllowlist = worldAllowlist;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    grantIfAllowed(event.getPlayer());
  }

  @EventHandler
  public void onWorldChange(PlayerChangedWorldEvent event) {
    grantIfAllowed(event.getPlayer());
  }

  private void grantIfAllowed(Player player) {
    if (player == null || !advancements.isEnabled()) {
      return;
    }
    boolean allowed = advancements.isWorldAllowed(player, "root");
    if (worldAllowlist != null) {
      World world = player.getWorld();
      allowed = allowed && worldAllowlist.isAllowed(world);
    }
    if (allowed) {
      advancements.grantRoot(player);
    }
  }
}
