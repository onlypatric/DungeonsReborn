package dev.patric.dungeonsreborn.menus;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiGive;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.storage.StorageArea;
import dev.patric.dungeonsreborn.gui.flow.ConfirmDialogWindow;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;

/**
 * Vault showcase: mixed storage slots (custom rules + vanilla slots).
 */
public final class ShowcaseVaultMenu extends Window {
  private static final int SIZE = 54;

  private static final int SLOT_TITLE = 0;
  private static final int SLOT_STATS = 4;
  private static final int SLOT_CLEAR = 7;
  private static final int SLOT_TRASH = 8;

  private final StorageArea bins = new StorageArea(1, 0, 1, 9);
  private final StorageArea vault = new StorageArea(2, 0, 3, 9);

  public ShowcaseVaultMenu() {
    super(SIZE, GuiMini.mm("<white><bold>Vault</bold></white>"), true);

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, Component.text("Back"))).autoDescribeInLore(false));
    navRight(new CloseButton(p -> GuiButtons.item(GuiButtons.Type.CLOSE, Component.text("Close"))).autoDescribeInLore(false));

    setFixed(SLOT_TITLE, new Label(GuiItems.named(Material.CHEST, Component.text("Vault"), List.of(
        GuiMini.mm("<gray>Top row: rule-based bins</gray>"),
        GuiMini.mm("<gray>Lower rows: vanilla storage slots</gray>")))));

    setFixed(SLOT_STATS, new Label(this::statsItem));

    setFixed(SLOT_CLEAR, new Button(p -> GuiItem.of(Material.TNT)
        .displayName(GuiMini.mm("<red><bold>Clear Vault</bold></red>"))
        .lore(List.of(GuiMini.mm("<gray>Opens a confirm dialog.</gray>")))
        .build(), ctx -> {
          openSubWindow(ctx.player(), clearConfirm());
          GuiSounds.click(ctx.player());
        }).autoDescribeInLore(false));

    setFixed(SLOT_TRASH, new Button(p -> GuiButtons.item(GuiButtons.Type.TRASH, Component.text("Trash")), ctx -> trash(ctx))
        .shiftRight(Component.text("Clear inventory"), ctx -> {
          openSubWindow(ctx.player(), clearInventoryConfirm());
          GuiSounds.click(ctx.player());
        })
        .autoDescribeInLore(true));

    configureBins();
    configureVault();

    bins.applyFixed(this);
    vault.applyFixed(this);

    bins.onChange((player, index, stack) -> redrawSlot(player, SLOT_STATS));
    vault.onChange((player, index, stack) -> redrawSlot(player, SLOT_STATS));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private void configureBins() {
    bins.slot(0).vanilla(false).emptyItem(binPlaceholder(Material.DIAMOND, "Only Diamonds"))
        .accepts(stack -> stack != null && stack.getType() == Material.DIAMOND);
    bins.slot(1).vanilla(false).emptyItem(binPlaceholder(Material.EMERALD, "Only Emeralds"))
        .accepts(stack -> stack != null && stack.getType() == Material.EMERALD);
    bins.slot(2).vanilla(false).emptyItem(binPlaceholder(Material.GOLD_INGOT, "Only Gold Ingots"))
        .accepts(stack -> stack != null && stack.getType() == Material.GOLD_INGOT);
    bins.slot(3).vanilla(false).emptyItem(binPlaceholder(Material.IRON_INGOT, "Only Iron Ingots"))
        .accepts(stack -> stack != null && stack.getType() == Material.IRON_INGOT);
    bins.slot(4).vanilla(false).emptyItem(binPlaceholder(Material.POTION, "Only Potions"))
        .accepts(stack -> stack != null && stack.getType() == Material.POTION);
    bins.slot(5).vanilla(false).emptyItem(binPlaceholder(Material.GRASS_BLOCK, "Only Blocks"))
        .accepts(stack -> stack != null && stack.getType().isBlock());
    bins.slot(6).vanilla(false).emptyItem(binPlaceholder(Material.COOKED_BEEF, "Only Food"))
        .accepts(stack -> stack != null && stack.getType().isEdible());
    bins.slot(7).vanilla(false).emptyItem(binPlaceholder(Material.NETHER_STAR, "Anything"))
        .accepts(stack -> stack != null && !stack.getType().isAir());
    bins.slot(8).vanilla(false).emptyItem(binPlaceholder(Material.BOOK, "Anything"))
        .accepts(stack -> stack != null && !stack.getType().isAir());
  }

  private void configureVault() {
    for (int i = 0; i < vault.size(); i++) {
      vault.slot(i).vanilla(true).accepts(stack -> true);
    }
  }

  private ItemStack binPlaceholder(Material icon, String rule) {
    return GuiItem.of(icon)
        .displayName(GuiMini.mm("<dark_gray><bold>" + rule + "</bold></dark_gray>"))
        .lore(List.of(
            GuiMini.mm("<dark_gray>Put items here.</dark_gray>")))
        .build();
  }

  private ItemStack statsItem(Player player) {
    int items = countItems(player);
    int stacks = countStacks(player);
    return GuiItems.named(Material.PAPER, Component.text("Vault Stats"), List.of(
        Component.text("Stacks: " + stacks),
        Component.text("Items: " + items)));
  }

  private int countStacks(Player player) {
    int count = 0;
    for (ItemStack stack : bins.contents(player)) {
      if (stack != null && !stack.getType().isAir() && stack.getAmount() > 0) {
        count++;
      }
    }
    for (ItemStack stack : vault.contents(player)) {
      if (stack != null && !stack.getType().isAir() && stack.getAmount() > 0) {
        count++;
      }
    }
    return count;
  }

  private int countItems(Player player) {
    int count = 0;
    for (ItemStack stack : bins.contents(player)) {
      if (stack != null && !stack.getType().isAir() && stack.getAmount() > 0) {
        count += stack.getAmount();
      }
    }
    for (ItemStack stack : vault.contents(player)) {
      if (stack != null && !stack.getType().isAir() && stack.getAmount() > 0) {
        count += stack.getAmount();
      }
    }
    return count;
  }

  private void trash(ClickContext ctx) {
    ItemStack cursor = ctx.event().getView().getCursor();
    if (cursor == null || cursor.getType().isAir()) {
      GuiSounds.error(ctx.player());
      return;
    }
    GuiGive.clearCursor(ctx);
    GuiSounds.click(ctx.player());
  }

  private Window clearConfirm() {
    return new ConfirmDialogWindow(
        GuiMini.mm("<white><bold>Clear Vault</bold></white>"),
        GuiMini.mm("<red><bold>Clear everything?</bold></red>"),
        List.of(GuiMini.mm("<gray>This removes all items stored in this vault for you.</gray>")),
        (player, result) -> {
          if (result == ConfirmDialogWindow.ConfirmResult.CONFIRM) {
            bins.clear(player);
            vault.clear(player);
            GuiSounds.success(player);
            redraw(player);
          } else {
            GuiSounds.error(player);
          }
        });
  }

  private Window clearInventoryConfirm() {
    return new ConfirmDialogWindow(
        GuiMini.mm("<white><bold>Clear Inventory</bold></white>"),
        GuiMini.mm("<red><bold>Really clear your inventory?</bold></red>"),
        List.of(GuiMini.mm("<gray>This does not affect the vault.</gray>")),
        (player, result) -> {
          if (result == ConfirmDialogWindow.ConfirmResult.CONFIRM) {
            GuiGive.clearInventory(player);
            GuiSounds.success(player);
          } else {
            GuiSounds.error(player);
          }
        });
  }
}
