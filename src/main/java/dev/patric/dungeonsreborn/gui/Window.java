package dev.patric.dungeonsreborn.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

/**
 * A component-based virtual inventory window.
 * <p>
 * This is currently a per-player concept: a {@link Window} instance is intended to be open for at most one player at a time.
 * The last player to open the window is stored in {@link #viewer()}.
 * <p>
 * If you need the same screen for multiple players simultaneously, create a new {@link Window} instance per player (or implement
 * a multi-viewer variant).
 */
public class Window implements InventoryHolder {
  public static final int NAV_EDITABLE_SLOTS = 7;

  public enum OpenReason {
    ROOT,
    PUSH,
    SHOW,
    RESUME,
    STACK_RETURN
  }

  public enum CloseReason {
    PLAYER,
    TEMPORARY,
    SWITCHED,
    EXTERNAL
  }

  public record OpenContext(Player player, OpenReason reason, String detail) {
    public OpenContext {
      Objects.requireNonNull(player, "player");
      Objects.requireNonNull(reason, "reason");
      detail = detail == null ? "" : detail;
    }
  }

  public record CloseContext(Player player, CloseReason reason, String detail) {
    public CloseContext {
      Objects.requireNonNull(player, "player");
      Objects.requireNonNull(reason, "reason");
      detail = detail == null ? "" : detail;
    }
  }

  public record TickContext(Player player, int tick) {
    public TickContext {
      Objects.requireNonNull(player, "player");
    }
  }

  public record ClickOutsideContext(Window window, Player player, InventoryClickEvent event) {
    public ClickOutsideContext {
      Objects.requireNonNull(window, "window");
      Objects.requireNonNull(player, "player");
      Objects.requireNonNull(event, "event");
    }

    public ClickType clickType() {
      return event.getClick();
    }

    public ItemStack cursor() {
      return event.getCursor();
    }

    public void close() {
      player.closeInventory();
    }

    public void redraw() {
      window.redraw(player);
    }
  }

  private final Inventory inventory;
  /**
   * Persistent components that stay registered between redraws.
   * Useful for static buttons/background elements.
   */
  private final Map<Integer, GuiComponent> fixed = new HashMap<>();
  /**
   * Components that are rebuilt on each {@link #redraw(Player)}. This map is cleared before {@link #build(Player)}.
   * Useful for paging/filtering/content lists.
   */
  private final Map<Integer, GuiComponent> dynamic = new HashMap<>();

  private Function<Player, ItemStack> background;
  private Consumer<Player> onOpen;
  private Consumer<Player> onClose;
  private Consumer<OpenContext> onOpenWithReason;
  private Consumer<CloseContext> onCloseWithReason;
  private Consumer<TickContext> onTick;
  private int tickIntervalTicks = 20;
  private int lastTickHandled = Integer.MIN_VALUE;
  private Consumer<ClickOutsideContext> onClickOutside;
  private final boolean allowPlayerInventoryClicks;
  private Boolean cancelTopInventoryDrags;
  private UUID viewer;

  public Window(int size, Component title) {
    this(size, title, false);
  }

  public Window(int size, Component title, boolean allowPlayerInventoryClicks) {
    if (size <= 0 || size % 9 != 0) {
      throw new IllegalArgumentException("size must be a positive multiple of 9");
    }
    this.inventory = Bukkit.createInventory(this, size, Objects.requireNonNull(title, "title"));
    this.allowPlayerInventoryClicks = allowPlayerInventoryClicks;
  }

  @Override
  public final Inventory getInventory() {
    return inventory;
  }

  public final int size() {
    return inventory.getSize();
  }

  public final int rows() {
    return size() / 9;
  }

  public final int slotAt(int row, int col) {
    if (row < 0 || row >= rows()) {
      throw new IllegalArgumentException("row out of bounds: " + row);
    }
    if (col < 0 || col >= 9) {
      throw new IllegalArgumentException("col out of bounds: " + col);
    }
    return row * 9 + col;
  }

  public final void setFixedAt(int row, int col, GuiComponent component) {
    setFixed(slotAt(row, col), component);
  }

  public final void setDynamicAt(int row, int col, GuiComponent component) {
    setDynamic(slotAt(row, col), component);
  }

  public final void background(ItemStack item) {
    background(p -> item);
  }

  public final void background(Function<Player, ItemStack> itemSupplier) {
    this.background = Objects.requireNonNull(itemSupplier, "itemSupplier");
  }

  public final void onOpen(Consumer<Player> onOpen) {
    this.onOpen = onOpen;
  }

  public final void onClose(Consumer<Player> onClose) {
    this.onClose = onClose;
  }

  /**
   * Fires when the window is opened, including a reason and an optional detail string.
   */
  public final Window onOpenWithReason(Consumer<OpenContext> handler) {
    this.onOpenWithReason = Objects.requireNonNull(handler, "handler");
    return this;
  }

  /**
   * Fires when the window is closed (and callbacks are not suppressed), including a reason and an optional detail string.
   */
  public final Window onCloseWithReason(Consumer<CloseContext> handler) {
    this.onCloseWithReason = Objects.requireNonNull(handler, "handler");
    return this;
  }

  /**
   * Fires periodically while the window is currently open for the player (top inventory holder == this).
   * <p>
   * Default interval is 20 ticks (1s).
   */
  public final Window onTick(Consumer<TickContext> handler) {
    this.onTick = Objects.requireNonNull(handler, "handler");
    return this;
  }

  public final Window tickEvery(int ticks) {
    if (ticks <= 0) {
      throw new IllegalArgumentException("ticks must be > 0");
    }
    this.tickIntervalTicks = ticks;
    return this;
  }

  /**
   * Called when the player clicks outside the inventory window bounds.
   */
  public final Window onClickOutside(Consumer<ClickOutsideContext> handler) {
    this.onClickOutside = Objects.requireNonNull(handler, "handler");
    return this;
  }

  public final void setFixed(int slot, GuiComponent component) {
    checkSlot(slot);
    GuiComponent value = Objects.requireNonNull(component, "component");
    fixed.put(slot, value);
    value.mounted(this, slot);
  }

  public final void setDynamic(int slot, GuiComponent component) {
    checkSlot(slot);
    GuiComponent value = Objects.requireNonNull(component, "component");
    dynamic.put(slot, value);
    value.mounted(this, slot);
  }

  public final void clearDynamic() {
    dynamic.clear();
  }

  public final GuiComponent componentAt(int slot) {
    GuiComponent component = dynamic.get(slot);
    return component != null ? component : fixed.get(slot);
  }

  public final void open(Player player) {
    GuiManager.get().open(player, this);
  }

  final void openInternal(Player player, OpenReason reason, String detail) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(reason, "reason");
    viewer = player.getUniqueId();
    GuiManager.get().debug("Window.openInternal: player=" + player.getName() + " window=" + getClass().getSimpleName()
        + " reason=" + reason + " detail=" + (detail == null ? "" : detail));
    redraw(player);
    if (onOpen != null) {
      onOpen.accept(player);
    }
    if (onOpenWithReason != null) {
      onOpenWithReason.accept(new OpenContext(player, reason, detail));
    }
    player.openInventory(inventory);
  }

  public final void redraw(Player player) {
    GuiManager.get().debug("Window.redraw: player=" + player.getName() + " window=" + getClass().getSimpleName());
    inventory.clear();
    dynamic.clear();
    build(player);
    draw(player);
    player.updateInventory();
  }

  protected void build(Player player) {
  }

  protected void draw(Player player) {
    if (background != null) {
      ItemStack item = background.apply(player);
      if (item != null) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
          if (inventory.getItem(slot) == null) {
            inventory.setItem(slot, item.clone());
          }
        }
      }
    }

    for (var entry : fixed.entrySet()) {
      ItemStack rendered = entry.getValue().render(player);
      inventory.setItem(entry.getKey(), rendered == null ? null : rendered.clone());
    }
    for (var entry : dynamic.entrySet()) {
      ItemStack rendered = entry.getValue().render(player);
      inventory.setItem(entry.getKey(), rendered == null ? null : rendered.clone());
    }
  }

  public final void handleTopClick(Player player, InventoryClickEvent event, int slot) {
    GuiComponent component = componentAt(slot);
    if (component == null) {
      return;
    }
    component.onClick(new ClickContext(this, player, event, slot));
  }

  public final boolean allowPlayerInventoryClicks() {
    return allowPlayerInventoryClicks;
  }

  /**
   * Overrides whether drag events that touch this window's top inventory should be cancelled.
   * <p>
   * Use {@link #useGlobalDragCancelBehavior()} to revert to the {@link GuiManager} default.
   */
  public final void cancelTopInventoryDrags(boolean cancel) {
    this.cancelTopInventoryDrags = cancel;
  }

  /**
   * Clears the per-window drag-cancel override, falling back to {@link GuiManager}'s global default.
   */
  public final void useGlobalDragCancelBehavior() {
    this.cancelTopInventoryDrags = null;
  }

  final boolean cancelTopInventoryDragsEffective(boolean globalDefault) {
    Boolean override = cancelTopInventoryDrags;
    return override != null ? override : globalDefault;
  }

  public final UUID viewer() {
    return viewer;
  }

  /**
   * Sets a component in the editable area of the navigation bar (7 slots between left/right controls).
   * <p>
   * Index 0..6 maps to the bottom row columns 1..7.
   */
  public Window nav(int index, GuiComponent component) {
    if (index < 0 || index >= NAV_EDITABLE_SLOTS) {
      throw new IllegalArgumentException("nav index out of bounds: " + index);
    }
    setFixed(navSlot(index), Objects.requireNonNull(component, "component"));
    return this;
  }

  /**
   * Sets the left control slot in the navigation bar (bottom row, column 0).
   */
  public Window navLeft(GuiComponent component) {
    setFixed(navLeftSlot(), Objects.requireNonNull(component, "component"));
    return this;
  }

  /**
   * Sets the right control slot in the navigation bar (bottom row, column 8).
   */
  public Window navRight(GuiComponent component) {
    setFixed(navRightSlot(), Objects.requireNonNull(component, "component"));
    return this;
  }

  /**
   * Opens {@code child} as a sub-window, pushing it onto the per-player window stack.
   * Closing the child returns to the previous window automatically.
   */
  public final void openSubWindow(Player player, Window child) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(child, "child");
    GuiManager.get().push(player, child);
  }

  final void handleClose(Player player, CloseReason reason, String detail, boolean suppressCallbacks) {
    GuiManager.get().debug("Window.handleClose: player=" + player.getName() + " window=" + getClass().getSimpleName()
        + " reason=" + reason + " suppressCallbacks=" + suppressCallbacks);
    if (suppressCallbacks) {
      return;
    }
    if (onClose != null) {
      onClose.accept(player);
    }
    if (onCloseWithReason != null) {
      onCloseWithReason.accept(new CloseContext(player, reason, detail));
    }
  }

  final void handleClickOutside(Player player, InventoryClickEvent event) {
    if (onClickOutside == null) {
      return;
    }
    onClickOutside.accept(new ClickOutsideContext(this, player, event));
  }

  final void handleTick(Player player, int tick) {
    if (onTick == null) {
      return;
    }
    if (lastTickHandled != Integer.MIN_VALUE && tick - lastTickHandled < tickIntervalTicks) {
      return;
    }
    lastTickHandled = tick;
    onTick.accept(new TickContext(player, tick));
  }

  private void checkSlot(int slot) {
    if (slot < 0 || slot >= size()) {
      throw new IllegalArgumentException("slot out of bounds: " + slot);
    }
  }

  private int navLeftSlot() {
    return size() - 9;
  }

  private int navRightSlot() {
    return size() - 1;
  }

  private int navSlot(int index) {
    return (size() - 9) + 1 + index;
  }

  /**
   * Updates a single slot without rebuilding the window.
   * <p>
   * This does not call {@link #build(Player)} and does not clear dynamic components.
   */
  public final void redrawSlot(Player player, int slot) {
    Objects.requireNonNull(player, "player");
    checkSlot(slot);

    GuiManager.get().debug("Window.redrawSlot: player=" + player.getName() + " window=" + getClass().getSimpleName()
        + " slot=" + slot);
    ItemStack item = null;
    GuiComponent component = componentAt(slot);
    if (component != null) {
      item = component.render(player);
    } else if (background != null) {
      item = background.apply(player);
    }
    inventory.setItem(slot, item == null ? null : item.clone());
    player.updateInventory();
  }

  public static final class ClickContext {
    private final Window window;
    private final Player player;
    private final InventoryClickEvent event;
    private final int slot;

    ClickContext(Window window, Player player, InventoryClickEvent event, int slot) {
      this.window = window;
      this.player = player;
      this.event = event;
      this.slot = slot;
    }

    public Window window() {
      return window;
    }

    public Player player() {
      return player;
    }

    public InventoryClickEvent event() {
      return event;
    }

    public int slot() {
      return slot;
    }

    public ClickType clickType() {
      return event.getClick();
    }

    public boolean isShiftClick() {
      return event.isShiftClick();
    }

    public boolean isKeyboardClick() {
      return event.getClick().isKeyboardClick();
    }

    public int hotbarButton() {
      return event.getHotbarButton();
    }

    public void close() {
      player.closeInventory();
    }

    public void redraw() {
      window.redraw(player);
    }

    public void redrawSlot() {
      window.redrawSlot(player, slot);
    }

    public void redrawSlot(int slot) {
      window.redrawSlot(player, slot);
    }
  }
}
