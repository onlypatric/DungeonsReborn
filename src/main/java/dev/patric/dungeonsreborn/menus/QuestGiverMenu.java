package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.ListSearchBar;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.quests.QuestGiverSpec;
import dev.patric.dungeonsreborn.quests.QuestService;
import dev.patric.dungeonsreborn.quests.QuestService.QuestAcceptResult;
import dev.patric.dungeonsreborn.quests.QuestService.QuestEntryStatus;
import dev.patric.dungeonsreborn.quests.QuestService.QuestLogEntry;
import dev.patric.dungeonsreborn.quests.QuestSpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class QuestGiverMenu extends Window {
  private record QuestEntry(QuestSpec spec, QuestLogEntry entry) {
  }

  private final QuestService quests;
  private final QuestGiverSpec giver;
  private final VirtualList<QuestEntry> list;

  public QuestGiverMenu(QuestService quests, QuestGiverSpec giver) {
    super(54, GuiI18n.tr("gui.quests.giver.title",
        Placeholder.unparsed("giver", giver == null ? "" : giver.title())));
    this.quests = Objects.requireNonNull(quests, "quests");
    this.giver = Objects.requireNonNull(giver, "giver");
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(1, 1, 4, 7,
        player -> questEntries(player),
        this::renderEntry,
        this::handleClick);
    this.list.searchKey(entry -> entry == null || entry.spec() == null ? "" : entry.spec().name());
    this.list.apply(this, Placement.FIXED);

    GuiNav.applyList(this, list, new BackButton(), new CloseButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));
    setFixedAt(0, 4, new Label(this::headerItem));
  }

  private List<QuestEntry> questEntries(Player player) {
    List<QuestEntry> entries = new ArrayList<>();
    List<String> questIds = quests.questIdsForGiver(player, giver);
    for (String questId : questIds) {
      QuestSpec spec = quests.registry().quest(questId);
      if (spec == null) {
        continue;
      }
      QuestLogEntry logEntry = quests.entryFor(player, spec);
      entries.add(new QuestEntry(spec, logEntry));
    }
    return entries;
  }

  private ItemStack renderEntry(Player player, QuestEntry entry) {
    if (entry == null || entry.spec() == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    String titleText = entry.spec().name().isBlank() ? entry.spec().id() : entry.spec().name();
    Component title = Component.text(titleText);
    List<Component> lore = new ArrayList<>();
    if (entry.entry() != null && entry.entry().statusLine() != null) {
      lore.add(entry.entry().statusLine());
    }
    if (!entry.spec().description().isEmpty()) {
      lore.add(GuiMini.mm(entry.spec().description().get(0)));
    }
    return GuiItems.head("ICON_QUESTS", title, lore);
  }

  private void handleClick(Window.ClickContext ctx, QuestEntry entry) {
    if (entry == null || entry.spec() == null) {
      return;
    }
    QuestLogEntry logEntry = entry.entry();
    if (logEntry != null && logEntry.status() == QuestEntryStatus.AVAILABLE) {
      QuestAcceptResult result = quests.accept(ctx.player(), entry.spec().id());
      if (result.message() != null) {
        ctx.player().sendMessage(result.message());
      }
      if (result.success()) {
        ctx.window().redraw(ctx.player());
      }
      return;
    }
    if (logEntry != null && logEntry.statusLine() != null) {
      ctx.player().sendMessage(logEntry.statusLine());
    }
  }

  private ItemStack headerItem(Player player) {
    List<Component> lore = new ArrayList<>();
    List<String> dialogue = quests.giverDialogue(player, giver);
    if (!dialogue.isEmpty()) {
      lore.add(GuiMini.mm(dialogue.get(0)));
    }
    return GuiItems.head("ICON_QUESTS",
        GuiI18n.tr(player, "gui.quests.giver.header",
            Placeholder.unparsed("giver", giver.title())),
        lore);
  }
}
