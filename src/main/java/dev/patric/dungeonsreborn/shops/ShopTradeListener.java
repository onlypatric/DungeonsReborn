package dev.patric.dungeonsreborn.shops;

import java.util.concurrent.TimeUnit;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.TradeSelectEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;

import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.logging.ServiceLogger;
import dev.patric.dungeonsreborn.advancements.AdvancementService;
import io.papermc.paper.event.player.PlayerTradeEvent;

public final class ShopTradeListener implements Listener {
  private final ShopYamlRegistry registry;
  private final ShopSessionManager sessions;
  private final ShopStockManager stockManager;
  private final ShopTradeMetrics metrics;
  private final AdvancementService advancements;
  private final ServiceLogger logger;

  public ShopTradeListener(ShopYamlRegistry registry, ShopSessionManager sessions, ShopStockManager stockManager,
      ShopTradeMetrics metrics, AdvancementService advancements, ServiceLogger logger) {
    this.registry = registry;
    this.sessions = sessions;
    this.stockManager = stockManager;
    this.metrics = metrics;
    this.advancements = advancements;
    this.logger = logger;
  }

  @EventHandler
  public void onTradeSelect(TradeSelectEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    if (sessions.openShopId(player) == null) {
      return;
    }
    sessions.setSelectedIndex(player, event.getIndex());
  }

  @EventHandler
  public void onTrade(PlayerTradeEvent event) {
    Player player = event.getPlayer();
    String shopId = sessions.openShopId(player);
    if (shopId == null) {
      return;
    }
    ShopSpec spec = registry.shop(shopId);
    if (spec == null) {
      event.setCancelled(true);
      player.sendMessage(Locales.component(player, "messages.shops.trade.missingShop"));
      auditDenied(player, shopId, -1, "missing_shop");
      return;
    }
    if (!spec.enabled()) {
      event.setCancelled(true);
      player.sendMessage(Locales.component(player, "messages.shops.trade.disabled"));
      auditDenied(player, shopId, -1, "disabled");
      return;
    }
    if (spec.permission() != null && !spec.permission().isBlank() && !player.hasPermission(spec.permission())) {
      event.setCancelled(true);
      player.sendMessage(Locales.component(player, "messages.shops.trade.missingPermission",
          Locales.placeholders("perm", spec.permission())));
      auditDenied(player, shopId, -1, "permission");
      return;
    }
    if (!spec.worlds().isEmpty() && !spec.worlds().contains(player.getWorld().getName())) {
      event.setCancelled(true);
      player.sendMessage(Locales.component(player, "messages.shops.trade.worldDenied"));
      auditDenied(player, shopId, -1, "world");
      return;
    }
    int tradeIndex = sessions.selectedIndex(player);
    if (player.getOpenInventory().getTopInventory() instanceof MerchantInventory inv) {
      tradeIndex = inv.getSelectedRecipeIndex();
    }
    if (tradeIndex < 0 || tradeIndex >= spec.trades().size()) {
      tradeIndex = -1;
    }
    long remainingMs = sessions.cooldownRemainingMillis(player, shopId, tradeIndex, spec.cooldownTicks());
    if (remainingMs > 0) {
      event.setCancelled(true);
      long remainingSeconds = Math.max(1L, TimeUnit.MILLISECONDS.toSeconds(remainingMs));
      player.sendMessage(Locales.component(player, "messages.shops.trade.cooldown",
          Locales.placeholders("seconds", String.valueOf(remainingSeconds))));
      auditDenied(player, shopId, tradeIndex, "cooldown");
      return;
    }
    if (!validateInputs(player, spec, tradeIndex)) {
      event.setCancelled(true);
      player.sendMessage(Locales.component(player, "messages.shops.trade.missingItems"));
      auditDenied(player, shopId, tradeIndex, "missing_items");
      return;
    }
    if (stockManager != null && !stockManager.consume(shopId, spec.stock())) {
      event.setCancelled(true);
      player.sendMessage(Locales.component(player, "messages.shops.trade.outOfStock"));
      auditDenied(player, shopId, tradeIndex, "out_of_stock");
      return;
    }
    sessions.markTrade(player, shopId, tradeIndex);
    auditSuccess(player, shopId, tradeIndex);
    if (advancements != null && event.getTrade() != null) {
      ItemStack result = event.getTrade().getResult();
      advancements.recordTokensFromItem(player, result);
    }
  }

  private boolean validateInputs(Player player, ShopSpec spec, int tradeIndex) {
    if (!(player.getOpenInventory().getTopInventory() instanceof MerchantInventory inv)) {
      return true;
    }
    ShopTradeSpec trade = tradeIndex >= 0 && tradeIndex < spec.trades().size() ? spec.trades().get(tradeIndex) : null;
    if (trade == null) {
      return true;
    }
    if (!matches(inv.getItem(0), trade.buyA())) {
      return false;
    }
    if (trade.buyB() != null && !matches(inv.getItem(1), trade.buyB())) {
      return false;
    }
    return true;
  }

  private boolean matches(ItemStack stack, ShopIngredientSpec ingredient) {
    if (ingredient == null) {
      return true;
    }
    if (stack == null || stack.getType() == Material.AIR) {
      return false;
    }
    if (stack.getAmount() < ingredient.amount()) {
      return false;
    }
    return switch (ingredient.type()) {
      case MATERIAL -> stack.getType() == ingredient.material();
      case TOKEN, ITEM_ID, ITEMSTACK -> {
        ItemStack resolved = ingredient.resolve(registry.itemResolver(), registry.tokenSpec());
        yield resolved != null && stack.isSimilar(resolved);
      }
    };
  }

  private void auditDenied(Player player, String shopId, int tradeIndex, String reason) {
    if (metrics != null) {
      metrics.recordDenied(shopId, tradeIndex, reason);
    }
    logger.info("[Shops] trade denied: player=" + player.getName() + " shop=" + shopId + " trade=" + tradeIndex
        + " reason=" + reason);
  }

  private void auditSuccess(Player player, String shopId, int tradeIndex) {
    if (metrics != null) {
      metrics.recordSuccess(shopId, tradeIndex);
    }
    logger.info("[Shops] trade success: player=" + player.getName() + " shop=" + shopId + " trade=" + tradeIndex);
  }
}
