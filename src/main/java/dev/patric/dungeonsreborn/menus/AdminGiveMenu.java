package dev.patric.dungeonsreborn.menus;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.locale.Locales;

public final class AdminGiveMenu extends Window {
  private static final int SIZE = 27;

  public AdminGiveMenu() {
    super(SIZE, GuiI18n.tr("gui.adminGive.title"), true);

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));
    navRight(new CloseButton(p -> GuiButtons.item(GuiButtons.Type.CLOSE, GuiI18n.tr(p, "gui.button.close"))).autoDescribeInLore(false));

    setFixed(4, new Label(GuiItems.named(Material.COMMAND_BLOCK, GuiI18n.tr("gui.adminGive.header.title"), List.of(
        GuiI18n.tr("gui.adminGive.header.subtitle")))));

    setFixed(10, giveButton(Material.CHEST, "gui.adminGive.item.title", "gui.adminGive.item.hint",
        "/dr admin give item <id> [player] [amount]"));
    setFixed(11, giveButton(Material.ENCHANTED_BOOK, "gui.adminGive.upgrade.title", "gui.adminGive.upgrade.hint",
        "/dr admin give upgrade <id> [player]"));
    setFixed(12, giveButton(Material.ZOMBIE_SPAWN_EGG, "gui.adminGive.mobEgg.title", "gui.adminGive.mobEgg.hint",
        "/dr admin give mob_egg <mobId> [player]"));
    setFixed(13, giveButton(Material.SPAWNER, "gui.adminGive.spawner.title", "gui.adminGive.spawner.hint",
        "/dr admin give spawner <spawnerId> [player]"));
    setFixed(14, giveButton(Material.BOOK, "gui.adminGive.recipe.title", "gui.adminGive.recipe.hint",
        "/dr admin give recipe <recipeId> [player]"));
    setFixed(15, giveButton(Material.EMERALD, "gui.adminGive.shopToken.title", "gui.adminGive.shopToken.hint",
        "/dr admin give shop_token <tier> [player] [amount]"));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private Button giveButton(Material material, String nameKey, String hintKey, String command) {
    return new Button(p -> GuiItems.named(material, GuiI18n.tr(p, nameKey), List.of(GuiI18n.tr(p, hintKey))), ctx -> {
      Player player = ctx.player();
      if (player != null) {
        player.sendMessage(Locales.component(player, "messages.command.adminGive.hint",
            Locales.placeholders("command", command)));
      }
      GuiSounds.click(player);
    }).autoDescribeInLore(false);
  }
}
