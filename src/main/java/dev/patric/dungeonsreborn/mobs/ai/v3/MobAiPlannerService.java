package dev.patric.dungeonsreborn.mobs.ai.v3;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MobAiPlannerService {
  private final MobAiUtilityEngine utilityEngine = new MobAiUtilityEngine();
  private final MobAiV3Metrics metrics = new MobAiV3Metrics();
  private final LinkedBlockingQueue<MobAiSnapshot> queue;
  private final Map<UUID, MobAiPlan> completedByEntity = new ConcurrentHashMap<>();
  private final Map<UUID, Long> pendingByEntityTick = new ConcurrentHashMap<>();
  private final ExecutorService workers;
  private final AtomicBoolean running = new AtomicBoolean(true);

  public MobAiPlannerService(int workerThreads, int queueCapacity) {
    int threads = Math.max(1, workerThreads);
    int cap = Math.max(128, queueCapacity);
    this.queue = new LinkedBlockingQueue<>(cap);
    this.workers = Executors.newFixedThreadPool(threads, threadFactory());
    for (int i = 0; i < threads; i++) {
      workers.submit(this::workerLoop);
    }
  }

  public boolean submit(MobAiSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    UUID entityId = snapshot.entityId();
    if (entityId == null) {
      return false;
    }
    Long pendingTick = pendingByEntityTick.get(entityId);
    if (pendingTick != null && pendingTick >= snapshot.tick()) {
      return false;
    }
    boolean accepted = queue.offer(snapshot);
    if (!accepted) {
      metrics.markDroppedBackpressure();
      return false;
    }
    pendingByEntityTick.put(entityId, snapshot.tick());
    metrics.markSubmitted();
    return true;
  }

  public MobAiPlan poll(UUID entityId) {
    if (entityId == null) {
      return null;
    }
    return completedByEntity.remove(entityId);
  }

  public void cancelEntity(UUID entityId) {
    if (entityId == null) {
      return;
    }
    pendingByEntityTick.remove(entityId);
    completedByEntity.remove(entityId);
  }

  public void clear() {
    queue.clear();
    pendingByEntityTick.clear();
    completedByEntity.clear();
  }

  public MobAiV3Metrics metrics() {
    return metrics;
  }

  public int queueSize() {
    return queue.size();
  }

  public void shutdown() {
    running.set(false);
    workers.shutdownNow();
    clear();
  }

  private void workerLoop() {
    while (running.get()) {
      try {
        MobAiSnapshot snapshot = queue.take();
        MobAiPlan plan = utilityEngine.plan(snapshot);
        if (plan != null && plan.entityId() != null) {
          completedByEntity.put(plan.entityId(), plan);
          metrics.markCompleted();
        }
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception ex) {
        metrics.markFailed();
      }
    }
  }

  private static ThreadFactory threadFactory() {
    return runnable -> {
      Thread thread = new Thread(runnable);
      thread.setName("dr-ai-v3-planner");
      thread.setDaemon(true);
      return thread;
    };
  }
}

