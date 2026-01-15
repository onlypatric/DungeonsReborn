package dev.patric.dungeonsreborn.dungeons;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.logging.ServiceLogger;
import dev.patric.dungeonsreborn.util.WorldAllowlist;
import net.kyori.adventure.text.Component;

public final class DungeonQueueService {
  public record QueueEntry(UUID playerId, String playerName, int level, long queuedAt) {
  }

  public record JoinResult(boolean success, Component message) {
  }

  public record LeaveResult(boolean success, Component message) {
  }

  public record QueueStatus(boolean queued, int level, int position, int totalInLevel, boolean active) {
  }

  private final Plugin plugin;
  private final DungeonYamlRegistry registry;
  private final DungeonProgressRepository progress;
  private final WorldAllowlist worldAllowlist;
  private final ServiceLogger logger;
  private final Map<Integer, Deque<QueueEntry>> queues = new HashMap<>();
  private QueueEntry active;
  private DungeonSessionManager sessions;

  public DungeonQueueService(Plugin plugin, DungeonYamlRegistry registry, DungeonProgressRepository progress,
      WorldAllowlist worldAllowlist, ServiceLogger logger) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.registry = Objects.requireNonNull(registry, "registry");
    this.progress = progress;
    this.worldAllowlist = worldAllowlist;
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  public synchronized void setSessionManager(DungeonSessionManager sessions) {
    this.sessions = sessions;
  }

  public synchronized JoinResult join(Player player, int level) {
    if (player == null) {
      return new JoinResult(false, Locales.component(null, "messages.dungeons.queue.error.playerMissing"));
    }
    DungeonSpec dungeon = registry.dungeon();
    if (dungeon == null) {
      return new JoinResult(false, Locales.component(player, "messages.dungeons.queue.error.notConfigured"));
    }
    if (!dungeon.levels().containsKey(level)) {
      return new JoinResult(false, Locales.component(player, "messages.dungeons.queue.error.invalidLevel"));
    }
    if (!isWorldAllowed(player.getWorld())) {
      return new JoinResult(false, Locales.component(player, "messages.dungeons.queue.error.worldDisabled"));
    }
    if (active != null && active.playerId().equals(player.getUniqueId())) {
      return new JoinResult(false, Locales.component(player, "messages.dungeons.queue.error.alreadyActive"));
    }
    QueueEntry existing = findEntry(player.getUniqueId());
    if (existing != null) {
      return new JoinResult(false, Locales.component(player, "messages.dungeons.queue.error.alreadyQueued",
          Locales.placeholders("level", existing.level())));
    }
    if (!isUnlocked(player.getUniqueId(), dungeon.id(), level)) {
      return new JoinResult(false, Locales.component(player, "messages.dungeons.queue.error.mustComplete",
          Locales.placeholders("level", level - 1)));
    }
    DungeonSpec.DungeonQueueConfig queueConfig = queueConfig(dungeon);
    int maxSize = queueConfig.maxSizePerLevel();
    if (maxSize > 0 && queueSize(level) >= maxSize) {
      return new JoinResult(false, Locales.component(player, "messages.dungeons.queue.error.full"));
    }
    QueueEntry entry = new QueueEntry(player.getUniqueId(), player.getName(), level, System.currentTimeMillis());
    queues.computeIfAbsent(level, ignored -> new ArrayDeque<>()).addLast(entry);
    autoStart();
    return new JoinResult(true, Locales.component(player, "messages.dungeons.queue.result.queued",
        Locales.placeholders("level", level)));
  }

  public synchronized LeaveResult leave(Player player) {
    if (player == null) {
      return new LeaveResult(false, Locales.component(null, "messages.dungeons.queue.error.playerMissing"));
    }
    UUID uuid = player.getUniqueId();
    if (active != null && active.playerId().equals(uuid)) {
      return new LeaveResult(false, Locales.component(player, "messages.dungeons.queue.error.alreadyActive"));
    }
    QueueEntry entry = removeEntry(uuid);
    if (entry == null) {
      return new LeaveResult(false, Locales.component(player, "messages.dungeons.queue.error.notQueued"));
    }
    return new LeaveResult(true, Locales.component(player, "messages.dungeons.queue.result.left"));
  }

  public synchronized QueueStatus status(UUID uuid) {
    if (uuid == null) {
      return new QueueStatus(false, 0, 0, 0, false);
    }
    if (active != null && active.playerId().equals(uuid)) {
      return new QueueStatus(true, active.level(), 0, 0, true);
    }
    QueueEntry entry = findEntry(uuid);
    if (entry == null) {
      return new QueueStatus(false, 0, 0, 0, false);
    }
    Deque<QueueEntry> queue = queues.get(entry.level());
    int position = 1;
    if (queue != null) {
      for (QueueEntry q : queue) {
        if (q.playerId().equals(uuid)) {
          break;
        }
        position++;
      }
    }
    int total = queue == null ? 0 : queue.size();
    return new QueueStatus(true, entry.level(), position, total, false);
  }

  public synchronized int queueSize(int level) {
    Deque<QueueEntry> queue = queues.get(level);
    return queue == null ? 0 : queue.size();
  }

  public synchronized int maxCompleted(UUID uuid, String dungeonId) {
    if (uuid == null || dungeonId == null || progress == null) {
      return 0;
    }
    return Math.max(0, progress.maxCompleted(uuid, dungeonId));
  }

  public synchronized void tick() {
    cleanupExpired();
    autoStart();
  }

  public synchronized boolean isActive() {
    return active != null;
  }

  public synchronized void clearQueues() {
    queues.clear();
  }

  public synchronized boolean debugStart(Player player, int level) {
    if (player == null || sessions == null) {
      return false;
    }
    if (active != null || sessions.isActive()) {
      return false;
    }
    DungeonSpec dungeon = registry.dungeon();
    if (dungeon == null || dungeon.levels() == null || !dungeon.levels().containsKey(level)) {
      return false;
    }
    QueueEntry entry = new QueueEntry(player.getUniqueId(), player.getName(), level, System.currentTimeMillis());
    if (!sessions.start(entry)) {
      return false;
    }
    active = entry;
    return true;
  }

  private boolean isWorldAllowed(World world) {
    return worldAllowlist == null || worldAllowlist.isAllowed(world);
  }

  private boolean isUnlocked(UUID uuid, String dungeonId, int level) {
    if (level <= 1) {
      return true;
    }
    int max = progress == null ? 0 : progress.maxCompleted(uuid, dungeonId);
    return max >= level - 1;
  }

  private QueueEntry findEntry(UUID uuid) {
    for (Deque<QueueEntry> queue : queues.values()) {
      for (QueueEntry entry : queue) {
        if (entry.playerId().equals(uuid)) {
          return entry;
        }
      }
    }
    return null;
  }

  private QueueEntry removeEntry(UUID uuid) {
    for (Deque<QueueEntry> queue : queues.values()) {
      for (QueueEntry entry : queue) {
        if (entry.playerId().equals(uuid)) {
          queue.remove(entry);
          return entry;
        }
      }
    }
    return null;
  }

  private void autoStart() {
    if (active != null) {
      return;
    }
    if (sessions != null && sessions.isActive()) {
      return;
    }
    QueueEntry next = nextEntry();
    if (next == null) {
      return;
    }
    if (sessions != null && sessions.start(next)) {
      active = next;
      return;
    }
    active = next;
    Player player = Bukkit.getPlayer(next.playerId());
    if (player != null) {
      player.sendMessage(Locales.component(player, "messages.dungeons.queue.stubStart"));
    }
    logger.info("[Dungeon] Queue auto-started level " + next.level() + " for " + next.playerName());
    Bukkit.getScheduler().runTask(plugin, () -> finishStub(next));
  }

  private QueueEntry nextEntry() {
    QueueEntry best = null;
    int bestLevel = 0;
    for (Map.Entry<Integer, Deque<QueueEntry>> entry : queues.entrySet()) {
      Deque<QueueEntry> queue = entry.getValue();
      if (queue == null || queue.isEmpty()) {
        continue;
      }
      QueueEntry candidate = queue.peekFirst();
      if (candidate == null) {
        continue;
      }
      if (best == null || candidate.queuedAt() < best.queuedAt()) {
        best = candidate;
        bestLevel = entry.getKey();
      }
    }
    if (best != null) {
      Deque<QueueEntry> queue = queues.get(bestLevel);
      if (queue != null) {
        queue.removeFirst();
      }
    }
    return best;
  }

  private synchronized void finishStub(QueueEntry entry) {
    if (active == null || entry == null || !active.playerId().equals(entry.playerId())) {
      return;
    }
    active = null;
    autoStart();
  }

  synchronized void onSessionFinished(QueueEntry entry) {
    if (entry != null && active != null && entry.playerId().equals(active.playerId())) {
      active = null;
    }
    autoStart();
  }

  private void cleanupExpired() {
    DungeonSpec dungeon = registry.dungeon();
    DungeonSpec.DungeonQueueConfig queueConfig = queueConfig(dungeon);
    int timeoutSeconds = queueConfig.entryTimeoutSeconds();
    if (timeoutSeconds <= 0) {
      return;
    }
    long cutoff = System.currentTimeMillis() - (timeoutSeconds * 1000L);
    for (Deque<QueueEntry> queue : queues.values()) {
      if (queue == null || queue.isEmpty()) {
        continue;
      }
      queue.removeIf(entry -> entry.queuedAt() < cutoff);
    }
  }

  private DungeonSpec.DungeonQueueConfig queueConfig(DungeonSpec dungeon) {
    if (dungeon == null || dungeon.queue() == null) {
      return new DungeonSpec.DungeonQueueConfig(0, 0);
    }
    return dungeon.queue();
  }
}
