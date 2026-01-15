package dev.patric.dungeonsreborn.effects.editor.menu;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.effects.AbilitySpec;
import dev.patric.dungeonsreborn.effects.editor.EditorAuditAction;
import dev.patric.dungeonsreborn.effects.editor.EditorAuditEvent;
import dev.patric.dungeonsreborn.effects.editor.EditorItemDraft;
import dev.patric.dungeonsreborn.effects.editor.EditorItemStore;
import dev.patric.dungeonsreborn.effects.editor.EditorServices;
import dev.patric.dungeonsreborn.effects.integration.InteractTrigger;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.util.YamlValues;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.components.input.CycleSelector;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class EditorItemBindingDetailMenu extends Window {
  private static final int SIZE = 54;
  private static final String LOCK_PREFIX = "item:";

  private final CycleSelector<InteractTrigger> clickSelector;
  private final EditorServices services;
  private final EditorItemStore store;
  private final EditorItemDraft draft;
  private final List<Map<String, Object>> bindings;
  private final int index;
  private final Runnable onCloseRefresh;

  public EditorItemBindingDetailMenu(EditorServices services, EditorItemStore store, EditorItemDraft draft,
      List<Map<String, Object>> bindings, int index, Runnable onCloseRefresh) {
    super(SIZE, GuiI18n.tr("gui.items.editor.bindingDetail.title"), true);
    this.services = Objects.requireNonNull(services, "services");
    this.store = Objects.requireNonNull(store, "store");
    this.draft = Objects.requireNonNull(draft, "draft");
    this.bindings = Objects.requireNonNull(bindings, "bindings");
    this.index = index;
    this.onCloseRefresh = onCloseRefresh == null ? () -> {
    } : onCloseRefresh;

    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, GuiI18n.tr(p, "gui.button.back"))));

    clickSelector = new CycleSelector<>(List.of(InteractTrigger.RIGHT_CLICK, InteractTrigger.LEFT_CLICK),
        (viewer, value) -> GuiItems.named(Material.ENDER_PEARL, GuiI18n.tr(viewer, "gui.items.editor.bindingDetail.click.title"), List.of(
            GuiI18n.tr(viewer, "gui.items.editor.bindingDetail.click.current", Placeholder.unparsed("value", value.name())))))
        .onChange((viewer, value) -> {
          binding().put("type", "interact");
          binding().put("click", value.name());
          saveDraft(viewer, "binding.click");
        });

    setFixedAt(0, 4, new Label(p -> GuiItems.named(Material.TRIPWIRE_HOOK, GuiI18n.tr(p, "gui.items.editor.bindingDetail.header.title",
        Placeholder.unparsed("index", String.valueOf(index + 1))))));
    setFixedAt(1, 1, clickSelector);
    setFixedAt(1, 3, abilityInput());
    setFixedAt(1, 5, permissionInput());
    setFixedAt(2, 1, sneakingToggle());
    setFixedAt(2, 3, cancelEventToggle());
    setFixedAt(2, 5, bindingIdInput());
    setFixedAt(2, 7, deleteButton());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> {
      this.onCloseRefresh.run();
      GuiSounds.close(ctx.player());
    });
  }

  @Override
  protected void build(Player player) {
    if (index < 0 || index >= bindings.size()) {
      return;
    }
    clickSelector.select(player, currentClick());
  }

  private Map<String, Object> binding() {
    if (index < 0 || index >= bindings.size()) {
      return Map.of();
    }
    return bindings.get(index);
  }

  private InteractTrigger currentClick() {
    String current = String.valueOf(binding().getOrDefault("click", InteractTrigger.RIGHT_CLICK.name()));
    return current.equalsIgnoreCase("LEFT_CLICK") ? InteractTrigger.LEFT_CLICK : InteractTrigger.RIGHT_CLICK;
  }

  private Button abilityInput() {
    return new Button(p -> {
      String value = YamlValues.string(binding(), "ability", "");
      List<Component> lore = new java.util.ArrayList<>();
      lore.add(GuiI18n.tr(p, "gui.items.editor.bindingDetail.ability.current",
          Placeholder.component("value", value.isBlank() ? GuiI18n.tr(p, "gui.common.none") : Component.text(value))));
      if (!value.isBlank()) {
        AbilitySpec spec = services.engine().abilitySpec(value);
        if (spec != null && spec.name() != null && !spec.name().isBlank()) {
          lore.add(GuiI18n.tr(p, "gui.items.editor.bindingDetail.ability.name",
              Placeholder.unparsed("value", spec.name())));
        }
      }
      return GuiItems.named(Material.BOOK, GuiI18n.tr(p, "gui.items.editor.bindingDetail.ability.title"), lore);
    })
        .left(GuiI18n.tr("gui.items.editor.bindingDetail.ability.select"), ctx -> {
          openSubWindow(ctx.player(), new EditorAbilityPickerMenu(services, (player, abilityId) -> {
            binding().put("ability", abilityId);
            saveDraft(player, "binding.ability");
            redrawSlot(player, slotAt(1, 3));
            GuiSounds.click(player);
          }));
        })
        .right(GuiI18n.tr("gui.items.editor.bindingDetail.ability.clear"), ctx -> {
          binding().put("ability", "");
          saveDraft(ctx.player(), "binding.ability.clear");
          ctx.window().redrawSlot(ctx.player(), slotAt(1, 3));
          GuiSounds.click(ctx.player());
        });
  }

  private TextButton permissionInput() {
    return new TextButton(
        p -> {
          String value = YamlValues.string(binding(), "permission", "");
          return GuiItems.named(Material.NAME_TAG, GuiI18n.tr(p, "gui.items.editor.bindingDetail.permission.title"), List.of(
              GuiI18n.tr(p, "gui.items.editor.bindingDetail.permission.current",
                  Placeholder.component("value", value.isBlank() ? GuiI18n.tr(p, "gui.common.none") : Component.text(value)))));
        },
        GuiI18n.tr("gui.items.editor.bindingDetail.permission.prompt"),
        Locales.text(null, "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (player == null) {
            return;
          }
          if (text == null || text.isBlank()) {
            binding().remove("permission");
          } else {
            binding().put("permission", text.trim());
          }
          saveDraft(player, "binding.permission");
          w.redrawSlot(player, slotAt(1, 5));
        },
        true)
            .inputMode(TextButton.InputMode.CHAT);
  }

  private TextButton bindingIdInput() {
    return new TextButton(
        p -> {
          String value = YamlValues.string(binding(), "id", "");
          return GuiItems.named(Material.PAPER, GuiI18n.tr(p, "gui.items.editor.bindingDetail.id.title"), List.of(
              GuiI18n.tr(p, "gui.items.editor.bindingDetail.id.current",
                  Placeholder.component("value", value.isBlank()
                      ? GuiI18n.tr(p, "gui.items.editor.bindingDetail.id.auto")
                      : Component.text(value)))));
        },
        GuiI18n.tr("gui.items.editor.bindingDetail.id.prompt"),
        Locales.text(null, "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (player == null) {
            return;
          }
          if (text == null || text.isBlank()) {
            binding().remove("id");
          } else {
            binding().put("id", text.trim());
          }
          saveDraft(player, "binding.id");
          w.redrawSlot(player, slotAt(2, 5));
        },
        true)
            .inputMode(TextButton.InputMode.CHAT);
  }

  private Button sneakingToggle() {
    return new Button(p -> {
      boolean enabled = YamlValues.bool(binding(), "requireSneaking", false);
      Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
      return GuiItems.named(mat, GuiI18n.tr(p, "gui.items.editor.bindingDetail.sneak.title"), List.of(
          GuiI18n.tr(p, "gui.common.line.status", Placeholder.component("value",
              GuiI18n.tr(p, enabled ? "gui.common.status.enabled" : "gui.common.status.disabled")))));
    }, ctx -> {
      boolean enabled = YamlValues.bool(binding(), "requireSneaking", false);
      binding().put("requireSneaking", !enabled);
      saveDraft(ctx.player(), "binding.require_sneaking");
      ctx.window().redrawSlot(ctx.player(), slotAt(2, 1));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button cancelEventToggle() {
    return new Button(p -> {
      boolean enabled = YamlValues.bool(binding(), "cancelEvent", true);
      Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
      return GuiItems.named(mat, GuiI18n.tr(p, "gui.items.editor.bindingDetail.cancel.title"), List.of(
          GuiI18n.tr(p, "gui.common.line.status", Placeholder.component("value",
              GuiI18n.tr(p, enabled ? "gui.common.status.enabled" : "gui.common.status.disabled")))));
    }, ctx -> {
      boolean enabled = YamlValues.bool(binding(), "cancelEvent", true);
      binding().put("cancelEvent", !enabled);
      saveDraft(ctx.player(), "binding.cancel_event");
      ctx.window().redrawSlot(ctx.player(), slotAt(2, 3));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button deleteButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.TRASH, GuiI18n.tr(p, "gui.items.editor.bindingDetail.delete.title")), ctx -> {
      if (index < 0 || index >= bindings.size()) {
        return;
      }
      bindings.remove(index);
      saveDraft(ctx.player(), "binding.delete");
      ctx.close();
    }).autoDescribeInLore(false);
  }

  private void saveDraft(Player player, String detail) {
    draft.setBindings(bindings);
    store.save(draft);
    services.audit().log(EditorAuditEvent.of(EditorAuditAction.EDIT, player.getUniqueId(), player.getName(),
        LOCK_PREFIX + draft.id(), detail));
  }

}
