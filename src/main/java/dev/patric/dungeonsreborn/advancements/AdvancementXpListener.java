package dev.patric.dungeonsreborn.advancements;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;

public final class AdvancementXpListener implements Listener {
  private final AdvancementService advancements;

  public AdvancementXpListener(AdvancementService advancements) {
    this.advancements = advancements;
  }

  @EventHandler
  public void onLevelChange(PlayerLevelChangeEvent event) {
    if (advancements == null || !advancements.isEnabled()) {
      return;
    }
    Player player = event.getPlayer();
    int newLevel = event.getNewLevel();
    if (newLevel <= event.getOldLevel()) {
      return;
    }
    advancements.recordXpLevel(player, newLevel);
  }

  @EventHandler
  public void onExpChange(PlayerExpChangeEvent event) {
    if (advancements == null || !advancements.isEnabled() || event == null) {
      return;
    }
    int amount = event.getAmount();
    if (amount <= 0) {
      return;
    }
    Player player = event.getPlayer();
    int total = Math.max(0, player.getTotalExperience() + amount);
    advancements.recordXpTotal(player, total);
  }
}
