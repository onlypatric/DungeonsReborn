package dev.patric.dungeonsreborn.menus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.admin.AdminAuditStore;
import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.components.storage.StorageArea;
import dev.patric.dungeonsreborn.gui.components.storage.StorageSlot;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.shops.ShopEditorDraft;
import dev.patric.dungeonsreborn.shops.ShopEditorStore;
import dev.patric.dungeonsreborn.shops.ShopTokenSpec;
import dev.patric.dungeonsreborn.shops.ShopTokenTierSpec;
import dev.patric.dungeonsreborn.shops.ShopTradeDraft;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import net.kyori.adventure.text.Component;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ShopEditorDetailMenu extends Window {
  private static final int SIZE = 54;
  private static final int SLOT_TITLE = 4;
  private static final int SLOT_SAVE = 49;
  private static final int SLOT_ADD_TRADE = 50;

  private record TradeEntry(int index, ShopTradeDraft trade) {
  }

  private final ShopYamlRegistry registry;
  private final ShopEditorStore store;
  private final ShopEditorDraft draft;
  private final ShopEditorListMenu parent;
  private final StorageArea iconSlot = new StorageArea(1, 4, 1, 1);
  private final VirtualList<TradeEntry> tradeList;

  public ShopEditorDetailMenu(ShopYamlRegistry registry, ShopEditorStore store, ShopEditorDraft draft,
      ShopEditorListMenu parent) {
    super(SIZE, GuiI18n.tr("gui.shop.editor.detail.title"), true);
    this.registry = Objects.requireNonNull(registry, "registry");
    this.store = Objects.requireNonNull(store, "store");
    this.draft = Objects.requireNonNull(draft, "draft");
    this.parent = Objects.requireNonNull(parent, "parent");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, GuiI18n.tr(p, "gui.button.back"))));
    navRight(new Button(this::saveButtonItem, ctx -> saveDraft(ctx.player())).autoDescribeInLore(false));

    setFixed(SLOT_TITLE, new Label(GuiItems.named(Material.EMERALD, GuiI18n.tr("gui.shop.editor.detail.header.title"), List.of(
        GuiI18n.tr("gui.shop.editor.detail.header.hint")))));

    configureIconSlot();
    iconSlot.apply(this, Placement.FIXED);

    setFixedAt(1, 0, enabledButton());
    setFixedAt(1, 1, shopIdButton());
    setFixedAt(1, 3, titleButton());
    setFixedAt(1, 5, permissionButton());
    setFixedAt(1, 7, cooldownButton());

    setFixedAt(2, 1, worldsButton());
    setFixedAt(2, 3, stockMinButton());
    setFixedAt(2, 5, stockMaxButton());
    setFixedAt(2, 7, restockButton());
    setFixedAt(2, 0, new Label(p -> auditItem()));

    setFixedAt(3, 0, new Label(GuiItems.named(Material.BOOK, GuiI18n.tr("gui.shop.editor.detail.trades.title"), List.of(
        GuiI18n.tr("gui.shop.editor.detail.trades.hint1"),
        GuiI18n.tr("gui.shop.editor.detail.trades.hint2")))));

    this.tradeList = new VirtualList<>(
        3, 1, 3, 7,
        this::trades,
        (player, entry) -> tradeItem(entry),
        (ctx, entry) -> tradeClick(ctx, entry));
    tradeList.apply(this, Placement.FIXED);
    nav(0, tradeList.prevButton());
    nav(1, tradeList.pageIndicator());
    nav(2, tradeList.nextButton());

    setFixed(SLOT_SAVE, new Button(this::saveButtonItem, ctx -> saveDraft(ctx.player()))
        .autoDescribeInLore(false));
    setFixed(SLOT_ADD_TRADE, new Button(this::addTradeItem, ctx -> addTrade(ctx.player()))
        .autoDescribeInLore(false));

    onOpenWithReason(ctx -> {
      iconSlot.set(ctx.player(), 0, draft.icon());
      GuiSounds.open(ctx.player());
    });
    onCloseWithReason(ctx -> {
      returnItems(ctx.player());
      GuiSounds.close(ctx.player());
    });
  }

  private Button enabledButton() {
    return new Button(p -> {
      boolean enabled = draft.enabled();
      Material material = enabled ? Material.LIME_DYE : Material.RED_DYE;
      String state = Locales.text(p, enabled ? "gui.common.status.enabled" : "gui.common.status.disabled");
      return GuiItems.named(material, GuiI18n.tr(p, "gui.shop.editor.detail.enabled.title"), List.of(
          Locales.component(p, "gui.common.line.status", Locales.placeholders("value", state)),
          GuiI18n.tr(p, "gui.shop.editor.detail.enabled.hint")));
    }, ctx -> {
      draft.enabled(!draft.enabled());
      ctx.window().redrawSlot(ctx.player(), slotAt(1, 0));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private void configureIconSlot() {
    StorageSlot slot = iconSlot.slot(0);
    slot.vanilla(true).accepts(item -> item != null && !item.getType().isAir());
    iconSlot.onChange((player, index, stack) -> draft.icon(stack));
  }

  private Button shopIdButton() {
    return new TextButton(
        p -> GuiItems.named(Material.NAME_TAG, GuiI18n.tr(p, "gui.shop.editor.detail.id.title"), List.of(
            Locales.component(p, "gui.shop.editor.detail.id.value", Locales.placeholders("value", safe(draft.id()))),
            GuiI18n.tr(p, "gui.shop.editor.detail.id.hint"))),
        GuiI18n.tr("gui.shop.editor.detail.id.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (window, text) -> {
          String normalized;
          try {
            normalized = Ids.normalize(text);
          } catch (IllegalArgumentException ex) {
            Player player = viewerPlayer(window);
            if (player != null) {
              player.sendMessage(Locales.component(player, "messages.common.invalidId",
                  Locales.placeholders("id", ex.getMessage())));
            }
            return;
          }
          draft.id(normalized);
          Player viewer = viewerPlayer(window);
          if (viewer != null) {
            redraw(viewer);
          }
        },
        true);
  }

  private Button titleButton() {
    return new TextButton(
        p -> GuiItems.named(Material.PAPER, GuiI18n.tr(p, "gui.shop.editor.detail.title.title"), List.of(
            Locales.component(p, "gui.shop.editor.detail.title.value", Locales.placeholders("value", safe(draft.title()))),
            GuiI18n.tr(p, "gui.shop.editor.detail.title.hint"))),
        GuiI18n.tr("gui.shop.editor.detail.title.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (window, text) -> {
          draft.title(text.trim());
          Player viewer = viewerPlayer(window);
          if (viewer != null) {
            redraw(viewer);
          }
        },
        true);
  }

  private Button permissionButton() {
    return new TextButton(
        p -> GuiItems.named(Material.IRON_DOOR, GuiI18n.tr(p, "gui.shop.editor.detail.permission.title"), List.of(
            Locales.component(p, "gui.shop.editor.detail.permission.value", Locales.placeholders("value", safe(draft.permission()))),
            GuiI18n.tr(p, "gui.shop.editor.detail.permission.hint"))),
        GuiI18n.tr("gui.shop.editor.detail.permission.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (window, text) -> {
          String value = text.trim();
          draft.permission(value.isBlank() ? null : value);
          Player viewer = viewerPlayer(window);
          if (viewer != null) {
            redraw(viewer);
          }
        },
        true);
  }

  private Button cooldownButton() {
    TextButton button = new TextButton(
        p -> GuiItems.named(Material.CLOCK, GuiI18n.tr(p, "gui.shop.editor.detail.cooldown.title"), List.of(
            Locales.component(p, "gui.shop.editor.detail.cooldown.value",
                Locales.placeholders("value", formatSeconds(draft.cooldownSeconds()))),
            GuiI18n.tr(p, "gui.shop.editor.detail.cooldown.hint"))),
        GuiI18n.tr("gui.shop.editor.detail.cooldown.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (window, text) -> {
          draft.cooldownSeconds(parseDouble(text, 0.0));
          Player viewer = viewerPlayer(window);
          if (viewer != null) {
            redraw(viewer);
          }
        },
        true);
    button.validate((w, p, input) -> isDouble(input) ? null : GuiI18n.tr(p, "gui.validation.number"));
    return button;
  }

  private Button worldsButton() {
    return new TextButton(
        p -> GuiItems.named(Material.MAP, GuiI18n.tr(p, "gui.shop.editor.detail.worlds.title"), List.of(
            Locales.component(p, "gui.shop.editor.detail.worlds.value", Locales.placeholders("value", worldsLabel())),
            GuiI18n.tr(p, "gui.shop.editor.detail.worlds.hint"))),
        GuiI18n.tr("gui.shop.editor.detail.worlds.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (window, text) -> {
          draft.worlds().clear();
          String raw = text.trim();
          if (!raw.isBlank()) {
            for (String part : raw.split(",")) {
              String world = part.trim();
              if (!world.isBlank()) {
                draft.worlds().add(world);
              }
            }
          }
          Player viewer = viewerPlayer(window);
          if (viewer != null) {
            redraw(viewer);
          }
        },
        true);
  }

  private Button stockMinButton() {
    TextButton button = new TextButton(
        p -> GuiItems.named(Material.CHEST, GuiI18n.tr(p, "gui.shop.editor.detail.stockMin.title"), List.of(
            Locales.component(p, "gui.shop.editor.detail.stockMin.value", Locales.placeholders("value", formatInt(draft.stockMin()))),
            GuiI18n.tr(p, "gui.shop.editor.detail.stockMin.hint"))),
        GuiI18n.tr("gui.shop.editor.detail.stockMin.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (window, text) -> {
          Integer value = parseInt(text);
          draft.stockMin(value);
          Player viewer = viewerPlayer(window);
          if (viewer != null) {
            redraw(viewer);
          }
        },
        true);
    button.validate((w, p, input) -> isInt(input) ? null : GuiI18n.tr(p, "gui.validation.integer"));
    return button;
  }

  private Button stockMaxButton() {
    TextButton button = new TextButton(
        p -> GuiItems.named(Material.BARREL, GuiI18n.tr(p, "gui.shop.editor.detail.stockMax.title"), List.of(
            Locales.component(p, "gui.shop.editor.detail.stockMax.value", Locales.placeholders("value", formatInt(draft.stockMax()))),
            GuiI18n.tr(p, "gui.shop.editor.detail.stockMax.hint"))),
        GuiI18n.tr("gui.shop.editor.detail.stockMax.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (window, text) -> {
          Integer value = parseInt(text);
          draft.stockMax(value);
          Player viewer = viewerPlayer(window);
          if (viewer != null) {
            redraw(viewer);
          }
        },
        true);
    button.validate((w, p, input) -> isInt(input) ? null : GuiI18n.tr(p, "gui.validation.integer"));
    return button;
  }

  private Button restockButton() {
    TextButton button = new TextButton(
        p -> GuiItems.named(Material.REPEATER, GuiI18n.tr(p, "gui.shop.editor.detail.restock.title"), List.of(
            Locales.component(p, "gui.shop.editor.detail.restock.value", Locales.placeholders("value", formatInt(draft.restockSeconds()))),
            GuiI18n.tr(p, "gui.shop.editor.detail.restock.hint"))),
        GuiI18n.tr("gui.shop.editor.detail.restock.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (window, text) -> {
          Long value = parseLong(text);
          draft.restockSeconds(value);
          Player viewer = viewerPlayer(window);
          if (viewer != null) {
            redraw(viewer);
          }
        },
        true);
    button.validate((w, p, input) -> isInt(input) ? null : GuiI18n.tr(p, "gui.validation.integer"));
    return button;
  }

  private List<TradeEntry> trades(Player player) {
    List<TradeEntry> entries = new ArrayList<>();
    for (int i = 0; i < draft.trades().size(); i++) {
      entries.add(new TradeEntry(i, draft.trades().get(i)));
    }
    return entries;
  }

  private void tradeClick(Window.ClickContext ctx, TradeEntry entry) {
    if (ctx.clickType() == ClickType.RIGHT || ctx.clickType() == ClickType.SHIFT_RIGHT) {
      draft.trades().remove(entry.index());
      ctx.redraw();
      GuiSounds.click(ctx.player());
      return;
    }
    GuiManager.get().push(ctx.player(), new ShopTradeEditorMenu(registry, entry.trade(), () -> ctx.redraw()));
    GuiSounds.click(ctx.player());
  }

  private ItemStack tradeItem(TradeEntry entry) {
    ShopTradeDraft trade = entry.trade();
    ItemStack result = trade.sell();
    ItemStack base = result != null && !result.getType().isAir() ? result.clone() : new ItemStack(Material.PAPER);
    List<Component> lore = new ArrayList<>();
    appendStockLore(lore);
    lore.add(Locales.component(null, "gui.shop.editor.detail.trade.buyA",
        Locales.placeholders("value", ingredientLabel(trade.buyA()))));
    appendTokenMarker(lore, Locales.text(null, "gui.shop.editor.detail.trade.buyA.short"), trade.buyA());
    lore.add(Locales.component(null, "gui.shop.editor.detail.trade.buyB",
        Locales.placeholders("value", ingredientLabel(trade.buyB()))));
    appendTokenMarker(lore, Locales.text(null, "gui.shop.editor.detail.trade.buyB.short"), trade.buyB());
    lore.add(Locales.component(null, "gui.shop.editor.detail.trade.maxUses",
        Locales.placeholders("value", String.valueOf(trade.maxUses()))));
    String expReward = Locales.text(null, trade.experienceReward() ? "messages.common.yes" : "messages.common.no");
    lore.add(Locales.component(null, "gui.shop.editor.detail.trade.expReward",
        Locales.placeholders("value", expReward)));
    if (trade.priceMultiplier() != 0.0f) {
      lore.add(Locales.component(null, "gui.shop.editor.detail.trade.priceMultiplier",
          Locales.placeholders("value", String.valueOf(trade.priceMultiplier()))));
    }
    appendDynamicPriceLore(lore, trade);
    lore.add(GuiI18n.tr("gui.common.action.leftClickEdit"));
    lore.add(GuiI18n.tr("gui.common.action.rightClickDelete"));
    return GuiItem.of(base)
        .displayName(Locales.component(null, "gui.shop.editor.detail.trade.title",
            Locales.placeholders("index", String.valueOf(entry.index() + 1))))
        .lore(lore)
        .build();
  }

  private ItemStack saveButtonItem(Player player) {
    return GuiItems.named(Material.LIME_DYE, GuiI18n.tr(player, "gui.shop.editor.detail.save.title"), List.of(
        GuiI18n.tr(player, "gui.shop.editor.detail.save.hint")));
  }

  private ItemStack addTradeItem(Player player) {
    return GuiItems.named(Material.WRITABLE_BOOK, GuiI18n.tr(player, "gui.shop.editor.detail.addTrade.title"), List.of(
        GuiI18n.tr(player, "gui.shop.editor.detail.addTrade.hint")));
  }

  private void addTrade(Player player) {
    ShopTradeDraft trade = new ShopTradeDraft();
    draft.trades().add(trade);
    GuiManager.get().push(player, new ShopTradeEditorMenu(registry, trade, () -> redraw(player)));
    GuiSounds.click(player);
  }

  private void saveDraft(Player player) {
    if (draft.id() == null || draft.id().isBlank()) {
      player.sendMessage(Locales.component(player, "messages.shop.editor.missingId"));
      GuiSounds.error(player);
      return;
    }
    String normalized = Ids.normalize(draft.id());
    draft.id(normalized);
    if (draft.originalId() == null || !draft.originalId().equals(normalized)) {
      if (registry.shop(normalized) != null) {
        player.sendMessage(Locales.component(player, "messages.shop.editor.exists"));
        GuiSounds.error(player);
        return;
      }
    }
    boolean saved = store.saveDraft(draft);
    if (!saved) {
      player.sendMessage(Locales.component(player, "messages.shop.editor.saveFailed"));
      GuiSounds.error(player);
      return;
    }
    registry.reload();
    AdminAuditStore.get().record("shop:" + draft.id(), player.getName());
    parent.redraw(player);
    player.sendMessage(Locales.component(player, "messages.shop.editor.saved"));
    GuiSounds.success(player);
  }

  private void returnItems(Player player) {
    ItemStack icon = iconSlot.get(player, 0);
    if (icon == null || icon.getType().isAir()) {
      iconSlot.clear(player);
      return;
    }
    var leftovers = player.getInventory().addItem(icon);
    if (!leftovers.isEmpty()) {
      leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }
    iconSlot.clear(player);
  }

  private String ingredientLabel(ItemStack stack) {
    if (stack == null || stack.getType().isAir()) {
      return Locales.text(null, "gui.common.none");
    }
    return "<white>" + stack.getType().name().toLowerCase(Locale.ROOT) + " x" + stack.getAmount() + "</white>";
  }

  private void appendStockLore(List<Component> lore) {
    Integer max = draft.stockMax();
    if (max == null || max <= 0) {
      return;
    }
    int min = Math.max(0, draft.stockMin() == null ? 0 : draft.stockMin());
    lore.add(Locales.component(null, "gui.shop.editor.detail.summary.stock",
        Locales.placeholders("min", String.valueOf(min), "max", String.valueOf(max))));
    Long restock = draft.restockSeconds();
    if (restock != null && restock > 0) {
      lore.add(Locales.component(null, "gui.shop.editor.detail.summary.restock",
          Locales.placeholders("value", String.valueOf(restock))));
    }
  }

  private void appendDynamicPriceLore(List<Component> lore, ShopTradeDraft trade) {
    if (trade == null || trade.dynamicPrice() == null) {
      return;
    }
    var spec = trade.dynamicPrice();
    String mode = spec.mode().name().toLowerCase(Locale.ROOT);
    lore.add(Locales.component(null, "gui.shop.editor.detail.summary.dynamic",
        Locales.placeholders("value", mode)));
    lore.add(Locales.component(null, "gui.shop.editor.detail.summary.multiplier",
        Locales.placeholders("min", String.valueOf(spec.minMultiplier()), "max", String.valueOf(spec.maxMultiplier()))));
    if (spec.periodSeconds() > 0) {
      lore.add(Locales.component(null, "gui.shop.editor.detail.summary.period",
          Locales.placeholders("value", String.valueOf(spec.periodSeconds()))));
    }
  }

  private void appendTokenMarker(List<Component> lore, String label, ItemStack item) {
    String tier = tokenTierLabel(item);
    if (tier == null) {
      return;
    }
    lore.add(Locales.component(null, "gui.shop.editor.detail.summary.token",
        Locales.placeholders("label", label, "tier", tier)));
  }

  private String tokenTierLabel(ItemStack item) {
    if (item == null || item.getType().isAir()) {
      return null;
    }
    ShopTokenSpec token = registry.tokenSpec();
    if (token != null && token.markerKey() != null && ItemMarkers.has(item, token.markerKey())) {
      return "token";
    }
    for (ShopTokenTierSpec tier : registry.tokenTiers().values()) {
      if (tier != null && tier.markerKey() != null && ItemMarkers.has(item, tier.markerKey())) {
        return tier.id();
      }
    }
    return null;
  }

  private ItemStack auditItem() {
    AdminAuditStore.Entry entry = AdminAuditStore.get().entry("shop:" + safeId());
    String editor = entry == null ? "unknown" : entry.editor();
    String when = entry == null ? formatTimestamp(registry.file().lastModified()) : formatTimestamp(entry.timestamp());
    return GuiItems.named(Material.PAPER, GuiI18n.tr("gui.shop.editor.detail.audit.title"), List.of(
        Locales.component(null, "gui.shop.editor.detail.audit.lastEditor", Locales.placeholders("value", editor)),
        Locales.component(null, "gui.shop.editor.detail.audit.lastChange", Locales.placeholders("value", when))));
  }

  private String worldsLabel() {
    if (draft.worlds().isEmpty()) {
      return Locales.text(null, "gui.shop.editor.detail.worlds.all");
    }
    return String.join(", ", draft.worlds());
  }

  private String safe(String value) {
    return value == null || value.isBlank() ? Locales.text(null, "gui.common.none") : value;
  }

  private String formatInt(Number value) {
    if (value == null) {
      return "0";
    }
    return String.valueOf(value.longValue());
  }

  private String formatSeconds(double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }

  private String formatTimestamp(long timestamp) {
    if (timestamp <= 0L) {
      return Locales.text(null, "gui.common.unknown");
    }
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    return formatter.format(Instant.ofEpochMilli(timestamp));
  }

  private String safeId() {
    if (draft.id() == null || draft.id().isBlank()) {
      return "new";
    }
    return draft.id();
  }

  private double parseDouble(String raw, double fallback) {
    try {
      return Double.parseDouble(raw.trim());
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private Integer parseInt(String raw) {
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private Long parseLong(String raw) {
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private boolean isDouble(String raw) {
    try {
      Double.parseDouble(raw.trim());
      return true;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  private boolean isInt(String raw) {
    try {
      Integer.parseInt(raw.trim());
      return true;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  private Player viewerPlayer(Window window) {
    if (window == null || window.viewer() == null) {
      return null;
    }
    return org.bukkit.Bukkit.getPlayer(window.viewer());
  }
}
