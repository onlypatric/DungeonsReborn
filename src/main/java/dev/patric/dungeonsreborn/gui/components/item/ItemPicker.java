package dev.patric.dungeonsreborn.gui.components.item;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.GuiText;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;

/**
 * A button that lets the player pick an item from their own inventory (without moving it).
 * <p>
 * When armed, the next click on the player's bottom inventory selects the clicked item (event is cancelled) and the selection is
 * stored per-player.
 */
public final class ItemPicker extends Button {
  private static final Component DEFAULT_PROMPT = Locales.component(null, "gui.itemPicker.prompt");

  private final Map<UUID, ItemStack> selected = new ConcurrentHashMap<>();
  private final Set<UUID> picking = ConcurrentHashMap.newKeySet();

  private Component prompt = DEFAULT_PROMPT;
  private Duration timeout = Duration.ofSeconds(30);
  private boolean allowAirSelection;
  private Predicate<ItemStack> filter = item -> item != null && !item.getType().isAir();
  private Component invalidMessage = Locales.component(null, "gui.itemPicker.invalid");
  private boolean redrawWindowOnChange = true;
  private BiConsumer<Window, ItemStack> onPick = (w, item) -> {
  };
  private BiConsumer<Window, Player> onCancel = (w, p) -> {
  };
  private BiConsumer<Window, Player> onTimeout = (w, p) -> {
  };

  public ItemPicker() {
    super(p -> GuiItem.of(Material.HOPPER).displayName(Locales.component(p, "gui.itemPicker.title")).build());
    bind(ClickType.LEFT, Locales.component(null, "gui.itemPicker.action"), this::startOrCancel);
  }

  public ItemPicker prompt(Component prompt) {
    this.prompt = Objects.requireNonNull(prompt, "prompt");
    return this;
  }

  public ItemPicker timeout(Duration timeout) {
    this.timeout = Objects.requireNonNull(timeout, "timeout");
    return this;
  }

  /**
   * If enabled, clicking an empty slot selects "no item" (null) which can be used to clear a selection.
   */
  public ItemPicker allowAirSelection(boolean enabled) {
    this.allowAirSelection = enabled;
    return this;
  }

  public ItemPicker filter(Predicate<ItemStack> filter) {
    this.filter = Objects.requireNonNull(filter, "filter");
    return this;
  }

  public ItemPicker invalidMessage(Component message) {
    this.invalidMessage = Objects.requireNonNull(message, "message");
    return this;
  }

  /**
   * Redraws the whole window after selection/cancel/timeout so other preview components update.
   */
  public ItemPicker redrawWindowOnChange(boolean enabled) {
    this.redrawWindowOnChange = enabled;
    return this;
  }

  public ItemPicker onPick(BiConsumer<Window, ItemStack> handler) {
    this.onPick = Objects.requireNonNull(handler, "handler");
    return this;
  }

  public ItemPicker onCancel(BiConsumer<Window, Player> handler) {
    this.onCancel = Objects.requireNonNull(handler, "handler");
    return this;
  }

  public ItemPicker onTimeout(BiConsumer<Window, Player> handler) {
    this.onTimeout = Objects.requireNonNull(handler, "handler");
    return this;
  }

  public ItemStack selected(Player player) {
    Objects.requireNonNull(player, "player");
    ItemStack value = selected.get(player.getUniqueId());
    return value == null ? null : value.clone();
  }

  public ItemPicker setSelected(Player player, ItemStack item) {
    Objects.requireNonNull(player, "player");
    if (item == null || item.getType().isAir()) {
      selected.remove(player.getUniqueId());
      return this;
    }
    selected.put(player.getUniqueId(), item.clone());
    return this;
  }

  public ItemPicker clear(Player player) {
    return setSelected(player, null);
  }

  private void startOrCancel(Window.ClickContext ctx) {
    UUID id = ctx.player().getUniqueId();
    if (picking.remove(id)) {
      GuiManager.get().cancelItemPick(ctx.player());
      ctx.redrawSlot();
      return;
    }

    picking.add(id);
    ctx.redrawSlot();

    GuiManager.get().requestItemPick(ctx.player(),
        new GuiManager.ItemPickRequest(
            prompt,
            timeout,
            ctx.window(),
            allowAirSelection,
            filter,
            invalidMessage,
            (p, picked) -> {
              picking.remove(p.getUniqueId());
              setSelected(p, picked);
              try {
                onPick.accept(ctx.window(), picked);
              } catch (Exception ex) {
                GuiManager.get().debug("ItemPicker: onPick handler threw", ex);
              }
              redrawIfNeeded(ctx.window(), p);
            },
            p -> {
              picking.remove(p.getUniqueId());
              try {
                onCancel.accept(ctx.window(), p);
              } catch (Exception ex) {
                GuiManager.get().debug("ItemPicker: onCancel handler threw", ex);
              }
              redrawIfNeeded(ctx.window(), p);
            },
            p -> {
              picking.remove(p.getUniqueId());
              try {
                onTimeout.accept(ctx.window(), p);
              } catch (Exception ex) {
                GuiManager.get().debug("ItemPicker: onTimeout handler threw", ex);
              }
              redrawIfNeeded(ctx.window(), p);
            }));
  }

  private void redrawIfNeeded(Window window, Player player) {
    if (!redrawWindowOnChange) {
      int slot = findMountedSlot(window);
      if (slot >= 0) {
        window.redrawSlot(player, slot);
      }
      return;
    }
    GuiManager.get().runNextTick(() -> window.redraw(player));
  }

  private int findMountedSlot(Window window) {
    // Best-effort: attempt to find ourselves by scanning for a matching instance.
    // If not found, do nothing (callers can enable full redraw).
    for (int slot = 0; slot < window.size(); slot++) {
      if (window.componentAt(slot) == this) {
        return slot;
      }
    }
    return -1;
  }

  @Override
  public ItemStack render(Player player) {
    ItemStack base = super.render(player);
    if (base == null) {
      return null;
    }

    UUID id = player.getUniqueId();
    if (picking.contains(id)) {
      return GuiItem.of(base).glint(true)
          .addLoreLine(Component.empty())
          .addLoreLine(Locales.component(player, "gui.itemPicker.status.picking"))
          .build();
    }

    ItemStack picked = selected.get(id);
    if (picked != null && !picked.getType().isAir()) {
      Component prefix = Locales.component(player, "gui.itemPicker.status.selected");
      return GuiItem.of(base)
          .addLoreLine(Component.empty())
          .addLoreLine(prefix.append(GuiText.itemName(picked.getType())))
          .build();
    }
    return base;
  }
}
