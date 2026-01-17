package dev.patric.dungeonsreborn.shops;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.MenuType;

import dev.patric.dungeonsreborn.logging.ServiceLogger;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.locale.Locales;

public final class ShopSessionManager {
  private final ShopYamlRegistry registry;
  private final ServiceLogger logger;
  private final ShopStockManager stockManager;
  private final boolean allowExperienceReward;
  private final Map<UUID, ShopSession> openSessions = new ConcurrentHashMap<>();
  private final Map<UUID, Map<String, Long>> lastTrades = new ConcurrentHashMap<>();

  public ShopSessionManager(ShopYamlRegistry registry, ShopStockManager stockManager, boolean allowExperienceReward,
      ServiceLogger logger) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.stockManager = stockManager;
    this.allowExperienceReward = allowExperienceReward;
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  public boolean openShop(Player player, String shopId, String source) {
    if (player == null) {
      return false;
    }
    if (shopId == null || shopId.isBlank()) {
      player.sendMessage(Locales.component(player, "messages.shops.open.missingId"));
      return false;
    }
    ShopSpec spec = registry.shop(shopId);
    if (spec == null) {
      player.sendMessage(Locales.component(player, "messages.shops.open.unknown",
          Locales.placeholders("id", shopId)));
      return false;
    }
    return openShop(player, spec, source);
  }

  public boolean openShop(Player player, ShopSpec spec, String source) {
    if (player == null || spec == null) {
      return false;
    }
    if (!spec.enabled()) {
      player.sendMessage(Locales.component(player, "messages.shops.open.disabled"));
      return false;
    }
    if (spec.permission() != null && !spec.permission().isBlank() && !player.hasPermission(spec.permission())) {
      player.sendMessage(Locales.component(player, "messages.shops.open.missingPermission",
          Locales.placeholders("perm", spec.permission())));
      return false;
    }
    if (!spec.worlds().isEmpty() && !spec.worlds().contains(player.getWorld().getName())) {
      player.sendMessage(Locales.component(player, "messages.shops.open.worldDenied"));
      return false;
    }
    var merchant = ShopMerchantBuilder.buildMerchant(spec, registry.tokenSpec(), registry.itemResolver(), stockManager,
        allowExperienceReward);
    var view = MenuType.MERCHANT.builder()
        .merchant(merchant)
        .title(GuiMini.mm(spec.title()))
        .build(player);
    player.openInventory(view);
    openSessions.put(player.getUniqueId(), new ShopSession(spec.id()));
    logger.debug("[Shops] open: player=" + player.getName() + " shop=" + spec.id() + " source=" + source);
    return true;
  }

  public String openShopId(Player player) {
    if (player == null) {
      return null;
    }
    ShopSession session = openSessions.get(player.getUniqueId());
    return session == null ? null : session.shopId();
  }

  public void close(Player player) {
    if (player == null) {
      return;
    }
    ShopSession session = openSessions.remove(player.getUniqueId());
    if (session != null) {
      logger.debug("[Shops] close: player=" + player.getName() + " shop=" + session.shopId());
    }
  }

  public void setSelectedIndex(Player player, int index) {
    if (player == null) {
      return;
    }
    ShopSession session = openSessions.get(player.getUniqueId());
    if (session != null) {
      session.selectedIndex = index;
    }
  }

  public int selectedIndex(Player player) {
    if (player == null) {
      return -1;
    }
    ShopSession session = openSessions.get(player.getUniqueId());
    return session == null ? -1 : session.selectedIndex;
  }

  public long cooldownRemainingMillis(Player player, String shopId, int tradeIndex, long cooldownTicks) {
    if (player == null || shopId == null || cooldownTicks <= 0) {
      return 0L;
    }
    long cooldownMs = cooldownTicks * 50L;
    long now = System.currentTimeMillis();
    Map<String, Long> playerCooldowns = lastTrades.get(player.getUniqueId());
    if (playerCooldowns == null) {
      return 0L;
    }
    String key = cooldownKey(shopId, tradeIndex);
    Long last = playerCooldowns.get(key);
    if (last == null) {
      return 0L;
    }
    long elapsed = now - last;
    if (elapsed >= cooldownMs) {
      return 0L;
    }
    return cooldownMs - elapsed;
  }

  public void markTrade(Player player, String shopId, int tradeIndex) {
    if (player == null || shopId == null) {
      return;
    }
    Map<String, Long> playerCooldowns = lastTrades.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>());
    playerCooldowns.put(cooldownKey(shopId, tradeIndex), System.currentTimeMillis());
  }

  private static String cooldownKey(String shopId, int tradeIndex) {
    if (tradeIndex < 0) {
      return shopId;
    }
    return shopId + "#" + tradeIndex;
  }

  private static final class ShopSession {
    private final String shopId;
    private volatile int selectedIndex = -1;

    private ShopSession(String shopId) {
      this.shopId = shopId;
    }

    public String shopId() {
      return shopId;
    }
  }
}
