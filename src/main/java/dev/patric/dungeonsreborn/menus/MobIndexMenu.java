package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.mobs.MobSpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class MobIndexMenu extends Window {
  private static final int SIZE = 54;
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

  private record MobEntry(String id, String name, String type, int minLevel) {
  }

  private final MobRegistry mobs;
  private final VirtualList<MobEntry> list;
  private List<MobEntry> cachedEntries = List.of();
  private boolean cacheValid;

  public MobIndexMenu(MobRegistry mobs) {
    super(SIZE, GuiI18n.tr("gui.mobs.index.title"), true);
    this.mobs = Objects.requireNonNull(mobs, "mobs");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        this::entryItem,
        (ctx, entry) -> viewOnly(ctx.player()));
    list.searchKey(entry -> entry.id + " " + entry.name + " " + entry.type);
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
    return new Label(p -> GuiItems.named(Material.SPAWNER,
        GuiI18n.tr(p, "gui.mobs.index.header.title"),
        List.of(GuiI18n.tr(p, "gui.mobs.index.header.hint",
            Placeholder.unparsed("count", String.valueOf(mobs.ids().size()))))));
  }

  private Button refreshButton() {
    return new Button(p -> GuiItems.named(Material.CLOCK,
        GuiI18n.tr(p, "gui.mobs.index.refresh.title"),
        List.of(GuiI18n.tr(p, "gui.mobs.index.refresh.hint"))), ctx -> {
      cacheValid = false;
      list.invalidate(ctx.player());
      list.redraw(ctx.window(), ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private List<MobEntry> entries(Player player) {
    if (cacheValid) {
      return cachedEntries;
    }
    List<MobEntry> out = new ArrayList<>();
    for (String id : mobs.ids()) {
      MobSpec spec = mobs.get(id);
      String name = id;
      String type = "";
      int minLevel = 0;
      if (spec != null) {
        Component display = spec.displayName();
        if (display != null) {
          String plain = PLAIN.serialize(display);
          if (!plain.isBlank()) {
            name = plain;
          }
        }
        if (spec.entityType() != null) {
          type = spec.entityType().name();
        }
        minLevel = spec.minXpLevel();
      }
      out.add(new MobEntry(id, name, type, minLevel));
    }
    out.sort(Comparator.comparing(MobEntry::id, String.CASE_INSENSITIVE_ORDER));
    cachedEntries = out;
    cacheValid = true;
    return cachedEntries;
  }

  private ItemStack entryItem(Player player, MobEntry entry) {
    ItemStack item = spawnEggFor(entry.type);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    meta.displayName(GuiI18n.tr(player, "gui.mobs.index.entry.title",
        Placeholder.unparsed("name", entry.name)));
    List<Component> lore = new ArrayList<>();
    if (!entry.type.isBlank()) {
      lore.add(GuiI18n.tr(player, "gui.mobs.index.entry.type",
          Placeholder.unparsed("type", entry.type)));
    }
    if (entry.minLevel > 0) {
      lore.add(GuiI18n.tr(player, "gui.mobs.index.entry.minLevel",
          Placeholder.unparsed("level", String.valueOf(entry.minLevel))));
    }
    lore.add(GuiI18n.tr(player, "gui.mobs.index.entry.hint"));
    meta.lore(lore);
    item.setItemMeta(meta);
    return item;
  }

  private ItemStack spawnEggFor(String type) {
    if (type != null && !type.isBlank()) {
      try {
        Material egg = Material.valueOf(type + "_SPAWN_EGG");
        return new ItemStack(egg);
      } catch (IllegalArgumentException ignored) {
      }
    }
    return new ItemStack(Material.SPAWNER);
  }

  private void viewOnly(Player player) {
    if (player == null) {
      return;
    }
    player.sendMessage(Locales.component(player, "messages.index.viewOnly"));
    GuiSounds.click(player);
  }
}
