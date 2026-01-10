package dev.patric.dungeonsreborn.gui.state;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.Window;

/**
 * A simple per-player state container with change listeners.
 * <p>
 * This is the foundation for "binding": when the state changes, a window (or specific slots) can be redrawn automatically.
 */
public final class GuiState<T> {
  @FunctionalInterface
  public interface Listener<T> {
    void onChange(Player player, T oldValue, T newValue);
  }

  public interface Subscription extends AutoCloseable {
    @Override
    void close();

    static Subscription noop() {
      return () -> {
      };
    }
  }

  private static final Object NULL = new Object();

  private final Function<Player, T> defaultValue;
  private final ConcurrentHashMap<UUID, Object> values = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<Listener<T>>> listeners = new ConcurrentHashMap<>();

  public GuiState(Function<Player, T> defaultValue) {
    this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
  }

  public static <T> GuiState<T> of(Function<Player, T> defaultValue) {
    return new GuiState<>(defaultValue);
  }

  public static <T> GuiState<T> ofValue(T defaultValue) {
    return new GuiState<>(p -> defaultValue);
  }

  public T get(Player player) {
    Objects.requireNonNull(player, "player");
    Object raw = values.get(player.getUniqueId());
    if (raw == null) {
      return defaultValue.apply(player);
    }
    if (raw == NULL) {
      return null;
    }
    @SuppressWarnings("unchecked")
    T value = (T) raw;
    return value;
  }

  /**
   * Sets an explicit value for this player (can be {@code null}).
   */
  public void set(Player player, T value) {
    Objects.requireNonNull(player, "player");
    UUID id = player.getUniqueId();
    T oldValue = get(player);
    Object nextRaw = value == null ? NULL : value;
    values.put(id, nextRaw);
    notifyListeners(player, oldValue, value);
  }

  /**
   * Clears the explicit value for this player, reverting to the default.
   */
  public void clear(Player player) {
    Objects.requireNonNull(player, "player");
    UUID id = player.getUniqueId();
    T oldValue = get(player);
    values.remove(id);
    T newValue = defaultValue.apply(player);
    notifyListeners(player, oldValue, newValue);
  }

  public void update(Player player, UnaryOperator<T> updater) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(updater, "updater");
    T current = get(player);
    set(player, updater.apply(current));
  }

  public Subscription listen(Player player, Listener<T> listener) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(listener, "listener");
    UUID id = player.getUniqueId();
    listeners.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>()).add(listener);
    return () -> {
      CopyOnWriteArrayList<Listener<T>> list = listeners.get(id);
      if (list != null) {
        list.remove(listener);
      }
    };
  }

  /**
   * Binds this state to a window's slots: when the state changes for this window's viewer, those slots are redrawn.
   * <p>
   * Intended usage: call from {@link Window#build(Player)} once the viewer is known.
   */
  public Subscription bindRedraw(Window window, int... slots) {
    Objects.requireNonNull(window, "window");
    UUID viewer = window.viewer();
    if (viewer == null) {
      return Subscription.noop();
    }

    int[] uniqueSlots = Arrays.stream(slots).distinct().toArray();
    AtomicBoolean scheduled = new AtomicBoolean(false);
    Listener<T> listener = (player, oldValue, newValue) -> {
      if (!player.getUniqueId().equals(viewer)) {
        return;
      }
      // Coalesce multiple state changes within the same tick.
      if (!scheduled.compareAndSet(false, true)) {
        return;
      }
      GuiManager.get().runNextTick(() -> {
        scheduled.set(false);
        if (!player.isOnline()) {
          return;
        }
        if (player.getOpenInventory().getTopInventory().getHolder() != window) {
          return;
        }
        for (int slot : uniqueSlots) {
          if (slot < 0 || slot >= window.size()) {
            continue;
          }
          window.redrawSlot(player, slot);
        }
      });
    };

    Player player = Bukkit.getPlayer(viewer);
    if (player == null) {
      return Subscription.noop();
    }
    return listen(player, listener);
  }

  private void notifyListeners(Player player, T oldValue, T newValue) {
    CopyOnWriteArrayList<Listener<T>> list = listeners.get(player.getUniqueId());
    if (list == null || list.isEmpty()) {
      return;
    }
    for (Listener<T> listener : list) {
      try {
        listener.onChange(player, oldValue, newValue);
      } catch (Exception ex) {
        GuiManager.get().debug("GuiState listener threw", ex);
      }
    }
  }
}

