package dev.patric.dungeonsreborn.crafting.vanilla;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CraftingCooldownStore {
  private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

  public long remainingMillis(UUID playerId, String recipeId) {
    if (playerId == null || recipeId == null) {
      return 0L;
    }
    Map<String, Long> entries = cooldowns.get(playerId);
    if (entries == null) {
      return 0L;
    }
    Long until = entries.get(recipeId);
    if (until == null) {
      return 0L;
    }
    return Math.max(0L, until - System.currentTimeMillis());
  }

  public boolean isCoolingDown(UUID playerId, String recipeId) {
    return remainingMillis(playerId, recipeId) > 0L;
  }

  public void startCooldown(UUID playerId, String recipeId, double cooldownSeconds) {
    if (playerId == null || recipeId == null || cooldownSeconds <= 0.0) {
      return;
    }
    long duration = (long) Math.ceil(cooldownSeconds * 1000.0);
    cooldowns.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
        .put(recipeId, System.currentTimeMillis() + Math.max(1L, duration));
  }

  public void clearPlayer(UUID playerId) {
    if (playerId != null) {
      cooldowns.remove(playerId);
    }
  }

  public void clearAll() {
    cooldowns.clear();
  }
}
