package dev.patric.dungeonsreborn.gui;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class GuiGive {
  private GuiGive() {
  }

  public static void addToCursor(Window.ClickContext ctx, ItemStack template, int addAmount) {
    Objects.requireNonNull(ctx, "ctx");
    Objects.requireNonNull(template, "template");

    if (!prepareCursorFor(ctx, template)) {
      return;
    }

    ItemStack cursor = ctx.event().getView().getCursor();
    int max = template.getMaxStackSize();
    int current = isEmpty(cursor) ? 0 : cursor.getAmount();
    int nextAmount = Math.min(max, current + Math.min(addAmount, max));
    if (nextAmount == current) {
      return;
    }
    setCursor(ctx, withAmount(template, nextAmount));
  }

  public static void setCursorAmount(Window.ClickContext ctx, ItemStack template, int amount) {
    Objects.requireNonNull(ctx, "ctx");
    Objects.requireNonNull(template, "template");

    if (!prepareCursorFor(ctx, template)) {
      return;
    }
    int max = template.getMaxStackSize();
    setCursor(ctx, withAmount(template, Math.min(max, amount)));
  }

  public static void giveToInventory(Window.ClickContext ctx, ItemStack template, int amount) {
    Objects.requireNonNull(ctx, "ctx");
    Objects.requireNonNull(template, "template");

    Player player = ctx.player();
    PlayerInventory inv = player.getInventory();
    if (!canFit(inv, template, amount)) {
      return;
    }

    int remaining = amount;
    int max = template.getMaxStackSize();
    while (remaining > 0) {
      int give = Math.min(max, remaining);
      ItemStack stack = withAmount(template, give);
      var leftovers = inv.addItem(stack);
      if (!leftovers.isEmpty()) {
        GuiManager.get().debug("GuiGive.giveToInventory: unexpected leftovers for player=" + player.getName());
        return;
      }
      remaining -= give;
    }
  }

  public static void clearCursor(Window.ClickContext ctx) {
    Objects.requireNonNull(ctx, "ctx");
    setCursor(ctx, null);
  }

  public static void clearInventory(Player player) {
    Objects.requireNonNull(player, "player");
    PlayerInventory inv = player.getInventory();
    inv.clear();
    inv.setArmorContents(null);
    inv.setItemInOffHand(null);
  }

  private static boolean prepareCursorFor(Window.ClickContext ctx, ItemStack template) {
    ItemStack cursor = ctx.event().getView().getCursor();
    if (isEmpty(cursor) || cursor.isSimilar(template)) {
      return true;
    }

    Player player = ctx.player();
    var leftovers = player.getInventory().addItem(cursor.clone());
    if (!leftovers.isEmpty()) {
      return false;
    }
    setCursor(ctx, null);
    return true;
  }

  private static boolean canFit(PlayerInventory inv, ItemStack template, int amount) {
    int needed = amount;
    int max = template.getMaxStackSize();

    for (ItemStack existing : inv.getStorageContents()) {
      if (isEmpty(existing)) {
        needed -= max;
      } else if (existing.isSimilar(template)) {
        needed -= Math.max(0, max - existing.getAmount());
      }
      if (needed <= 0) {
        return true;
      }
    }
    return needed <= 0;
  }

  private static ItemStack withAmount(ItemStack template, int amount) {
    ItemStack stack = template.clone();
    stack.setAmount(amount);
    return stack;
  }

  private static void setCursor(Window.ClickContext ctx, ItemStack stack) {
    ctx.event().getView().setCursor(stack);
    GuiManager.get().setCursorNextTick(ctx.player(), stack);
  }

  private static boolean isEmpty(ItemStack stack) {
    return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
  }
}

