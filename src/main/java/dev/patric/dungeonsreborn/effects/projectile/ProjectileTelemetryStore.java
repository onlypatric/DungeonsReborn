package dev.patric.dungeonsreborn.effects.projectile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ProjectileTelemetryStore {
  private final ConcurrentMap<UUID, TimedTelemetry> entries = new ConcurrentHashMap<>();

  private volatile int maxActive = 10_000;
  private volatile long staleTicks = 600L;

  public void configure(int maxActive, long staleTicks) {
    this.maxActive = Math.max(1, maxActive);
    this.staleTicks = Math.max(20L, staleTicks);
  }

  public boolean upsert(ProjectileTelemetry telemetry, long tickNow) {
    Objects.requireNonNull(telemetry, "telemetry");
    if (entries.size() >= maxActive && !entries.containsKey(telemetry.projectileId())) {
      return false;
    }
    entries.put(telemetry.projectileId(), new TimedTelemetry(telemetry, tickNow));
    return true;
  }

  public ProjectileTelemetry get(UUID projectileId) {
    TimedTelemetry timed = entries.get(projectileId);
    return timed == null ? null : timed.telemetry();
  }

  public void remove(UUID projectileId) {
    if (projectileId == null) {
      return;
    }
    entries.remove(projectileId);
  }

  public List<ProjectileTelemetry> snapshot() {
    ArrayList<ProjectileTelemetry> out = new ArrayList<>(entries.size());
    for (TimedTelemetry value : entries.values()) {
      out.add(value.telemetry());
    }
    return out;
  }

  public int cleanup(long tickNow) {
    long maxAge = staleTicks;
    int removed = 0;
    for (var entry : entries.entrySet()) {
      if (tickNow - entry.getValue().tick() > maxAge) {
        if (entries.remove(entry.getKey(), entry.getValue())) {
          removed++;
        }
      }
    }
    return removed;
  }

  public int size() {
    return entries.size();
  }

  private record TimedTelemetry(ProjectileTelemetry telemetry, long tick) {
  }
}
