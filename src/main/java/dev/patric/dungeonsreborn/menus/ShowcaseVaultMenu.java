package dev.patric.dungeonsreborn.menus;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiGive;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.storage.StorageArea;
import dev.patric.dungeonsreborn.gui.flow.ConfirmDialogWindow;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

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
    super(SIZE, GuiI18n.tr("gui.showcase.vault.title"), true);

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, GuiI18n.tr(p, "gui.button.back"))).autoDescribeInLore(false));
    navRight(new CloseButton(p -> GuiButtons.item(GuiButtons.Type.CLOSE, GuiI18n.tr(p, "gui.button.close"))).autoDescribeInLore(false));

    setFixed(SLOT_TITLE, new Label(GuiItems.named(Material.CHEST, GuiI18n.tr("gui.showcase.vault.header.title"), List.of(
        GuiI18n.tr("gui.showcase.vault.header.bins"),
        GuiI18n.tr("gui.showcase.vault.header.storage")))));

    setFixed(SLOT_STATS, new Label(this::statsItem));

    setFixed(SLOT_CLEAR, new Button(p -> GuiItem.of(Material.TNT)
        .displayName(GuiI18n.tr(p, "gui.showcase.vault.clear.title"))
        .lore(List.of(GuiI18n.tr(p, "gui.showcase.vault.clear.hint")))
        .build(), ctx -> {
          openSubWindow(ctx.player(), clearConfirm());
          GuiSounds.click(ctx.player());
        }).autoDescribeInLore(false));

    setFixed(SLOT_TRASH, new Button(p -> GuiButtons.item(GuiButtons.Type.TRASH, GuiI18n.tr(p, "gui.showcase.vault.trash.title")),
        ctx -> trash(ctx))
        .shiftRight(GuiI18n.tr("gui.showcase.vault.trash.clearInventory"), ctx -> {
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
    bins.slot(0).vanilla(false).emptyItem(binPlaceholder(Material.DIAMOND, GuiI18n.str(GuiI18n.defaultLocale(), "gui.showcase.vault.rule.diamonds")))
        .accepts(stack -> stack != null && stack.getType() == Material.DIAMOND);
    bins.slot(1).vanilla(false).emptyItem(binPlaceholder(Material.EMERALD, GuiI18n.str(GuiI18n.defaultLocale(), "gui.showcase.vault.rule.emeralds")))
        .accepts(stack -> stack != null && stack.getType() == Material.EMERALD);
    bins.slot(2).vanilla(false).emptyItem(binPlaceholder(Material.GOLD_INGOT, GuiI18n.str(GuiI18n.defaultLocale(), "gui.showcase.vault.rule.gold")))
        .accepts(stack -> stack != null && stack.getType() == Material.GOLD_INGOT);
    bins.slot(3).vanilla(false).emptyItem(binPlaceholder(Material.IRON_INGOT, GuiI18n.str(GuiI18n.defaultLocale(), "gui.showcase.vault.rule.iron")))
        .accepts(stack -> stack != null && stack.getType() == Material.IRON_INGOT);
    bins.slot(4).vanilla(false).emptyItem(binPlaceholder(Material.POTION, GuiI18n.str(GuiI18n.defaultLocale(), "gui.showcase.vault.rule.potions")))
        .accepts(stack -> stack != null && stack.getType() == Material.POTION);
    bins.slot(5).vanilla(false).emptyItem(binPlaceholder(Material.GRASS_BLOCK, GuiI18n.str(GuiI18n.defaultLocale(), "gui.showcase.vault.rule.blocks")))
        .accepts(stack -> stack != null && stack.getType().isBlock());
    bins.slot(6).vanilla(false).emptyItem(binPlaceholder(Material.COOKED_BEEF, GuiI18n.str(GuiI18n.defaultLocale(), "gui.showcase.vault.rule.food")))
        .accepts(stack -> stack != null && stack.getType().isEdible());
    bins.slot(7).vanilla(false).emptyItem(binPlaceholder(Material.NETHER_STAR, GuiI18n.str(GuiI18n.defaultLocale(), "gui.showcase.vault.rule.anything")))
        .accepts(stack -> stack != null && !stack.getType().isAir());
    bins.slot(8).vanilla(false).emptyItem(binPlaceholder(Material.BOOK, GuiI18n.str(GuiI18n.defaultLocale(), "gui.showcase.vault.rule.anything")))
        .accepts(stack -> stack != null && !stack.getType().isAir());
  }

  private void configureVault() {
    for (int i = 0; i < vault.size(); i++) {
      vault.slot(i).vanilla(true).accepts(stack -> true);
    }
  }

  private ItemStack binPlaceholder(Material icon, String rule) {
    return GuiItem.of(icon)
        .displayName(GuiI18n.tr("gui.showcase.vault.bin.title", Placeholder.unparsed("rule", rule)))
        .lore(List.of(
            GuiI18n.tr("gui.showcase.vault.bin.hint")))
        .build();
  }

  private ItemStack statsItem(Player player) {
    int items = countItems(player);
    int stacks = countStacks(player);
    return GuiItems.named(Material.PAPER, GuiI18n.tr(player, "gui.showcase.vault.stats.title"), List.of(
        GuiI18n.tr(player, "gui.showcase.vault.stats.stacks", Placeholder.unparsed("value", String.valueOf(stacks))),
        GuiI18n.tr(player, "gui.showcase.vault.stats.items", Placeholder.unparsed("value", String.valueOf(items)))));
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
        GuiI18n.tr("gui.showcase.vault.clear.confirm.title"),
        GuiI18n.tr("gui.showcase.vault.clear.confirm.header"),
        List.of(GuiI18n.tr("gui.showcase.vault.clear.confirm.detail")),
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
        GuiI18n.tr("gui.showcase.vault.clearInventory.title"),
        GuiI18n.tr("gui.showcase.vault.clearInventory.header"),
        List.of(GuiI18n.tr("gui.showcase.vault.clearInventory.detail")),
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
