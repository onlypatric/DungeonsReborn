package dev.patric.dungeonsreborn.gui.components;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiComponent;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.Window;

public final class DraggableSlot implements GuiComponent {
  /**
   * A slot that stores one stack and lets the player interact using normal cursor behavior.
   * <p>
   * Behavior:
   * <ul>
   * <li>Left-click: move full stack between cursor and slot</li>
   * <li>Right-click: split/take half from slot; or place 1 item into an empty slot; or merge 1 item</li>
   * <li>Shift-click: move slot stack into the player's inventory (best-effort)</li>
   * </ul>
   * <p>
   * Vanilla mode:
   * <ul>
   * <li>When created via {@link #vanilla(BiConsumer)}, the slot behaves like a real empty chest slot (number keys, drop, double-click, etc.).</li>
   * <li>This requires leaving the slot visually empty when no item is stored (no placeholder item).</li>
   * </ul>
   */
  private final ItemStack emptyItem;
  private final Predicate<ItemStack> accepts;
  private final boolean allowTake;
  private final boolean allowPut;
  private final BiConsumer<Player, ItemStack> onChange;
  private final boolean vanilla;

  private final Map<UUID, ItemStack> storedByPlayer = new HashMap<>();
  private static final UUID GLOBAL_KEY = new UUID(0L, 0L);
  private Window mountedWindow;
  private int mountedSlot = -1;

  public DraggableSlot(Predicate<ItemStack> accepts, boolean allowTake, boolean allowPut,
      BiConsumer<Player, ItemStack> onChange) {
    this(null, accepts, allowTake, allowPut, onChange, false);
  }

  public DraggableSlot(ItemStack emptyItem, Predicate<ItemStack> accepts, boolean allowTake, boolean allowPut,
      BiConsumer<Player, ItemStack> onChange) {
    this(emptyItem, accepts, allowTake, allowPut, onChange, false);
  }

  private DraggableSlot(ItemStack emptyItem, Predicate<ItemStack> accepts, boolean allowTake, boolean allowPut,
      BiConsumer<Player, ItemStack> onChange, boolean vanilla) {
    this.emptyItem = emptyItem;
    this.accepts = Objects.requireNonNull(accepts, "accepts");
    this.allowTake = allowTake;
    this.allowPut = allowPut;
    this.onChange = Objects.requireNonNull(onChange, "onChange");
    this.vanilla = vanilla;
  }

  /**
   * Creates a truly-vanilla draggable slot (behaves like a free chest slot).
   */
  public static DraggableSlot vanilla(BiConsumer<Player, ItemStack> onChange) {
    return new DraggableSlot(null, item -> true, true, true, onChange, true);
  }

  public ItemStack stored(Player player) {
    Objects.requireNonNull(player, "player");
    return copyOrNull(storedByPlayer.get(player.getUniqueId()));
  }

  public ItemStack stored() {
    UUID key = implicitKey();
    return copyOrNull(storedByPlayer.get(key));
  }

  public void stored(Player player, ItemStack stored) {
    Objects.requireNonNull(player, "player");
    setStored(player.getUniqueId(), stored);
  }

  public void stored(ItemStack stored) {
    setStored(implicitKey(), stored);
  }

  @Override
  public ItemStack render(Player player) {
    ItemStack stored = storedByPlayer.get(player.getUniqueId());
    if (vanilla) {
      return stored;
    }
    if (stored != null) {
      return stored;
    }
    return emptyItem;
  }

  @Override
  public void mounted(Window window, int slot) {
    this.mountedWindow = window;
    this.mountedSlot = slot;
  }

  @Override
  public boolean allowVanillaClicks() {
    return vanilla;
  }

  @Override
  public boolean allowVanillaDrags() {
    return vanilla;
  }

  @Override
  public void afterVanillaClick(Window.ClickContext ctx) {
    syncFromInventory(ctx.player());
  }

  @Override
  public void afterVanillaDrag(Window window, Player player, InventoryDragEvent event, Set<Integer> rawSlots) {
    if (mountedWindow != window) {
      return;
    }
    if (mountedSlot < 0 || !rawSlots.contains(mountedSlot)) {
      return;
    }
    syncFromInventory(player);
  }

  @Override
  public void onClick(Window.ClickContext ctx) {
    if (vanilla) {
      // In vanilla mode, GuiManager lets Bukkit handle the click and we sync in afterVanillaClick/afterVanillaDrag.
      return;
    }
    ctx.event().setCancelled(true);
    if (ctx.isKeyboardClick()) {
      return;
    }
    if (ctx.isShiftClick()) {
      shiftTake(ctx);
      return;
    }

    ClickType click = ctx.clickType();
    if (click != ClickType.LEFT && click != ClickType.RIGHT) {
      return;
    }

    ItemStack cursor = ctx.event().getCursor();
    ItemStack slotStack = storedByPlayer.get(ctx.player().getUniqueId());

    if (isEmpty(cursor)) {
      takeFromSlot(ctx, click, slotStack);
      return;
    }

    if (isEmpty(slotStack)) {
      putIntoEmptySlot(ctx, click, cursor);
      return;
    }

    if (!allowPut || !accepts.test(cursor)) {
      return;
    }

    if (tryMerge(ctx, click, slotStack, cursor)) {
      return;
    }

    if (!allowTake) {
      return;
    }
    swap(ctx, slotStack, cursor);
  }

  private void takeFromSlot(Window.ClickContext ctx, ClickType click, ItemStack slotStack) {
    if (!allowTake || isEmpty(slotStack)) {
      return;
    }

    // LEFT: take the whole stack. RIGHT: take half (Minecraft-like behavior).
    GuiManager.get().debug("DraggableSlot.take: player=" + ctx.player().getName()
        + " click=" + click + " stored=" + slotStack.getType() + "x" + slotStack.getAmount());
    ItemStack take = slotStack.clone();
    if (click == ClickType.RIGHT && take.getAmount() > 1) {
      int half = (take.getAmount() + 1) / 2;
      take.setAmount(half);

      ItemStack remaining = slotStack.clone();
      remaining.setAmount(remaining.getAmount() - half);
      setStored(ctx.player().getUniqueId(), remaining.getAmount() <= 0 ? null : remaining);
    } else {
      setStored(ctx.player().getUniqueId(), null);
    }

    setCursor(ctx, take);
    commit(ctx);
  }

  private void putIntoEmptySlot(Window.ClickContext ctx, ClickType click, ItemStack cursorStack) {
    if (!allowPut || isEmpty(cursorStack) || !accepts.test(cursorStack)) {
      return;
    }

    // LEFT: place the whole cursor stack. RIGHT: place 1 item.
    GuiManager.get().debug("DraggableSlot.put: player=" + ctx.player().getName()
        + " click=" + click + " cursor=" + cursorStack.getType() + "x" + cursorStack.getAmount());
    if (click == ClickType.RIGHT && cursorStack.getAmount() > 1) {
      ItemStack one = cursorStack.clone();
      one.setAmount(1);
      setStored(ctx.player().getUniqueId(), one);

      ItemStack remaining = cursorStack.clone();
      remaining.setAmount(remaining.getAmount() - 1);
      setCursor(ctx, remaining.getAmount() <= 0 ? null : remaining);
    } else {
      setStored(ctx.player().getUniqueId(), cursorStack.clone());
      setCursor(ctx, null);
    }

    commit(ctx);
  }

  private boolean tryMerge(Window.ClickContext ctx, ClickType click, ItemStack slotStack, ItemStack cursorStack) {
    if (!slotStack.isSimilar(cursorStack) || slotStack.getAmount() >= slotStack.getMaxStackSize()) {
      return false;
    }

    GuiManager.get().debug("DraggableSlot.merge: player=" + ctx.player().getName()
        + " click=" + click + " stored=" + slotStack.getType() + "x" + slotStack.getAmount()
        + " cursor=" + cursorStack.getType() + "x" + cursorStack.getAmount());
    if (click == ClickType.RIGHT) {
      setStored(ctx.player().getUniqueId(), withAmount(slotStack, slotStack.getAmount() + 1));
      setCursor(ctx, decrement(cursorStack, 1));
    } else {
      int maxAdd = slotStack.getMaxStackSize() - slotStack.getAmount();
      int add = Math.min(maxAdd, cursorStack.getAmount());
      setStored(ctx.player().getUniqueId(), withAmount(slotStack, slotStack.getAmount() + add));
      setCursor(ctx, decrement(cursorStack, add));
    }

    commit(ctx);
    return true;
  }

  private void swap(Window.ClickContext ctx, ItemStack slotStack, ItemStack cursorStack) {
    GuiManager.get().debug("DraggableSlot.swap: player=" + ctx.player().getName()
        + " stored=" + slotStack.getType() + "x" + slotStack.getAmount()
        + " cursor=" + cursorStack.getType() + "x" + cursorStack.getAmount());
    setStored(ctx.player().getUniqueId(), cursorStack.clone());
    setCursor(ctx, slotStack.clone());
    commit(ctx);
  }

  private void shiftTake(Window.ClickContext ctx) {
    ItemStack stored = storedByPlayer.get(ctx.player().getUniqueId());
    if (!allowTake || stored == null || stored.getType().isAir()) {
      return;
    }
    ItemStack toMove = stored.clone();
    setStored(ctx.player().getUniqueId(), null);

    var leftovers = ctx.player().getInventory().addItem(toMove);
    if (!leftovers.isEmpty()) {
      setStored(ctx.player().getUniqueId(), leftovers.values().iterator().next().clone());
    }
    commit(ctx);
  }

  private void commit(Window.ClickContext ctx) {
    onChange.accept(ctx.player(), stored(ctx.player()));
    ctx.redrawSlot();
  }

  private void syncFromInventory(Player player) {
    if (mountedWindow == null || mountedSlot < 0) {
      return;
    }
    ItemStack now = mountedWindow.getInventory().getItem(mountedSlot);
    ItemStack normalized = isEmpty(now) ? null : now.clone();
    UUID id = player.getUniqueId();
    ItemStack stored = storedByPlayer.get(id);
    if (sameStack(stored, normalized)) {
      return;
    }
    setStored(id, normalized);
    onChange.accept(player, stored(player));
  }

  private void setCursor(Window.ClickContext ctx, ItemStack cursor) {
    ctx.event().getView().setCursor(cursor);
    GuiManager.get().setCursorNextTick(ctx.player(), cursor);
  }

  private static boolean isEmpty(ItemStack stack) {
    return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
  }

  private static ItemStack withAmount(ItemStack stack, int amount) {
    ItemStack copy = stack.clone();
    copy.setAmount(amount);
    return copy;
  }

  private static ItemStack decrement(ItemStack stack, int by) {
    ItemStack copy = stack.clone();
    copy.setAmount(copy.getAmount() - by);
    return copy.getAmount() <= 0 ? null : copy;
  }

  private void setStored(UUID playerId, ItemStack stored) {
    if (stored == null || stored.getType().isAir() || stored.getAmount() <= 0) {
      storedByPlayer.remove(playerId);
      return;
    }
    storedByPlayer.put(playerId, stored.clone());
  }

  private UUID implicitKey() {
    if (mountedWindow == null) {
      return GLOBAL_KEY;
    }
    UUID viewer = mountedWindow.viewer();
    return viewer != null ? viewer : GLOBAL_KEY;
  }

  private static ItemStack copyOrNull(ItemStack stack) {
    return stack == null ? null : stack.clone();
  }

  private static boolean sameStack(ItemStack a, ItemStack b) {
    if (isEmpty(a) && isEmpty(b)) {
      return true;
    }
    if (isEmpty(a) || isEmpty(b)) {
      return false;
    }
    return a.isSimilar(b) && a.getAmount() == b.getAmount();
  }
}
