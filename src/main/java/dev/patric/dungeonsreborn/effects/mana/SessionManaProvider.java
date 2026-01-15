package dev.patric.dungeonsreborn.effects.mana;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

/**
 * In-memory mana provider (session-based).
 * <p>
 * Mana resets when the player goes offline and comes back.
 */
public final class SessionManaProvider implements ManaProvider {
  private final double defaultMax;
  private final ConcurrentHashMap<UUID, Double> manaByPlayer = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, Double> maxByPlayer = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, Double> maxBonusByPlayer = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, Double> regenBonusByPlayer = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, Double> classMaxBonusByPlayer = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, Double> classRegenBonusByPlayer = new ConcurrentHashMap<>();

  public SessionManaProvider(double defaultMax) {
    if (defaultMax <= 0) {
      throw new IllegalArgumentException("defaultMax must be > 0");
    }
    this.defaultMax = defaultMax;
  }

  public void init(Player player) {
    Objects.requireNonNull(player, "player");
    UUID id = player.getUniqueId();
    maxByPlayer.putIfAbsent(id, defaultMax);
    manaByPlayer.putIfAbsent(id, maxByPlayer.get(id));
  }

  public void reset(Player player) {
    Objects.requireNonNull(player, "player");
    UUID id = player.getUniqueId();
    maxByPlayer.put(id, defaultMax);
    manaByPlayer.put(id, defaultMax);
    maxBonusByPlayer.remove(id);
    regenBonusByPlayer.remove(id);
    classMaxBonusByPlayer.remove(id);
    classRegenBonusByPlayer.remove(id);
  }

  public void clear(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    manaByPlayer.remove(playerId);
    maxByPlayer.remove(playerId);
    maxBonusByPlayer.remove(playerId);
    regenBonusByPlayer.remove(playerId);
    classMaxBonusByPlayer.remove(playerId);
    classRegenBonusByPlayer.remove(playerId);
  }

  @Override
  public double get(Player player) {
    Objects.requireNonNull(player, "player");
    init(player);
    UUID id = player.getUniqueId();
    double max = getMax(player);
    Double v = manaByPlayer.get(id);
    if (v == null) {
      manaByPlayer.put(id, max);
      return max;
    }
    return clamp(v, 0.0, max);
  }

  @Override
  public double getMax(Player player) {
    Objects.requireNonNull(player, "player");
    init(player);
    UUID id = player.getUniqueId();
    double base = baseMax(id);
    double bonus = maxBonusByPlayer.getOrDefault(id, 0.0) + classMaxBonusByPlayer.getOrDefault(id, 0.0);
    return Math.max(0.0, base + bonus);
  }

  @Override
  public void set(Player player, double value) {
    Objects.requireNonNull(player, "player");
    init(player);
    UUID id = player.getUniqueId();
    double max = getMax(player);
    manaByPlayer.put(id, clamp(value, 0.0, max));
  }

  @Override
  public void setMax(Player player, double max) {
    Objects.requireNonNull(player, "player");
    if (max <= 0) {
      throw new IllegalArgumentException("max must be > 0");
    }
    init(player);
    UUID id = player.getUniqueId();
    maxByPlayer.put(id, max);
    double bonus = maxBonusByPlayer.getOrDefault(id, 0.0);
    double classBonus = classMaxBonusByPlayer.getOrDefault(id, 0.0);
    double total = Math.max(0.0, max + bonus + classBonus);
    Double current = manaByPlayer.get(id);
    if (current != null) {
      manaByPlayer.put(id, clamp(current, 0.0, total));
    }
  }

  public void setMaxBonus(Player player, double bonus) {
    Objects.requireNonNull(player, "player");
    init(player);
    UUID id = player.getUniqueId();
    if (!Double.isFinite(bonus) || Math.abs(bonus) < 1e-9) {
      maxBonusByPlayer.remove(id);
    } else {
      maxBonusByPlayer.put(id, bonus);
    }
    double base = baseMax(id);
    double total = Math.max(0.0, base + maxBonusByPlayer.getOrDefault(id, 0.0)
        + classMaxBonusByPlayer.getOrDefault(id, 0.0));
    Double current = manaByPlayer.get(id);
    if (current != null) {
      manaByPlayer.put(id, clamp(current, 0.0, total));
    }
  }

  public double maxBonus(Player player) {
    Objects.requireNonNull(player, "player");
    init(player);
    return maxBonusByPlayer.getOrDefault(player.getUniqueId(), 0.0);
  }

  public void setClassMaxBonus(Player player, double bonus) {
    Objects.requireNonNull(player, "player");
    init(player);
    UUID id = player.getUniqueId();
    if (!Double.isFinite(bonus) || Math.abs(bonus) < 1e-9) {
      classMaxBonusByPlayer.remove(id);
    } else {
      classMaxBonusByPlayer.put(id, bonus);
    }
    double base = baseMax(id);
    double total = Math.max(0.0, base + maxBonusByPlayer.getOrDefault(id, 0.0)
        + classMaxBonusByPlayer.getOrDefault(id, 0.0));
    Double current = manaByPlayer.get(id);
    if (current != null) {
      manaByPlayer.put(id, clamp(current, 0.0, total));
    }
  }

  public double classMaxBonus(Player player) {
    Objects.requireNonNull(player, "player");
    init(player);
    return classMaxBonusByPlayer.getOrDefault(player.getUniqueId(), 0.0);
  }

  public void setRegenBonus(Player player, double bonus) {
    Objects.requireNonNull(player, "player");
    init(player);
    UUID id = player.getUniqueId();
    if (!Double.isFinite(bonus) || Math.abs(bonus) < 1e-9) {
      regenBonusByPlayer.remove(id);
    } else {
      regenBonusByPlayer.put(id, bonus);
    }
  }

  public void setClassRegenBonus(Player player, double bonus) {
    Objects.requireNonNull(player, "player");
    init(player);
    UUID id = player.getUniqueId();
    if (!Double.isFinite(bonus) || Math.abs(bonus) < 1e-9) {
      classRegenBonusByPlayer.remove(id);
    } else {
      classRegenBonusByPlayer.put(id, bonus);
    }
  }

  public double regenBonus(Player player) {
    Objects.requireNonNull(player, "player");
    init(player);
    return regenBonusByPlayer.getOrDefault(player.getUniqueId(), 0.0);
  }

  public double classRegenBonus(Player player) {
    Objects.requireNonNull(player, "player");
    init(player);
    return classRegenBonusByPlayer.getOrDefault(player.getUniqueId(), 0.0);
  }

  @Override
  public Component tryConsume(Player player, double amount) {
    Objects.requireNonNull(player, "player");
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    double current = get(player);
    if (current + 1e-9 < amount) {
      return Component.text("§cNot enough mana. (" + format(current) + "/" + format(getMax(player)) + ")");
    }
    set(player, current - amount);
    return null;
  }

  private static double clamp(double v, double min, double max) {
    return Math.max(min, Math.min(max, v));
  }

  private double baseMax(UUID id) {
    Double v = maxByPlayer.get(id);
    if (v == null || v <= 0) {
      maxByPlayer.put(id, defaultMax);
      return defaultMax;
    }
    return v;
  }

  private static String format(double v) {
    if (Math.abs(v - Math.round(v)) < 1e-9) {
      return String.valueOf((long) Math.round(v));
    }
    return String.format(java.util.Locale.ROOT, "%.2f", v);
  }
}
