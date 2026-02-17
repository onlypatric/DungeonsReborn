package dev.patric.dungeonsreborn.gui.components.input;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.patric.dungeonsreborn.gui.GuiComponent;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;

/**
 * A single-slot input that cycles through a list of options (left = next, right = previous).
 */
public final class CycleSelector<T> implements GuiComponent {
  private final List<T> options;
  private final BiFunction<Player, T, ItemStack> itemFactory;
  private final Map<UUID, Integer> indexByPlayer = new HashMap<>();
  private BiConsumer<Player, T> onChange = (p, v) -> {
  };
  private final Button button;

  public CycleSelector(List<T> options, BiFunction<Player, T, ItemStack> itemFactory) {
    this.options = List.copyOf(Objects.requireNonNull(options, "options"));
    if (this.options.isEmpty()) {
      throw new IllegalArgumentException("options must not be empty");
    }
    this.itemFactory = Objects.requireNonNull(itemFactory, "itemFactory");

    this.button = new Button(p -> this.itemFactory.apply(p, selected(p)))
        .left(Locales.component(null, "gui.cycle.next"), ctx -> {
          next(ctx.player());
          ctx.redrawSlot();
        })
        .right(Locales.component(null, "gui.cycle.previous"), ctx -> {
          previous(ctx.player());
          ctx.redrawSlot();
        });
  }

  /**
   * Exposes the backing button so devs can customize lore formatting, control headers, etc.
   */
  public Button button() {
    return button;
  }

  public CycleSelector<T> onChange(BiConsumer<Player, T> onChange) {
    this.onChange = Objects.requireNonNull(onChange, "onChange");
    return this;
  }

  public T selected(Player player) {
    Objects.requireNonNull(player, "player");
    int idx = indexByPlayer.getOrDefault(player.getUniqueId(), 0);
    idx = ((idx % options.size()) + options.size()) % options.size();
    return options.get(idx);
  }

  public void select(Player player, T value) {
    Objects.requireNonNull(player, "player");
    int idx = options.indexOf(value);
    if (idx < 0) {
      throw new IllegalArgumentException("value not in options");
    }
    indexByPlayer.put(player.getUniqueId(), idx);
  }

  @Override
  public ItemStack render(Player player) {
    return withHint(player, button.render(player));
  }

  @Override
  public void onClick(Window.ClickContext ctx) {
    button.onClick(ctx);
  }

  @Override
  public void mounted(Window window, int slot) {
    button.mounted(window, slot);
  }

  private ItemStack withHint(Player player, ItemStack item) {
    if (item == null) {
      return null;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    List<Component> lore = meta.hasLore() ? List.copyOf(meta.lore()) : List.of();
    Component hint = Locales.component(player, "gui.cycle.hint");
    if (hint != null) {
      List<Component> nextLore = new java.util.ArrayList<>(lore.size() + 1);
      nextLore.addAll(lore);
      nextLore.add(hint);
      meta.lore(nextLore);
      item.setItemMeta(meta);
    }
    return item;
  }

  private void next(Player player) {
    UUID id = player.getUniqueId();
    int idx = indexByPlayer.getOrDefault(id, 0);
    idx = (idx + 1) % options.size();
    indexByPlayer.put(id, idx);
    onChange.accept(player, options.get(idx));
  }

  private void previous(Player player) {
    UUID id = player.getUniqueId();
    int idx = indexByPlayer.getOrDefault(id, 0);
    idx = (idx - 1 + options.size()) % options.size();
    indexByPlayer.put(id, idx);
    onChange.accept(player, options.get(idx));
  }
}
