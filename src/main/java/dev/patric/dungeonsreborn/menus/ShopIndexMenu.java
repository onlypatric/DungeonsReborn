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
import dev.patric.dungeonsreborn.shops.ShopIngredientSpec;
import dev.patric.dungeonsreborn.shops.ShopMerchantBuilder;
import dev.patric.dungeonsreborn.shops.ShopSessionManager;
import dev.patric.dungeonsreborn.shops.ShopSpec;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ShopIndexMenu extends Window {
  private final ShopYamlRegistry registry;
  private final ShopSessionManager sessions;
  private final VirtualList<ShopSpec> list;
  private boolean debugLogged;

  public static void open(Player player, ShopYamlRegistry registry, ShopSessionManager sessions) {
    Objects.requireNonNull(player, "player");
    GuiManager.get().open(player, new ShopIndexMenu(registry, sessions));
  }

  public ShopIndexMenu(ShopYamlRegistry registry, ShopSessionManager sessions) {
    super(54, GuiI18n.tr("gui.shops.title"));
    this.registry = Objects.requireNonNull(registry, "registry");
    this.sessions = Objects.requireNonNull(sessions, "sessions");
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(1, 1, 4, 7,
        this::visibleShops,
        this::renderShop,
        this::openShopPreview);
    this.list.searchKey(spec -> spec == null ? "" : spec.title());
    this.list.apply(this, Placement.FIXED);

    GuiNav.applyList(this, list, new BackButton(), new CloseButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));
    setFixedAt(0, 4, new Label(this::headerItem));
  }

  private List<ShopSpec> visibleShops(Player player) {
    List<ShopSpec> entries = new ArrayList<>();
    int total = registry.shops().size();
    for (ShopSpec spec : registry.shops().values()) {
      if (spec == null) {
        continue;
      }
      if (sessions.isVisible(player, spec)) {
        entries.add(spec);
      }
    }
    debugLogged = GuiDebug.logIndexOnce(debugLogged, "shops", entries.size(),
        "visible=" + entries.size() + " total=" + total);
    entries.sort(Comparator.comparing(spec -> spec.title().toLowerCase(java.util.Locale.ROOT)));
    return entries;
  }

  private ItemStack renderShop(Player player, ShopSpec spec) {
    if (spec == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    ItemStack icon = resolveIcon(spec.icon());
    String titleText = spec.title() == null || spec.title().isBlank() ? spec.id() : spec.title();
    Component title = Component.text(titleText);
    Component trades = GuiI18n.tr(player, "gui.shops.list.trades",
        Placeholder.unparsed("count", String.valueOf(spec.trades().size())));
    ItemStack item = icon == null ? GuiItems.head("ICON_SHOPS", title, List.of(trades)) : icon;
    return GuiItems.named(item, title, List.of(trades), true);
  }

  private void openShopPreview(Window.ClickContext ctx, ShopSpec spec) {
    if (spec == null) {
      return;
    }
    ctx.window().openSubWindow(ctx.player(), new ShopPreviewMenu(registry, sessions, spec));
  }

  private ItemStack resolveIcon(ShopIngredientSpec ingredient) {
    if (ingredient == null) {
      return null;
    }
    ItemStack resolved = ShopMerchantBuilder.buildIngredient(ingredient, registry.tokenSpec(), registry.itemResolver());
    if (resolved == null) {
      return null;
    }
    ItemMeta meta = resolved.getItemMeta();
    if (meta != null) {
      resolved.setItemMeta(meta);
    }
    return resolved;
  }

  private ItemStack headerItem(Player player) {
    return GuiItems.head("ICON_SHOPS", GuiI18n.tr(player, "gui.shops.header"), List.of());
  }
}
