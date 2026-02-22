package dev.patric.dungeonsreborn.effects.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class CombatPlannerService {
  public record PlannedBindings(long tick, List<CombatEventBinding> bindings) {
  }

  private final ExecutorService executor;
  private final AtomicInteger queued = new AtomicInteger();
  private volatile int queueCapacity;
  private volatile boolean enabled;

  public CombatPlannerService(int workers, int queueCapacity) {
    int workerCount = Math.max(1, workers);
    this.queueCapacity = Math.max(1, queueCapacity);
    ThreadFactory factory = r -> {
      Thread thread = new Thread(r, "dr-combat-planner");
      thread.setDaemon(true);
      return thread;
    };
    this.executor = Executors.newFixedThreadPool(workerCount, factory);
    this.enabled = true;
  }

  public void configure(boolean enabled, int queueCapacity) {
    this.enabled = enabled;
    this.queueCapacity = Math.max(1, queueCapacity);
  }

  public boolean enabled() {
    return enabled;
  }

  public int queued() {
    return queued.get();
  }

  public boolean submit(CombatEventContext context, List<CombatEventBinding> all, Consumer<PlannedBindings> callback) {
    if (!enabled) {
      return false;
    }
    if (queued.incrementAndGet() > queueCapacity) {
      queued.decrementAndGet();
      return false;
    }
    executor.execute(() -> {
      try {
        ArrayList<CombatEventBinding> out = new ArrayList<>();
        for (CombatEventBinding binding : all) {
          if (binding.chance() <= 0.0) {
            continue;
          }
          if (binding.filters().minDamage() > 0.0 && context.damage() < binding.filters().minDamage()) {
            continue;
          }
          if (binding.filters().critOnly() && !context.crit()) {
            continue;
          }
          if (binding.filters().blockedOnly() && !context.blocked()) {
            continue;
          }
          out.add(binding);
        }
        callback.accept(new PlannedBindings(context.tick(), List.copyOf(out)));
      } finally {
        queued.decrementAndGet();
      }
    });
    return true;
  }

  public void shutdown() {
    executor.shutdownNow();
  }
}

