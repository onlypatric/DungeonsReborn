package dev.patric.dungeonsreborn.system;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Single-threaded tick scheduler to consolidate periodic tasks.
 */
public final class SharedTickScheduler {
  public interface Handle {
    void cancel();
  }

  private final JavaPlugin plugin;
  private final Logger logger;
  private final List<Task> tasks = new ArrayList<>();
  private BukkitTask ticker;
  private long tick;

  public SharedTickScheduler(JavaPlugin plugin, Logger logger) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  public void start() {
    if (ticker != null) {
      return;
    }
    ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
  }

  public void stop() {
    if (ticker != null) {
      ticker.cancel();
      ticker = null;
    }
    tasks.clear();
  }

  public Handle schedule(String name, long periodTicks, Runnable runnable) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(runnable, "runnable");
    long period = Math.max(1L, periodTicks);
    Task task = new Task(name, period, runnable);
    tasks.add(task);
    return task;
  }

  private void tick() {
    if (tasks.isEmpty()) {
      return;
    }
    tick++;
    for (Iterator<Task> it = tasks.iterator(); it.hasNext();) {
      Task task = it.next();
      if (task.cancelled) {
        it.remove();
        continue;
      }
      if (tick < task.nextTick) {
        continue;
      }
      try {
        task.runnable.run();
      } catch (Exception ex) {
        logger.log(Level.WARNING, "[Scheduler] task '" + task.name + "' threw: " + ex.getMessage(), ex);
      }
      task.nextTick = tick + task.periodTicks;
    }
  }

  private final class Task implements Handle {
    private final String name;
    private final long periodTicks;
    private final Runnable runnable;
    private long nextTick;
    private boolean cancelled;

    private Task(String name, long periodTicks, Runnable runnable) {
      this.name = name;
      this.periodTicks = periodTicks;
      this.runnable = runnable;
      this.nextTick = tick + periodTicks;
    }

    @Override
    public void cancel() {
      cancelled = true;
    }
  }
}
