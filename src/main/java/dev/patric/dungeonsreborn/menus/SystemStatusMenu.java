package dev.patric.dungeonsreborn.menus;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.ListSearchBar;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.system.SystemStatusStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class SystemStatusMenu extends Window {
  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
      .withZone(ZoneId.systemDefault());

  private final VirtualList<SystemStatusStore.Entry> list;

  public SystemStatusMenu() {
    super(54, GuiI18n.tr("gui.systemStatus.title"));
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(1, 1, 4, 7,
        player -> entries(),
        this::renderEntry,
        this::handleEntryClick);
    this.list.searchKey(entry -> entry == null ? "" : entry.label());
    this.list.apply(this, Placement.FIXED);

    GuiNav.applyList(this, list, new BackButton(), new CloseButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));
    setFixedAt(0, 4, new Label(this::headerItem));
  }

  private List<SystemStatusStore.Entry> entries() {
    List<SystemStatusStore.Entry> entries = new ArrayList<>(SystemStatusStore.get().entries());
    entries.sort(Comparator.comparing(entry -> entry.label().toLowerCase(java.util.Locale.ROOT)));
    return entries;
  }

  private ItemStack renderEntry(Player player, SystemStatusStore.Entry entry) {
    if (entry == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    String headId = entry.errorCount() > 0 ? "NAV_WARNING" : "NAV_INFO";
    Component title = Component.text(entry.label());
    List<Component> lore = new ArrayList<>();
    if (entry.source() != null && !entry.source().isBlank()) {
      lore.add(GuiI18n.tr(player, "gui.systemStatus.entry.source",
          Placeholder.unparsed("source", entry.source())));
    }
    if (entry.detail() != null && !entry.detail().isBlank()) {
      lore.add(GuiI18n.tr(player, "gui.systemStatus.entry.detail",
          Placeholder.unparsed("detail", entry.detail())));
    }
    if (entry.errorCount() > 0) {
      lore.add(GuiI18n.tr(player, "gui.systemStatus.entry.errors",
          Placeholder.unparsed("count", String.valueOf(entry.errorCount()))));
    }
    lore.add(Component.text(TIME_FORMAT.format(Instant.ofEpochMilli(entry.timestampMs()))));
    return GuiItems.head(headId, title, lore);
  }

  private void handleEntryClick(Window.ClickContext ctx, SystemStatusStore.Entry entry) {
    if (entry == null) {
      return;
    }
    if (entry.errorCount() > 0) {
      ctx.window().openSubWindow(ctx.player(), new SystemStatusErrorsMenu(entry.id(), entry.label()));
    }
  }

  private ItemStack headerItem(Player player) {
    Component title = GuiI18n.tr(player, "gui.systemStatus.header",
        Placeholder.unparsed("count", String.valueOf(entries().size())));
    return GuiItems.head("ICON_STATUS", title, List.of());
  }
}
