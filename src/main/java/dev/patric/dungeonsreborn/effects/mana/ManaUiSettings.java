package dev.patric.dungeonsreborn.effects.mana;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

public final class ManaUiSettings {
  public enum Flag {
    ACTIONBAR,
    WARNINGS,
    SCOREBOARD
  }

  private final EnumMap<Flag, Boolean> defaults = new EnumMap<>(Flag.class);
  private final Map<UUID, EnumMap<Flag, Boolean>> overrides = new ConcurrentHashMap<>();
  private final Map<UUID, Long> lastWarningTicks = new ConcurrentHashMap<>();

  public ManaUiSettings() {
    for (Flag flag : Flag.values()) {
      defaults.put(flag, Boolean.TRUE);
    }
  }

  public boolean enabled(Player player, Flag flag) {
    if (player == null) {
      return false;
    }
    return enabled(player.getUniqueId(), flag);
  }

  public boolean enabled(UUID playerId, Flag flag) {
    EnumMap<Flag, Boolean> map = overrides.get(playerId);
    if (map != null && map.containsKey(flag)) {
      return Boolean.TRUE.equals(map.get(flag));
    }
    return Boolean.TRUE.equals(defaults.get(flag));
  }

  public void set(UUID playerId, Flag flag, boolean enabled) {
    overrides.computeIfAbsent(playerId, id -> new EnumMap<>(Flag.class)).put(flag, enabled);
  }

  public void clear(UUID playerId) {
    overrides.remove(playerId);
    lastWarningTicks.remove(playerId);
  }

  public void setDefault(Flag flag, boolean enabled) {
    defaults.put(flag, enabled);
  }

  public boolean tryWarn(UUID playerId, long nowTick, long cooldownTicks) {
    if (cooldownTicks <= 0) {
      return true;
    }
    Long last = lastWarningTicks.get(playerId);
    if (last != null && nowTick - last < cooldownTicks) {
      return false;
    }
    lastWarningTicks.put(playerId, nowTick);
    return true;
  }
}
