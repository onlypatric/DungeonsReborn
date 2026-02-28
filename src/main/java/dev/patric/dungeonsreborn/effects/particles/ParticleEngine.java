package dev.patric.dungeonsreborn.effects.particles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Batched particle emission with per-viewer filtering and per-tick budgets.
 * <p>
 * All methods must be called from the primary thread.
 */
public final class ParticleEngine {
  public record Stats(
      int queuedRequests,
      int maxQueuedRequestsPerTick,
      int maxParticlesPerPlayerPerTick,
      double quality,
      double defaultRange,
      long lastFlushNanos,
      int lastFlushRequests,
      long lastFlushParticlesSent,
      long lastFlushParticlesDroppedByBudget,
      long lastDroppedRequestsByQueueCap) {
  }

  private record Request(
      World world,
      Location location,
      Particle particle,
      int count,
      double offsetX,
      double offsetY,
      double offsetZ,
      double extra,
      Object data,
      double range,
      String permission,
      UUID excludeViewer) {
    private Request {
      Objects.requireNonNull(world, "world");
      Objects.requireNonNull(location, "location");
      Objects.requireNonNull(particle, "particle");
      if (count < 0) {
        throw new IllegalArgumentException("count must be >= 0");
      }
      if (range < 0) {
        throw new IllegalArgumentException("range must be >= 0");
      }
    }
  }

  private final List<Request> queue = new ArrayList<>();
  private int maxQueuedRequestsPerTick = 25_000;
  private int maxParticlesPerPlayerPerTick = 900;
  private double quality = 1.0;
  private double minQuality = 0.25;
  private double maxQuality = 1.0;
  private double qualityStepDown = 0.1;
  private double qualityStepUp = 0.05;
  private long qualityCooldownTicks = 20L;
  private long lastQualityAdjustTick = -1L;
  private boolean autoQualityEnabled = true;
  private double defaultPlayerQuality = 1.0;
  private final Map<UUID, Double> playerQuality = new HashMap<>();
  private double defaultRange = 48.0;
  private long droppedRequestsThisTick;
  private long lastFlushNanos;
  private int lastFlushRequests;
  private long lastFlushParticlesSent;
  private long lastFlushParticlesDroppedByBudget;
  private long lastDroppedRequestsByQueueCap;

  public void setMaxQueuedRequestsPerTick(int max) {
    if (max < 0) {
      throw new IllegalArgumentException("max must be >= 0");
    }
    maxQueuedRequestsPerTick = max;
  }

  public int maxQueuedRequestsPerTick() {
    return maxQueuedRequestsPerTick;
  }

  public void setMaxParticlesPerPlayerPerTick(int max) {
    if (max < 0) {
      throw new IllegalArgumentException("max must be >= 0");
    }
    maxParticlesPerPlayerPerTick = max;
  }

  public int maxParticlesPerPlayerPerTick() {
    return maxParticlesPerPlayerPerTick;
  }

  /**
   * Global quality multiplier applied to particle counts (clamped to >= 0).
   */
  public void setQuality(double quality) {
    if (!Double.isFinite(quality)) {
      throw new IllegalArgumentException("quality must be finite");
    }
    this.quality = clampQuality(quality);
  }

  public double quality() {
    return quality;
  }

  public double lodFactor() {
    return quality;
  }

  public void setQualityBounds(double min, double max) {
    if (!Double.isFinite(min) || !Double.isFinite(max) || min < 0.0 || max < min) {
      throw new IllegalArgumentException("invalid quality bounds");
    }
    minQuality = min;
    maxQuality = max;
    quality = clampQuality(quality);
  }

  public void setQualitySteps(double stepDown, double stepUp, long cooldownTicks) {
    if (!Double.isFinite(stepDown) || !Double.isFinite(stepUp) || stepDown < 0.0 || stepUp < 0.0) {
      throw new IllegalArgumentException("quality steps must be finite and >= 0");
    }
    if (cooldownTicks < 0) {
      throw new IllegalArgumentException("cooldownTicks must be >= 0");
    }
    qualityStepDown = stepDown;
    qualityStepUp = stepUp;
    qualityCooldownTicks = cooldownTicks;
  }

  public void setAutoQualityEnabled(boolean enabled) {
    autoQualityEnabled = enabled;
  }

  public boolean autoQualityEnabled() {
    return autoQualityEnabled;
  }

  public void autoAdjustQuality(long tickNow) {
    if (!autoQualityEnabled) {
      return;
    }
    boolean dropped = lastFlushParticlesDroppedByBudget > 0 || lastDroppedRequestsByQueueCap > 0;
    if (dropped) {
      if (qualityStepDown > 0.0) {
        quality = clampQuality(quality - qualityStepDown);
      }
      lastQualityAdjustTick = tickNow;
      return;
    }
    if (quality >= maxQuality || qualityStepUp <= 0.0) {
      return;
    }
    if (lastQualityAdjustTick >= 0 && tickNow - lastQualityAdjustTick < qualityCooldownTicks) {
      return;
    }
    quality = clampQuality(quality + qualityStepUp);
    lastQualityAdjustTick = tickNow;
  }

  private double clampQuality(double value) {
    double clamped = Math.max(minQuality, Math.min(maxQuality, value));
    return Math.max(0.0, clamped);
  }

  public void setDefaultPlayerQuality(double quality) {
    if (!Double.isFinite(quality) || quality < 0.0) {
      throw new IllegalArgumentException("quality must be finite and >= 0");
    }
    defaultPlayerQuality = quality;
  }

  public double defaultPlayerQuality() {
    return defaultPlayerQuality;
  }

  public double playerQuality(UUID playerId) {
    if (playerId == null) {
      return defaultPlayerQuality;
    }
    return playerQuality.getOrDefault(playerId, defaultPlayerQuality);
  }

  public void setPlayerQuality(UUID playerId, double quality) {
    if (playerId == null) {
      return;
    }
    if (!Double.isFinite(quality) || quality < 0.0) {
      throw new IllegalArgumentException("quality must be finite and >= 0");
    }
    playerQuality.put(playerId, quality);
  }

  public void clearPlayerQuality(UUID playerId) {
    if (playerId == null) {
      return;
    }
    playerQuality.remove(playerId);
  }

  public void setDefaultRange(double range) {
    if (!Double.isFinite(range) || range < 0) {
      throw new IllegalArgumentException("range must be finite and >= 0");
    }
    defaultRange = range;
  }

  public double defaultRange() {
    return defaultRange;
  }

  public void emit(World world, Location location, Particle particle, int count,
      double offsetX, double offsetY, double offsetZ, double extra) {
    emit(world, location, particle, count, offsetX, offsetY, offsetZ, extra, null, defaultRange, null, null);
  }

  public void emit(World world, Location location, Particle particle, int count,
      double offsetX, double offsetY, double offsetZ, double extra, Object data) {
    emit(world, location, particle, count, offsetX, offsetY, offsetZ, extra, data, defaultRange, null, null);
  }

  public void emit(World world, Location location, Particle particle, int count,
      double offsetX, double offsetY, double offsetZ, double extra, Object data,
      double range, String permission, UUID excludeViewer) {
    if (world == null || location == null || particle == null) {
      return;
    }

    if (count <= 0) {
      return;
    }
    int scaled = (int) Math.round(count * quality);
    // Avoid "vanishing" small-count effects (e.g., beams using count=1) at low-but-nonzero quality.
    if (quality > 0.0) {
      scaled = Math.max(1, scaled);
    }
    if (scaled <= 0) {
      return;
    }
    if (maxQueuedRequestsPerTick > 0 && queue.size() >= maxQueuedRequestsPerTick) {
      droppedRequestsThisTick++;
      return;
    }
    queue.add(new Request(
        world,
        location.clone(),
        particle,
        scaled,
        offsetX,
        offsetY,
        offsetZ,
        extra,
        data,
        range,
        permission,
        excludeViewer));
  }

  public void flush() {
    if (queue.isEmpty()) {
      lastDroppedRequestsByQueueCap = droppedRequestsThisTick;
      droppedRequestsThisTick = 0;
      return;
    }

    long start = System.nanoTime();
    long sentParticles = 0L;
    long droppedByBudget = 0L;
    int flushRequests = queue.size();
    Map<UUID, Integer> used = maxParticlesPerPlayerPerTick <= 0 ? null : new HashMap<>();

    for (int i = 0; i < queue.size(); i++) {
      Request r = queue.get(i);
      World world = r.world();
      if (world == null) {
        continue;
      }
      List<Player> players = world.getPlayers();
      if (players.isEmpty()) {
        continue;
      }

      double rangeSq = r.range() * r.range();
      for (Player player : players) {
        if (player == null) {
          continue;
        }
        if (r.excludeViewer() != null && r.excludeViewer().equals(player.getUniqueId())) {
          continue;
        }
        if (r.permission() != null && !player.hasPermission(r.permission())) {
          continue;
        }
        if (r.range() > 0) {
          Location pl = player.getLocation();
          if (pl == null || pl.getWorld() != world) {
            continue;
          }
          if (pl.distanceSquared(r.location()) > rangeSq) {
            continue;
          }
        }

        int sendCount = r.count();
        double perPlayerQuality = playerQuality(player.getUniqueId());
        if (perPlayerQuality > 0.0 && perPlayerQuality != 1.0) {
          sendCount = (int) Math.round(sendCount * perPlayerQuality);
          sendCount = Math.max(1, sendCount);
        } else if (perPlayerQuality <= 0.0) {
          sendCount = 0;
        }
        if (used != null) {
          int already = used.getOrDefault(player.getUniqueId(), 0);
          int remaining = maxParticlesPerPlayerPerTick - already;
          if (remaining <= 0) {
            droppedByBudget += sendCount;
            continue;
          }
          if (sendCount > remaining) {
            droppedByBudget += (sendCount - remaining);
            sendCount = remaining;
          }
          used.put(player.getUniqueId(), already + sendCount);
        }

        try {
          if (r.data() == null) {
            player.spawnParticle(r.particle(), r.location(), sendCount, r.offsetX(), r.offsetY(), r.offsetZ(), r.extra());
          } else if (r.particle() == Particle.DUST && r.data() instanceof Particle.DustOptions dustOptions) {
            // Use the typed overload for dust to keep custom color/size deterministic.
            player.spawnParticle(Particle.DUST, r.location(), sendCount, dustOptions);
          } else if (r.particle() == Particle.DUST_COLOR_TRANSITION
              && r.data() instanceof Particle.DustTransition dustTransition) {
            // Same as DUST: prefer typed overload to avoid client/default fallback color.
            player.spawnParticle(Particle.DUST_COLOR_TRANSITION, r.location(), sendCount, dustTransition);
          } else {
            player.spawnParticle(r.particle(), r.location(), sendCount, r.offsetX(), r.offsetY(), r.offsetZ(), r.extra(), r.data());
          }
          sentParticles += sendCount;
        } catch (IllegalArgumentException ignored) {
          // Some particle/data combinations are invalid; ignore to avoid hard-failing the tick.
        }
      }
    }

    queue.clear();
    lastFlushNanos = Math.max(0L, System.nanoTime() - start);
    lastFlushRequests = flushRequests;
    lastFlushParticlesSent = sentParticles;
    lastFlushParticlesDroppedByBudget = droppedByBudget;
    lastDroppedRequestsByQueueCap = droppedRequestsThisTick;
    droppedRequestsThisTick = 0;
  }

  public Stats stats() {
    return new Stats(
        queue.size(),
        maxQueuedRequestsPerTick,
      maxParticlesPerPlayerPerTick,
      quality,
      defaultRange,
        lastFlushNanos,
        lastFlushRequests,
        lastFlushParticlesSent,
        lastFlushParticlesDroppedByBudget,
        lastDroppedRequestsByQueueCap);
  }
}
