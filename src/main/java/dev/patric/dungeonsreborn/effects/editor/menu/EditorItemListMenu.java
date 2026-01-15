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
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.EmptyState;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.components.list.ListSearchBar;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

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
    super(SIZE, GuiI18n.tr("gui.items.editor.list.title"), true);
    this.services = Objects.requireNonNull(services, "services");
    this.store = new EditorItemStore(services.engine().plugin(), services.engine().logger());

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(player, entry),
        (ctx, entry) -> openEntry(ctx.player(), entry));
    list.searchKey(entry -> entry.id);
    list.emptyStateItem(EmptyState.list());
    list.apply(this, Placement.FIXED);

    navLeft(GuiNav.backButton());
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());
    nav(4, createButton());
    nav(5, refreshButton());

    setFixedAt(0, 1, header());
    setFixedAt(0, 7, ListSearchBar.searchButton(list, slotAt(0, 7)));
    setFixedAt(0, 8, ListSearchBar.clearButton(list, slotAt(0, 7)));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private Label header() {
    return new Label(p -> GuiItems.named(Material.CHEST, GuiI18n.tr(p, "gui.items.editor.list.header.title"), List.of(
        GuiI18n.tr(p, "gui.items.editor.list.header.hint1"),
        GuiI18n.tr(p, "gui.items.editor.list.header.hint2"))));
  }

  private Button createButton() {
    return new TextButton(
        p -> GuiButtons.item(GuiButtons.Type.PRIMARY, GuiI18n.tr(p, "gui.items.editor.list.create.title")),
        GuiI18n.tr("gui.items.editor.list.create.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(45),
        (w, text) -> {
          Player player = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (player == null) {
            return;
          }
          if (!services.access().canEdit(player)) {
            player.sendMessage(GuiI18n.tr(player, "messages.command.missingPermission",
                Placeholder.unparsed("permission", "dungeonsreborn.editor.edit")));
            return;
          }
          String id;
          try {
            id = Ids.normalize(text);
          } catch (Exception ex) {
            player.sendMessage(GuiI18n.tr(player, "messages.items.editor.invalidId",
                Placeholder.unparsed("reason", ex.getMessage())));
            return;
          }
          if (store.load(id).isPresent()) {
            player.sendMessage(GuiI18n.tr(player, "messages.items.editor.exists",
                Placeholder.unparsed("id", id)));
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
        true).inputMode(TextButton.InputMode.CHAT);
  }

  private Button refreshButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.INFO, GuiI18n.tr(p, "gui.items.editor.list.refresh.title")), ctx -> {
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
      item.displayName(GuiI18n.tr(player, "gui.items.editor.list.entry.title",
          Placeholder.unparsed("id", entry.id)));
    }
    List<Component> lore = new ArrayList<>();
    lore.add(GuiI18n.tr(player, "gui.common.line.id", Placeholder.unparsed("value", entry.id)));
    lore.add(GuiI18n.tr(player, "gui.items.editor.list.entry.bindings",
        Placeholder.unparsed("count", String.valueOf(entry.bindings))));
    if (base == null || base.getType().isAir()) {
      lore.add(GuiI18n.tr(player, "gui.items.editor.list.entry.noItem"));
    }
    item.lore(lore);
    return item.build();
  }

  private void openEntry(Player player, ItemEntry entry) {
    if (!services.access().canEdit(player)) {
      player.sendMessage(GuiI18n.tr(player, "messages.items.editor.noEdit"));
      return;
    }
    EditorLockManager.LockResult lock = services.locks().tryLock(LOCK_PREFIX + entry.id, player);
    if (!lock.acquired()) {
      player.sendMessage(GuiI18n.tr(player, "messages.items.editor.locked",
          Placeholder.unparsed("name", lock.lock().ownerName())));
      return;
    }
    openDraft(player, entry.draft);
  }

  private void openDraft(Player player, EditorItemDraft draft) {
    EditorItemDetailMenu detail = new EditorItemDetailMenu(services, store, draft, () -> list.invalidateAll());
    openSubWindow(player, detail);
  }
}
