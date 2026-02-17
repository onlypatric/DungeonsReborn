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

public final class SystemStatusErrorsMenu extends Window {
  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
      .withZone(ZoneId.systemDefault());

  private final String entryId;
  @SuppressWarnings("unused")
  private final String label;
  private final VirtualList<SystemStatusStore.ErrorEntry> list;

  public SystemStatusErrorsMenu(String entryId, String label) {
    super(54, GuiI18n.tr("gui.systemStatus.errors.title",
        Placeholder.unparsed("label", label == null ? "" : label)));
    this.entryId = entryId;
    this.label = label;
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(1, 1, 4, 7,
        player -> errors(),
        this::renderEntry,
        (ctx, entry) -> {
        });
    this.list.searchKey(entry -> entry == null ? "" : entry.message());
    this.list.apply(this, Placement.FIXED);

    GuiNav.applyList(this, list, new BackButton(), new CloseButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));
    setFixedAt(0, 4, new Label(this::headerItem));
  }

  private List<SystemStatusStore.ErrorEntry> errors() {
    List<SystemStatusStore.ErrorEntry> entries = new ArrayList<>(SystemStatusStore.get().errors(entryId));
    entries.sort(Comparator.comparing(entry -> entry.message().toLowerCase(java.util.Locale.ROOT)));
    return entries;
  }

  private ItemStack renderEntry(Player player, SystemStatusStore.ErrorEntry entry) {
    if (entry == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    List<Component> lore = new ArrayList<>();
    if (entry.source() != null && !entry.source().isBlank()) {
      lore.add(GuiI18n.tr(player, "gui.systemStatus.entry.source",
          Placeholder.unparsed("source", entry.source())));
    }
    lore.add(Component.text(TIME_FORMAT.format(Instant.ofEpochMilli(entry.timestampMs()))));
    return GuiItems.head("NAV_ERROR", Component.text(entry.message()), lore);
  }

  private ItemStack headerItem(Player player) {
    Component title = GuiI18n.tr(player, "gui.systemStatus.errors.header",
        Placeholder.unparsed("count", String.valueOf(errors().size())));
    return GuiItems.head("NAV_ERROR", title, List.of());
  }
}
