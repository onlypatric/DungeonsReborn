package dev.patric.dungeonsreborn.effects.editor.menu;

import java.util.List;
import java.util.Objects;

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
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.item.ItemPreview;
import dev.patric.dungeonsreborn.gui.flow.ConfirmDialogWindow;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

public final class EditorItemDetailMenu extends Window {
  private static final int SIZE = 54;
  private static final String LOCK_PREFIX = "item:";

  private final EditorServices services;
  private final EditorItemStore store;
  private final EditorItemDraft draft;
  private final Runnable onCloseRefresh;

  public EditorItemDetailMenu(EditorServices services, EditorItemStore store, EditorItemDraft draft, Runnable onCloseRefresh) {
    super(SIZE, GuiMini.mm("<white><bold>Edit Item</bold></white>"), true);
    this.services = Objects.requireNonNull(services, "services");
    this.store = Objects.requireNonNull(store, "store");
    this.draft = Objects.requireNonNull(draft, "draft");
    this.onCloseRefresh = onCloseRefresh == null ? () -> {
    } : onCloseRefresh;

    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, Component.text("Back"))));

    setFixedAt(0, 4, new Label(p -> GuiItems.named(Material.CHEST, GuiMini.mm("<gold><bold>" + draft.id() + "</bold></gold>"), List.of(
        GuiMini.mm("<gray>Editing item</gray>")))));

    setFixedAt(1, 4, new ItemPreview(p -> draft.item())
        .placeholder(GuiItems.named(Material.GRAY_STAINED_GLASS_PANE, Component.text("No item"))));

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

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> {
      services.locks().release(LOCK_PREFIX + draft.id(), ctx.player().getUniqueId());
      this.onCloseRefresh.run();
      GuiSounds.close(ctx.player());
    });
  }

  private Button setFromHandButton() {
    return new Button(p -> GuiItems.named(Material.ANVIL, GuiMini.mm("<aqua><bold>Use Held Item</bold></aqua>"), List.of(
        GuiMini.mm("<gray>Copy your main-hand item.</gray>"))), ctx -> {
      ItemStack hand = ctx.player().getInventory().getItemInMainHand();
      if (hand == null || hand.getType().isAir()) {
        ctx.player().sendMessage(Component.text("§cHold an item in your main hand."));
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
      Component current = Component.text("(none)");
      if (item != null && !item.getType().isAir()) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
          current = meta.displayName();
        }
      }
      return GuiItems.named(Material.NAME_TAG, GuiMini.mm("<aqua><bold>Name</bold></aqua>"), List.of(
          GuiMini.mm("<gray>Current:</gray>"),
          current));
    }, ctx -> {
      Player player = ctx.player();
      GuiManager.get().prepareTemporaryClose(player);
      ctx.close();
      GuiManager.get().requestText(player,
          new dev.patric.dungeonsreborn.gui.GuiManager.TextRequest(
              GuiMini.mm("<gray>Enter a display name (blank to clear)</gray>"),
              "cancel",
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
      return GuiItems.named(Material.WRITABLE_BOOK, GuiMini.mm("<aqua><bold>Lore</bold></aqua>"), List.of(
          GuiMini.mm("<gray>Lines:</gray> <white>" + (lines == 0 ? "(none)" : lines) + "</white>"),
          GuiMini.mm("<gray>Use \\\\n for new lines.</gray>")));
    }, ctx -> {
      Player player = ctx.player();
      GuiManager.get().prepareTemporaryClose(player);
      ctx.close();
      GuiManager.get().requestText(player,
          new dev.patric.dungeonsreborn.gui.GuiManager.TextRequest(
              GuiMini.mm("<gray>Enter lore (use \\\\n for lines, blank to clear)</gray>"),
              "cancel",
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
    return new Button(p -> GuiItems.named(Material.LIME_DYE, GuiMini.mm("<aqua><bold>Give Item</bold></aqua>"), List.of(
        GuiMini.mm("<gray>Give yourself the item.</gray>"))), ctx -> {
      ItemStack item = draft.item();
      if (item == null || item.getType().isAir()) {
        ctx.player().sendMessage(Component.text("§cItem is not set."));
        return;
      }
      ItemStack given = EditorItemLore.applyAbilityLore(item.clone(), draft.bindings(), services.engine());
      ItemMarkers.setItemId(given, draft.id());
      ctx.player().getInventory().addItem(given);
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button bindingsButton() {
    return new Button(p -> GuiItems.named(Material.TRIPWIRE_HOOK, GuiMini.mm("<aqua><bold>Bindings</bold></aqua>"), List.of(
        GuiMini.mm("<gray>Edit click bindings.</gray>"))), ctx -> {
      openSubWindow(ctx.player(), new EditorItemBindingsMenu(services, store, draft, this::refreshAfterChild));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button glintToggle() {
    return new Button(p -> {
      ItemStack item = draft.item();
      boolean enabled = item != null && EditorItemLore.hasGlint(item);
      Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
      return GuiItems.named(mat, GuiMini.mm("<aqua><bold>Glint</bold></aqua>"), List.of(
          GuiMini.mm("<gray>Status:</gray> <white>" + (enabled ? "on" : "off") + "</white>")));
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
      return GuiItems.named(mat, GuiMini.mm("<aqua><bold>Unbreakable</bold></aqua>"), List.of(
          GuiMini.mm("<gray>Status:</gray> <white>" + (enabled ? "on" : "off") + "</white>")));
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
      return GuiItems.named(mat, GuiMini.mm("<aqua><bold>Hide Attributes</bold></aqua>"), List.of(
          GuiMini.mm("<gray>Status:</gray> <white>" + (enabled ? "on" : "off") + "</white>")));
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
      return GuiItems.named(mat, GuiMini.mm("<aqua><bold>Hide Enchants</bold></aqua>"), List.of(
          GuiMini.mm("<gray>Status:</gray> <white>" + (enabled ? "on" : "off") + "</white>")));
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
    return new Button(p -> GuiItems.named(Material.BARRIER, GuiMini.mm("<red><bold>Clear Item</bold></red>"), List.of(
        GuiMini.mm("<gray>Remove the stored item stack.</gray>"))), ctx -> {
      draft.setItem(null);
      saveDraft(ctx.player(), "item.clear");
      ctx.window().redrawSlot(ctx.player(), slotAt(1, 4));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button reloadButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.INFO, Component.text("Reload YAML")), ctx -> {
      EffectsYamlAbilities yaml = services.yaml();
      if (yaml == null) {
        ctx.player().sendMessage(Component.text("§cYAML loader is not available."));
        return;
      }
      EffectsYamlAbilities.ReloadResult result = yaml.reload();
      if (result.errors().isEmpty()) {
        ctx.player().sendMessage(Component.text("§aReloaded YAML. Abilities: §f" + result.loadedAbilities()
            + " §aItem bindings: §f" + result.loadedItemBindings()));
      } else {
        ctx.player().sendMessage(Component.text("§eReloaded with " + result.errors().size()
            + " errors. Check server logs."));
      }
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button deleteButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.TRASH, Component.text("Delete")), ctx -> {
      ConfirmDialogWindow confirm = new ConfirmDialogWindow(
          GuiMini.mm("<red><bold>Delete Item</bold></red>"),
          GuiMini.mm("<red>Delete " + draft.id() + "?</red>"),
          List.of(GuiMini.mm("<gray>This cannot be undone.</gray>")),
          (player, result) -> {
            if (result != ConfirmDialogWindow.ConfirmResult.CONFIRM) {
              return;
            }
            if (!services.access().canDelete(player)) {
              player.sendMessage(Component.text("§cMissing permission: dungeonsreborn.editor.delete"));
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
      player.sendMessage(Component.text("§cSet an item first."));
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
    services.audit().log(EditorAuditEvent.of(EditorAuditAction.EDIT, player.getUniqueId(), player.getName(),
        LOCK_PREFIX + draft.id(), detail));
  }
}
