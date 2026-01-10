package dev.patric.dungeonsreborn.menus;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.DraggableSlot;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;

/**
 * DraggableSlot showcase: illustrates vanilla-like slots and rule-based slots.
 */
public final class ShowcaseDraggableSlotsMenu extends Window {
  private static final int SIZE = 54;

  private static final int SLOT_HELP = 0;
  private static final int SLOT_STATS = 4;

  private static final int SLOT_DIAMONDS_ONLY = 20;
  private static final int SLOT_VANILLA = 22;
  private static final int SLOT_DEPOSIT_ONLY = 24;
  private static final int SLOT_WITHDRAW_ONLY = 31;

  private final Set<UUID> seeded = new HashSet<>();

  private final DraggableSlot diamondsOnly = new DraggableSlot(
      placeholder(Material.DIAMOND, "Diamonds Only"),
      stack -> stack != null && stack.getType() == Material.DIAMOND,
      true,
      true,
      (player, stack) -> redrawSlot(player, SLOT_STATS));

  private final DraggableSlot vanilla = DraggableSlot.vanilla((player, stack) -> redrawSlot(player, SLOT_STATS));

  private final DraggableSlot depositOnly = new DraggableSlot(
      placeholder(Material.HOPPER, "Deposit Only"),
      stack -> stack != null && !stack.getType().isAir(),
      false,
      true,
      (player, stack) -> redrawSlot(player, SLOT_STATS));

  private final DraggableSlot withdrawOnly = new DraggableSlot(
      GuiItem.of(Material.CHEST)
          .displayName(GuiMini.mm("<dark_gray><bold>Withdraw Only</bold></dark_gray>"))
          .lore(List.of(GuiMini.mm("<dark_gray>Take items from here.</dark_gray>")))
          .build(),
      stack -> false,
      true,
      false,
      (player, stack) -> redrawSlot(player, SLOT_STATS));

  public ShowcaseDraggableSlotsMenu() {
    super(SIZE, GuiMini.mm("<white><bold>Draggable Slots</bold></white>"), true);

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, Component.text("Back"))).autoDescribeInLore(false));
    navRight(new CloseButton(p -> GuiButtons.item(GuiButtons.Type.CLOSE, Component.text("Close"))).autoDescribeInLore(false));

    setFixed(SLOT_HELP, new Label(GuiItem.of(Material.PAPER)
        .displayName(GuiMini.mm("<white><bold>Try clicking the slots</bold></white>"))
        .lore(List.of(
            GuiMini.mm("<gray>These are DraggableSlot components, not normal buttons.</gray>"),
            GuiMini.mm("<dark_gray>• Diamonds Only: accepts diamonds</dark_gray>"),
            GuiMini.mm("<dark_gray>• Vanilla Slot: behaves like a free chest slot</dark_gray>"),
            GuiMini.mm("<dark_gray>• Deposit Only: you can put items, but not take</dark_gray>"),
            GuiMini.mm("<dark_gray>• Withdraw Only: starts with items, but you can't put</dark_gray>")))
        .build()));

    setFixed(SLOT_STATS, new Label(this::statsItem));

    setFixed(SLOT_DIAMONDS_ONLY, diamondsOnly);
    setFixed(SLOT_VANILLA, vanilla);
    setFixed(SLOT_DEPOSIT_ONLY, depositOnly);
    setFixed(SLOT_WITHDRAW_ONLY, withdrawOnly);

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  @Override
  protected void build(Player player) {
    if (seeded.add(player.getUniqueId())) {
      withdrawOnly.stored(player, new ItemStack(Material.GOLD_INGOT, 16));
    }
  }

  private ItemStack statsItem(Player player) {
    return GuiItem.of(Material.PAPER)
        .displayName(GuiMini.mm("<yellow><bold>Slot Contents</bold></yellow>"))
        .lore(List.of(
            loreLine("Diamonds Only", diamondsOnly.stored(player)),
            loreLine("Vanilla Slot", vanilla.stored(player)),
            loreLine("Deposit Only", depositOnly.stored(player)),
            loreLine("Withdraw Only", withdrawOnly.stored(player))))
        .build();
  }

  private static Component loreLine(String label, ItemStack stack) {
    if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
      return GuiMini.mm("<gray>" + label + ":</gray> <dark_gray>(empty)</dark_gray>");
    }
    return Component.text(label + ": " + stack.getType() + " x" + stack.getAmount());
  }

  private static ItemStack placeholder(Material icon, String title) {
    return GuiItem.of(icon)
        .displayName(GuiMini.mm("<dark_gray><bold>" + title + "</bold></dark_gray>"))
        .lore(List.of(GuiMini.mm("<dark_gray>Drop items here.</dark_gray>")))
        .build();
  }
}
