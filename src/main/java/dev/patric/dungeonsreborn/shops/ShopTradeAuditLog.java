package dev.patric.dungeonsreborn.shops;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.logging.ServiceLogger;

public final class ShopTradeAuditLog {
  private static final String HEADER = "timestamp,player_uuid,player_name,shop_id,trade_index,source,result,reason,"
      + "buyback,buys,sells";
  private static final int MAX_HISTORY = 50;

  private final JavaPlugin plugin;
  private final ServiceLogger logger;
  private final File file;
  private final Map<UUID, Deque<AuditEntry>> history = new ConcurrentHashMap<>();
  private final Object fileLock = new Object();

  public ShopTradeAuditLog(JavaPlugin plugin, ServiceLogger logger) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.file = new File(plugin.getDataFolder(), "shop_trades.log");
  }

  public void recordSuccess(Player player, String shopId, int tradeIndex, String source, boolean buyback,
      String buysLabel, String sellsLabel) {
    record(player, shopId, tradeIndex, source, "SUCCESS", null, buyback, buysLabel, sellsLabel);
  }

  public void recordDenied(Player player, String shopId, int tradeIndex, String source, String reason,
      boolean buyback, String buysLabel, String sellsLabel) {
    record(player, shopId, tradeIndex, source, "DENIED", reason, buyback, buysLabel, sellsLabel);
  }

  public List<AuditEntry> recent(UUID playerId) {
    if (playerId == null) {
      return List.of();
    }
    Deque<AuditEntry> entries = history.get(playerId);
    if (entries == null) {
      return List.of();
    }
    synchronized (entries) {
      return List.copyOf(entries);
    }
  }

  private void record(Player player, String shopId, int tradeIndex, String source, String result, String reason,
      boolean buyback, String buysLabel, String sellsLabel) {
    if (player == null || shopId == null || shopId.isBlank()) {
      return;
    }
    AuditEntry entry = new AuditEntry(Instant.now().toString(), player.getUniqueId(), player.getName(), shopId,
        tradeIndex, source == null ? "" : source, result, reason == null ? "" : reason, buyback,
        sanitize(buysLabel), sanitize(sellsLabel));
    Deque<AuditEntry> entries = history.computeIfAbsent(player.getUniqueId(), id -> new ArrayDeque<>(MAX_HISTORY));
    synchronized (entries) {
      if (entries.size() >= MAX_HISTORY) {
        entries.removeFirst();
      }
      entries.addLast(entry);
    }
    appendAsync(entry);
  }

  private void appendAsync(AuditEntry entry) {
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> append(entry));
  }

  private void append(AuditEntry entry) {
    if (entry == null) {
      return;
    }
    synchronized (fileLock) {
      try {
        if (!file.exists()) {
          Files.writeString(file.toPath(), HEADER + System.lineSeparator(),
              StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        Files.writeString(file.toPath(), entry.toCsv() + System.lineSeparator(),
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      } catch (IOException ex) {
        logger.warn("[Shops] Failed to write shop trade audit log", ex);
      }
    }
  }

  private static String sanitize(String value) {
    if (value == null) {
      return "";
    }
    return value.replace('\n', ' ').replace('\r', ' ').trim();
  }

  public static final class AuditEntry {
    private final String timestamp;
    private final UUID playerId;
    private final String playerName;
    private final String shopId;
    private final int tradeIndex;
    private final String source;
    private final String result;
    private final String reason;
    private final boolean buyback;
    private final String buys;
    private final String sells;

    private AuditEntry(String timestamp, UUID playerId, String playerName, String shopId, int tradeIndex,
        String source, String result, String reason, boolean buyback, String buys, String sells) {
      this.timestamp = timestamp;
      this.playerId = playerId;
      this.playerName = playerName;
      this.shopId = shopId;
      this.tradeIndex = tradeIndex;
      this.source = source;
      this.result = result;
      this.reason = reason;
      this.buyback = buyback;
      this.buys = buys;
      this.sells = sells;
    }

    public String toCsv() {
      return joinCsv(timestamp, playerId, playerName, shopId, tradeIndex, source, result, reason, buyback, buys, sells);
    }

    private static String joinCsv(Object... values) {
      StringBuilder out = new StringBuilder();
      for (int i = 0; i < values.length; i++) {
        if (i > 0) {
          out.append(',');
        }
        String value = values[i] == null ? "" : String.valueOf(values[i]);
        out.append('"').append(value.replace("\"", "\"\"")).append('"');
      }
      return out.toString();
    }
  }
}
