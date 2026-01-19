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

public final class EditorRequirementsMenu extends Window {
  private static final int SIZE = 27;

  private final EditorServices services;
  private final EditorAbilityDraft draft;
  private final Runnable onCloseRefresh;

  public EditorRequirementsMenu(EditorServices services, EditorAbilityDraft draft, Runnable onCloseRefresh) {
    super(SIZE, GuiI18n.tr("gui.effects.editor.requirements.title"), true);
    this.services = Objects.requireNonNull(services, "services");
    this.draft = Objects.requireNonNull(draft, "draft");
    this.onCloseRefresh = onCloseRefresh == null ? () -> {
    } : onCloseRefresh;

    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));
    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, GuiI18n.tr(p, "gui.button.back"))));

    setFixedAt(0, 4, new Label(GuiItems.named(Material.LEATHER_BOOTS, GuiI18n.tr("gui.effects.editor.requirements.header.title"))));

    setFixedAt(1, 1, sneakingToggle());
    setFixedAt(1, 3, permissionInput());
    setFixedAt(1, 5, itemTagInput());
    setFixedAt(2, 4, clearAll());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> {
      this.onCloseRefresh.run();
      GuiSounds.close(ctx.player());
    });
  }

  private Button sneakingToggle() {
    return new Button(p -> {
      boolean enabled = EditorAbilityYaml.findByType(EditorAbilityYaml.requirements(draft), "sneaking") != null;
      Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
      return GuiItems.named(mat, GuiI18n.tr(p, "gui.effects.editor.requirements.sneaking.title"), List.of(
          GuiI18n.tr(p, "gui.effects.editor.requirements.sneaking.hint"),
          GuiI18n.tr(p, "gui.effects.editor.requirements.sneaking.status",
              Placeholder.component("value", GuiI18n.tr(p, enabled
                  ? "gui.common.status.enabled"
                  : "gui.common.status.disabled")))));
    }, ctx -> {
      List<Map<String, Object>> reqs = EditorAbilityYaml.requirements(draft);
      boolean enabled = EditorAbilityYaml.findByType(reqs, "sneaking") != null;
      if (enabled) {
        EditorAbilityYaml.replaceByType(reqs, "sneaking", null);
      } else {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "sneaking");
        EditorAbilityYaml.replaceByType(reqs, "sneaking", entry);
      }
      EditorAbilityYaml.writeRequirements(draft, reqs);
      saveDraft(ctx.player(), "requirements.sneaking");
      ctx.window().redrawSlot(ctx.player(), slotAt(1, 1));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private TextButton permissionInput() {
    return new TextButton(
        p -> {
          Map<String, Object> entry = EditorAbilityYaml.findByType(EditorAbilityYaml.requirements(draft), "permission");
          String value = entry == null ? null : String.valueOf(entry.get("permission"));
          return GuiItems.named(Material.NAME_TAG, GuiI18n.tr(p, "gui.effects.editor.requirements.permission.title"), List.of(
              GuiI18n.tr(p, "gui.effects.editor.requirements.permission.hint"),
              GuiI18n.tr(p, "gui.effects.editor.requirements.permission.current",
                  Placeholder.unparsed("value", value == null || value.isBlank()
                      ? GuiI18n.str(p, "gui.common.none")
                      : value))));
        },
        GuiI18n.tr("gui.effects.editor.requirements.permission.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (player == null) {
            return;
          }
          List<Map<String, Object>> reqs = EditorAbilityYaml.requirements(draft);
          if (text == null || text.isBlank()) {
            EditorAbilityYaml.replaceByType(reqs, "permission", null);
          } else {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "permission");
            entry.put("permission", text.trim());
            EditorAbilityYaml.replaceByType(reqs, "permission", entry);
          }
          EditorAbilityYaml.writeRequirements(draft, reqs);
          saveDraft(player, "requirements.permission");
          w.redrawSlot(player, slotAt(1, 3));
        },
        true)
            .inputMode(TextButton.InputMode.CHAT);
  }

  private TextButton itemTagInput() {
    return new TextButton(
        p -> {
          Map<String, Object> entry = EditorAbilityYaml.findByType(EditorAbilityYaml.requirements(draft), "has_item_tag");
          String value = entry == null ? null : String.valueOf(entry.get("key"));
          return GuiItems.named(Material.TRIPWIRE_HOOK, GuiI18n.tr(p, "gui.effects.editor.requirements.itemTag.title"), List.of(
              GuiI18n.tr(p, "gui.effects.editor.requirements.itemTag.hint"),
              GuiI18n.tr(p, "gui.effects.editor.requirements.itemTag.current",
                  Placeholder.unparsed("value", value == null || value.isBlank()
                      ? GuiI18n.str(p, "gui.common.none")
                      : value))));
        },
        GuiI18n.tr("gui.effects.editor.requirements.itemTag.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (player == null) {
            return;
          }
          List<Map<String, Object>> reqs = EditorAbilityYaml.requirements(draft);
          if (text == null || text.isBlank()) {
            EditorAbilityYaml.replaceByType(reqs, "has_item_tag", null);
          } else {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "has_item_tag");
            entry.put("key", text.trim());
            EditorAbilityYaml.replaceByType(reqs, "has_item_tag", entry);
          }
          EditorAbilityYaml.writeRequirements(draft, reqs);
          saveDraft(player, "requirements.item_tag");
          w.redrawSlot(player, slotAt(1, 5));
        },
        true)
            .inputMode(TextButton.InputMode.CHAT);
  }

  private Button clearAll() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.TRASH, GuiI18n.tr(p, "gui.effects.editor.requirements.clear")), ctx -> {
      EditorAbilityYaml.writeRequirements(draft, List.of());
      saveDraft(ctx.player(), "requirements.clear");
      ctx.window().redraw(ctx.player());
    }).autoDescribeInLore(false);
  }

  private void saveDraft(Player player, String detail) {
    services.drafts().save(draft);
    services.audit().log(EditorAuditEvent.of(EditorAuditAction.EDIT, player.getUniqueId(), player.getName(), draft.id(), detail));
  }
}
