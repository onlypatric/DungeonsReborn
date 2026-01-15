package dev.patric.dungeonsreborn.shops;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.logging.ServiceLogger;

public final class ShopTradeMetrics {
  private final JavaPlugin plugin;
  private final ServiceLogger logger;
  private final File file;
  private final Map<String, TradeMetrics> metrics = new ConcurrentHashMap<>();
  private volatile boolean saveScheduled;

  public ShopTradeMetrics(JavaPlugin plugin, ServiceLogger logger) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.file = new File(plugin.getDataFolder(), "shop_metrics.yml");
  }

  public void load() {
    if (!file.exists()) {
      return;
    }
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection root = cfg.getConfigurationSection("metrics");
    if (root == null) {
      return;
    }
    for (String shopId : root.getKeys(false)) {
      ConfigurationSection shopSec = root.getConfigurationSection(shopId);
      if (shopSec == null) {
        continue;
      }
      ConfigurationSection tradesSec = shopSec.getConfigurationSection("trades");
      if (tradesSec == null) {
        continue;
      }
      for (String tradeKey : tradesSec.getKeys(false)) {
        ConfigurationSection tradeSec = tradesSec.getConfigurationSection(tradeKey);
        if (tradeSec == null) {
          continue;
        }
        int tradeIndex = parseTradeIndex(tradeKey);
        TradeMetrics trade = new TradeMetrics(shopId, tradeIndex);
        trade.success = tradeSec.getLong("success", 0L);
        trade.denied = tradeSec.getLong("denied", 0L);
        ConfigurationSection reasonsSec = tradeSec.getConfigurationSection("deniedReasons");
        if (reasonsSec != null) {
          for (String reason : reasonsSec.getKeys(false)) {
            trade.deniedReasons.put(reason, reasonsSec.getLong(reason, 0L));
          }
        }
        metrics.put(metricKey(shopId, tradeIndex), trade);
      }
    }
  }

  public void saveNow() {
    YamlConfiguration cfg = new YamlConfiguration();
    for (TradeMetrics trade : metrics.values()) {
      String tradeKey = tradeKey(trade.tradeIndex);
      String base = "metrics." + trade.shopId + ".trades." + tradeKey;
      cfg.set(base + ".success", trade.success);
      cfg.set(base + ".denied", trade.denied);
      if (!trade.deniedReasons.isEmpty()) {
        for (Map.Entry<String, Long> entry : trade.deniedReasons.entrySet()) {
          cfg.set(base + ".deniedReasons." + entry.getKey(), entry.getValue());
        }
      }
    }
    try {
      cfg.save(file);
    } catch (IOException ex) {
      logger.warn("[Shops] Failed to save shop metrics", ex);
    }
  }

  public void recordSuccess(String shopId, int tradeIndex) {
    record(shopId, tradeIndex, true, null);
  }

  public void recordDenied(String shopId, int tradeIndex, String reason) {
    record(shopId, tradeIndex, false, reason);
  }

  private void record(String shopId, int tradeIndex, boolean success, String reason) {
    if (shopId == null || shopId.isBlank()) {
      return;
    }
    TradeMetrics trade = metrics.computeIfAbsent(metricKey(shopId, tradeIndex),
        key -> new TradeMetrics(shopId, tradeIndex));
    synchronized (trade) {
      if (success) {
        trade.success++;
      } else {
        trade.denied++;
        if (reason != null && !reason.isBlank()) {
          String normalized = normalizeReason(reason);
          Long current = trade.deniedReasons.get(normalized);
          trade.deniedReasons.put(normalized, current == null ? 1L : current + 1L);
        }
      }
    }
    scheduleSave();
  }

  private void scheduleSave() {
    if (saveScheduled) {
      return;
    }
    saveScheduled = true;
    Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
      saveScheduled = false;
      saveNow();
    }, 40L);
  }

  private static String metricKey(String shopId, int tradeIndex) {
    return shopId + "#" + tradeIndex;
  }

  private static String tradeKey(int tradeIndex) {
    return tradeIndex >= 0 ? String.valueOf(tradeIndex) : "unknown";
  }

  private static int parseTradeIndex(String tradeKey) {
    if ("unknown".equalsIgnoreCase(tradeKey)) {
      return -1;
    }
    try {
      return Integer.parseInt(tradeKey);
    } catch (NumberFormatException ex) {
      return -1;
    }
  }

  private static String normalizeReason(String reason) {
    String normalized = reason.trim().toLowerCase();
    StringBuilder out = new StringBuilder(normalized.length());
    for (int i = 0; i < normalized.length(); i++) {
      char c = normalized.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        out.append(c);
      } else if (c == ' ' || c == '-' || c == '_') {
        out.append('_');
      }
    }
    return out.toString();
  }

  private static final class TradeMetrics {
    private final String shopId;
    private final int tradeIndex;
    private long success;
    private long denied;
    private final Map<String, Long> deniedReasons = new HashMap<>();

    private TradeMetrics(String shopId, int tradeIndex) {
      this.shopId = shopId;
      this.tradeIndex = tradeIndex;
    }
  }
}
