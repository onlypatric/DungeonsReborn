package dev.patric.dungeonsreborn.logging;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.party.Party;

public final class PartyAuditLog {
  private static final String HEADER = "timestamp,action,party_id,leader_uuid,leader_name,actor_uuid,actor_name,"
      + "target_uuid,target_name,reason";

  private final JavaPlugin plugin;
  private final ServiceLogger logger;
  private final File file;
  private final Object fileLock = new Object();

  public PartyAuditLog(JavaPlugin plugin, ServiceLogger logger) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.file = new File(plugin.getDataFolder(), "party_audit.log");
  }

  public void record(String action, Party party, UUID actorId, String actorName,
      UUID targetId, String targetName, String reason) {
    if (party == null || action == null || action.isBlank()) {
      return;
    }
    UUID leaderId = party.leader();
    String leaderName = resolveLeaderName(leaderId);
    AuditEntry entry = new AuditEntry(Instant.now().toString(), action.trim().toLowerCase(java.util.Locale.ROOT),
        party.id(), leaderId, leaderName, actorId, sanitize(actorName), targetId, sanitize(targetName),
        sanitize(reason));
    appendAsync(entry);
  }

  private String resolveLeaderName(UUID leaderId) {
    if (leaderId == null) {
      return "unknown";
    }
    Player player = Bukkit.getPlayer(leaderId);
    if (player != null && player.getName() != null && !player.getName().isBlank()) {
      return player.getName();
    }
    return leaderId.toString();
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
        logger.warn("[Party] Failed to write party audit log", ex);
      }
    }
  }

  private static String sanitize(String value) {
    if (value == null) {
      return "";
    }
    return value.replace('\n', ' ').replace('\r', ' ').trim();
  }

  private static final class AuditEntry {
    private final String timestamp;
    private final String action;
    private final UUID partyId;
    private final UUID leaderId;
    private final String leaderName;
    private final UUID actorId;
    private final String actorName;
    private final UUID targetId;
    private final String targetName;
    private final String reason;

    private AuditEntry(String timestamp, String action, UUID partyId, UUID leaderId, String leaderName,
        UUID actorId, String actorName, UUID targetId, String targetName, String reason) {
      this.timestamp = timestamp;
      this.action = action;
      this.partyId = partyId;
      this.leaderId = leaderId;
      this.leaderName = leaderName;
      this.actorId = actorId;
      this.actorName = actorName;
      this.targetId = targetId;
      this.targetName = targetName;
      this.reason = reason;
    }

    private String toCsv() {
      return joinCsv(timestamp, action, partyId, leaderId, leaderName, actorId, actorName,
          targetId, targetName, reason);
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
