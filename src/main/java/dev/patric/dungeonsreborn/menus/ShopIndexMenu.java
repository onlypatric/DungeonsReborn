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
import dev.patric.dungeonsreborn.gui.GuiMini;
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
import dev.patric.dungeonsreborn.shops.ShopIngredientSpec;
import dev.patric.dungeonsreborn.shops.ShopIngredientType;
import dev.patric.dungeonsreborn.shops.ShopSpec;
import dev.patric.dungeonsreborn.shops.ShopTokenSpec;
import dev.patric.dungeonsreborn.shops.ShopTradeSpec;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ShopIndexMenu extends Window {
  private static final int SIZE = 54;
  private static final int MAX_TRADE_LINES = 3;

  private record ShopEntry(String id, String title, ShopSpec spec) {
  }

  private final ShopYamlRegistry shops;
  private final VirtualList<ShopEntry> list;
  private List<ShopEntry> cachedEntries = List.of();
  private boolean cacheValid;

  public ShopIndexMenu(ShopYamlRegistry shops) {
    super(SIZE, GuiI18n.tr("gui.shops.index.title"), true);
    this.shops = Objects.requireNonNull(shops, "shops");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        this::entryItem,
        (ctx, entry) -> openPreview(ctx.player(), entry));
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
    return new Label(p -> GuiItems.named(Material.EMERALD,
        GuiI18n.tr(p, "gui.shops.index.header.title"),
        List.of(GuiI18n.tr(p, "gui.shops.index.header.hint",
            Placeholder.unparsed("count", String.valueOf(shops.shops().size()))))));
  }

  private Button refreshButton() {
    return new Button(p -> GuiItems.named(Material.CLOCK,
        GuiI18n.tr(p, "gui.shops.index.refresh.title"),
        List.of(GuiI18n.tr(p, "gui.shops.index.refresh.hint"))), ctx -> {
      cacheValid = false;
      list.invalidate(ctx.player());
      list.redraw(ctx.window(), ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private List<ShopEntry> entries(Player player) {
    if (cacheValid) {
      return cachedEntries;
    }
    List<ShopEntry> out = new ArrayList<>();
    for (Map.Entry<String, ShopSpec> entry : shops.shops().entrySet()) {
      String id = entry.getKey();
      ShopSpec spec = entry.getValue();
      String title = spec == null || spec.title() == null || spec.title().isBlank() ? id : spec.title();
      out.add(new ShopEntry(id, title, spec));
    }
    out.sort(Comparator.comparing(ShopEntry::id, String.CASE_INSENSITIVE_ORDER));
    cachedEntries = out;
    cacheValid = true;
    return cachedEntries;
  }

  private ItemStack entryItem(Player player, ShopEntry entry) {
    ItemStack item = iconFor(entry.spec);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    meta.displayName(GuiI18n.tr(player, "gui.shops.index.entry.title",
        Placeholder.component("title", GuiMini.mm(entry.title))));
    List<Component> lore = new ArrayList<>();
    if (entry.spec != null) {
      lore.add(GuiI18n.tr(player, "gui.shops.index.entry.trades",
          Placeholder.unparsed("count", String.valueOf(entry.spec.trades().size()))));
      int minLevel = minTradeLevel(entry.spec);
      if (minLevel > 0) {
        lore.add(GuiI18n.tr(player, "gui.shops.index.entry.minLevel",
            Placeholder.unparsed("level", String.valueOf(minLevel))));
      }
      lore.addAll(tradePreview(player, entry.spec));
    }
    lore.add(GuiI18n.tr(player, "gui.shops.index.entry.hint"));
    meta.lore(lore);
    item.setItemMeta(meta);
    return item;
  }

  private int minTradeLevel(ShopSpec spec) {
    if (spec == null || spec.trades() == null || spec.trades().isEmpty()) {
      return 0;
    }
    int min = Integer.MAX_VALUE;
    for (ShopTradeSpec trade : spec.trades()) {
      if (trade == null) {
        continue;
      }
      int level = trade.minLevel();
      if (level > 0 && level < min) {
        min = level;
      }
    }
    return min == Integer.MAX_VALUE ? 0 : min;
  }

  private List<Component> tradePreview(Player player, ShopSpec spec) {
    List<Component> out = new ArrayList<>();
    if (spec == null || spec.trades() == null) {
      return out;
    }
    int shown = 0;
    for (ShopTradeSpec trade : spec.trades()) {
      if (trade == null) {
        continue;
      }
      if (shown >= MAX_TRADE_LINES) {
        break;
      }
      String buy = ingredientLabel(trade.buyA());
      if (trade.buyB() != null) {
        buy = buy + " + " + ingredientLabel(trade.buyB());
      }
      String sell = ingredientLabel(trade.sell());
      out.add(GuiI18n.tr(player, "gui.shops.index.entry.trade",
          Placeholder.unparsed("buy", buy),
          Placeholder.unparsed("sell", sell)));
      shown++;
    }
    if (spec.trades().size() > shown) {
      int remaining = spec.trades().size() - shown;
      out.add(GuiI18n.tr(player, "gui.shops.index.entry.more",
          Placeholder.unparsed("count", String.valueOf(remaining))));
    }
    return out;
  }

  private String ingredientLabel(ShopIngredientSpec spec) {
    if (spec == null) {
      return "?";
    }
    String name = "?";
    if (spec.type() == ShopIngredientType.TOKEN) {
      ShopTokenSpec token = shops.tokenSpec();
      ItemStack base = token == null ? null : token.item();
      name = stackName(base, "Token");
    } else if (spec.type() == ShopIngredientType.ITEM_ID) {
      name = spec.itemId();
      ItemStack base = shops.itemResolver().apply(spec.itemId());
      name = stackName(base, name);
    } else if (spec.type() == ShopIngredientType.MATERIAL) {
      name = spec.material() == null ? "Material" : spec.material().name();
    } else if (spec.type() == ShopIngredientType.ITEMSTACK) {
      name = stackName(spec.item(), "Item");
    }
    return spec.amount() + "x " + name;
  }

  private static String stackName(ItemStack stack, String fallback) {
    if (stack == null) {
      return fallback;
    }
    ItemMeta meta = stack.getItemMeta();
    if (meta != null && meta.hasDisplayName()) {
      Component display = meta.displayName();
      if (display != null) {
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(display);
        if (!plain.isBlank()) {
          return plain;
        }
      }
    }
    return stack.getType() == null ? fallback : stack.getType().name();
  }

  private ItemStack iconFor(ShopSpec spec) {
    if (spec != null && spec.icon() != null) {
      ItemStack resolved = spec.icon().resolve(shops.itemResolver(), shops.tokenSpec());
      if (resolved != null && !resolved.getType().isAir()) {
        return resolved.clone();
      }
    }
    return new ItemStack(Material.EMERALD);
  }

  private void viewOnly(Player player) {
    if (player == null) {
      return;
    }
    player.sendMessage(Locales.component(player, "messages.index.viewOnly"));
    GuiSounds.click(player);
  }

  private void openPreview(Player player, ShopEntry entry) {
    if (player == null || entry == null) {
      return;
    }
    if (entry.spec == null) {
      viewOnly(player);
      return;
    }
    new ShopPreviewMenu(shops, entry.id, entry.title, entry.spec).open(player);
  }
}
