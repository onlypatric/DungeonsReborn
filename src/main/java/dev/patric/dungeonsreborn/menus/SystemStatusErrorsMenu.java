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
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.EmptyState;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.ListSearchBar;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.system.SystemStatusStore;
import net.kyori.adventure.text.Component;

public final class SystemStatusErrorsMenu extends Window {
  private static final int SIZE = 54;

  private final SystemStatusStore store;
  private final String filterId;
  private final VirtualList<SystemStatusStore.ErrorEntry> list;
  public SystemStatusErrorsMenu(SystemStatusStore store, String filterId, String title) {
    super(SIZE, Locales.component(null, "gui.systemStatus.errors.windowTitle", Locales.placeholders("title", title)), true);
    this.store = Objects.requireNonNull(store, "store");
    this.filterId = filterId;

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(player, entry),
        (ctx, entry) -> GuiSounds.click(ctx.player()));
    list.searchKey(entry -> entry.label() + " " + entry.message());
    list.emptyStateItem(EmptyState.list());
    list.apply(this, Placement.FIXED);

    navLeft(GuiNav.backButton());
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));

    setFixedAt(0, 4, new Label(GuiItems.named(Material.BARRIER, GuiI18n.tr("gui.systemStatus.errors.header.title"), List.of(
        GuiI18n.tr("gui.systemStatus.errors.header.hint")))));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private List<SystemStatusStore.ErrorEntry> entries(Player player) {
    List<SystemStatusStore.ErrorEntry> entries = new ArrayList<>(store.errors(filterId));
    entries.sort(Comparator.comparing(SystemStatusStore.ErrorEntry::label, String.CASE_INSENSITIVE_ORDER));
    return entries;
  }

  private ItemStack entryItem(Player player, SystemStatusStore.ErrorEntry entry) {
    List<Component> lore = new ArrayList<>();
    lore.add(Locales.component(player, "gui.common.line.subsystem", Locales.placeholders("value", entry.label())));
    if (!entry.source().isBlank()) {
      lore.add(Locales.component(player, "gui.common.line.source", Locales.placeholders("value", entry.source())));
    }
    lore.add(Locales.component(player, "gui.common.line.lastReload",
        Locales.placeholders("value", formatAgo(player, entry.timestampMs()))));
    lore.add(Locales.component(player, "gui.systemStatus.errors.entry.message",
        Locales.placeholders("message", entry.message())));
    return GuiItem.of(new ItemStack(Material.PAPER))
        .displayName(GuiI18n.tr(player, "gui.systemStatus.errors.entry.title"))
        .lore(lore)
        .build();
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
