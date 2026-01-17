package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.EmptyState;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.ListSearchBar;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.quests.QuestGiverMode;
import dev.patric.dungeonsreborn.quests.QuestGiverSpec;
import dev.patric.dungeonsreborn.quests.QuestGiverYamlRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class QuestGiverIndexMenu extends Window {
  private static final int SIZE = 54;

  private record GiverEntry(String id, String title, QuestGiverSpec spec) {
  }

  private final QuestGiverYamlRegistry givers;
  private final VirtualList<GiverEntry> list;
  private List<GiverEntry> cachedEntries = List.of();
  private boolean cacheValid;

  public QuestGiverIndexMenu(QuestGiverYamlRegistry givers) {
    super(SIZE, GuiI18n.tr("gui.questGivers.index.title"), true);
    this.givers = Objects.requireNonNull(givers, "givers");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        this::entryItem,
        (ctx, entry) -> sendVisitHint(ctx.player(), entry));
    list.searchKey(entry -> entry.id + " " + entry.title);
    list.emptyStateItem(EmptyState.list());
    list.apply(this, Placement.FIXED);

    navLeft(GuiNav.backButton().autoDescribeInLore(false));
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));
    nav(5, refreshButton());

    setFixedAt(0, 1, header());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private Label header() {
    return new Label(p -> GuiItems.named(Material.WRITABLE_BOOK,
        GuiI18n.tr(p, "gui.questGivers.index.header.title"),
        List.of(GuiI18n.tr(p, "gui.questGivers.index.header.hint",
            Placeholder.unparsed("count", String.valueOf(givers.givers().size()))))));
  }

  private Button refreshButton() {
    return new Button(p -> GuiItems.named(Material.CLOCK,
        GuiI18n.tr(p, "gui.questGivers.index.refresh.title"),
        List.of(GuiI18n.tr(p, "gui.questGivers.index.refresh.hint"))), ctx -> {
      cacheValid = false;
      list.invalidate(ctx.player());
      list.redraw(ctx.window(), ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private List<GiverEntry> entries(Player player) {
    if (cacheValid) {
      return cachedEntries;
    }
    List<GiverEntry> out = new ArrayList<>();
    for (Map.Entry<String, QuestGiverSpec> entry : givers.givers().entrySet()) {
      String id = entry.getKey();
      QuestGiverSpec spec = entry.getValue();
      String title = spec == null || spec.title() == null || spec.title().isBlank() ? id : spec.title();
      out.add(new GiverEntry(id, title, spec));
    }
    out.sort(Comparator.comparing(GiverEntry::id, String.CASE_INSENSITIVE_ORDER));
    cachedEntries = out;
    cacheValid = true;
    return cachedEntries;
  }

  private ItemStack entryItem(Player player, GiverEntry entry) {
    ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    meta.displayName(GuiI18n.tr(player, "gui.questGivers.index.entry.title",
        Placeholder.unparsed("title", entry.title)));
    List<Component> lore = new ArrayList<>();
    QuestGiverSpec spec = entry.spec;
    if (spec != null) {
      lore.add(GuiI18n.tr(player, "gui.questGivers.index.entry.mode",
          Placeholder.unparsed("mode", modeLabel(player, spec.mode()))));
      lore.add(GuiI18n.tr(player, "gui.questGivers.index.entry.count",
          Placeholder.unparsed("count", String.valueOf(spec.questIds().size()))));
    }
    lore.add(GuiI18n.tr(player, "gui.questGivers.index.entry.hint"));
    meta.lore(lore);
    item.setItemMeta(meta);
    return item;
  }

  private void sendVisitHint(Player player, GiverEntry entry) {
    if (player == null) {
      return;
    }
    player.sendMessage(Locales.component(player, "messages.quests.giverIndex.visitHint",
        Locales.placeholders("id", entry.id, "title", entry.title)));
    GuiSounds.click(player);
  }

  private static String modeLabel(Player player, QuestGiverMode mode) {
    if (mode == QuestGiverMode.RANDOM_POOL) {
      return GuiI18n.str(player, "gui.questGivers.index.entry.mode.random");
    }
    return GuiI18n.str(player, "gui.questGivers.index.entry.mode.fixed");
  }
}
