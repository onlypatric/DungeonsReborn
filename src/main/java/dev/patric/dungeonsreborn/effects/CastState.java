package dev.patric.dungeonsreborn.effects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/**
 * Per-cast mutable state: variables, RNG seed, and cancellation of scheduled work.
 */
public final class CastState {
  public record Timing(long count, long nanos) {
  }

  private final UUID castId;
  private final Random rng;
  private final Map<String, Object> variables = new HashMap<>();
  private final Map<String, Timing> timings = new HashMap<>();
  private final ArrayList<EffectsEngine.ScheduledHandle> handles = new ArrayList<>();
  private final ArrayList<Runnable> cancelHooks = new ArrayList<>();
  private boolean cancelled;

  CastState(UUID castId) {
    this.castId = Objects.requireNonNull(castId, "castId");
    // Stable per-cast RNG seed.
    this.rng = new Random(castId.getMostSignificantBits() ^ castId.getLeastSignificantBits());
  }

  public UUID castId() {
    return castId;
  }

  public Random rng() {
    return rng;
  }

  public boolean isCancelled() {
    return cancelled;
  }

  public void cancel() {
    if (cancelled) {
      return;
    }
    for (int i = 0; i < cancelHooks.size(); i++) {
      try {
        cancelHooks.get(i).run();
      } catch (Exception ignored) {
      }
    }
    cancelHooks.clear();
    cancelled = true;
    for (int i = 0; i < handles.size(); i++) {
      try {
        handles.get(i).cancel();
      } catch (Exception ignored) {
      }
    }
    handles.clear();
  }

  public void track(EffectsEngine.ScheduledHandle handle) {
    Objects.requireNonNull(handle, "handle");
    if (cancelled) {
      handle.cancel();
      return;
    }
    handles.add(handle);
  }

  public void onCancel(Runnable hook) {
    Objects.requireNonNull(hook, "hook");
    if (cancelled) {
      hook.run();
      return;
    }
    cancelHooks.add(hook);
  }

  public void recordTiming(String name, long nanos) {
    Objects.requireNonNull(name, "name");
    if (nanos < 0) {
      return;
    }
    Timing existing = timings.get(name);
    if (existing == null) {
      timings.put(name, new Timing(1L, nanos));
      return;
    }
    timings.put(name, new Timing(existing.count() + 1L, existing.nanos() + nanos));
  }

  public Map<String, Timing> timings() {
    return java.util.Collections.unmodifiableMap(timings);
  }

  public Map<String, Object> variables() {
    return variables;
  }

  public Object get(String key) {
    return variables.get(key);
  }

  public <T> T get(VarKey<T> key) {
    Objects.requireNonNull(key, "key");
    Object v = variables.get(key.name());
    if (v == null) {
      return null;
    }
    if (!key.type().isInstance(v)) {
      return null;
    }
    return key.type().cast(v);
  }

  public void put(String key, Object value) {
    Objects.requireNonNull(key, "key");
    if (value == null) {
      variables.remove(key);
    } else {
      variables.put(key, value);
    }
  }

  public <T> void put(VarKey<T> key, T value) {
    Objects.requireNonNull(key, "key");
    put(key.name(), value);
  }
}
