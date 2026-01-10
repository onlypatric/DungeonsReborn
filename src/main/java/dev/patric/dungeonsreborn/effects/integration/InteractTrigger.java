package dev.patric.dungeonsreborn.effects.integration;

import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public enum InteractTrigger {
  RIGHT_CLICK,
  LEFT_CLICK;

  public boolean matches(PlayerInteractEvent event) {
    // Ignore off-hand duplicates; allow null hand (some actions report null).
    if (event.getHand() == EquipmentSlot.OFF_HAND) {
      return false;
    }
    Action action = event.getAction();
    return switch (this) {
      case RIGHT_CLICK -> action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
      case LEFT_CLICK -> action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
    };
  }
}
