package dev.patric.dungeonsreborn.shops;

import java.util.concurrent.TimeUnit;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.TradeSelectEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;

import dev.patric.dungeonsreborn.advancements.AdvancementService;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.logging.ServiceLogger;
import dev.patric.dungeonsreborn.progression.custom.CustomXpService;
import io.papermc.paper.event.player.PlayerTradeEvent;

public final class ShopTradeListener implements Listener {
  private final ShopYamlRegistry registry;
  private final ShopSessionManager sessions;
  private final ShopStockManager stockManager;
  private final ShopTradeMetrics metrics;
  private final AdvancementService advancements;
  private final CustomXpService customXpService;
  private final int customXpReward;
  private final ServiceLogger logger;
  public ShopTradeListener(ShopYamlRegistry registry, ShopSessionManager sessions, ShopStockManager stockManager,
      ShopTradeMetrics metrics, AdvancementService advancements, CustomXpService customXpService,
      int customXpReward, ServiceLogger logger) {
    this.registry = registry;
    this.sessions = sessions;
    this.stockManager = stockManager;
    this.metrics = metrics;
    this.advancements = advancements;
    this.customXpService = customXpService;
    this.customXpReward = Math.max(0, customXpReward);
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
    if (!sessions.beginTrade(player)) {
      event.setCancelled(true);
      player.sendMessage(Locales.component(player, "messages.shops.trade.busy"));
      auditDenied(player, shopId, -1, null, false, "busy");
      return;
    }
    try {
    ShopSpec spec = registry.shop(shopId);
    if (spec == null) {
      event.setCancelled(true);
      player.sendMessage(Locales.component(player, "messages.shops.trade.missingShop"));
      auditDenied(player, shopId, -1, null, false, "missing_shop");
      return;
    }
    if (!spec.enabled()) {
      event.setCancelled(true);
      player.sendMessage(Locales.component(player, "messages.shops.trade.disabled"));
      auditDenied(player, shopId, -1, null, false, "disabled");
      return;
    }
    if (spec.permission() != null && !spec.permission().isBlank() && !player.hasPermission(spec.permission())) {
      event.setCancelled(true);
      player.sendMessage(Locales.component(player, "messages.shops.trade.missingPermission",
          Locales.placeholders("perm", spec.permission())));
      auditDenied(player, shopId, -1, null, false, "permission");
      return;
    }
    if (!spec.worlds().isEmpty() && !spec.worlds().contains(player.getWorld().getName())) {
      event.setCancelled(true);
      player.sendMessage(Locales.component(player, "messages.shops.trade.worldDenied"));
      auditDenied(player, shopId, -1, null, false, "world");
      return;
    }
    if (spec.availability() != null && !spec.availability().isAvailableNow()) {
      event.setCancelled(true);
      player.sendMessage(Locales.component(player, "messages.shops.trade.unavailable"));
      auditDenied(player, shopId, -1, null, false, "unavailable");
      return;
    }
    ShopRequirementResult shopReq = ShopRequirements.check(player, spec.requirements(),
        sessions.requirementServices(),
        "messages.shops.trade");
    if (!shopReq.allowed()) {
      event.setCancelled(true);
      if (shopReq.message() != null) {
        player.sendMessage(shopReq.message());
      }
      auditDenied(player, shopId, -1, null, false, shopReq.reason());
      return;
    }
    int tradeIndex = sessions.selectedIndex(player);
    if (player.getOpenInventory().getTopInventory() instanceof MerchantInventory inv) {
      tradeIndex = inv.getSelectedRecipeIndex();
    }
    if (tradeIndex < 0 || tradeIndex >= spec.trades().size()) {
      tradeIndex = -1;
    }
    ShopTradeSpec trade = tradeIndex >= 0 && tradeIndex < spec.trades().size() ? spec.trades().get(tradeIndex) : null;
    if (trade != null && trade.availability() != null && !trade.availability().isAvailableNow()) {
      event.setCancelled(true);
      player.sendMessage(Locales.component(player, "messages.shops.trade.unavailable"));
      auditDenied(player, shopId, tradeIndex, trade, false, "unavailable");
      return;
    }
    if (trade != null) {
      ShopRequirementResult tradeReq = ShopRequirements.check(player, trade.requirements(),
          sessions.requirementServices(),
          "messages.shops.trade");
      if (!tradeReq.allowed()) {
        event.setCancelled(true);
        if (tradeReq.message() != null) {
          player.sendMessage(tradeReq.message());
        }
        auditDenied(player, shopId, tradeIndex, trade, false, tradeReq.reason());
        return;
      }
    }
    if (trade != null && trade.minLevel() > 0) {
      if (customXpService == null) {
        logger.warn("[Shops] trade gating skipped (custom XP unavailable): shop=" + shopId + " trade="
            + tradeIndex);
      } else {
        int level = customXpService.getOrCreate(player.getUniqueId()).level();
        if (level < trade.minLevel()) {
          event.setCancelled(true);
          player.sendMessage(Locales.component(player, "messages.shops.trade.requiresLevel",
              Locales.placeholders("level", String.valueOf(trade.minLevel()))));
          auditDenied(player, shopId, tradeIndex, trade, false, "min_level");
          return;
        }
      }
    }
    long remainingMs = sessions.cooldownRemainingMillis(player, shopId, tradeIndex, spec.cooldownTicks());
    if (remainingMs > 0) {
      event.setCancelled(true);
      long remainingSeconds = Math.max(1L, TimeUnit.MILLISECONDS.toSeconds(remainingMs));
      player.sendMessage(Locales.component(player, "messages.shops.trade.cooldown",
          Locales.placeholders("seconds", String.valueOf(remainingSeconds))));
      auditDenied(player, shopId, tradeIndex, trade, false, "cooldown");
      return;
    }
    if (!validateInputs(player, spec, tradeIndex)) {
      event.setCancelled(true);
      player.sendMessage(Locales.component(player, "messages.shops.trade.missingItems"));
      auditDenied(player, shopId, tradeIndex, trade, false, "missing_items");
      return;
    }
    ShopStockSpec stock = trade != null && trade.stock() != null ? trade.stock() : spec.stock();
    if (stockManager != null && !stockManager.consume(shopId, tradeIndex, player.getUniqueId(), stock)) {
      event.setCancelled(true);
      player.sendMessage(Locales.component(player, "messages.shops.trade.outOfStock"));
      auditDenied(player, shopId, tradeIndex, trade, false, "out_of_stock");
      return;
    }
    sessions.markTrade(player, shopId, tradeIndex);
    auditSuccess(player, shopId, tradeIndex, trade, false);
    if (advancements != null && event.getTrade() != null) {
      ItemStack result = event.getTrade().getResult();
      advancements.recordTokensFromItem(player, result);
    }
    if (trade != null && trade.experienceReward() && customXpService != null && customXpReward > 0) {
      customXpService.awardXp(player, customXpReward);
    }
    } finally {
      sessions.endTrade(player);
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
    return ingredient.matches(stack, registry.itemResolver(), registry.tokenSpec());
  }

  private void auditDenied(Player player, String shopId, int tradeIndex, ShopTradeSpec trade, boolean buyback,
      String reason) {
    if (metrics != null) {
      metrics.recordDenied(shopId, tradeIndex, reason);
    }
    ShopTradeAuditLog auditLog = sessions.auditLog();
    if (auditLog != null) {
      String buysLabel = trade == null ? "" : costLabel(trade.buys());
      String sellsLabel = trade == null ? "" : costLabel(trade.sells());
      auditLog.recordDenied(player, shopId, tradeIndex, "merchant", reason, buyback, buysLabel, sellsLabel);
    }
    logger.info("[Shops] trade denied: player=" + player.getName() + " shop=" + shopId + " trade=" + tradeIndex
        + " reason=" + reason);
  }

  private void auditSuccess(Player player, String shopId, int tradeIndex, ShopTradeSpec trade, boolean buyback) {
    if (metrics != null) {
      metrics.recordSuccess(shopId, tradeIndex);
    }
    ShopTradeAuditLog auditLog = sessions.auditLog();
    if (auditLog != null) {
      String buysLabel = trade == null ? "" : costLabel(trade.buys());
      String sellsLabel = trade == null ? "" : costLabel(trade.sells());
      auditLog.recordSuccess(player, shopId, tradeIndex, "merchant", buyback, buysLabel, sellsLabel);
    }
    logger.info("[Shops] trade success: player=" + player.getName() + " shop=" + shopId + " trade=" + tradeIndex);
  }

  private String costLabel(java.util.List<ShopIngredientSpec> ingredients) {
    if (ingredients == null || ingredients.isEmpty()) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < ingredients.size(); i++) {
      if (i > 0) {
        out.append(" + ");
      }
      ShopIngredientSpec spec = ingredients.get(i);
      if (spec == null) {
        continue;
      }
      String name = spec.displayLabel(registry.itemResolver(), registry.tokenSpec());
      out.append(spec.amount()).append("x ").append(name);
    }
    return out.toString();
  }
}
