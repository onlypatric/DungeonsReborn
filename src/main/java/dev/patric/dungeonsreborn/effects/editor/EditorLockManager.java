package dev.patric.dungeonsreborn.effects.editor;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.effects.Ids;

public final class EditorLockManager {
  public record LockInfo(String abilityId, UUID ownerId, String ownerName, Instant acquiredAt) {
  }

  public record LockResult(boolean acquired, LockInfo lock, String message) {
  }

  private final Map<String, LockInfo> locks = new ConcurrentHashMap<>();

  public LockResult tryLock(String abilityId, Player player) {
    Objects.requireNonNull(player, "player");
    String normalized = Ids.normalize(abilityId);
    LockInfo existing = locks.get(normalized);
    if (existing != null) {
      if (existing.ownerId().equals(player.getUniqueId())) {
        return new LockResult(true, existing, "already locked by you");
      }
      return new LockResult(false, existing, "locked by " + existing.ownerName());
    }
    LockInfo lock = new LockInfo(normalized, player.getUniqueId(), player.getName(), Instant.now());
    locks.put(normalized, lock);
    return new LockResult(true, lock, "lock acquired");
  }

  public boolean release(String abilityId, UUID ownerId) {
    String normalized = Ids.normalize(abilityId);
    LockInfo existing = locks.get(normalized);
    if (existing == null || !existing.ownerId().equals(ownerId)) {
      return false;
    }
    locks.remove(normalized);
    return true;
  }

  public void releaseAll(UUID ownerId) {
    locks.entrySet().removeIf(entry -> entry.getValue().ownerId().equals(ownerId));
  }

  public boolean isLockedByOther(String abilityId, UUID ownerId) {
    String normalized = Ids.normalize(abilityId);
    LockInfo existing = locks.get(normalized);
    return existing != null && !existing.ownerId().equals(ownerId);
  }

  public LockInfo lockInfo(String abilityId) {
    String normalized = Ids.normalize(abilityId);
    return locks.get(normalized);
  }
}
