package dev.patric.dungeonsreborn.menus;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.flow.ConfirmDialogWindow;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;

public final class ShowcaseAboutMenu extends Window {
  private static final int SIZE = 54;
  private static final int SLOT_SNAPSHOT = 22;
  private static final int SLOT_REASON = 13;

  private final AtomicReference<OpenContext> lastOpen = new AtomicReference<>();

  public ShowcaseAboutMenu() {
    super(SIZE, GuiMini.mm("<white><bold>About / Debug</bold></white>"), true);

    background(dev.patric.dungeonsreborn.gui.GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, Component.text("Back"))).autoDescribeInLore(false));
    navRight(new CloseButton(p -> GuiButtons.item(GuiButtons.Type.CLOSE, Component.text("Close"))).autoDescribeInLore(false));

    setFixed(10, new Label(p -> dev.patric.dungeonsreborn.gui.GuiItems.named(Material.BOOK, Component.text("What this shows"), List.of(
        GuiMini.mm("<gray>- Window stack + nav bar</gray>"),
        GuiMini.mm("<gray>- Click-outside hook</gray>"),
        GuiMini.mm("<gray>- Open/close reasons</gray>"),
        GuiMini.mm("<gray>- Window tick hook (this screen updates)</gray>"),
        GuiMini.mm("<gray>- Sound helpers</gray>")))));

    setFixed(16, new Button(p -> GuiButtons.item(GuiButtons.Type.INFO, Component.text("Toggle Debug Logs")), ctx -> {
      GuiManager mgr = GuiManager.get();
      mgr.setDebug(!mgr.isDebugEnabled());
      GuiSounds.click(ctx.player());
      ctx.redraw();
    }).autoDescribeInLore(false));

    setFixed(28, new Button(p -> GuiButtons.item(GuiButtons.Type.INFO, Component.text("Confirm Dialog")), ctx -> {
      openSubWindow(ctx.player(), confirmExample());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false));

    setFixed(SLOT_SNAPSHOT, new Label(this::snapshotItem));
    setFixed(SLOT_REASON, new Label(this::openReasonItem));

    onClickOutside(ctx -> {
      GuiSounds.error(ctx.player());
      ctx.player().sendMessage(GuiMini.mm("<gray>You clicked outside the window.</gray>"));
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
        Component.text("expectedWindow=" + snap.expectedWindow()),
        Component.text("actualTopHolder=" + snap.actualTopHolder()),
        Component.text("pendingResumeWindow=" + snap.pendingResumeWindow()),
        Component.text("lastExternalTopHolder=" + snap.lastExternalTopHolder()),
        Component.text("lastEvent=" + snap.lastEvent()));
    return dev.patric.dungeonsreborn.gui.GuiItems.named(Material.PAPER, Component.text("Active Window Snapshot"), lore);
  }

  private org.bukkit.inventory.ItemStack openReasonItem(Player player) {
    OpenContext ctx = lastOpen.get();
    if (ctx == null) {
      return dev.patric.dungeonsreborn.gui.GuiItems.named(Material.PAPER, Component.text("Open Reason"), List.of(Component.text("N/A")));
    }
    return dev.patric.dungeonsreborn.gui.GuiItems.named(Material.PAPER, Component.text("Open Reason"), List.of(
        Component.text("reason=" + ctx.reason()),
        Component.text("detail=" + ctx.detail())));
  }

  public static Window confirmExample() {
    return new ConfirmDialogWindow(
        GuiMini.mm("<white><bold>Confirm</bold></white>"),
        GuiMini.mm("<yellow><bold>Do the thing?</bold></yellow>"),
        List.of(GuiMini.mm("<gray>This is a modal subwindow example.</gray>")),
        (player, result) -> {
          if (result == ConfirmDialogWindow.ConfirmResult.CONFIRM) {
            GuiSounds.success(player);
            player.sendMessage(GuiMini.mm("<green>Confirmed.</green>"));
          } else {
            GuiSounds.error(player);
            player.sendMessage(GuiMini.mm("<red>Cancelled.</red>"));
          }
        });
  }
}
