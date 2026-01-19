package dev.patric.dungeonsreborn.effects.editor.menu;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.effects.editor.EditorAbilityDraft;
import dev.patric.dungeonsreborn.effects.editor.EditorAbilityYaml;
import dev.patric.dungeonsreborn.effects.editor.EditorAuditAction;
import dev.patric.dungeonsreborn.effects.editor.EditorAuditEvent;
import dev.patric.dungeonsreborn.effects.editor.EditorServices;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class EditorCostsMenu extends Window {
  private static final int SIZE = 27;

  private final EditorServices services;
  private final EditorAbilityDraft draft;
  private final Runnable onCloseRefresh;

  public EditorCostsMenu(EditorServices services, EditorAbilityDraft draft, Runnable onCloseRefresh) {
    super(SIZE, GuiI18n.tr("gui.effects.editor.costs.title"), true);
    this.services = Objects.requireNonNull(services, "services");
    this.draft = Objects.requireNonNull(draft, "draft");
    this.onCloseRefresh = onCloseRefresh == null ? () -> {
    } : onCloseRefresh;

    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));
    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, GuiI18n.tr(p, "gui.button.back"))));

    setFixedAt(0, 4, new Label(GuiItems.named(Material.AMETHYST_SHARD, GuiI18n.tr("gui.effects.editor.costs.header.title"))));

    setFixedAt(1, 1, manaCost());
    setFixedAt(1, 3, consumeCost());
    setFixedAt(1, 5, durabilityCost());
    setFixedAt(2, 5, allowBreakToggle());
    setFixedAt(2, 4, clearAll());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> {
      this.onCloseRefresh.run();
      GuiSounds.close(ctx.player());
    });
  }

  private TextButton manaCost() {
    return new TextButton(
        p -> {
          Map<String, Object> entry = EditorAbilityYaml.findByType(EditorAbilityYaml.costs(draft), "mana");
          String value = entry == null ? null : String.valueOf(entry.get("amount"));
          return GuiItems.named(Material.LAPIS_LAZULI, GuiI18n.tr(p, "gui.effects.editor.costs.mana.title"), List.of(
              GuiI18n.tr(p, "gui.effects.editor.costs.mana.hint"),
              GuiI18n.tr(p, "gui.effects.editor.costs.mana.current",
                  Placeholder.unparsed("value", value == null ? GuiI18n.str(p, "gui.common.none") : value))));
        },
        GuiI18n.tr("gui.effects.editor.costs.mana.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (player == null) {
            return;
          }
          List<Map<String, Object>> costs = EditorAbilityYaml.costs(draft);
          double value = parseDouble(text);
          if (!(value > 0.0)) {
            EditorAbilityYaml.replaceByType(costs, "mana", null);
          } else {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "mana");
            entry.put("amount", value);
            EditorAbilityYaml.replaceByType(costs, "mana", entry);
          }
          EditorAbilityYaml.writeCosts(draft, costs);
          saveDraft(player, "costs.mana");
          w.redrawSlot(player, slotAt(1, 1));
        },
        true)
            .inputMode(TextButton.InputMode.CHAT)
            .validate((window, player, input) -> {
              if (input == null || input.isBlank()) {
                return null;
              }
              try {
                Double.parseDouble(input);
                return null;
              } catch (NumberFormatException ex) {
                return GuiI18n.tr(player, "gui.textInput.error.integer");
              }
            });
  }

  private TextButton consumeCost() {
    return new TextButton(
        p -> {
          Map<String, Object> entry = EditorAbilityYaml.findByType(EditorAbilityYaml.costs(draft), "consume_item");
          String value = entry == null ? null : String.valueOf(entry.get("amount"));
          return GuiItems.named(Material.CHEST, GuiI18n.tr(p, "gui.effects.editor.costs.consume.title"), List.of(
              GuiI18n.tr(p, "gui.effects.editor.costs.consume.hint"),
              GuiI18n.tr(p, "gui.effects.editor.costs.consume.current",
                  Placeholder.unparsed("value", value == null ? GuiI18n.str(p, "gui.common.none") : value))));
        },
        GuiI18n.tr("gui.effects.editor.costs.consume.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (player == null) {
            return;
          }
          List<Map<String, Object>> costs = EditorAbilityYaml.costs(draft);
          int value = parseInt(text);
          if (value <= 0) {
            EditorAbilityYaml.replaceByType(costs, "consume_item", null);
          } else {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "consume_item");
            entry.put("amount", value);
            EditorAbilityYaml.replaceByType(costs, "consume_item", entry);
          }
          EditorAbilityYaml.writeCosts(draft, costs);
          saveDraft(player, "costs.consume_item");
          w.redrawSlot(player, slotAt(1, 3));
        },
        true)
            .inputMode(TextButton.InputMode.CHAT)
            .integer();
  }

  private TextButton durabilityCost() {
    return new TextButton(
        p -> {
          Map<String, Object> entry = EditorAbilityYaml.findByType(EditorAbilityYaml.costs(draft), "durability");
          String value = entry == null ? null : String.valueOf(entry.get("damage"));
          return GuiItems.named(Material.ANVIL, GuiI18n.tr(p, "gui.effects.editor.costs.durability.title"), List.of(
              GuiI18n.tr(p, "gui.effects.editor.costs.durability.hint"),
              GuiI18n.tr(p, "gui.effects.editor.costs.durability.current",
                  Placeholder.unparsed("value", value == null ? GuiI18n.str(p, "gui.common.none") : value))));
        },
        GuiI18n.tr("gui.effects.editor.costs.durability.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (player == null) {
            return;
          }
          List<Map<String, Object>> costs = EditorAbilityYaml.costs(draft);
          int value = parseInt(text);
          if (value <= 0) {
            EditorAbilityYaml.replaceByType(costs, "durability", null);
          } else {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "durability");
            entry.put("damage", value);
            entry.put("allowBreak", allowBreakValue(costs));
            EditorAbilityYaml.replaceByType(costs, "durability", entry);
          }
          EditorAbilityYaml.writeCosts(draft, costs);
          saveDraft(player, "costs.durability");
          w.redrawSlot(player, slotAt(1, 5));
          w.redrawSlot(player, slotAt(2, 5));
        },
        true)
            .inputMode(TextButton.InputMode.CHAT)
            .integer();
  }

  private Button allowBreakToggle() {
    return new Button(p -> {
      List<Map<String, Object>> costs = EditorAbilityYaml.costs(draft);
      Map<String, Object> entry = EditorAbilityYaml.findByType(costs, "durability");
      boolean enabled = entry != null && Boolean.TRUE.equals(entry.get("allowBreak"));
      Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
      return GuiItems.named(mat, GuiI18n.tr(p, "gui.effects.editor.costs.allowBreak.title"), List.of(
          GuiI18n.tr(p, "gui.effects.editor.costs.allowBreak.hint"),
          GuiI18n.tr(p, "gui.effects.editor.costs.allowBreak.status",
              Placeholder.component("value", GuiI18n.tr(p, enabled
                  ? "gui.common.status.enabled"
                  : "gui.common.status.disabled")))));
    }, ctx -> {
      List<Map<String, Object>> costs = EditorAbilityYaml.costs(draft);
      Map<String, Object> entry = EditorAbilityYaml.findByType(costs, "durability");
      if (entry == null) {
        ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "gui.effects.editor.costs.allowBreak.missing"));
        return;
      }
      boolean enabled = Boolean.TRUE.equals(entry.get("allowBreak"));
      entry.put("allowBreak", !enabled);
      EditorAbilityYaml.replaceByType(costs, "durability", entry);
      EditorAbilityYaml.writeCosts(draft, costs);
      saveDraft(ctx.player(), "costs.allow_break");
      ctx.window().redrawSlot(ctx.player(), slotAt(2, 5));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button clearAll() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.TRASH, GuiI18n.tr(p, "gui.effects.editor.costs.clear")), ctx -> {
      EditorAbilityYaml.writeCosts(draft, List.of());
      saveDraft(ctx.player(), "costs.clear");
      ctx.window().redraw(ctx.player());
    }).autoDescribeInLore(false);
  }

  private void saveDraft(Player player, String detail) {
    services.drafts().save(draft);
    services.audit().log(EditorAuditEvent.of(EditorAuditAction.EDIT, player.getUniqueId(), player.getName(), draft.id(), detail));
  }

  private static double parseDouble(String text) {
    if (text == null || text.isBlank()) {
      return 0.0;
    }
    try {
      return Double.parseDouble(text.trim());
    } catch (NumberFormatException ex) {
      return 0.0;
    }
  }

  private static int parseInt(String text) {
    if (text == null || text.isBlank()) {
      return 0;
    }
    try {
      return Integer.parseInt(text.trim());
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  private static boolean allowBreakValue(List<Map<String, Object>> costs) {
    Map<String, Object> entry = EditorAbilityYaml.findByType(costs, "durability");
    return entry != null && Boolean.TRUE.equals(entry.get("allowBreak"));
  }
}
