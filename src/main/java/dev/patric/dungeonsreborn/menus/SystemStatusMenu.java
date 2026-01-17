package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.EmptyState;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.StatusRow;
import dev.patric.dungeonsreborn.gui.components.list.ListSearchBar;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.system.SystemStatusStore;
import net.kyori.adventure.text.Component;

public final class SystemStatusMenu extends Window {
  private static final int SIZE = 54;

  private final SystemStatusStore store;
  private final VirtualList<SystemStatusStore.Entry> list;

  public SystemStatusMenu(SystemStatusStore store) {
    super(SIZE, GuiI18n.tr("gui.systemStatus.title"), true);
    this.store = Objects.requireNonNull(store, "store");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(player, entry),
        (ctx, entry) -> handleClick(ctx, entry));
    list.searchKey(entry -> entry.label() + " " + entry.id());
    list.emptyStateItem(EmptyState.list());
    list.apply(this, Placement.FIXED);

    navLeft(GuiNav.backButton());
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));

    setFixedAt(0, 4, new Label(GuiItems.named(Material.COMPASS, GuiI18n.tr("gui.systemStatus.header.title"), List.of(
        GuiI18n.tr("gui.systemStatus.header.hint")))));
    setFixedAt(5, 4, errorReportButton());

    new StatusRow(0)
        .column(0, new Label(p -> GuiItems.named(Material.PAPER, GuiI18n.tr(p, "gui.systemStatus.overview.title"), List.of(
            Locales.component(p, "gui.systemStatus.overview.subsystems",
                Locales.placeholders("count", store.entries().size())),
            Locales.component(p, "gui.systemStatus.overview.errors",
                Locales.placeholders("count", store.errors().size()))))))
        .apply(this);

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private List<SystemStatusStore.Entry> entries(Player player) {
    List<SystemStatusStore.Entry> entries = new ArrayList<>(store.entries());
    entries.sort(Comparator.comparing(SystemStatusStore.Entry::label, String.CASE_INSENSITIVE_ORDER));
    return entries;
  }

  private ItemStack entryItem(Player player, SystemStatusStore.Entry entry) {
    Material material = entry.errorCount() > 0 ? Material.BARRIER : Material.PAPER;
    List<Component> lore = new ArrayList<>();
    lore.add(Locales.component(player, "gui.common.line.id", Locales.placeholders("value", entry.id())));
    if (!entry.detail().isBlank()) {
      lore.add(Locales.component(player, "gui.common.line.detail", Locales.placeholders("value", entry.detail())));
    }
    if (!entry.source().isBlank()) {
      lore.add(Locales.component(player, "gui.common.line.source", Locales.placeholders("value", entry.source())));
    }
    lore.add(Locales.component(player, "gui.common.line.lastReload",
        Locales.placeholders("value", formatAgo(player, entry.timestampMs()))));
    if (entry.errorCount() > 0) {
      lore.add(Locales.component(player, "gui.common.errors.count", Locales.placeholders("count", entry.errorCount())));
      lore.add(GuiI18n.tr(player, "gui.common.errors.clickView"));
    } else {
      lore.add(GuiI18n.tr(player, "gui.common.errors.none"));
    }
    return GuiItem.of(new ItemStack(material))
        .displayName(Locales.component(player, "gui.systemStatus.entry.title", Locales.placeholders("label", entry.label())))
        .lore(lore)
        .build();
  }

  private void handleClick(Window.ClickContext ctx, SystemStatusStore.Entry entry) {
    if (entry.errorCount() <= 0) {
      GuiSounds.click(ctx.player());
      return;
    }
    GuiManager.get().push(ctx.player(), new SystemStatusErrorsMenu(store, entry.id(), entry.label()));
    GuiSounds.click(ctx.player());
  }

  private Button errorReportButton() {
    return new Button(p -> GuiItems.named(Material.REDSTONE_TORCH, GuiI18n.tr(p, "gui.systemStatus.errors.title"), List.of(
        GuiI18n.tr(p, "gui.systemStatus.errors.hint"))), ctx -> {
      GuiManager.get().push(ctx.player(), new SystemStatusErrorsMenu(store, null,
          Locales.text(ctx.player(), "gui.systemStatus.errors.allSystems")));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private static String formatAgo(Player player, long timestampMs) {
    if (timestampMs <= 0L) {
      return Locales.text(player, "gui.common.time.never");
    }
    long delta = Math.max(0L, System.currentTimeMillis() - timestampMs);
    long seconds = delta / 1000L;
    long minutes = seconds / 60L;
    long hours = minutes / 60L;
    long days = hours / 24L;
    if (days > 0) {
      return Locales.text(player, "gui.common.time.daysHours", Locales.placeholders(
          "days", days,
          "hours", hours % 24));
    }
    if (hours > 0) {
      return Locales.text(player, "gui.common.time.hoursMinutes", Locales.placeholders(
          "hours", hours,
          "minutes", minutes % 60));
    }
    if (minutes > 0) {
      return Locales.text(player, "gui.common.time.minutesSeconds", Locales.placeholders(
          "minutes", minutes,
          "seconds", seconds % 60));
    }
    return Locales.text(player, "gui.common.time.seconds", Locales.placeholders(
        "seconds", seconds));
  }
}
