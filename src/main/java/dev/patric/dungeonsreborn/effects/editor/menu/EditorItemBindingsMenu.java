package dev.patric.dungeonsreborn.effects.editor.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.effects.editor.EditorAuditAction;
import dev.patric.dungeonsreborn.effects.editor.EditorAuditEvent;
import dev.patric.dungeonsreborn.effects.editor.EditorItemDraft;
import dev.patric.dungeonsreborn.effects.editor.EditorItemLore;
import dev.patric.dungeonsreborn.effects.editor.EditorItemStore;
import dev.patric.dungeonsreborn.effects.editor.EditorServices;
import dev.patric.dungeonsreborn.effects.integration.InteractTrigger;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.util.YamlValues;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class EditorItemBindingsMenu extends Window {
  private static final int SIZE = 54;
  private static final String LOCK_PREFIX = "item:";

  private record BindingEntry(int index, Map<String, Object> binding) {
  }

  private final EditorServices services;
  private final EditorItemStore store;
  private final EditorItemDraft draft;
  private final List<Map<String, Object>> bindings;
  private final Runnable onCloseRefresh;
  private final VirtualList<BindingEntry> list;

  public EditorItemBindingsMenu(EditorServices services, EditorItemStore store, EditorItemDraft draft, Runnable onCloseRefresh) {
    super(SIZE, GuiI18n.tr("gui.items.editor.bindings.title"), true);
    this.services = Objects.requireNonNull(services, "services");
    this.store = Objects.requireNonNull(store, "store");
    this.draft = Objects.requireNonNull(draft, "draft");
    this.bindings = new ArrayList<>(draft.bindings());
    this.onCloseRefresh = onCloseRefresh == null ? () -> {
    } : onCloseRefresh;

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(entry),
        (ctx, entry) -> openDetail(ctx.player(), entry.index()));
    list.apply(this, Placement.FIXED);

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, GuiI18n.tr(p, "gui.button.back"))));
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());
    nav(4, addBindingButton());
    nav(5, clearBindingsButton());

    setFixedAt(0, 4, new Label(GuiItems.named(Material.TRIPWIRE_HOOK, GuiI18n.tr("gui.items.editor.bindings.header.title"), List.of(
        GuiI18n.tr("gui.items.editor.bindings.header.hint")))));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> {
      saveDraft(ctx.player(), "bindings.close");
      this.onCloseRefresh.run();
      GuiSounds.close(ctx.player());
    });
  }

  private List<BindingEntry> entries(Player player) {
    List<BindingEntry> out = new ArrayList<>();
    for (int i = 0; i < bindings.size(); i++) {
      out.add(new BindingEntry(i, bindings.get(i)));
    }
    return out;
  }

  private org.bukkit.inventory.ItemStack entryItem(BindingEntry entry) {
    Map<String, Object> binding = entry.binding;
    String click = YamlValues.string(binding, "click", "RIGHT_CLICK");
    String ability = YamlValues.string(binding, "ability", "(unset)");
    boolean sneaking = YamlValues.bool(binding, "requireSneaking", false);
    boolean cancel = YamlValues.bool(binding, "cancelEvent", true);
    Material mat = click.equalsIgnoreCase("LEFT_CLICK") ? Material.FEATHER : Material.TRIPWIRE_HOOK;
    List<Component> lore = new ArrayList<>();
    lore.add(GuiI18n.tr("gui.items.editor.bindings.entry.click",
        Placeholder.unparsed("value", click.toUpperCase(Locale.ROOT))));
    lore.add(GuiI18n.tr("gui.items.editor.bindings.entry.ability",
        Placeholder.unparsed("value", ability)));
    lore.add(GuiI18n.tr("gui.items.editor.bindings.entry.sneak",
        Placeholder.component("value", GuiI18n.tr(sneaking ? "messages.common.yes" : "messages.common.no"))));
    lore.add(GuiI18n.tr("gui.items.editor.bindings.entry.cancel",
        Placeholder.component("value", GuiI18n.tr(cancel ? "messages.common.yes" : "messages.common.no"))));
    return GuiItem.of(mat)
        .displayName(GuiI18n.tr("gui.items.editor.bindings.entry.title",
            Placeholder.unparsed("index", String.valueOf(entry.index + 1))))
        .lore(lore)
        .build();
  }

  private Button addBindingButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.PRIMARY, GuiI18n.tr(p, "gui.items.editor.bindings.add.title")), ctx -> {
      Map<String, Object> binding = new java.util.LinkedHashMap<>();
      binding.put("type", "interact");
      binding.put("click", InteractTrigger.RIGHT_CLICK.name());
      binding.put("ability", "");
      binding.put("requireSneaking", false);
      binding.put("cancelEvent", true);
      bindings.add(binding);
      saveDraft(ctx.player(), "bindings.add");
      list.invalidate(ctx.player());
      list.redraw(this, ctx.player());
      openDetail(ctx.player(), bindings.size() - 1);
    }).autoDescribeInLore(false);
  }

  private Button clearBindingsButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.CANCEL, GuiI18n.tr(p, "gui.items.editor.bindings.clear.title")), ctx -> {
      bindings.clear();
      saveDraft(ctx.player(), "bindings.clear");
      list.invalidate(ctx.player());
      list.redraw(this, ctx.player());
    }).autoDescribeInLore(false);
  }

  private void openDetail(Player player, int index) {
    openSubWindow(player, new EditorItemBindingDetailMenu(services, store, draft, bindings, index, this::refreshList));
  }

  private void refreshList() {
    Player viewer = viewer() == null ? null : org.bukkit.Bukkit.getPlayer(viewer());
    if (viewer != null) {
      list.invalidate(viewer);
      list.redraw(this, viewer);
    }
  }

  private void saveDraft(Player player, String detail) {
    draft.setBindings(bindings);
    if (draft.item() != null && !draft.item().getType().isAir()) {
      draft.setItem(EditorItemLore.applyAbilityLore(draft.item(), bindings, services.engine()));
    }
    store.save(draft);
    services.audit().log(EditorAuditEvent.of(EditorAuditAction.EDIT, player.getUniqueId(), player.getName(),
        LOCK_PREFIX + draft.id(), detail));
  }

}
