package dev.patric.dungeonsreborn.gui.flow;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import net.kyori.adventure.text.Component;

/**
 * A simple confirm dialog: message + confirm/cancel buttons.
 * <p>
 * Intended to be opened as a subwindow via {@link Window#openSubWindow(Player, Window)}.
 */
public final class ConfirmDialogWindow extends Window {
  private static final int SIZE = 27;

  private static final int SLOT_MESSAGE = 13;
  private static final int SLOT_CONFIRM = 11;
  private static final int SLOT_CANCEL = 15;

  private final Component messageTitle;
  private final List<Component> messageLore;
  private final BiConsumer<Player, ConfirmResult> onResult;
  private boolean resolved;

  public enum ConfirmResult {
    CONFIRM,
    CANCEL
  }

  public ConfirmDialogWindow(Component title, Component messageTitle, List<Component> messageLore,
      BiConsumer<Player, ConfirmResult> onResult) {
    super(SIZE, Objects.requireNonNull(title, "title"), true);
    this.messageTitle = Objects.requireNonNull(messageTitle, "messageTitle");
    this.messageLore = List.copyOf(Objects.requireNonNull(messageLore, "messageLore"));
    this.onResult = Objects.requireNonNull(onResult, "onResult");

    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));

    setFixed(SLOT_MESSAGE, new Label(p -> GuiItems.named(Material.PAPER, this.messageTitle, this.messageLore)));

    setFixed(SLOT_CONFIRM, new Button(GuiItems.named(Material.LIME_CONCRETE, Component.text("Confirm")), ctx -> resolve(ctx.player(), ConfirmResult.CONFIRM))
        .autoDescribeInLore(false));
    setFixed(SLOT_CANCEL, new Button(GuiItems.named(Material.RED_CONCRETE, Component.text("Cancel")), ctx -> resolve(ctx.player(), ConfirmResult.CANCEL))
        .autoDescribeInLore(false));

    // Optional UX affordance: close button in the top-right.
    setFixed(8, new CloseButton().autoDescribeInLore(false));

    onClose(player -> {
      if (!resolved) {
        resolve(player, ConfirmResult.CANCEL);
      }
    });
  }

  private void resolve(Player player, ConfirmResult result) {
    if (resolved) {
      return;
    }
    resolved = true;
    onResult.accept(player, result);
    player.closeInventory();
  }
}

