package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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
import dev.patric.dungeonsreborn.shops.ShopTokenTierSpec;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class AdminGiveMenu extends Window {
  private record TokenEntry(String id, ItemStack item) {
  }

  private final ShopYamlRegistry shops;
  private final VirtualList<TokenEntry> list;

  public AdminGiveMenu(ShopYamlRegistry shops) {
    super(54, GuiI18n.tr("gui.adminGive.title"));
    this.shops = Objects.requireNonNull(shops, "shops");
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(1, 1, 4, 7,
        player -> tokenEntries(),
        this::renderEntry,
        this::handleEntryClick);
    this.list.searchKey(entry -> entry == null ? "" : entry.id());
    this.list.apply(this, Placement.FIXED);

    GuiNav.applyList(this, list, new BackButton(), new CloseButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));
    setFixedAt(0, 4, new Label(this::headerItem));
  }

  private List<TokenEntry> tokenEntries() {
    List<TokenEntry> entries = new ArrayList<>();
    ItemStack base = shops.resolveTokenItem("token");
    if (base != null) {
      entries.add(new TokenEntry("token", base));
    }
    for (ShopTokenTierSpec tier : shops.tokenTiers().values()) {
      if (tier == null) {
        continue;
      }
      String id = tier.id();
      ItemStack item = shops.resolveTokenItem(id);
      if (item != null) {
        entries.add(new TokenEntry(id, item));
      }
    }
    entries.sort(Comparator.comparing(entry -> entry.id().toLowerCase(java.util.Locale.ROOT)));
    return entries;
  }

  private ItemStack renderEntry(Player player, TokenEntry entry) {
    if (entry == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    Component title = Component.text(entry.id());
    List<Component> lore = new ArrayList<>();
    lore.add(GuiI18n.tr(player, "gui.adminGive.entry.id", Placeholder.unparsed("id", entry.id())));
    lore.add(GuiI18n.tr(player, "gui.adminGive.entry.giveOne"));
    lore.add(GuiI18n.tr(player, "gui.adminGive.entry.giveStack"));
    ItemStack item = entry.item().clone();
    return GuiItems.named(item, title, lore, true);
  }

  private void handleEntryClick(Window.ClickContext ctx, TokenEntry entry) {
    if (entry == null) {
      return;
    }
    if (!ctx.clickType().isLeftClick()) {
      return;
    }
    int amount = ctx.isShiftClick() ? 64 : 1;
    ItemStack item = entry.item().clone();
    item.setAmount(amount);
    giveToPlayer(ctx.player(), item);
  }

  private ItemStack headerItem(Player player) {
    Component title = GuiI18n.tr(player, "gui.adminGive.header",
        Placeholder.unparsed("count", String.valueOf(tokenEntries().size())));
    return GuiItems.head("ICON_GIVE", title, List.of());
  }

  private static void giveToPlayer(Player player, ItemStack item) {
    var leftovers = player.getInventory().addItem(item);
    if (!leftovers.isEmpty()) {
      for (ItemStack stack : leftovers.values()) {
        player.getWorld().dropItemNaturally(player.getLocation(), stack);
      }
    }
  }
}
