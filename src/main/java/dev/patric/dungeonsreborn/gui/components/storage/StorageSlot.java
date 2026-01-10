package dev.patric.dungeonsreborn.gui.components.storage;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiComponent;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.Window;

/**
 * One slot inside a {@link StorageArea}.
 */
public final class StorageSlot implements GuiComponent {
  private final StorageArea area;
  private final int index;

  private Predicate<ItemStack> accepts = item -> item != null && !item.getType().isAir();
  private boolean allowTake = true;
  private boolean allowPut = true;
  private boolean vanilla = false;
  private ItemStack emptyItem;
  private BiConsumer<Player, ItemStack> onChange;

  private Window mountedWindow;
  private int mountedSlot = -1;

  StorageSlot(StorageArea area, int index) {
    this.area = Objects.requireNonNull(area, "area");
    this.index = index;
  }

  public int index() {
    return index;
  }

  /**
   * If enabled, this slot allows vanilla inventory behavior (number keys, double-click, drop, etc.).
   * <p>
   * Use {@link #accepts(Predicate)} / {@link #allowPut(boolean)} to restrict what can be placed.
   */
  public StorageSlot vanilla(boolean enabled) {
    this.vanilla = enabled;
    return this;
  }

  public boolean isVanilla() {
    return vanilla;
  }

  public StorageSlot accepts(Predicate<ItemStack> predicate) {
    this.accepts = Objects.requireNonNull(predicate, "predicate");
    return this;
  }

  public StorageSlot allowTake(boolean enabled) {
    this.allowTake = enabled;
    return this;
  }

  public StorageSlot allowPut(boolean enabled) {
    this.allowPut = enabled;
    return this;
  }

  /**
   * Placeholder shown when empty (only used when {@link #vanilla(boolean)} is false).
   */
  public StorageSlot emptyItem(ItemStack item) {
    this.emptyItem = Objects.requireNonNull(item, "item");
    return this;
  }

  public StorageSlot onChange(BiConsumer<Player, ItemStack> handler) {
    this.onChange = Objects.requireNonNull(handler, "handler");
    return this;
  }

  public ItemStack stored(Player player) {
    Objects.requireNonNull(player, "player");
    return area.get(player, index);
  }

  public void stored(Player player, ItemStack stack) {
    Objects.requireNonNull(player, "player");
    area.set(player, index, stack);
  }

  @Override
  public ItemStack render(Player player) {
    ItemStack stored = area.get(player, index);
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
  public boolean beforeVanillaClick(Window.ClickContext ctx) {
    if (!vanilla) {
      return true;
    }
    if (ctx.isKeyboardClick() && ctx.clickType() != ClickType.NUMBER_KEY && ctx.clickType() != ClickType.SWAP_OFFHAND) {
      // Be conservative for unknown keyboard clicks.
      return allowTake;
    }

    ItemStack slotStack = currentSlotItem(ctx.player());

    // Actions that may remove items from the slot.
    if (!allowTake) {
      if (ctx.clickType() == ClickType.SHIFT_LEFT || ctx.clickType() == ClickType.SHIFT_RIGHT
          || ctx.clickType() == ClickType.DROP || ctx.clickType() == ClickType.CONTROL_DROP
          || ctx.clickType() == ClickType.DOUBLE_CLICK || ctx.clickType() == ClickType.NUMBER_KEY
          || ctx.clickType() == ClickType.SWAP_OFFHAND) {
        if (!isEmpty(slotStack)) {
          return false;
        }
      }
    }

    // Actions that may put items into the slot.
    if (allowPut) {
      ItemStack incoming = incomingForVanillaClick(ctx);
      if (!isEmpty(incoming) && !accepts.test(incoming)) {
        return false;
      }
      return true;
    }

    ItemStack incoming = incomingForVanillaClick(ctx);
    return isEmpty(incoming);
  }

  @Override
  public boolean beforeVanillaDrag(Window window, Player player, InventoryDragEvent event, Set<Integer> rawSlots) {
    if (!vanilla) {
      return true;
    }
    if (mountedWindow != window) {
      return true;
    }
    if (mountedSlot < 0 || !rawSlots.contains(mountedSlot)) {
      return true;
    }
    if (!allowPut) {
      return false;
    }
    ItemStack dragged = event.getOldCursor();
    return isEmpty(dragged) || accepts.test(dragged);
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
    ItemStack slotStack = area.get(ctx.player(), index);

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

    ItemStack take = slotStack.clone();
    if (click == ClickType.RIGHT && take.getAmount() > 1) {
      int half = (take.getAmount() + 1) / 2;
      take.setAmount(half);

      ItemStack remaining = slotStack.clone();
      remaining.setAmount(remaining.getAmount() - half);
      stored(ctx.player(), remaining.getAmount() <= 0 ? null : remaining);
    } else {
      stored(ctx.player(), null);
    }

    setCursor(ctx, take);
    commit(ctx);
  }

  private void putIntoEmptySlot(Window.ClickContext ctx, ClickType click, ItemStack cursorStack) {
    if (!allowPut || isEmpty(cursorStack) || !accepts.test(cursorStack)) {
      return;
    }

    if (click == ClickType.RIGHT && cursorStack.getAmount() > 1) {
      ItemStack one = cursorStack.clone();
      one.setAmount(1);
      stored(ctx.player(), one);

      ItemStack remaining = cursorStack.clone();
      remaining.setAmount(remaining.getAmount() - 1);
      setCursor(ctx, remaining.getAmount() <= 0 ? null : remaining);
    } else {
      stored(ctx.player(), cursorStack.clone());
      setCursor(ctx, null);
    }

    commit(ctx);
  }

  private boolean tryMerge(Window.ClickContext ctx, ClickType click, ItemStack slotStack, ItemStack cursorStack) {
    if (!slotStack.isSimilar(cursorStack) || slotStack.getAmount() >= slotStack.getMaxStackSize()) {
      return false;
    }

    if (click == ClickType.RIGHT) {
      stored(ctx.player(), withAmount(slotStack, slotStack.getAmount() + 1));
      setCursor(ctx, decrement(cursorStack, 1));
    } else {
      int maxAdd = slotStack.getMaxStackSize() - slotStack.getAmount();
      int add = Math.min(maxAdd, cursorStack.getAmount());
      stored(ctx.player(), withAmount(slotStack, slotStack.getAmount() + add));
      setCursor(ctx, decrement(cursorStack, add));
    }

    commit(ctx);
    return true;
  }

  private void swap(Window.ClickContext ctx, ItemStack slotStack, ItemStack cursorStack) {
    stored(ctx.player(), cursorStack.clone());
    setCursor(ctx, slotStack.clone());
    commit(ctx);
  }

  private void shiftTake(Window.ClickContext ctx) {
    ItemStack stack = area.get(ctx.player(), index);
    if (!allowTake || isEmpty(stack)) {
      return;
    }
    ItemStack toMove = stack.clone();
    stored(ctx.player(), null);

    var leftovers = ctx.player().getInventory().addItem(toMove);
    if (!leftovers.isEmpty()) {
      stored(ctx.player(), leftovers.values().iterator().next().clone());
    }
    commit(ctx);
  }

  private void commit(Window.ClickContext ctx) {
    ItemStack stack = area.get(ctx.player(), index);
    if (onChange != null) {
      onChange.accept(ctx.player(), stack == null ? null : stack.clone());
    }
    area.commit(ctx.player(), index, stack);
    ctx.redrawSlot();
  }

  private void syncFromInventory(Player player) {
    if (mountedWindow == null || mountedSlot < 0) {
      return;
    }

    ItemStack now = mountedWindow.getInventory().getItem(mountedSlot);
    ItemStack normalized = isEmpty(now) ? null : now.clone();

    if (normalized != null && (!allowPut || !accepts.test(normalized))) {
      // Revert invalid state (should be prevented by beforeVanillaClick/Drag, but keep this as a safety net).
      ItemStack[] contents = area.mutableContents(player.getUniqueId());
      ItemStack previous = contents[index];
      mountedWindow.getInventory().setItem(mountedSlot, previous == null ? null : previous.clone());
      player.updateInventory();
      return;
    }

    UUID id = player.getUniqueId();
    ItemStack[] contents = area.mutableContents(id);
    ItemStack previous = contents[index];
    if (sameStack(previous, normalized)) {
      return;
    }
    contents[index] = normalized == null ? null : normalized.clone();

    if (onChange != null) {
      onChange.accept(player, normalized == null ? null : normalized.clone());
    }
    area.commit(player, index, normalized);
  }

  private ItemStack currentSlotItem(Player player) {
    if (mountedWindow == null || mountedSlot < 0) {
      return area.get(player, index);
    }
    return mountedWindow.getInventory().getItem(mountedSlot);
  }

  private ItemStack incomingForVanillaClick(Window.ClickContext ctx) {
    ClickType type = ctx.clickType();
    if (type == ClickType.LEFT || type == ClickType.RIGHT) {
      return ctx.event().getCursor();
    }
    if (type == ClickType.NUMBER_KEY) {
      int button = ctx.hotbarButton();
      if (button < 0) {
        return null;
      }
      return ctx.player().getInventory().getItem(button);
    }
    if (type == ClickType.SWAP_OFFHAND) {
      return ctx.player().getInventory().getItemInOffHand();
    }
    return null;
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

