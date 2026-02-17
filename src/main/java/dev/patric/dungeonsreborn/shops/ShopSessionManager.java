package dev.patric.dungeonsreborn.shops;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.advancements.AdvancementService;
import dev.patric.dungeonsreborn.classes.ClassService;
import dev.patric.dungeonsreborn.logging.ServiceLogger;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.progression.custom.CustomXpService;
import dev.patric.dungeonsreborn.quests.QuestService;

public final class ShopSessionManager {
  private final ShopYamlRegistry registry;
  private final ServiceLogger logger;
  @SuppressWarnings("unused")
  private final ShopStockManager stockManager;
  @SuppressWarnings("unused")
  private final boolean allowExperienceReward;
  @SuppressWarnings("unused")
  private ShopTradeMetrics metrics;
  private ShopTradeAuditLog auditLog;
  @SuppressWarnings("unused")
  private AdvancementService advancements;
  private CustomXpService customXpService;
  @SuppressWarnings("unused")
  private int customXpReward;
  private QuestService questService;
  private ClassService classService;
  private ShopFactionService factionService;
  private final Map<UUID, ShopSession> openSessions = new ConcurrentHashMap<>();
  private final Map<UUID, Map<String, Long>> lastTrades = new ConcurrentHashMap<>();
  private final Map<UUID, java.util.Set<String>> favoriteShops = new ConcurrentHashMap<>();
  private final Map<UUID, Long> inFlightTrades = new ConcurrentHashMap<>();

  public ShopSessionManager(ShopYamlRegistry registry, ShopStockManager stockManager, boolean allowExperienceReward,
      ServiceLogger logger) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.stockManager = stockManager;
    this.allowExperienceReward = allowExperienceReward;
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  public void setTradeServices(ShopTradeMetrics metrics, AdvancementService advancements,
      CustomXpService customXpService, int customXpReward) {
    this.metrics = metrics;
    this.advancements = advancements;
    this.customXpService = customXpService;
    this.customXpReward = Math.max(0, customXpReward);
  }

  public void setAuditLog(ShopTradeAuditLog auditLog) {
    this.auditLog = auditLog;
  }

  public void setRequirementServices(QuestService questService, ClassService classService,
      ShopFactionService factionService) {
    this.questService = questService;
    this.classService = classService;
    this.factionService = factionService;
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
    if (spec.availability() != null && !spec.availability().isAvailableNow()) {
      player.sendMessage(Locales.component(player, "messages.shops.open.unavailable"));
      return false;
    }
    ShopRequirementResult requirement = ShopRequirements.check(player, spec.requirements(),
        requirementServices(), "messages.shops.open");
    if (!requirement.allowed()) {
      if (requirement.message() != null) {
        player.sendMessage(requirement.message());
      }
      return false;
    }
    player.sendMessage(Locales.component(player, "messages.command.systemUnavailable",
        Locales.placeholders("system", Locales.component(player, "labels.system.shops"))));
    return false;
  }

  public boolean isVisible(Player player, ShopSpec spec) {
    if (player == null || spec == null) {
      return false;
    }
    if (!spec.enabled()) {
      return false;
    }
    if (spec.permission() != null && !spec.permission().isBlank() && !player.hasPermission(spec.permission())) {
      return false;
    }
    if (!spec.worlds().isEmpty() && !spec.worlds().contains(player.getWorld().getName())) {
      return false;
    }
    if (spec.availability() != null && !spec.availability().isAvailableNow()) {
      return false;
    }
    return ShopRequirements.isVisible(player, spec.visibilityRequirements(), requirementServices());
  }

  public ShopRequirements.Services requirementServices() {
    return new ShopRequirements.Services(questService, classService, customXpService, factionService);
  }

  public ShopTradeAuditLog auditLog() {
    return auditLog;
  }

  public boolean beginTrade(Player player) {
    if (player == null) {
      return false;
    }
    long now = System.currentTimeMillis();
    Long previous = inFlightTrades.put(player.getUniqueId(), now);
    if (previous == null) {
      return true;
    }
    if (now - previous > 2000L) {
      inFlightTrades.put(player.getUniqueId(), now);
      return true;
    }
    return false;
  }

  public void endTrade(Player player) {
    if (player == null) {
      return;
    }
    inFlightTrades.remove(player.getUniqueId());
  }

  public boolean isFavoriteShop(Player player, String shopId) {
    if (player == null || shopId == null || shopId.isBlank()) {
      return false;
    }
    java.util.Set<String> favorites = favoriteShops.get(player.getUniqueId());
    return favorites != null && favorites.contains(shopId);
  }

  public boolean toggleFavoriteShop(Player player, String shopId) {
    if (player == null || shopId == null || shopId.isBlank()) {
      return false;
    }
    java.util.Set<String> favorites = favoriteShops.computeIfAbsent(player.getUniqueId(),
        id -> ConcurrentHashMap.newKeySet());
    if (favorites.contains(shopId)) {
      favorites.remove(shopId);
      return false;
    }
    favorites.add(shopId);
    return true;
  }

  public java.util.Set<String> favoriteShops(Player player) {
    if (player == null) {
      return java.util.Set.of();
    }
    java.util.Set<String> favorites = favoriteShops.get(player.getUniqueId());
    return favorites == null ? java.util.Set.of() : java.util.Set.copyOf(favorites);
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
