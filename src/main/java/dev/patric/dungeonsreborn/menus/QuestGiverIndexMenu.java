package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiDebug;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.ListSearchBar;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.quests.QuestGiverSpec;
import dev.patric.dungeonsreborn.quests.QuestGiverYamlRegistry;
import dev.patric.dungeonsreborn.quests.QuestService;
import net.kyori.adventure.text.Component;

public final class QuestGiverIndexMenu extends Window {
  private final QuestService quests;
  private final QuestGiverYamlRegistry givers;
  private final VirtualList<QuestGiverSpec> list;
  private boolean debugLogged;

  public static void open(Player player, QuestService quests, QuestGiverYamlRegistry givers) {
    Objects.requireNonNull(player, "player");
    GuiManager.get().open(player, new QuestGiverIndexMenu(quests, givers));
  }

  public QuestGiverIndexMenu(QuestService quests, QuestGiverYamlRegistry givers) {
    super(54, GuiI18n.tr("gui.quests.giverIndex.title"));
    this.quests = Objects.requireNonNull(quests, "quests");
    this.givers = Objects.requireNonNull(givers, "givers");
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(1, 1, 4, 7,
        player -> giverEntries(),
        this::renderEntry,
        this::handleClick);
    this.list.searchKey(spec -> spec == null ? "" : spec.title());
    this.list.apply(this, Placement.FIXED);

    GuiNav.applyList(this, list, new BackButton(), new CloseButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));
    setFixedAt(0, 4, new Label(this::headerItem));
  }

  private List<QuestGiverSpec> giverEntries() {
    List<QuestGiverSpec> entries = new ArrayList<>(givers.givers().values());
    debugLogged = GuiDebug.logIndexOnce(debugLogged, "quest_givers", entries.size());
    entries.sort(Comparator.comparing(spec -> spec.title().toLowerCase(java.util.Locale.ROOT)));
    return entries;
  }

  private ItemStack renderEntry(Player player, QuestGiverSpec spec) {
    if (spec == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    String titleText = spec.title() == null || spec.title().isBlank() ? spec.id() : spec.title();
    Component title = Component.text(titleText);
    Component count = GuiI18n.tr(player, "gui.quests.giverIndex.count",
        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("count", String.valueOf(spec.questIds().size())));
    return GuiItems.head("ICON_QUESTS", title, List.of(count));
  }

  private void handleClick(Window.ClickContext ctx, QuestGiverSpec spec) {
    if (spec == null) {
      return;
    }
    ctx.window().openSubWindow(ctx.player(), new QuestGiverMenu(quests, spec));
  }

  private ItemStack headerItem(Player player) {
    return GuiItems.head("ICON_QUESTS", GuiI18n.tr(player, "gui.quests.giverIndex.header"), List.of());
  }
}
