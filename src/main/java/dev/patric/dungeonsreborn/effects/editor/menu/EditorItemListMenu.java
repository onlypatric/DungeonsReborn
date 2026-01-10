package dev.patric.dungeonsreborn.effects.editor.menu;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.editor.EditorAuditAction;
import dev.patric.dungeonsreborn.effects.editor.EditorAuditEvent;
import dev.patric.dungeonsreborn.effects.editor.EditorItemDraft;
import dev.patric.dungeonsreborn.effects.editor.EditorItemStore;
import dev.patric.dungeonsreborn.effects.editor.EditorLockManager;
import dev.patric.dungeonsreborn.effects.editor.EditorServices;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;

public final class EditorItemListMenu extends Window {
  private static final int SIZE = 54;
  private static final String LOCK_PREFIX = "item:";

  private record ItemEntry(
      String id,
      EditorItemDraft draft,
      ItemStack item,
      int bindings) {
  }

  private final EditorServices services;
  private final EditorItemStore store;
  private final VirtualList<ItemEntry> list;

  public EditorItemListMenu(EditorServices services) {
    super(SIZE, GuiMini.mm("<white><bold>Items</bold></white>"), true);
    this.services = Objects.requireNonNull(services, "services");
    this.store = new EditorItemStore(services.engine().plugin());

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(player, entry),
        (ctx, entry) -> openEntry(ctx.player(), entry));
    list.searchKey(entry -> entry.id);
    list.apply(this, Placement.FIXED);

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, Component.text("Back"))));
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());
    nav(4, createButton());
    nav(5, refreshButton());

    setFixedAt(0, 1, header());
    setFixedAt(0, 7, filterButton());
    setFixedAt(0, 8, clearFilterButton());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private Label header() {
    return new Label(p -> GuiItems.named(Material.CHEST, GuiMini.mm("<gold><bold>Items</bold></gold>"), List.of(
        GuiMini.mm("<gray>Manage item definitions.</gray>"),
        GuiMini.mm("<gray>Each item binds to abilities.</gray>"))));
  }

  private TextButton filterButton() {
    return new TextButton(
        p -> GuiItems.named(Material.NAME_TAG, GuiMini.mm("<aqua><bold>Filter</bold></aqua>"), List.of(
            GuiMini.mm("<gray>Set a search query.</gray>"),
            GuiMini.mm("<gray>Current:</gray> <white>" + (list.query(p).isBlank() ? "(none)" : list.query(p)) + "</white>"))),
        GuiMini.mm("<gray>Type a filter query (or 'cancel')</gray>"),
        "cancel",
        Duration.ofSeconds(30),
        (w, text) -> {
          Player viewer = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (viewer == null) {
            return;
          }
          list.query(viewer, text);
          list.redraw(w, viewer);
          w.redrawSlot(viewer, slotAt(0, 7));
        },
        true)
            .inputMode(TextButton.InputMode.CHAT);
  }

  private Button clearFilterButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.CANCEL, Component.text("Clear")), ctx -> {
      list.clearFilter(ctx.player());
      list.redraw(ctx.window(), ctx.player());
      ctx.window().redrawSlot(ctx.player(), slotAt(0, 7));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button createButton() {
    return new TextButton(
        p -> GuiButtons.item(GuiButtons.Type.PRIMARY, Component.text("New Item")),
        GuiMini.mm("<gray>Enter a new item id</gray>"),
        "cancel",
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (player == null) {
            return;
          }
          if (!services.access().canEdit(player)) {
            player.sendMessage(Component.text("§cMissing permission: dungeonsreborn.editor.edit"));
            return;
          }
          String id;
          try {
            id = Ids.normalize(text);
          } catch (Exception ex) {
            player.sendMessage(Component.text("§cInvalid id: " + ex.getMessage()));
            return;
          }
          if (store.load(id).isPresent()) {
            player.sendMessage(Component.text("§cItem already exists: " + id));
            return;
          }
          EditorItemDraft draft = store.create(id);
          ItemStack hand = player.getInventory().getItemInMainHand();
          if (hand != null && !hand.getType().isAir()) {
            draft.setItem(hand);
          }
          store.save(draft);
          services.audit().log(EditorAuditEvent.of(EditorAuditAction.CREATE, player.getUniqueId(), player.getName(),
              LOCK_PREFIX + id, "item"));
          list.invalidateAll();
          openDraft(player, draft);
        },
        true)
            .inputMode(TextButton.InputMode.CHAT);
  }

  private Button refreshButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.INFO, Component.text("Refresh")), ctx -> {
      list.invalidate(ctx.player());
      list.redraw(ctx.window(), ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private List<ItemEntry> entries(Player player) {
    List<ItemEntry> entries = new ArrayList<>();
    for (EditorItemDraft draft : store.loadAll()) {
      entries.add(new ItemEntry(draft.id(), draft, draft.item(), draft.bindings().size()));
    }
    entries.sort(Comparator.comparing(entry -> entry.id.toLowerCase(Locale.ROOT)));
    return entries;
  }

  private ItemStack entryItem(Player player, ItemEntry entry) {
    ItemStack base = entry.item == null ? null : entry.item.clone();
    Material mat = base == null || base.getType().isAir() ? Material.PAPER : base.getType();
    GuiItem item = GuiItem.of(base == null || base.getType().isAir() ? new ItemStack(mat) : base);
    if (base == null || base.getItemMeta() == null || !base.getItemMeta().hasDisplayName()) {
      item.displayName(GuiMini.mm("<aqua><bold>" + entry.id + "</bold></aqua>"));
    }
    List<Component> lore = new ArrayList<>();
    lore.add(GuiMini.mm("<gray>ID:</gray> <white>" + entry.id + "</white>"));
    lore.add(GuiMini.mm("<gray>Bindings:</gray> <white>" + entry.bindings + "</white>"));
    if (base == null || base.getType().isAir()) {
      lore.add(GuiMini.mm("<red>No item set</red>"));
    }
    item.lore(lore);
    return item.build();
  }

  private void openEntry(Player player, ItemEntry entry) {
    if (!services.access().canEdit(player)) {
      player.sendMessage(Component.text("§cYou cannot edit items."));
      return;
    }
    EditorLockManager.LockResult lock = services.locks().tryLock(LOCK_PREFIX + entry.id, player);
    if (!lock.acquired()) {
      player.sendMessage(Component.text("§cItem is locked by " + lock.lock().ownerName()));
      return;
    }
    openDraft(player, entry.draft);
  }

  private void openDraft(Player player, EditorItemDraft draft) {
    EditorItemDetailMenu detail = new EditorItemDetailMenu(services, store, draft, () -> list.invalidateAll());
    openSubWindow(player, detail);
  }
}
