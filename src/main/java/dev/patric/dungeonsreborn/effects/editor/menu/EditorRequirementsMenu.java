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
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;

public final class EditorRequirementsMenu extends Window {
  private static final int SIZE = 27;

  private final EditorServices services;
  private final EditorAbilityDraft draft;
  private final Runnable onCloseRefresh;

  public EditorRequirementsMenu(EditorServices services, EditorAbilityDraft draft, Runnable onCloseRefresh) {
    super(SIZE, GuiMini.mm("<white><bold>Requirements</bold></white>"), true);
    this.services = Objects.requireNonNull(services, "services");
    this.draft = Objects.requireNonNull(draft, "draft");
    this.onCloseRefresh = onCloseRefresh == null ? () -> {
    } : onCloseRefresh;

    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));
    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, Component.text("Back"))));

    setFixedAt(0, 4, new Label(GuiItems.named(Material.LEATHER_BOOTS, GuiMini.mm("<gold><bold>Requirements</bold></gold>"))));

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
      return GuiItems.named(mat, GuiMini.mm("<aqua><bold>Sneaking</bold></aqua>"), List.of(
          GuiMini.mm("<gray>Require sneaking to cast.</gray>"),
          GuiMini.mm("<gray>Status:</gray> <white>" + (enabled ? "on" : "off") + "</white>")));
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
          return GuiItems.named(Material.NAME_TAG, GuiMini.mm("<aqua><bold>Permission</bold></aqua>"), List.of(
              GuiMini.mm("<gray>Required permission node.</gray>"),
              GuiMini.mm("<gray>Current:</gray> <white>" + (value == null || value.isBlank() ? "(none)" : value) + "</white>")));
        },
        GuiMini.mm("<gray>Enter a permission (blank to clear)</gray>"),
        "cancel",
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
          return GuiItems.named(Material.TRIPWIRE_HOOK, GuiMini.mm("<aqua><bold>Item Tag</bold></aqua>"), List.of(
              GuiMini.mm("<gray>Require item tag (NamespacedKey).</gray>"),
              GuiMini.mm("<gray>Current:</gray> <white>" + (value == null || value.isBlank() ? "(none)" : value) + "</white>")));
        },
        GuiMini.mm("<gray>Enter item tag key (blank to clear)</gray>"),
        "cancel",
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
    return new Button(p -> GuiButtons.item(GuiButtons.Type.TRASH, Component.text("Clear All")), ctx -> {
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
