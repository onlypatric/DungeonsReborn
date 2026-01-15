package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.upgrades.UpgradeLore;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeService;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeSpec;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class UpgradeInspectMenu extends Window {
  private static final int SIZE = 27;
  private static final int SLOT_TITLE = 4;
  private static final int SLOT_ITEM = 13;
  private static final int SLOT_UPGRADES = 11;
  private static final int SLOT_CLEAR = 15;

  private final UpgradeService upgrades;

  public UpgradeInspectMenu(UpgradeService upgrades) {
    super(SIZE, GuiI18n.tr("gui.upgrades.inspect.title"), true);
    this.upgrades = upgrades;

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));
    navRight(new CloseButton(p -> GuiButtons.item(GuiButtons.Type.CLOSE, GuiI18n.tr(p, "gui.button.close")))
        .autoDescribeInLore(false));

    setFixed(SLOT_TITLE, new Label(GuiItems.named(Material.BOOK, GuiI18n.tr("gui.upgrades.inspect.header.title"), List.of(
        GuiI18n.tr("gui.upgrades.inspect.header.hint")))));

    setFixed(SLOT_ITEM, new Label(this::heldItem));
    setFixed(SLOT_UPGRADES, new Label(this::upgradeList));
    setFixed(SLOT_CLEAR, new Button(p -> GuiItems.named(Material.BARRIER, GuiI18n.tr(p, "gui.upgrades.inspect.clear.title"), List.of(
        GuiI18n.tr(p, "gui.upgrades.inspect.clear.hint"))), ctx -> clearUpgrades(ctx.player()))
        .autoDescribeInLore(false));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private ItemStack heldItem(Player player) {
    ItemStack item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      return GuiItems.named(Material.GRAY_DYE, GuiI18n.tr(player, "gui.upgrades.inspect.noItem.title"), List.of(
          GuiI18n.tr(player, "gui.upgrades.inspect.noItem.hint")));
    }
    return item.clone();
  }

  private ItemStack upgradeList(Player player) {
    ItemStack item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      return GuiItems.named(Material.GRAY_DYE, GuiI18n.tr(player, "gui.upgrades.inspect.noUpgrades.title"), List.of(
          GuiI18n.tr(player, "gui.upgrades.inspect.noUpgrades.hint")));
    }
    List<String> records = upgrades.upgradeRecords(item);
    if (records.isEmpty()) {
      return GuiItems.named(Material.GRAY_DYE, GuiI18n.tr(player, "gui.upgrades.inspect.noneApplied.title"), List.of(
          GuiI18n.tr(player, "gui.upgrades.inspect.noneApplied.hint")));
    }
    List<Component> lore = new ArrayList<>();
    for (String record : records) {
      if (record == null || record.isBlank()) {
        continue;
      }
      if (record.startsWith("vanilla:")) {
        lore.add(GuiI18n.tr(player, "gui.upgrades.inspect.vanillaBook"));
        continue;
      }
      UpgradeSpec spec = upgrades.registry().upgradeSpec(record);
      if (spec != null && spec.name() != null && !spec.name().isBlank()) {
        lore.add(Component.text("• ", NamedTextColor.GRAY).append(UpgradeLore.parseRichText(spec.name())));
      } else {
        lore.add(Component.text("• " + record, NamedTextColor.GRAY));
      }
    }
    return GuiItems.named(Material.ENCHANTED_BOOK, GuiI18n.tr(player, "gui.upgrades.inspect.applied.title"), lore);
  }

  private void clearUpgrades(Player player) {
    ItemStack item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      GuiSounds.error(player);
      player.sendMessage(GuiI18n.tr(player, "gui.upgrades.inspect.clear.missingItem"));
      return;
    }
    ItemStack updated = upgrades.clearUpgrades(item);
    player.getInventory().setItemInMainHand(updated);
    redraw(player);
    GuiSounds.success(player);
  }
}
