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
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.components.input.CycleSelector;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;

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
    super(SIZE, GuiMini.mm("<white><bold>Binding</bold></white>"), true);
    this.services = Objects.requireNonNull(services, "services");
    this.store = Objects.requireNonNull(store, "store");
    this.draft = Objects.requireNonNull(draft, "draft");
    this.bindings = Objects.requireNonNull(bindings, "bindings");
    this.index = index;
    this.onCloseRefresh = onCloseRefresh == null ? () -> {
    } : onCloseRefresh;

    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, Component.text("Back"))));

    clickSelector = new CycleSelector<>(List.of(InteractTrigger.RIGHT_CLICK, InteractTrigger.LEFT_CLICK),
        (viewer, value) -> GuiItems.named(Material.ENDER_PEARL, GuiMini.mm("<aqua><bold>Click</bold></aqua>"), List.of(
            GuiMini.mm("<gray>Current:</gray> <white>" + value.name() + "</white>"))))
        .onChange((viewer, value) -> {
          binding().put("type", "interact");
          binding().put("click", value.name());
          saveDraft(viewer, "binding.click");
        });

    setFixedAt(0, 4, new Label(p -> GuiItems.named(Material.TRIPWIRE_HOOK, GuiMini.mm("<gold><bold>Binding #" + (index + 1) + "</bold></gold>"))));
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
      String value = string(binding(), "ability", "");
      List<Component> lore = new java.util.ArrayList<>();
      lore.add(GuiMini.mm("<gray>Current:</gray> <white>" + (value.isBlank() ? "(unset)" : value) + "</white>"));
      if (!value.isBlank()) {
        AbilitySpec spec = services.engine().abilitySpec(value);
        if (spec != null && spec.name() != null && !spec.name().isBlank()) {
          lore.add(GuiMini.mm("<gray>Name:</gray> <white>" + spec.name() + "</white>"));
        }
      }
      return GuiItems.named(Material.BOOK, GuiMini.mm("<aqua><bold>Ability</bold></aqua>"), lore);
    })
        .left(Component.text("Select ability"), ctx -> {
          openSubWindow(ctx.player(), new EditorAbilityPickerMenu(services, (player, abilityId) -> {
            binding().put("ability", abilityId);
            saveDraft(player, "binding.ability");
            redrawSlot(player, slotAt(1, 3));
            GuiSounds.click(player);
          }));
        })
        .right(Component.text("Clear ability"), ctx -> {
          binding().put("ability", "");
          saveDraft(ctx.player(), "binding.ability.clear");
          ctx.window().redrawSlot(ctx.player(), slotAt(1, 3));
          GuiSounds.click(ctx.player());
        });
  }

  private TextButton permissionInput() {
    return new TextButton(
        p -> {
          String value = string(binding(), "permission", "");
          return GuiItems.named(Material.NAME_TAG, GuiMini.mm("<aqua><bold>Permission</bold></aqua>"), List.of(
              GuiMini.mm("<gray>Current:</gray> <white>" + (value.isBlank() ? "(none)" : value) + "</white>")));
        },
        GuiMini.mm("<gray>Enter permission (blank to clear)</gray>"),
        "cancel",
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
          String value = string(binding(), "id", "");
          return GuiItems.named(Material.PAPER, GuiMini.mm("<aqua><bold>Binding Id</bold></aqua>"), List.of(
              GuiMini.mm("<gray>Current:</gray> <white>" + (value.isBlank() ? "(auto)" : value) + "</white>")));
        },
        GuiMini.mm("<gray>Enter binding id (blank for auto)</gray>"),
        "cancel",
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
      boolean enabled = bool(binding(), "requireSneaking", false);
      Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
      return GuiItems.named(mat, GuiMini.mm("<aqua><bold>Sneaking</bold></aqua>"), List.of(
          GuiMini.mm("<gray>Status:</gray> <white>" + (enabled ? "on" : "off") + "</white>")));
    }, ctx -> {
      boolean enabled = bool(binding(), "requireSneaking", false);
      binding().put("requireSneaking", !enabled);
      saveDraft(ctx.player(), "binding.require_sneaking");
      ctx.window().redrawSlot(ctx.player(), slotAt(2, 1));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button cancelEventToggle() {
    return new Button(p -> {
      boolean enabled = bool(binding(), "cancelEvent", true);
      Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
      return GuiItems.named(mat, GuiMini.mm("<aqua><bold>Cancel Event</bold></aqua>"), List.of(
          GuiMini.mm("<gray>Status:</gray> <white>" + (enabled ? "on" : "off") + "</white>")));
    }, ctx -> {
      boolean enabled = bool(binding(), "cancelEvent", true);
      binding().put("cancelEvent", !enabled);
      saveDraft(ctx.player(), "binding.cancel_event");
      ctx.window().redrawSlot(ctx.player(), slotAt(2, 3));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button deleteButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.TRASH, Component.text("Delete")), ctx -> {
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

  private static String string(Map<String, Object> node, String key, String def) {
    Object raw = node.get(key);
    if (raw == null) {
      return def;
    }
    String value = raw.toString();
    return value.isBlank() ? def : value;
  }

  private static boolean bool(Map<String, Object> node, String key, boolean def) {
    Object raw = node.get(key);
    return raw == null ? def : Boolean.parseBoolean(raw.toString());
  }
}
