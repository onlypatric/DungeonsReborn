package dev.patric.dungeonsreborn.menus;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.DraggableSlot;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

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
      placeholder(Material.DIAMOND, GuiI18n.str(GuiI18n.defaultLocale(), "gui.showcase.slots.rule.diamonds")),
      stack -> stack != null && stack.getType() == Material.DIAMOND,
      true,
      true,
      (player, stack) -> redrawSlot(player, SLOT_STATS));

  private final DraggableSlot vanilla = DraggableSlot.vanilla((player, stack) -> redrawSlot(player, SLOT_STATS));

  private final DraggableSlot depositOnly = new DraggableSlot(
      placeholder(Material.HOPPER, GuiI18n.str(GuiI18n.defaultLocale(), "gui.showcase.slots.rule.deposit")),
      stack -> stack != null && !stack.getType().isAir(),
      false,
      true,
      (player, stack) -> redrawSlot(player, SLOT_STATS));

  private final DraggableSlot withdrawOnly = new DraggableSlot(
      GuiItem.of(Material.CHEST)
          .displayName(GuiI18n.tr("gui.showcase.slots.rule.withdraw"))
          .lore(List.of(GuiI18n.tr("gui.showcase.slots.withdrawHint")))
          .build(),
      stack -> false,
      true,
      false,
      (player, stack) -> redrawSlot(player, SLOT_STATS));

  public ShowcaseDraggableSlotsMenu() {
    super(SIZE, GuiI18n.tr("gui.showcase.slots.title"), true);

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, GuiI18n.tr(p, "gui.button.back"))).autoDescribeInLore(false));
    navRight(new CloseButton(p -> GuiButtons.item(GuiButtons.Type.CLOSE, GuiI18n.tr(p, "gui.button.close"))).autoDescribeInLore(false));

    setFixed(SLOT_HELP, new Label(GuiItem.of(Material.PAPER)
        .displayName(GuiI18n.tr("gui.showcase.slots.help.title"))
        .lore(List.of(
            GuiI18n.tr("gui.showcase.slots.help.summary"),
            GuiI18n.tr("gui.showcase.slots.help.diamonds"),
            GuiI18n.tr("gui.showcase.slots.help.vanilla"),
            GuiI18n.tr("gui.showcase.slots.help.deposit"),
            GuiI18n.tr("gui.showcase.slots.help.withdraw")))
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
        .displayName(GuiI18n.tr(player, "gui.showcase.slots.stats.title"))
        .lore(List.of(
            loreLine("gui.showcase.slots.rule.diamonds", diamondsOnly.stored(player)),
            loreLine("gui.showcase.slots.rule.vanilla", vanilla.stored(player)),
            loreLine("gui.showcase.slots.rule.deposit", depositOnly.stored(player)),
            loreLine("gui.showcase.slots.rule.withdraw", withdrawOnly.stored(player))))
        .build();
  }

  private static Component loreLine(String key, ItemStack stack) {
    String label = GuiI18n.str(GuiI18n.defaultLocale(), key);
    if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
      return GuiI18n.tr("gui.showcase.slots.stats.empty", Placeholder.unparsed("label", label));
    }
    return GuiI18n.tr("gui.showcase.slots.stats.value",
        Placeholder.unparsed("label", label),
        Placeholder.unparsed("item", stack.getType().toString()),
        Placeholder.unparsed("amount", String.valueOf(stack.getAmount())));
  }

  private static ItemStack placeholder(Material icon, String title) {
    return GuiItem.of(icon)
        .displayName(GuiI18n.tr("gui.showcase.slots.placeholder", Placeholder.unparsed("title", title)))
        .lore(List.of(GuiI18n.tr("gui.showcase.slots.placeholderHint")))
        .build();
  }
}
