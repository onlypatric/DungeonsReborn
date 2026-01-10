package dev.patric.dungeonsreborn.effects.editor.menu;

import java.time.Duration;
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
import dev.patric.dungeonsreborn.gui.components.input.NumericInput;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class EditorAbilityDetailMenu extends Window {
  private static final int SIZE = 54;
  private static final MiniMessage MINI = MiniMessage.miniMessage();
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  private final EditorServices services;
  private final EditorAbilityDraft draft;
  private final Runnable onCloseRefresh;

  public EditorAbilityDetailMenu(EditorServices services, EditorAbilityDraft draft, Runnable onCloseRefresh) {
    super(SIZE, GuiMini.mm("<white><bold>Edit Ability</bold></white>"), true);
    this.services = Objects.requireNonNull(services, "services");
    this.draft = Objects.requireNonNull(draft, "draft");
    this.onCloseRefresh = onCloseRefresh == null ? () -> {
    } : onCloseRefresh;

    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, Component.text("Back"))));

    setFixedAt(0, 4, new Label(p -> GuiItems.named(Material.BOOK, GuiMini.mm("<gold><bold>" + draft.id() + "</bold></gold>"), List.of(
        GuiMini.mm("<gray>Editing draft metadata</gray>")))));

    setFixedAt(1, 1, nameButton());
    setFixedAt(1, 3, descriptionButton());

    NumericInput cooldownInput = cooldownInput();
    cooldownInput.apply(this, Placement.FIXED);

    setFixedAt(2, 1, cooldownKeyButton());
    setFixedAt(2, 3, requirementsButton());
    setFixedAt(2, 5, costsButton());
    setFixedAt(2, 7, triggersButton());
    setFixedAt(3, 1, actionsButton());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> {
      services.locks().release(draft.id(), ctx.player().getUniqueId());
      this.onCloseRefresh.run();
      GuiSounds.close(ctx.player());
    });
  }

  private TextButton nameButton() {
    return new TextButton(
        p -> GuiItems.named(Material.PAPER, GuiMini.mm("<aqua><bold>Name</bold></aqua>"), List.of(
            GuiMini.mm("<gray>Current:</gray> ")
                .append(render(EditorAbilityYaml.name(draft) == null ? "(none)" : EditorAbilityYaml.name(draft))))),
        GuiMini.mm("<gray>Enter a display name</gray>"),
        "cancel",
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (player == null) {
            return;
          }
          EditorAbilityYaml.setName(draft, text);
          saveDraft(player, "name");
          w.redrawSlot(player, slotAt(1, 1));
        },
        true)
            .inputMode(TextButton.InputMode.CHAT);
  }

  private TextButton descriptionButton() {
    return new TextButton(
        p -> {
          String desc = EditorAbilityYaml.description(draft);
          Component preview = desc == null ? Component.text("(none)") : render(desc);
          return GuiItems.named(Material.WRITABLE_BOOK, GuiMini.mm("<aqua><bold>Description</bold></aqua>"), List.of(
              GuiMini.mm("<gray>Current:</gray>"),
              preview,
              GuiMini.mm("<gray>Use \\\\n for line breaks.</gray>")));
        },
        GuiMini.mm("<gray>Enter a description (use \\\\n for line breaks)</gray>"),
        "cancel",
        Duration.ofSeconds(45),
        (w, text) -> {
          Player player = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (player == null) {
            return;
          }
          String normalized = text == null ? "" : text.replace("\\\\n", "\n");
          EditorAbilityYaml.setDescription(draft, normalized);
          saveDraft(player, "description");
          w.redrawSlot(player, slotAt(1, 3));
        },
        true)
            .inputMode(TextButton.InputMode.CHAT);
  }

  private NumericInput cooldownInput() {
    return new NumericInput(1, 5,
        p -> EditorAbilityYaml.cooldownTicks(draft),
        (player, value) -> {
          EditorAbilityYaml.setCooldownTicks(draft, value);
          saveDraft(player, "cooldown ticks");
        })
            .label(GuiMini.mm("<yellow><bold>Cooldown</bold></yellow>"))
            .typingPrompt(GuiMini.mm("<gray>Enter cooldown ticks (0 disables)</gray>"))
            .range(0, 72000)
            .step(5)
            .shiftStep(20);
  }

  private TextButton cooldownKeyButton() {
    return new TextButton(
        p -> GuiItems.named(Material.TRIPWIRE_HOOK, GuiMini.mm("<aqua><bold>Cooldown Key</bold></aqua>"), List.of(
            GuiMini.mm("<gray>Current:</gray> <white>" + nullToNone(EditorAbilityYaml.cooldownKey(draft)) + "</white>"))),
        GuiMini.mm("<gray>Enter a cooldown key (blank to clear)</gray>"),
        "cancel",
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (player == null) {
            return;
          }
          EditorAbilityYaml.setCooldownKey(draft, text);
          saveDraft(player, "cooldown key");
          w.redrawSlot(player, slotAt(2, 1));
        },
        true)
            .inputMode(TextButton.InputMode.CHAT);
  }

  private Button requirementsButton() {
    return new Button(p -> GuiItems.named(Material.LEATHER_BOOTS, GuiMini.mm("<aqua><bold>Requirements</bold></aqua>"), List.of(
        GuiMini.mm("<gray>Edit cast requirements.</gray>"))), ctx -> {
      openSubWindow(ctx.player(), new EditorRequirementsMenu(services, draft, this::refreshAfterChild));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button costsButton() {
    return new Button(p -> GuiItems.named(Material.AMETHYST_SHARD, GuiMini.mm("<aqua><bold>Costs</bold></aqua>"), List.of(
        GuiMini.mm("<gray>Edit cast costs.</gray>"))), ctx -> {
      openSubWindow(ctx.player(), new EditorCostsMenu(services, draft, this::refreshAfterChild));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button triggersButton() {
    return new Button(p -> GuiItems.named(Material.TRIPWIRE_HOOK, GuiMini.mm("<aqua><bold>Bindings</bold></aqua>"), List.of(
        GuiMini.mm("<gray>Edit click bindings.</gray>"))), ctx -> {
      openSubWindow(ctx.player(), new EditorBindingsMenu(services, draft, this::refreshAfterChild));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button actionsButton() {
    return new Button(p -> GuiItems.named(Material.REPEATER, GuiMini.mm("<aqua><bold>Actions</bold></aqua>"), List.of(
        GuiMini.mm("<gray>Edit action graph.</gray>"))), ctx -> {
      Map<String, Object> root = dev.patric.dungeonsreborn.effects.editor.EditorActionTree.root(draft);
      List<Map<String, Object>> list = dev.patric.dungeonsreborn.effects.editor.EditorActionTree.rootActions(draft);
      openSubWindow(ctx.player(), new EditorActionGraphMenu(services, draft, root, list, "Actions", this::refreshAfterChild));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private void refreshAfterChild() {
    Player viewer = viewer() == null ? null : org.bukkit.Bukkit.getPlayer(viewer());
    if (viewer != null) {
      redrawSlot(viewer, slotAt(2, 3));
      redrawSlot(viewer, slotAt(2, 5));
    }
  }

  private void saveDraft(Player player, String detail) {
    services.drafts().save(draft);
    services.audit().log(EditorAuditEvent.of(EditorAuditAction.EDIT, player.getUniqueId(), player.getName(), draft.id(), detail));
  }

  private static String nullToNone(String value) {
    return value == null || value.isBlank() ? "(none)" : value;
  }

  private static Component render(String raw) {
    if (raw == null) {
      return Component.text("(none)");
    }
    if (raw.indexOf('§') >= 0) {
      return LEGACY.deserialize(raw);
    }
    try {
      return MINI.deserialize(raw);
    } catch (Exception ignored) {
      return LEGACY.deserialize(raw.replace('&', '§'));
    }
  }
}
