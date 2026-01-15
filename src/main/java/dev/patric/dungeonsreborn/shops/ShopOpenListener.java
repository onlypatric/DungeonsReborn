package dev.patric.dungeonsreborn.shops;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.InventoryType;

public final class ShopOpenListener implements Listener {
  private final ShopSessionManager sessions;

  public ShopOpenListener(ShopSessionManager sessions) {
    this.sessions = sessions;
  }

  @EventHandler
  public void onInteract(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND) {
      return;
    }
    switch (event.getAction()) {
      case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {
      }
      default -> {
        return;
      }
    }
    ItemStack item = event.getItem();
    String shopId = ShopMarkers.getShopId(item);
    if (shopId == null) {
      return;
    }
    Player player = event.getPlayer();
    if (sessions.openShop(player, shopId, "item")) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onInteractEntity(PlayerInteractEntityEvent event) {
    if (event.getHand() != EquipmentSlot.HAND) {
      return;
    }
    String shopId = ShopMarkers.getShopId(event.getRightClicked());
    if (shopId == null) {
      return;
    }
    Player player = event.getPlayer();
    if (sessions.openShop(player, shopId, "entity")) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    if (event.getInventory().getType() != InventoryType.MERCHANT) {
      return;
    }
    if (event.getPlayer() instanceof Player player) {
      sessions.close(player);
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    sessions.close(event.getPlayer());
  }
}
