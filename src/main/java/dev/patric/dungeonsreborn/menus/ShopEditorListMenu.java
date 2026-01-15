package dev.patric.dungeonsreborn.menus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.EmptyState;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.StatusRow;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.components.list.ListSearchBar;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.shops.ShopEditorDraft;
import dev.patric.dungeonsreborn.shops.ShopEditorStore;
import dev.patric.dungeonsreborn.shops.ShopSpec;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import net.kyori.adventure.text.Component;

public final class ShopEditorListMenu extends Window {
  private static final int SIZE = 54;
  private static final Duration DELETE_TIMEOUT = Duration.ofSeconds(20);

  private record Entry(ShopSpec spec) {
  }

  private final ShopYamlRegistry registry;
  private final ShopEditorStore store;
  private final VirtualList<Entry> list;

  public ShopEditorListMenu(ShopYamlRegistry registry) {
    super(SIZE, GuiI18n.tr("gui.shop.editor.list.title"), true);
    this.registry = Objects.requireNonNull(registry, "registry");
    this.store = new ShopEditorStore(registry);

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(player, entry),
        (ctx, entry) -> handleClick(ctx, entry));
    list.searchKey(entry -> entry.spec().id() + " " + entry.spec().title());
    list.emptyStateItem(EmptyState.list());
    list.apply(this, Placement.FIXED);

    navLeft(GuiNav.backButton());
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));

    setFixedAt(0, 4, new Label(GuiItems.named(Material.EMERALD, GuiI18n.tr("gui.shop.editor.list.header.title"), List.of(
        GuiI18n.tr("gui.shop.editor.list.header.hint1"),
        GuiI18n.tr("gui.shop.editor.list.header.hint2")))));

    setFixedAt(5, 4, createButton());
    setFixedAt(5, 5, batchButton());
    new StatusRow(0)
        .column(0, new Label(p -> GuiItems.named(Material.PAPER, GuiI18n.tr(p, "gui.shop.editor.list.status.title"), List.of(
            Locales.component(p, "gui.shop.editor.list.status.count",
                Locales.placeholders("count", registry.shops().size())),
            GuiI18n.tr(p, "gui.shop.editor.list.status.reloadHint")))))
        .apply(this);

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private TextButton createButton() {
    return new TextButton(
        p -> GuiItems.named(Material.WRITABLE_BOOK, GuiI18n.tr(p, "gui.shop.editor.list.create.title"), List.of(
            GuiI18n.tr(p, "gui.shop.editor.list.create.hint"))),
        GuiI18n.tr("gui.shop.editor.list.create.prompt"),
        Locales.text(null, "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (window, text) -> {
          Player player = window.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(window.viewer());
          if (player == null) {
            return;
          }
          String id = text == null ? "" : text.trim();
          if (id.isBlank()) {
            player.sendMessage(GuiI18n.tr(player, "gui.shop.editor.list.create.missingId"));
            return;
          }
          if (registry.shop(id) != null) {
            player.sendMessage(GuiI18n.tr(player, "gui.shop.editor.list.create.exists"));
            return;
          }
          ShopEditorDraft draft = store.createDraft();
          draft.id(id);
          GuiManager.get().push(player, new ShopEditorDetailMenu(registry, store, draft, this));
        },
        true);
  }

  private Button batchButton() {
    return new Button(p -> GuiItems.named(Material.COMPARATOR, GuiI18n.tr(p, "gui.shop.editor.list.batch.title"), List.of(
        GuiI18n.tr(p, "gui.shop.editor.list.batch.hint"))), ctx -> {
      GuiManager.get().push(ctx.player(), new ShopBatchMenu(registry, this::refreshAfterChild));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private List<Entry> entries(Player player) {
    List<Entry> entries = new ArrayList<>();
    for (ShopSpec spec : registry.shops().values()) {
      entries.add(new Entry(spec));
    }
    entries.sort(Comparator.comparing(entry -> entry.spec().id().toLowerCase(Locale.ROOT)));
    return entries;
  }

  private void handleClick(Window.ClickContext ctx, Entry entry) {
    if (ctx.clickType() == ClickType.RIGHT || ctx.clickType() == ClickType.SHIFT_RIGHT) {
      confirmDelete(ctx.player(), entry.spec());
      return;
    }
    ShopEditorDraft draft = store.loadDraft(entry.spec().id());
    if (draft == null) {
      GuiSounds.error(ctx.player());
      ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "gui.shop.editor.list.error.notFound"));
      return;
    }
    GuiManager.get().push(ctx.player(), new ShopEditorDetailMenu(registry, store, draft, this));
    GuiSounds.click(ctx.player());
  }

  private ItemStack entryItem(Player player, Entry entry) {
    ShopSpec spec = entry.spec();
    ItemStack icon = spec.icon() != null ? spec.icon().resolve(registry.itemResolver(), registry.tokenSpec()) : null;
    ItemStack base = icon != null ? icon.clone() : new ItemStack(Material.EMERALD);
    List<Component> lore = new ArrayList<>();
    lore.add(Locales.component(player, "gui.common.line.id", Locales.placeholders("value", spec.id())));
    lore.add(Locales.component(player, "gui.shop.editor.list.entry.trades",
        Locales.placeholders("count", spec.trades().size())));
    String status = spec.enabled()
        ? Locales.text(player, "gui.common.status.enabled")
        : Locales.text(player, "gui.common.status.disabled");
    lore.add(Locales.component(player, "gui.common.line.status", Locales.placeholders("value", status)));
    if (spec.permission() != null && !spec.permission().isBlank()) {
      lore.add(Locales.component(player, "gui.common.line.permission", Locales.placeholders("value", spec.permission())));
    }
    lore.add(GuiI18n.tr(player, "gui.common.action.leftClickEdit"));
    lore.add(GuiI18n.tr(player, "gui.common.action.rightClickDelete"));
    return GuiItem.of(base)
        .displayName(Locales.component(player, "gui.shop.editor.list.entry.title", Locales.placeholders("title", spec.title())))
        .lore(lore)
        .build();
  }

  private void confirmDelete(Player player, ShopSpec spec) {
    GuiManager.get().prepareTemporaryClose(player);
    player.closeInventory();
    String deleteWord = Locales.text(player, "gui.shop.editor.list.delete.word");
    Component prompt = Locales.component(player, "gui.shop.editor.list.delete.prompt",
        Locales.placeholders("word", deleteWord, "id", spec.id()));
    GuiManager.get().requestText(player, new GuiManager.TextRequest(
        prompt,
        Locales.text(player, "gui.textInput.cancelWord"),
        DELETE_TIMEOUT,
        (p, text) -> {
          if (!deleteWord.equalsIgnoreCase(text.trim())) {
            p.sendMessage(GuiI18n.tr(p, "gui.shop.editor.list.delete.cancelled"));
            GuiManager.get().resume(p, this, "delete-cancel");
            return;
          }
          if (!store.deleteShop(spec.id())) {
            p.sendMessage(GuiI18n.tr(p, "gui.shop.editor.list.delete.failed"));
          } else {
            p.sendMessage(GuiI18n.tr(p, "gui.shop.editor.list.delete.success"));
          }
          registry.reload();
          GuiManager.get().resume(p, this, "delete");
        },
        p -> GuiManager.get().resume(p, this, "delete-cancel"),
        p -> GuiManager.get().resume(p, this, "delete-timeout")
    ));
  }

  private void refreshAfterChild() {
    Player player = viewerPlayer(this);
    if (player == null) {
      return;
    }
    registry.reload();
    list.invalidate(player);
    list.redraw(this, player);
  }

  private Player viewerPlayer(Window window) {
    if (window == null || window.viewer() == null) {
      return null;
    }
    return org.bukkit.Bukkit.getPlayer(window.viewer());
  }
}
