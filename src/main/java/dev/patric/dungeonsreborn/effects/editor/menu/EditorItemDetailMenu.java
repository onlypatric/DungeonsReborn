package dev.patric.dungeonsreborn.effects.editor.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.editor.EditorAuditAction;
import dev.patric.dungeonsreborn.effects.editor.EditorAuditEvent;
import dev.patric.dungeonsreborn.effects.editor.EditorItemDraft;
import dev.patric.dungeonsreborn.effects.editor.EditorItemLore;
import dev.patric.dungeonsreborn.effects.editor.EditorItemStore;
import dev.patric.dungeonsreborn.effects.editor.EditorServices;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;
import dev.patric.dungeonsreborn.admin.AdminAuditStore;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.item.ItemPreview;
import dev.patric.dungeonsreborn.gui.flow.ConfirmDialogWindow;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

public final class EditorItemDetailMenu extends Window {
  private static final int SIZE = 54;
  private static final String LOCK_PREFIX = "item:";
  private static final DateTimeFormatter AUDIT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final EditorServices services;
  private final EditorItemStore store;
  private final EditorItemDraft draft;
  private final Runnable onCloseRefresh;
  private boolean dirty;

  public EditorItemDetailMenu(EditorServices services, EditorItemStore store, EditorItemDraft draft, Runnable onCloseRefresh) {
    super(SIZE, GuiI18n.tr("gui.items.editor.detail.title"), true);
    this.services = Objects.requireNonNull(services, "services");
    this.store = Objects.requireNonNull(store, "store");
    this.draft = Objects.requireNonNull(draft, "draft");
    this.onCloseRefresh = onCloseRefresh == null ? () -> {
    } : onCloseRefresh;

    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, GuiI18n.tr(p, "gui.button.back"))));

    setFixedAt(0, 6, new Label(p -> auditItem()));
    setFixedAt(0, 7, new Label(this::dirtyIndicatorItem));

    setFixedAt(0, 4, new Label(p -> GuiItems.named(Material.CHEST, GuiI18n.tr(p, "gui.items.editor.detail.header.title",
        Placeholder.unparsed("id", draft.id())), List.of(
        GuiI18n.tr(p, "gui.items.editor.detail.header.hint")))));

    setFixedAt(1, 4, new ItemPreview(p -> draft.item())
        .placeholder(GuiItems.named(Material.GRAY_STAINED_GLASS_PANE, GuiI18n.tr("gui.items.editor.detail.preview.empty"))));

    setFixedAt(1, 1, nameButton());
    setFixedAt(1, 2, setFromHandButton());
    setFixedAt(1, 6, giveItemButton());
    setFixedAt(1, 7, loreButton());
    setFixedAt(2, 1, glintToggle());
    setFixedAt(2, 2, unbreakableToggle());
    setFixedAt(2, 3, hideAttributesToggle());
    setFixedAt(2, 5, bindingsButton());
    setFixedAt(2, 4, reloadButton());
    setFixedAt(2, 6, deleteButton());
    setFixedAt(2, 7, hideEnchantsToggle());
    setFixedAt(3, 4, clearItemButton());
    setFixedAt(3, 5, applyButton());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> {
      services.locks().release(LOCK_PREFIX + draft.id(), ctx.player().getUniqueId());
      this.onCloseRefresh.run();
      GuiSounds.close(ctx.player());
    });
  }

  private Button setFromHandButton() {
    return new Button(p -> GuiItems.named(Material.ANVIL, GuiI18n.tr(p, "gui.items.editor.detail.useHeld.title"), List.of(
        GuiI18n.tr(p, "gui.items.editor.detail.useHeld.hint"))), ctx -> {
      ItemStack hand = ctx.player().getInventory().getItemInMainHand();
      if (hand == null || hand.getType().isAir()) {
        ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "messages.items.editor.holdItem"));
        return;
      }
      draft.setItem(hand);
      saveDraft(ctx.player(), "item.set");
      ctx.window().redrawSlot(ctx.player(), slotAt(1, 4));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button nameButton() {
    return new Button(p -> {
      ItemStack item = draft.item();
      Component current = GuiI18n.tr(p, "gui.common.none");
      if (item != null && !item.getType().isAir()) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
          current = meta.displayName();
        }
      }
      return GuiItems.named(Material.NAME_TAG, GuiI18n.tr(p, "gui.items.editor.detail.name.title"), List.of(
          GuiI18n.tr(p, "gui.items.editor.detail.name.current", Placeholder.component("value", current))));
    }, ctx -> {
      Player player = ctx.player();
      GuiManager.get().prepareTemporaryClose(player);
      ctx.close();
      GuiManager.get().requestText(player,
          new dev.patric.dungeonsreborn.gui.GuiManager.TextRequest(
              GuiI18n.tr(player, "gui.items.editor.detail.name.prompt"),
              Locales.text(player, "gui.textInput.cancelWord"),
              java.time.Duration.ofSeconds(45),
              (p, text) -> {
                ItemStack item = requireItem(p);
                if (item == null) {
                  return;
                }
                ItemMeta meta = item.getItemMeta();
                if (meta == null) {
                  return;
                }
                if (text == null || text.isBlank()) {
                  meta.displayName(null);
                } else {
                  meta.displayName(EditorItemLore.parseRichText(text));
                }
                item.setItemMeta(meta);
                draft.setItem(item);
                saveDraft(p, "item.name");
                GuiManager.get().resume(p, this, "item.name");
              },
              p -> GuiManager.get().resume(p, this, "item.name.cancel"),
              p -> GuiManager.get().resume(p, this, "item.name.timeout")));
    }).autoDescribeInLore(false);
  }

  private Button loreButton() {
    return new Button(p -> {
      ItemStack item = draft.item();
      int lines = 0;
      if (item != null && !item.getType().isAir()) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.lore() != null) {
          lines = meta.lore().size();
        }
      }
      return GuiItems.named(Material.WRITABLE_BOOK, GuiI18n.tr(p, "gui.items.editor.detail.lore.title"), List.of(
          GuiI18n.tr(p, "gui.items.editor.detail.lore.lines",
              Placeholder.component("value", lines == 0
                  ? GuiI18n.tr(p, "gui.common.none")
                  : Component.text(String.valueOf(lines)))),
          GuiI18n.tr(p, "gui.items.editor.detail.lore.hint")));
    }, ctx -> {
      Player player = ctx.player();
      GuiManager.get().prepareTemporaryClose(player);
      ctx.close();
      GuiManager.get().requestText(player,
          new dev.patric.dungeonsreborn.gui.GuiManager.TextRequest(
              GuiI18n.tr(player, "gui.items.editor.detail.lore.prompt"),
              Locales.text(player, "gui.textInput.cancelWord"),
              java.time.Duration.ofSeconds(60),
              (p, text) -> {
                ItemStack item = requireItem(p);
                if (item == null) {
                  return;
                }
                ItemMeta meta = item.getItemMeta();
                if (meta == null) {
                  return;
                }
                if (text == null || text.isBlank()) {
                  meta.lore(null);
                } else {
                  String normalized = text.replace("\\\\n", "\n");
                  String[] lines = normalized.split("\n", -1);
                  java.util.ArrayList<Component> lore = new java.util.ArrayList<>();
                  for (String line : lines) {
                    lore.add(EditorItemLore.parseRichText(line));
                  }
                  meta.lore(lore);
                }
                item.setItemMeta(meta);
                draft.setItem(item);
                saveDraft(p, "item.lore");
                GuiManager.get().resume(p, this, "item.lore");
              },
              p -> GuiManager.get().resume(p, this, "item.lore.cancel"),
              p -> GuiManager.get().resume(p, this, "item.lore.timeout")));
    }).autoDescribeInLore(false);
  }

  private Button giveItemButton() {
    return new Button(p -> GuiItems.named(Material.LIME_DYE, GuiI18n.tr(p, "gui.items.editor.detail.give.title"), List.of(
        GuiI18n.tr(p, "gui.items.editor.detail.give.hint"))), ctx -> {
      ItemStack item = draft.item();
      if (item == null || item.getType().isAir()) {
        ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "messages.items.editor.notSet"));
        return;
      }
      ItemStack given = EditorItemLore.applyAbilityLore(item.clone(), draft.bindings(), services.engine());
      ItemMarkers.setItemId(given, draft.id());
      ctx.player().getInventory().addItem(given);
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button bindingsButton() {
    return new Button(p -> GuiItems.named(Material.TRIPWIRE_HOOK, GuiI18n.tr(p, "gui.items.editor.detail.bindings.title"), bindingsSummaryLore()), ctx -> {
      openSubWindow(ctx.player(), new EditorItemBindingsMenu(services, store, draft, this::refreshAfterChild));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button applyButton() {
    return new Button(p -> GuiItems.named(Material.LIME_DYE, GuiI18n.tr(p, "gui.items.editor.detail.apply.title"), List.of(
        GuiI18n.tr(p, "gui.items.editor.detail.apply.hint"))), ctx -> {
      services.yaml().reload();
      dirty = false;
      redrawSlot(ctx.player(), slotAt(0, 7));
      ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "messages.items.editor.reloaded"));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button glintToggle() {
    return new Button(p -> {
      ItemStack item = draft.item();
      boolean enabled = item != null && EditorItemLore.hasGlint(item);
      Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
      return GuiItems.named(mat, GuiI18n.tr(p, "gui.items.editor.detail.glint.title"), List.of(
          GuiI18n.tr(p, "gui.common.line.status", Placeholder.component("value",
              GuiI18n.tr(p, enabled ? "gui.common.status.enabled" : "gui.common.status.disabled")))));
    }, ctx -> {
      ItemStack item = requireItem(ctx.player());
      if (item == null) {
        return;
      }
      boolean enabled = EditorItemLore.hasGlint(item);
      item = EditorItemLore.setGlint(item, !enabled);
      draft.setItem(item);
      saveDraft(ctx.player(), "item.glint");
      ctx.window().redrawSlot(ctx.player(), slotAt(1, 4));
      ctx.window().redrawSlot(ctx.player(), slotAt(2, 1));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button unbreakableToggle() {
    return new Button(p -> {
      ItemStack item = draft.item();
      boolean enabled = item != null && item.hasItemMeta() && item.getItemMeta().isUnbreakable();
      Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
      return GuiItems.named(mat, GuiI18n.tr(p, "gui.items.editor.detail.unbreakable.title"), List.of(
          GuiI18n.tr(p, "gui.common.line.status", Placeholder.component("value",
              GuiI18n.tr(p, enabled ? "gui.common.status.enabled" : "gui.common.status.disabled")))));
    }, ctx -> {
      ItemStack item = requireItem(ctx.player());
      if (item == null) {
        return;
      }
      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
        return;
      }
      boolean enabled = meta.isUnbreakable();
      meta.setUnbreakable(!enabled);
      item.setItemMeta(meta);
      draft.setItem(item);
      saveDraft(ctx.player(), "item.unbreakable");
      ctx.window().redrawSlot(ctx.player(), slotAt(1, 4));
      ctx.window().redrawSlot(ctx.player(), slotAt(2, 2));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button hideAttributesToggle() {
    return new Button(p -> {
      ItemStack item = draft.item();
      boolean enabled = item != null && item.hasItemMeta() && item.getItemMeta().getItemFlags().contains(ItemFlag.HIDE_ATTRIBUTES);
      Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
      return GuiItems.named(mat, GuiI18n.tr(p, "gui.items.editor.detail.hideAttributes.title"), List.of(
          GuiI18n.tr(p, "gui.common.line.status", Placeholder.component("value",
              GuiI18n.tr(p, enabled ? "gui.common.status.enabled" : "gui.common.status.disabled")))));
    }, ctx -> {
      ItemStack item = requireItem(ctx.player());
      if (item == null) {
        return;
      }
      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
        return;
      }
      boolean enabled = meta.getItemFlags().contains(ItemFlag.HIDE_ATTRIBUTES);
      EditorItemLore.setFlag(meta, ItemFlag.HIDE_ATTRIBUTES, !enabled);
      item.setItemMeta(meta);
      draft.setItem(item);
      saveDraft(ctx.player(), "item.hide_attributes");
      ctx.window().redrawSlot(ctx.player(), slotAt(1, 4));
      ctx.window().redrawSlot(ctx.player(), slotAt(2, 3));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button hideEnchantsToggle() {
    return new Button(p -> {
      ItemStack item = draft.item();
      boolean enabled = item != null && item.hasItemMeta() && item.getItemMeta().getItemFlags().contains(ItemFlag.HIDE_ENCHANTS);
      Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
      return GuiItems.named(mat, GuiI18n.tr(p, "gui.items.editor.detail.hideEnchants.title"), List.of(
          GuiI18n.tr(p, "gui.common.line.status", Placeholder.component("value",
              GuiI18n.tr(p, enabled ? "gui.common.status.enabled" : "gui.common.status.disabled")))));
    }, ctx -> {
      ItemStack item = requireItem(ctx.player());
      if (item == null) {
        return;
      }
      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
        return;
      }
      boolean enabled = meta.getItemFlags().contains(ItemFlag.HIDE_ENCHANTS);
      EditorItemLore.setFlag(meta, ItemFlag.HIDE_ENCHANTS, !enabled);
      item.setItemMeta(meta);
      draft.setItem(item);
      saveDraft(ctx.player(), "item.hide_enchants");
      ctx.window().redrawSlot(ctx.player(), slotAt(1, 4));
      ctx.window().redrawSlot(ctx.player(), slotAt(2, 7));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button clearItemButton() {
    return new Button(p -> GuiItems.named(Material.BARRIER, GuiI18n.tr(p, "gui.items.editor.detail.clear.title"), List.of(
        GuiI18n.tr(p, "gui.items.editor.detail.clear.hint"))), ctx -> {
      draft.setItem(null);
      saveDraft(ctx.player(), "item.clear");
      ctx.window().redrawSlot(ctx.player(), slotAt(1, 4));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button reloadButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.INFO, GuiI18n.tr(p, "gui.items.editor.detail.reload.title")), ctx -> {
      EffectsYamlAbilities yaml = services.yaml();
      if (yaml == null) {
        ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "messages.items.editor.yamlMissing"));
        return;
      }
      EffectsYamlAbilities.ReloadResult result = yaml.reload();
      if (result.errors().isEmpty()) {
        ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "messages.items.editor.yamlReload.ok",
            Placeholder.unparsed("abilities", String.valueOf(result.loadedAbilities())),
            Placeholder.unparsed("bindings", String.valueOf(result.loadedItemBindings()))));
      } else {
        ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "messages.items.editor.yamlReload.errors",
            Placeholder.unparsed("count", String.valueOf(result.errors().size()))));
      }
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button deleteButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.TRASH, GuiI18n.tr(p, "gui.items.editor.detail.delete.title")), ctx -> {
      ConfirmDialogWindow confirm = new ConfirmDialogWindow(
          GuiI18n.tr("gui.items.editor.detail.delete.confirm.title"),
          GuiI18n.tr("gui.items.editor.detail.delete.confirm.header",
              Placeholder.unparsed("id", draft.id())),
          List.of(GuiI18n.tr("gui.items.editor.detail.delete.confirm.detail")),
          (player, result) -> {
            if (result != ConfirmDialogWindow.ConfirmResult.CONFIRM) {
              return;
            }
            if (!services.access().canDelete(player)) {
              player.sendMessage(GuiI18n.tr(player, "messages.command.missingPermission",
                  Placeholder.unparsed("permission", "dungeonsreborn.editor.delete")));
              return;
            }
            store.delete(draft.id());
            services.audit().log(EditorAuditEvent.of(EditorAuditAction.DELETE, player.getUniqueId(), player.getName(),
                LOCK_PREFIX + draft.id(), "item"));
          });
      openSubWindow(ctx.player(), confirm);
    }).autoDescribeInLore(false);
  }

  private void refreshAfterChild() {
    Player viewer = viewer() == null ? null : org.bukkit.Bukkit.getPlayer(viewer());
    if (viewer != null) {
      redraw(viewer);
    }
  }

  private ItemStack requireItem(Player player) {
    ItemStack item = draft.item();
    if (item == null || item.getType().isAir()) {
      player.sendMessage(GuiI18n.tr(player, "messages.items.editor.setItemFirst"));
      return null;
    }
    return item.clone();
  }

  private void saveDraft(Player player, String detail) {
    ItemStack item = draft.item();
    if (item != null && !item.getType().isAir()) {
      ItemStack updated = EditorItemLore.applyAbilityLore(item, draft.bindings(), services.engine());
      ItemMarkers.setItemId(updated, draft.id());
      draft.setItem(updated);
    }
    store.save(draft);
    AdminAuditStore.get().record("item:" + draft.id(), player.getName());
    services.audit().log(EditorAuditEvent.of(EditorAuditAction.EDIT, player.getUniqueId(), player.getName(),
        LOCK_PREFIX + draft.id(), detail));
    dirty = true;
    redrawSlot(player, slotAt(0, 7));
  }

  private ItemStack auditItem() {
    AdminAuditStore.Entry entry = AdminAuditStore.get().entry("item:" + draft.id());
    List<Component> lore = new ArrayList<>();
    if (entry == null || entry.timestamp() <= 0L) {
      lore.add(GuiI18n.tr("gui.items.editor.detail.audit.none"));
    } else {
      String when = AUDIT_FORMAT.format(Instant.ofEpochMilli(entry.timestamp()).atZone(ZoneId.systemDefault()));
      lore.add(GuiI18n.tr("gui.items.editor.detail.audit.when",
          Placeholder.unparsed("value", when)));
      lore.add(GuiI18n.tr("gui.items.editor.detail.audit.by",
          Placeholder.unparsed("value", entry.editor())));
    }
    return GuiItems.named(Material.CLOCK, GuiI18n.tr("gui.items.editor.detail.audit.title"), lore);
  }

  private ItemStack dirtyIndicatorItem(Player player) {
    if (dirty) {
      return GuiItems.named(Material.ORANGE_DYE, GuiI18n.tr(player, "gui.items.editor.detail.dirty.title"), List.of(
          GuiI18n.tr(player, "gui.items.editor.detail.dirty.hint")));
    }
    return GuiItems.named(Material.LIME_DYE, GuiI18n.tr(player, "gui.items.editor.detail.clean.title"), List.of(
        GuiI18n.tr(player, "gui.items.editor.detail.clean.hint")));
  }

  private List<Component> bindingsSummaryLore() {
    List<Component> lore = new ArrayList<>();
    lore.add(GuiI18n.tr("gui.items.editor.detail.bindings.hint"));
    List<java.util.Map<String, Object>> bindings = draft.bindings();
    if (bindings.isEmpty()) {
      lore.add(GuiI18n.tr("gui.items.editor.detail.bindings.none"));
      return lore;
    }
    int shown = 0;
    for (java.util.Map<String, Object> binding : bindings) {
      if (shown >= 5) {
        break;
      }
      String icon = bindingIcon(binding);
      String abilityId = String.valueOf(binding.getOrDefault("ability", "unknown"));
      dev.patric.dungeonsreborn.effects.AbilitySpec spec = services.engine().abilitySpec(abilityId);
      String name = spec != null && spec.name() != null && !spec.name().isBlank() ? spec.name() : abilityId;
      lore.add(GuiI18n.tr("gui.items.editor.detail.bindings.entry",
          Placeholder.unparsed("icon", icon),
          Placeholder.unparsed("name", name)));
      String summary = spec != null ? summarize(spec.description()) : null;
      if (summary != null) {
        lore.add(GuiI18n.tr("gui.items.editor.detail.bindings.summary",
            Placeholder.unparsed("text", summary)));
      }
      shown++;
    }
    if (bindings.size() > shown) {
      lore.add(GuiI18n.tr("gui.items.editor.detail.bindings.more",
          Placeholder.unparsed("count", String.valueOf(bindings.size() - shown))));
    }
    return lore;
  }

  private static String summarize(String description) {
    if (description == null || description.isBlank()) {
      return null;
    }
    String line = description.split("\n", 2)[0].trim();
    if (line.length() > 48) {
      return line.substring(0, 45) + "...";
    }
    return line;
  }

  private static String bindingIcon(java.util.Map<String, Object> binding) {
    String type = String.valueOf(binding.getOrDefault("type", "interact")).toLowerCase(Locale.ROOT);
    String click = String.valueOf(binding.getOrDefault("click", "RIGHT_CLICK")).toUpperCase(Locale.ROOT);
    boolean passive = "passive".equals(type) || "PASSIVE".equals(click);
    if (passive) {
      return "[P]";
    }
    return switch (click) {
      case "LEFT_CLICK" -> "[L]";
      case "SHIFT_LEFT", "SHIFT_LEFT_CLICK", "SHIFT_LEFT_CLICK_AIR", "SHIFT_LEFT_CLICK_BLOCK" -> "[SL]";
      case "SHIFT_RIGHT", "SHIFT_RIGHT_CLICK", "SHIFT_RIGHT_CLICK_AIR", "SHIFT_RIGHT_CLICK_BLOCK" -> "[SR]";
      case "RIGHT_CLICK" -> "[R]";
      default -> "[?]";
    };
  }
}
