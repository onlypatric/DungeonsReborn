package dev.patric.dungeonsreborn.menus;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.flow.ConfirmDialogWindow;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ShowcaseAboutMenu extends Window {
  private static final int SIZE = 54;
  private static final int SLOT_SNAPSHOT = 22;
  private static final int SLOT_REASON = 13;

  private final AtomicReference<OpenContext> lastOpen = new AtomicReference<>();

  public ShowcaseAboutMenu() {
    super(SIZE, GuiI18n.tr("gui.showcase.about.title"), true);

    background(dev.patric.dungeonsreborn.gui.GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, GuiI18n.tr(p, "gui.button.back"))).autoDescribeInLore(false));
    navRight(new CloseButton(p -> GuiButtons.item(GuiButtons.Type.CLOSE, GuiI18n.tr(p, "gui.button.close"))).autoDescribeInLore(false));

    setFixed(10, new Label(p -> dev.patric.dungeonsreborn.gui.GuiItems.named(Material.BOOK, GuiI18n.tr(p, "gui.showcase.about.what.title"), List.of(
        GuiI18n.tr(p, "gui.showcase.about.what.stack"),
        GuiI18n.tr(p, "gui.showcase.about.what.clickOutside"),
        GuiI18n.tr(p, "gui.showcase.about.what.reasons"),
        GuiI18n.tr(p, "gui.showcase.about.what.tick"),
        GuiI18n.tr(p, "gui.showcase.about.what.sound")))));

    setFixed(16, new Button(p -> GuiButtons.item(GuiButtons.Type.INFO, GuiI18n.tr(p, "gui.showcase.about.toggleDebug")), ctx -> {
      GuiManager mgr = GuiManager.get();
      mgr.setDebug(!mgr.isDebugEnabled());
      GuiSounds.click(ctx.player());
      ctx.redraw();
    }).autoDescribeInLore(false));

    setFixed(28, new Button(p -> GuiButtons.item(GuiButtons.Type.INFO, GuiI18n.tr(p, "gui.showcase.about.confirmDialog")), ctx -> {
      openSubWindow(ctx.player(), confirmExample());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false));

    setFixed(SLOT_SNAPSHOT, new Label(this::snapshotItem));
    setFixed(SLOT_REASON, new Label(this::openReasonItem));

    onClickOutside(ctx -> {
      GuiSounds.error(ctx.player());
      ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "gui.showcase.about.clickOutside"));
    });

    onOpenWithReason(ctx -> {
      lastOpen.set(ctx);
      GuiSounds.open(ctx.player());
      GuiManager.get().runNextTick(() -> redrawSlot(ctx.player(), SLOT_REASON));
    });
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));

    onTick(ctx -> {
      redrawSlot(ctx.player(), SLOT_SNAPSHOT);
      redrawSlot(ctx.player(), SLOT_REASON);
    });
    tickEvery(20);
  }

  private org.bukkit.inventory.ItemStack snapshotItem(Player player) {
    GuiManager.ActiveWindowSnapshot snap = GuiManager.get().activeWindow(player);
    List<Component> lore = List.of(
        GuiI18n.tr(player, "gui.showcase.about.snapshot.expected", Placeholder.unparsed("value", String.valueOf(snap.expectedWindow()))),
        GuiI18n.tr(player, "gui.showcase.about.snapshot.actual", Placeholder.unparsed("value", String.valueOf(snap.actualTopHolder()))),
        GuiI18n.tr(player, "gui.showcase.about.snapshot.pending", Placeholder.unparsed("value", String.valueOf(snap.pendingResumeWindow()))),
        GuiI18n.tr(player, "gui.showcase.about.snapshot.external", Placeholder.unparsed("value", String.valueOf(snap.lastExternalTopHolder()))),
        GuiI18n.tr(player, "gui.showcase.about.snapshot.event", Placeholder.unparsed("value", String.valueOf(snap.lastEvent()))));
    return dev.patric.dungeonsreborn.gui.GuiItems.named(Material.PAPER, GuiI18n.tr(player, "gui.showcase.about.snapshot.title"), lore);
  }

  private org.bukkit.inventory.ItemStack openReasonItem(Player player) {
    OpenContext ctx = lastOpen.get();
    if (ctx == null) {
      return dev.patric.dungeonsreborn.gui.GuiItems.named(Material.PAPER, GuiI18n.tr(player, "gui.showcase.about.reason.title"),
          List.of(GuiI18n.tr(player, "gui.showcase.about.reason.none")));
    }
    return dev.patric.dungeonsreborn.gui.GuiItems.named(Material.PAPER, GuiI18n.tr(player, "gui.showcase.about.reason.title"), List.of(
        GuiI18n.tr(player, "gui.showcase.about.reason.reason", Placeholder.unparsed("value", String.valueOf(ctx.reason()))),
        GuiI18n.tr(player, "gui.showcase.about.reason.detail", Placeholder.unparsed("value", String.valueOf(ctx.detail())))));
  }

  public static Window confirmExample() {
    return new ConfirmDialogWindow(
        GuiI18n.tr("gui.showcase.about.confirm.title"),
        GuiI18n.tr("gui.showcase.about.confirm.header"),
        List.of(GuiI18n.tr("gui.showcase.about.confirm.detail")),
        (player, result) -> {
          if (result == ConfirmDialogWindow.ConfirmResult.CONFIRM) {
            GuiSounds.success(player);
            player.sendMessage(GuiI18n.tr(player, "gui.showcase.about.confirm.confirmed"));
          } else {
            GuiSounds.error(player);
            player.sendMessage(GuiI18n.tr(player, "gui.showcase.about.confirm.cancelled"));
          }
        });
  }
}
