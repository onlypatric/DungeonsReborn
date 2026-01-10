package dev.patric.dungeonsreborn.gui.components.input;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.gui.Window;

/**
 * A small helper to manage a group of toggle buttons (single-choice or multi-choice).
 * <p>
 * A {@link ChoiceButton} created with this group will redraw all registered group buttons on change.
 */
public final class ChoiceGroup<T> {
  public enum Mode {
    SINGLE,
    MULTI
  }

  private final Mode mode;
  private boolean allowNoneSelected;

  private final Map<UUID, T> singleByPlayer = new HashMap<>();
  private final Map<UUID, Set<T>> multiByPlayer = new HashMap<>();
  private final Map<Window, Set<Integer>> slotsByWindow = new IdentityHashMap<>();

  private BiConsumer<Player, T> onChangeSingle = (p, v) -> {
  };
  private BiConsumer<Player, Set<T>> onChangeMulti = (p, v) -> {
  };

  private ChoiceGroup(Mode mode) {
    this.mode = mode;
  }

  public static <T> ChoiceGroup<T> single() {
    return new ChoiceGroup<>(Mode.SINGLE);
  }

  public static <T> ChoiceGroup<T> multi() {
    return new ChoiceGroup<>(Mode.MULTI);
  }

  public ChoiceGroup<T> allowNoneSelected(boolean allow) {
    this.allowNoneSelected = allow;
    return this;
  }

  public ChoiceGroup<T> onChangeSingle(BiConsumer<Player, T> onChange) {
    this.onChangeSingle = Objects.requireNonNull(onChange, "onChange");
    return this;
  }

  public ChoiceGroup<T> onChangeMulti(BiConsumer<Player, Set<T>> onChange) {
    this.onChangeMulti = Objects.requireNonNull(onChange, "onChange");
    return this;
  }

  Mode mode() {
    return mode;
  }

  void register(Window window, int slot) {
    slotsByWindow.computeIfAbsent(window, w -> new HashSet<>()).add(slot);
  }

  void redraw(Window window, Player player) {
    Set<Integer> slots = slotsByWindow.get(window);
    if (slots == null || slots.isEmpty()) {
      return;
    }
    for (Integer slot : slots) {
      if (slot == null) {
        continue;
      }
      window.redrawSlot(player, slot);
    }
  }

  public boolean isSelected(Player player, T option) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(option, "option");
    UUID id = player.getUniqueId();
    if (mode == Mode.SINGLE) {
      T selected = singleByPlayer.get(id);
      return Objects.equals(selected, option);
    }
    return multiByPlayer.getOrDefault(id, Set.of()).contains(option);
  }

  public T selected(Player player) {
    Objects.requireNonNull(player, "player");
    return singleByPlayer.get(player.getUniqueId());
  }

  public Set<T> selectedSet(Player player) {
    Objects.requireNonNull(player, "player");
    Set<T> set = multiByPlayer.get(player.getUniqueId());
    return set == null ? Set.of() : Collections.unmodifiableSet(set);
  }

  void toggle(Window window, Player player, T option) {
    Objects.requireNonNull(window, "window");
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(option, "option");
    UUID id = player.getUniqueId();

    if (mode == Mode.SINGLE) {
      T current = singleByPlayer.get(id);
      if (Objects.equals(current, option)) {
        if (allowNoneSelected) {
          singleByPlayer.remove(id);
          onChangeSingle.accept(player, null);
        }
        return;
      }
      singleByPlayer.put(id, option);
      onChangeSingle.accept(player, option);
      return;
    }

    Set<T> set = multiByPlayer.computeIfAbsent(id, k -> new HashSet<>());
    boolean changed;
    if (set.contains(option)) {
      changed = set.remove(option);
    } else {
      changed = set.add(option);
    }
    if (changed) {
      onChangeMulti.accept(player, Collections.unmodifiableSet(new HashSet<>(set)));
    }
  }
}

