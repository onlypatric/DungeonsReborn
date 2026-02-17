package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;
import dev.patric.dungeonsreborn.effects.items.ItemTemplateSnapshot;
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ItemIndexMenu extends Window {
  private final EffectsYamlAbilities items;
  private final VirtualList<String> list;
  private final boolean allowGive;
  private boolean debugLogged;

  public static void open(Player player, EffectsYamlAbilities items) {
    Objects.requireNonNull(player, "player");
    GuiManager.get().open(player, new ItemIndexMenu(items, false));
  }

  public static void openAdmin(Player player, EffectsYamlAbilities items) {
    Objects.requireNonNull(player, "player");
    GuiManager.get().open(player, new ItemIndexMenu(items, true));
  }

  public ItemIndexMenu(EffectsYamlAbilities items) {
    this(items, false);
  }

  public ItemIndexMenu(EffectsYamlAbilities items, boolean allowGive) {
    super(54, GuiI18n.tr("gui.items.title"));
    this.items = Objects.requireNonNull(items, "items");
    this.allowGive = allowGive;
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(1, 1, 4, 7,
        player -> itemEntries(),
        this::renderEntry,
        this::handleEntryClick);
    this.list.searchKey(this::itemSearchKey);
    this.list.apply(this, Placement.FIXED);

    GuiNav.applyList(this, list, new BackButton(), new CloseButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));
    setFixedAt(0, 4, new Label(this::headerItem));
  }

  private List<String> itemEntries() {
    List<String> ids = new ArrayList<>(items.loadedItemIds());
    debugLogged = GuiDebug.logIndexOnce(debugLogged, "items", ids.size());
    ids.sort(Comparator.comparing(id -> itemTitleKey(id).toLowerCase(java.util.Locale.ROOT)));
    return ids;
  }

  private ItemStack renderEntry(Player player, String id) {
    if (id == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    ItemStack item = items.itemTemplate(id);
    Component title = titleFromItem(item, id);
    List<Component> lore = new ArrayList<>();
    ItemTemplateSnapshot snapshot = items.itemTemplateSnapshot(id);
    if (snapshot != null && snapshot.rarityId() != null) {
      lore.add(GuiI18n.tr(player, "gui.items.entry.rarity",
          Placeholder.unparsed("rarity", snapshot.rarityId())));
    }
    if (snapshot != null && snapshot.baseStats() != null && !snapshot.baseStats().isEmpty()) {
      lore.add(GuiI18n.tr(player, "gui.items.entry.stats",
          Placeholder.unparsed("count", String.valueOf(snapshot.baseStats().values().size()))));
    }
    if (snapshot != null && snapshot.affixPool() != null) {
      lore.add(GuiI18n.tr(player, "gui.items.entry.affixes"));
    }
    if (allowGive && player.hasPermission("dungeonsreborn.items.give")) {
      lore.add(GuiI18n.tr(player, "gui.items.entry.giveOne"));
      lore.add(GuiI18n.tr(player, "gui.items.entry.giveStack"));
      lore.add(GuiI18n.tr(player, "gui.items.entry.inspectHint"));
    }
    if (item == null) {
      return GuiItems.head("ICON_ITEMS", title, lore);
    }
    return GuiItems.named(item, title, lore, true);
  }

  private String itemSearchKey(String id) {
    if (id == null) {
      return "";
    }
    ItemStack item = items.itemTemplate(id);
    Component title = titleFromItem(item, id);
    return PlainTextComponentSerializer.plainText().serialize(title);
  }

  private String itemTitleKey(String id) {
    if (id == null) {
      return "";
    }
    ItemStack item = items.itemTemplate(id);
    Component title = titleFromItem(item, id);
    return PlainTextComponentSerializer.plainText().serialize(title);
  }

  private static Component titleFromItem(ItemStack item, String fallback) {
    if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
      Component display = item.getItemMeta().displayName();
      if (display != null) {
        return display;
      }
    }
    return Component.text(fallback);
  }

  private void handleEntryClick(Window.ClickContext ctx, String id) {
    if (id == null) {
      return;
    }
    if (allowGive && ctx.player().hasPermission("dungeonsreborn.items.give")) {
      if (ctx.clickType().isLeftClick()) {
        int amount = ctx.isShiftClick() ? 64 : 1;
        ItemStack item = items.itemTemplate(id);
        if (item != null) {
          item.setAmount(amount);
          var leftovers = ctx.player().getInventory().addItem(item);
          if (!leftovers.isEmpty()) {
            for (ItemStack stack : leftovers.values()) {
              ctx.player().getWorld().dropItemNaturally(ctx.player().getLocation(), stack);
            }
          }
        }
        return;
      }
    }
    ctx.window().openSubWindow(ctx.player(), new ItemInspectMenu(items, id));
  }

  private ItemStack headerItem(Player player) {
    Component title = GuiI18n.tr(player, "gui.items.header",
        Placeholder.unparsed("count", String.valueOf(items.loadedItemIds().size())));
    return GuiItems.head("ICON_ITEMS", title, List.of());
  }
}
