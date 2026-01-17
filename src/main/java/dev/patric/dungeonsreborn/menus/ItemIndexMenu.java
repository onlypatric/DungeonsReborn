package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ItemIndexMenu extends Window {
  private static final int SIZE = 54;

  private record ItemEntry(String id, ItemStack item, String name) {
  }

  private final EffectsYamlAbilities effects;
  private final VirtualList<ItemEntry> list;
  private List<ItemEntry> cachedEntries = List.of();
  private boolean cacheValid;

  public ItemIndexMenu(EffectsYamlAbilities effects) {
    super(SIZE, GuiI18n.tr("gui.items.index.title"), true);
    this.effects = Objects.requireNonNull(effects, "effects");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        this::entryItem,
        (ctx, entry) -> viewOnly(ctx.player()));
    list.searchKey(entry -> entry.id + " " + entry.name);
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
    return new Label(p -> GuiItems.named(Material.CHEST,
        GuiI18n.tr(p, "gui.items.index.header.title"),
        List.of(GuiI18n.tr(p, "gui.items.index.header.hint",
            Placeholder.unparsed("count", String.valueOf(effects.loadedItemIds().size()))))));
  }

  private Button refreshButton() {
    return new Button(p -> GuiItems.named(Material.CLOCK,
        GuiI18n.tr(p, "gui.items.index.refresh.title"),
        List.of(GuiI18n.tr(p, "gui.items.index.refresh.hint"))), ctx -> {
      cacheValid = false;
      list.invalidate(ctx.player());
      list.redraw(ctx.window(), ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private List<ItemEntry> entries(Player player) {
    if (cacheValid) {
      return cachedEntries;
    }
    List<ItemEntry> out = new ArrayList<>();
    for (String id : effects.loadedItemIds()) {
      ItemStack item = effects.itemTemplate(id);
      if (item == null) {
        continue;
      }
      String name = id;
      ItemMeta meta = item.getItemMeta();
      if (meta != null && meta.hasDisplayName()) {
        Component display = meta.displayName();
        if (display != null) {
          name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(display);
        }
      } else if (item.getType() != null && item.getType() != Material.AIR) {
        name = item.getType().name();
      }
      out.add(new ItemEntry(id, item, name));
    }
    out.sort(Comparator.comparing(ItemEntry::id, String.CASE_INSENSITIVE_ORDER));
    cachedEntries = out;
    cacheValid = true;
    return cachedEntries;
  }

  private ItemStack entryItem(Player player, ItemEntry entry) {
    ItemStack item = entry.item.clone();
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    List<Component> lore = meta.lore();
    if (lore == null) {
      lore = new ArrayList<>();
    } else {
      lore = new ArrayList<>(lore);
    }
    lore.add(Component.empty());
    lore.add(GuiI18n.tr(player, "gui.items.index.entry.hint"));
    meta.lore(lore);
    item.setItemMeta(meta);
    return item;
  }

  private void viewOnly(Player player) {
    if (player == null) {
      return;
    }
    player.sendMessage(Locales.component(player, "messages.index.viewOnly"));
    GuiSounds.click(player);
  }
}
