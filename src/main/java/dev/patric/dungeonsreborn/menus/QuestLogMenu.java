package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.ListSearchBar;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.quests.QuestGiverYamlRegistry;
import dev.patric.dungeonsreborn.quests.QuestService;
import dev.patric.dungeonsreborn.quests.QuestService.QuestAcceptResult;
import dev.patric.dungeonsreborn.quests.QuestService.QuestEntryStatus;
import dev.patric.dungeonsreborn.quests.QuestService.QuestLogEntry;
import net.kyori.adventure.text.Component;

public final class QuestLogMenu extends Window {
  private final QuestService quests;
  private final QuestGiverYamlRegistry givers;
  private final VirtualList<QuestLogEntry> list;

  public static void open(Player player, QuestService quests, QuestGiverYamlRegistry givers) {
    Objects.requireNonNull(player, "player");
    GuiManager.get().open(player, new QuestLogMenu(quests, givers));
  }

  public QuestLogMenu(QuestService quests, QuestGiverYamlRegistry givers) {
    super(54, GuiI18n.tr("gui.quests.title"));
    this.quests = Objects.requireNonNull(quests, "quests");
    this.givers = Objects.requireNonNull(givers, "givers");
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(1, 1, 4, 7,
        player -> quests.logEntries(player),
        this::renderEntry,
        this::handleClick);
    this.list.searchKey(entry -> entry == null || entry.spec() == null ? "" : entry.spec().name());
    this.list.apply(this, Placement.FIXED);

    GuiNav.applyList(this, list, new BackButton(), new CloseButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));
    nav(6, giversButton());
    setFixedAt(0, 4, new Label(this::headerItem));
  }

  private ItemStack renderEntry(Player player, QuestLogEntry entry) {
    if (entry == null || entry.spec() == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    String titleText = entry.spec().name().isBlank() ? entry.spec().id() : entry.spec().name();
    Component title = Component.text(titleText);
    List<Component> lore = new ArrayList<>();
    if (entry.statusLine() != null) {
      lore.add(entry.statusLine());
    }
    if (!entry.spec().description().isEmpty()) {
      lore.add(GuiMini.mm(entry.spec().description().get(0)));
    }
    return GuiItems.head("ICON_QUESTS", title, lore);
  }

  private void handleClick(Window.ClickContext ctx, QuestLogEntry entry) {
    if (entry == null || entry.spec() == null) {
      return;
    }
    if (ctx.clickType().isRightClick()) {
      ctx.window().openSubWindow(ctx.player(), new QuestInspectMenu(entry.spec()));
      return;
    }
    QuestEntryStatus status = entry.status();
    if (status == QuestEntryStatus.AVAILABLE) {
      QuestAcceptResult result = quests.accept(ctx.player(), entry.spec().id());
      if (result.message() != null) {
        ctx.player().sendMessage(result.message());
      }
      if (result.success()) {
        ctx.window().redraw(ctx.player());
      }
      return;
    }
    if (entry.statusLine() != null) {
      ctx.player().sendMessage(entry.statusLine());
    }
  }

  private Button giversButton() {
    Button button = new Button(player -> GuiItems.head("ICON_QUESTS",
        GuiI18n.tr(player, "gui.quests.givers.title"),
        List.of(GuiI18n.tr(player, "gui.quests.givers.desc"))));
    button.left(GuiI18n.tr("gui.controls.action"), ctx ->
        ctx.window().openSubWindow(ctx.player(), new QuestGiverIndexMenu(quests, givers)));
    button.autoDescribeInLore(false);
    return button;
  }

  private ItemStack headerItem(Player player) {
    return GuiItems.head("ICON_QUESTS", GuiI18n.tr(player, "gui.quests.header"), List.of());
  }
}
