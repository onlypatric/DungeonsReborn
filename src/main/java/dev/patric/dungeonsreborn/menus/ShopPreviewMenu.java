package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.ListSearchBar;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.shops.ShopIngredientSpec;
import dev.patric.dungeonsreborn.shops.ShopMerchantBuilder;
import dev.patric.dungeonsreborn.shops.ShopSessionManager;
import dev.patric.dungeonsreborn.shops.ShopSpec;
import dev.patric.dungeonsreborn.shops.ShopTradeSpec;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ShopPreviewMenu extends Window {
  private static final int LIST_TOP_ROW = 1;
  private static final int LIST_LEFT_COL = 4;
  private static final int LIST_ROWS = 4;
  private static final int LIST_COLS = 1;

  private record TradeEntry(ShopTradeSpec trade, int index) {
  }

  private final ShopYamlRegistry registry;
  private final ShopSessionManager sessions;
  private final ShopSpec shop;
  private final VirtualList<TradeEntry> list;

  public static void open(Player player, ShopYamlRegistry registry, ShopSessionManager sessions, ShopSpec shop) {
    Objects.requireNonNull(player, "player");
    GuiManager.get().open(player, new ShopPreviewMenu(registry, sessions, shop));
  }

  public ShopPreviewMenu(ShopYamlRegistry registry, ShopSessionManager sessions, ShopSpec shop) {
    super(54, GuiI18n.tr("gui.shops.preview.title",
        Placeholder.unparsed("shop", shop == null ? "" : shop.title())));
    this.registry = Objects.requireNonNull(registry, "registry");
    this.sessions = Objects.requireNonNull(sessions, "sessions");
    this.shop = Objects.requireNonNull(shop, "shop");
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(LIST_TOP_ROW, LIST_LEFT_COL, LIST_ROWS, LIST_COLS,
        player -> tradeEntries(),
        this::renderTrade,
        this::openTrade);
    this.list.searchKey(entry -> entry == null ? "" : tradeSearchLabel(entry.trade()));
    this.list.apply(this, Placement.FIXED);

    GuiNav.applyDetail(this, new BackButton(), new CloseButton());
    nav(0, prevButton());
    nav(1, list.pageIndicator());
    nav(2, nextButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));
    nav(6, favoriteButton());
    setFixedAt(0, 4, new Label(this::headerItem));
  }

  @Override
  protected void build(Player player) {
    super.build(player);
    for (int row = 0; row < LIST_ROWS; row++) {
      final int rowIndex = row;
      setDynamicAt(LIST_TOP_ROW + row, 0, new Label(p -> rowStatusPane(p, rowIndex)));
      setDynamicAt(LIST_TOP_ROW + row, 8, new Label(p -> rowStatusPane(p, rowIndex)));
      setDynamicAt(LIST_TOP_ROW + row, 1, new Label(p -> costSlot(p, rowIndex, 0)));
      setDynamicAt(LIST_TOP_ROW + row, 2, new Label(p -> costSlot(p, rowIndex, 1)));
      setDynamicAt(LIST_TOP_ROW + row, 3, new Label(p -> costSlot(p, rowIndex, 2)));
      setDynamicAt(LIST_TOP_ROW + row, 5, new Label(p -> resultSlot(p, rowIndex, 0)));
      setDynamicAt(LIST_TOP_ROW + row, 6, new Label(p -> resultSlot(p, rowIndex, 1)));
      setDynamicAt(LIST_TOP_ROW + row, 7, new Label(p -> resultSlot(p, rowIndex, 2)));
    }
  }

  private List<TradeEntry> tradeEntries() {
    List<TradeEntry> entries = new ArrayList<>();
    List<ShopTradeSpec> trades = shop.trades();
    for (int i = 0; i < trades.size(); i++) {
      ShopTradeSpec trade = trades.get(i);
      if (trade == null) {
        continue;
      }
      entries.add(new TradeEntry(trade, i));
    }
    return entries;
  }

  private ItemStack renderTrade(Player player, TradeEntry entry) {
    if (entry == null || entry.trade() == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    ShopTradeSpec trade = entry.trade();
    Component title = GuiI18n.tr(player, "gui.shops.trade.buy.title");
    List<Component> lore = new ArrayList<>();
    lore.add(GuiI18n.tr(player, "gui.shops.trade.buy.desc"));
    TradeAvailability availability = tradeAvailability(player, trade);
    lore.add(GuiMini.mm("<dark_gray> </dark_gray>"));
    switch (availability) {
      case FULL -> lore.add(GuiI18n.tr(player, "gui.shops.trade.status.ready"));
      case PARTIAL -> lore.add(GuiI18n.tr(player, "gui.shops.trade.status.partial"));
      case NONE -> lore.add(GuiI18n.tr(player, "gui.shops.trade.status.missing"));
    }
    return GuiButtons.item(GuiButtons.Type.CONFIRM, title, lore);
  }

  private void openTrade(Window.ClickContext ctx, TradeEntry entry) {
    if (entry == null || entry.trade() == null) {
      return;
    }
    Player player = ctx.player();
    ShopTradeSpec trade = entry.trade();
    TradeAvailability availability = tradeAvailability(player, trade);
    if (availability != TradeAvailability.FULL) {
      player.sendMessage(Locales.component(player, "messages.shops.trade.missingItems"));
      return;
    }
    if (!consumeCosts(player, trade)) {
      player.sendMessage(Locales.component(player, "messages.shops.trade.missingItems"));
      return;
    }
    if (!grantResults(player, trade)) {
      player.sendMessage(Locales.component(player, "messages.shops.trade.invalid"));
      return;
    }
    player.sendMessage(Locales.component(player, "messages.shops.trade.ok"));
    ctx.window().redraw(player);
  }

  private ItemStack rowStatusPane(Player player, int rowIndex) {
    TradeAvailability availability = rowAvailability(player, rowIndex);
    Material material = switch (availability) {
      case FULL -> Material.GREEN_STAINED_GLASS_PANE;
      case PARTIAL -> Material.YELLOW_STAINED_GLASS_PANE;
      case NONE -> Material.RED_STAINED_GLASS_PANE;
    };
    return GuiItems.blankPane(material);
  }

  private TradeAvailability rowAvailability(Player player, int rowIndex) {
    TradeAvailability best = TradeAvailability.NONE;
    TradeEntry entry = list.visibleEntry(player, rowIndex);
    if (entry != null && entry.trade() != null) {
      TradeAvailability availability = tradeAvailability(player, entry.trade());
      if (availability == TradeAvailability.FULL) {
        return TradeAvailability.FULL;
      }
      if (availability == TradeAvailability.PARTIAL) {
        best = TradeAvailability.PARTIAL;
      }
    }
    return best;
  }

  private TradeAvailability tradeAvailability(Player player, ShopTradeSpec trade) {
    if (trade == null) {
      return TradeAvailability.NONE;
    }
    boolean any = false;
    for (ShopIngredientSpec ingredient : trade.buys()) {
      if (ingredient == null) {
        continue;
      }
      if (ingredient.type() == dev.patric.dungeonsreborn.shops.ShopIngredientType.XP
          || ingredient.type() == dev.patric.dungeonsreborn.shops.ShopIngredientType.CUSTOM_XP) {
        return TradeAvailability.NONE;
      }
      int have = countMatching(player, ingredient);
      if (have >= ingredient.amount()) {
        any = true;
        continue;
      }
      if (have > 0) {
        any = true;
      }
      return any ? TradeAvailability.PARTIAL : TradeAvailability.NONE;
    }
    return any ? TradeAvailability.FULL : TradeAvailability.NONE;
  }

  private int countMatching(Player player, ShopIngredientSpec ingredient) {
    if (player == null || ingredient == null) {
      return 0;
    }
    int total = 0;
    ItemStack[] contents = player.getInventory().getContents();
    for (ItemStack stack : contents) {
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      if (ingredient.matches(stack, registry.itemResolver(), registry.tokenSpec())) {
        total += stack.getAmount();
      }
    }
    return total;
  }

  private enum TradeAvailability {
    NONE,
    PARTIAL,
    FULL
  }

  private boolean consumeCosts(Player player, ShopTradeSpec trade) {
    for (ShopIngredientSpec ingredient : trade.buys()) {
      if (ingredient == null) {
        continue;
      }
      if (!consumeIngredient(player, ingredient)) {
        return false;
      }
    }
    return true;
  }

  private boolean consumeIngredient(Player player, ShopIngredientSpec ingredient) {
    if (ingredient.type() == dev.patric.dungeonsreborn.shops.ShopIngredientType.XP
        || ingredient.type() == dev.patric.dungeonsreborn.shops.ShopIngredientType.CUSTOM_XP) {
      return false;
    }
    int remaining = ingredient.amount();
    ItemStack[] contents = player.getInventory().getContents();
    for (int i = 0; i < contents.length; i++) {
      ItemStack stack = contents[i];
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      if (!ingredient.matches(stack, registry.itemResolver(), registry.tokenSpec())) {
        continue;
      }
      int take = Math.min(remaining, stack.getAmount());
      stack.setAmount(stack.getAmount() - take);
      remaining -= take;
      if (stack.getAmount() <= 0) {
        contents[i] = null;
      }
      if (remaining <= 0) {
        break;
      }
    }
    player.getInventory().setContents(contents);
    return remaining <= 0;
  }

  private boolean grantResults(Player player, ShopTradeSpec trade) {
    for (ShopIngredientSpec ingredient : trade.sells()) {
      if (ingredient == null) {
        continue;
      }
      ItemStack stack = resolveIngredient(ingredient);
      if (stack == null) {
        return false;
      }
      var leftovers = player.getInventory().addItem(stack);
      if (!leftovers.isEmpty()) {
        for (ItemStack leftover : leftovers.values()) {
          player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
      }
    }
    return true;
  }

  private Button favoriteButton() {
    Button button = new Button(player -> {
      boolean favorite = sessions.isFavoriteShop(player, shop.id());
      GuiButtons.Type type = favorite ? GuiButtons.Type.FILTER_ON : GuiButtons.Type.FILTER_OFF;
      Component status = favorite ? GuiI18n.tr(player, "gui.shops.favorite.on") : GuiI18n.tr(player, "gui.shops.favorite.off");
      return GuiButtons.item(type, GuiI18n.tr(player, "gui.shops.favorite.title"), List.of(status));
    });
    button.left(GuiI18n.tr("gui.controls.action"), ctx -> {
      sessions.toggleFavoriteShop(ctx.player(), shop.id());
      ctx.window().redraw(ctx.player());
    });
    button.autoDescribeInLore(false);
    return button;
  }

  private Button prevButton() {
    Button button = new Button(player -> GuiButtons.item(
        GuiButtons.Type.PREV,
        GuiI18n.tr(player, "gui.list.prev.title"),
        List.of(GuiI18n.tr(player, "gui.list.page",
            Placeholder.unparsed("current", String.valueOf(list.page(player) + 1)),
            Placeholder.unparsed("total", String.valueOf(Math.max(1, list.page(player) + 1)))))));
    button.left(GuiI18n.tr("gui.list.prev.action"), ctx -> {
      list.page(ctx.player(), list.page(ctx.player()) - 1);
      ctx.window().redraw(ctx.player());
    });
    button.autoDescribeInLore(false);
    return button;
  }

  private Button nextButton() {
    Button button = new Button(player -> GuiButtons.item(
        GuiButtons.Type.NEXT,
        GuiI18n.tr(player, "gui.list.next.title"),
        List.of(GuiI18n.tr(player, "gui.list.page",
            Placeholder.unparsed("current", String.valueOf(list.page(player) + 1)),
            Placeholder.unparsed("total", String.valueOf(Math.max(1, list.page(player) + 1)))))));
    button.left(GuiI18n.tr("gui.list.next.action"), ctx -> {
      list.page(ctx.player(), list.page(ctx.player()) + 1);
      ctx.window().redraw(ctx.player());
    });
    button.autoDescribeInLore(false);
    return button;
  }

  private ItemStack headerItem(Player player) {
    Component title = GuiI18n.tr(player, "gui.shops.preview.header",
        Placeholder.unparsed("shop", shop.title()));
    Component trades = GuiI18n.tr(player, "gui.shops.list.trades",
        Placeholder.unparsed("count", String.valueOf(shop.trades().size())));
    return GuiItems.head("ICON_SHOPS", title, List.of(trades));
  }

  private ItemStack costSlot(Player player, int rowIndex, int slotIndex) {
    TradeEntry entry = list.visibleEntry(player, rowIndex);
    if (entry == null || entry.trade() == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    List<ShopIngredientSpec> buys = entry.trade().buys();
    if (slotIndex >= buys.size()) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    ItemStack stack = resolveIngredient(buys.get(slotIndex));
    if (stack == null) {
      return GuiItems.named(Material.BARRIER, GuiI18n.tr(player, "gui.shops.trade.costItem"), List.of());
    }
    return stack;
  }

  private ItemStack resultSlot(Player player, int rowIndex, int slotIndex) {
    TradeEntry entry = list.visibleEntry(player, rowIndex);
    if (entry == null || entry.trade() == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    List<ShopIngredientSpec> sells = entry.trade().sells();
    if (slotIndex >= sells.size()) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    ItemStack stack = resolveIngredient(sells.get(slotIndex));
    if (stack == null) {
      return GuiItems.head("ICON_SHOPS", GuiI18n.tr(player, "gui.shops.trade.reward"), List.of());
    }
    return stack;
  }

  private String tradeSearchLabel(ShopTradeSpec trade) {
    if (trade == null) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    for (ShopIngredientSpec ingredient : trade.buys()) {
      if (ingredient == null) {
        continue;
      }
      builder.append(ingredient.displayLabel(registry.itemResolver(), registry.tokenSpec())).append(' ');
    }
    for (ShopIngredientSpec ingredient : trade.sells()) {
      if (ingredient == null) {
        continue;
      }
      builder.append(ingredient.displayLabel(registry.itemResolver(), registry.tokenSpec())).append(' ');
    }
    return builder.toString().trim();
  }

  private ItemStack resolveIngredient(ShopIngredientSpec ingredient) {
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
}
