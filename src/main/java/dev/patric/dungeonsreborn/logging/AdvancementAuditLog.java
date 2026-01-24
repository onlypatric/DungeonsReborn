package dev.patric.dungeonsreborn.logging;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdvancementAuditLog {
  private static final String HEADER = "timestamp,action,player_uuid,player_name,advancement_id,category_id,source,"
      + "detail,amount,total";

  private final JavaPlugin plugin;
  private final ServiceLogger logger;
  private final File file;
  private final Object fileLock = new Object();

  public AdvancementAuditLog(JavaPlugin plugin, ServiceLogger logger) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.file = new File(plugin.getDataFolder(), "advancement_audit.log");
  }

  public void recordProgress(Player player, String advancementId, String categoryId, String source, String detail,
      int amount, int total) {
    record("progress", player, advancementId, categoryId, source, detail, amount, total);
  }

  public void recordGrant(Player player, String advancementId, String categoryId, String source, String detail,
      int amount, int total) {
    record("grant", player, advancementId, categoryId, source, detail, amount, total);
  }

  public void recordRevoke(Player player, String advancementId, String categoryId, String source, String detail,
      int amount, int total) {
    record("revoke", player, advancementId, categoryId, source, detail, amount, total);
  }

  public List<AuditEvent> loadEvents() {
    if (!file.exists()) {
      return List.of();
    }
    List<AuditEvent> events = new ArrayList<>();
    try {
      List<String> lines = Files.readAllLines(file.toPath());
      for (String line : lines) {
        if (line == null || line.isBlank() || line.startsWith("timestamp,")) {
          continue;
        }
        AuditEvent event = AuditEvent.fromCsv(line);
        if (event != null) {
          events.add(event);
        }
      }
    } catch (IOException ex) {
      logger.warn("[Advancements] Failed to read advancement audit log", ex);
    }
    return events;
  }

  private void record(String action, Player player, String advancementId, String categoryId, String source,
      String detail, int amount, int total) {
    if (player == null || advancementId == null || advancementId.isBlank()) {
      return;
    }
    String normalizedAction = action == null ? "progress"
        : action.trim().toLowerCase(Locale.ROOT);
    AuditEvent event = new AuditEvent(
        Instant.now().toString(),
        normalizedAction,
        player.getUniqueId(),
        safeName(player.getName()),
        safeId(advancementId),
        safeId(categoryId),
        safeId(source),
        safeDetail(detail),
        amount,
        total
    );
    appendAsync(event);
  }

  private void appendAsync(AuditEvent event) {
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> append(event));
  }

  private void append(AuditEvent event) {
    if (event == null) {
      return;
    }
    synchronized (fileLock) {
      try {
        if (!file.exists()) {
          Files.writeString(file.toPath(), HEADER + System.lineSeparator(),
              StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        Files.writeString(file.toPath(), event.toCsv() + System.lineSeparator(),
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      } catch (IOException ex) {
        logger.warn("[Advancements] Failed to write advancement audit log", ex);
      }
    }
  }

  private static String safeName(String value) {
    if (value == null) {
      return "";
    }
    return value.replace('\n', ' ').replace('\r', ' ').trim();
  }

  private static String safeId(String value) {
    if (value == null) {
      return "";
    }
    return value.replace('\n', ' ').replace('\r', ' ').trim();
  }

  private static String safeDetail(String value) {
    if (value == null) {
      return "";
    }
    return value.replace('\n', ' ').replace('\r', ' ').trim();
  }

  public record AuditEvent(String timestamp, String action, UUID playerId, String playerName,
      String advancementId, String categoryId, String source, String detail, int amount, int total) {

    private String toCsv() {
      return joinCsv(timestamp, action, playerId, playerName, advancementId, categoryId, source, detail,
          amount, total);
    }

    public static AuditEvent fromCsv(String line) {
      if (line == null || line.isBlank()) {
        return null;
      }
      List<String> parts = parseCsv(line);
      if (parts.size() < 10) {
        return null;
      }
      UUID playerId = null;
      try {
        if (!parts.get(2).isBlank()) {
          playerId = UUID.fromString(parts.get(2));
        }
      } catch (IllegalArgumentException ignored) {
      }
      int amount = parseInt(parts.get(8));
      int total = parseInt(parts.get(9));
      return new AuditEvent(
          parts.get(0),
          parts.get(1),
          playerId,
          parts.get(3),
          parts.get(4),
          parts.get(5),
          parts.get(6),
          parts.get(7),
          amount,
          total
      );
    }

    private static int parseInt(String value) {
      if (value == null || value.isBlank()) {
        return 0;
      }
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException ex) {
        return 0;
      }
    }

    private static List<String> parseCsv(String line) {
      List<String> values = new ArrayList<>();
      StringBuilder current = new StringBuilder();
      boolean inQuotes = false;
      int length = line.length();
      for (int i = 0; i < length; i++) {
        char c = line.charAt(i);
        if (c == '"') {
          if (inQuotes && i + 1 < length && line.charAt(i + 1) == '"') {
            current.append('"');
            i++;
          } else {
            inQuotes = !inQuotes;
          }
        } else if (c == ',' && !inQuotes) {
          values.add(current.toString());
          current.setLength(0);
        } else {
          current.append(c);
        }
      }
      values.add(current.toString());
      return values;
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
