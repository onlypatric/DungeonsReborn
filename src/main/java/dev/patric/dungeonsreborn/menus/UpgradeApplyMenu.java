package dev.patric.dungeonsreborn.menus;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.upgrades.UpgradeService;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.storage.StorageArea;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import net.kyori.adventure.text.Component;

public final class UpgradeApplyMenu extends Window {
  private final UpgradeService upgrades;
  private final StorageArea upgradeSlot;
  private final StorageArea targetSlot;

  public static void open(Player player, UpgradeService upgrades) {
    Objects.requireNonNull(player, "player");
    GuiManager.get().open(player, new UpgradeApplyMenu(upgrades));
  }

  public UpgradeApplyMenu(UpgradeService upgrades) {
    super(54, GuiI18n.tr("gui.upgrades.apply.title"), true);
    this.upgrades = Objects.requireNonNull(upgrades, "upgrades");
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.upgradeSlot = new StorageArea(2, 3, 1, 1);
    this.targetSlot = new StorageArea(2, 5, 1, 1);

    upgradeSlot.slot(0)
        .vanilla(true)
        .accepts(stack -> upgrades.resolveSpec(stack) != null);
    targetSlot.slot(0)
        .vanilla(true)
        .accepts(stack -> stack != null && !stack.getType().isAir());

    upgradeSlot.apply(this, Placement.FIXED);
    targetSlot.apply(this, Placement.FIXED);

    GuiNav.applyDetail(this, new BackButton(), new CloseButton());
    setFixedAt(0, 4, new Label(this::headerItem));
    setFixedAt(1, 3, new Label(player -> slotLabel(player, "gui.upgrades.apply.upgrade")));
    setFixedAt(1, 5, new Label(player -> slotLabel(player, "gui.upgrades.apply.target")));
    setFixedAt(4, 4, applyButton());

    onClose(this::returnItems);
  }

  private ItemStack headerItem(Player player) {
    Component title = GuiI18n.tr(player, "gui.upgrades.apply.header");
    Component desc = GuiI18n.tr(player, "gui.upgrades.apply.desc");
    return GuiItems.head("ICON_UPGRADES", title, List.of(desc));
  }

  private ItemStack slotLabel(Player player, String key) {
    Component title = GuiI18n.tr(player, key + ".title");
    Component desc = GuiI18n.tr(player, key + ".desc");
    return GuiItems.head("ICON_UPGRADES", title, List.of(desc));
  }

  private Button applyButton() {
    Button button = new Button(player -> GuiButtons.item(GuiButtons.Type.CONFIRM,
        GuiI18n.tr(player, "gui.upgrades.apply.action.title"),
        List.of(GuiI18n.tr(player, "gui.upgrades.apply.action.desc"))));
    button.left(GuiI18n.tr("gui.controls.action"), ctx -> {
      Player player = ctx.player();
      ItemStack upgradeItem = upgradeSlot.get(player, 0);
      ItemStack targetItem = targetSlot.get(player, 0);
      if (upgradeItem == null || upgradeItem.getType().isAir()) {
        player.sendMessage(GuiI18n.tr(player, "gui.upgrades.apply.error.noUpgrade"));
        return;
      }
      if (targetItem == null || targetItem.getType().isAir()) {
        player.sendMessage(GuiI18n.tr(player, "gui.upgrades.apply.error.noTarget"));
        return;
      }
      var result = upgrades.apply(player, targetItem, upgradeItem);
      if (!result.success()) {
        String message = result.error() == null ? "Upgrade failed." : result.error();
        player.sendMessage(GuiMini.mm("<red>" + message + "</red>"));
        return;
      }
      targetSlot.set(player, 0, result.updated());
      upgradeSlot.set(player, 0, null);
      ctx.window().redraw(player);
      player.sendMessage(GuiMini.mm("<green>Upgrade applied.</green>"));
    });
    button.autoDescribeInLore(false);
    return button;
  }

  private void returnItems(Player player) {
    returnItems(player, upgradeSlot);
    returnItems(player, targetSlot);
  }

  private void returnItems(Player player, StorageArea area) {
    ItemStack stored = area.get(player, 0);
    area.clear(player);
    if (stored == null || stored.getType().isAir()) {
      return;
    }
    var leftovers = player.getInventory().addItem(stored);
    if (!leftovers.isEmpty()) {
      for (ItemStack stack : leftovers.values()) {
        player.getWorld().dropItemNaturally(player.getLocation(), stack);
      }
    }
  }
}
