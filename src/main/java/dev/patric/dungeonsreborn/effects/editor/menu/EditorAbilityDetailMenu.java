package dev.patric.dungeonsreborn.effects.editor.menu;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.editor.EditorAbilityDraft;
import dev.patric.dungeonsreborn.effects.editor.EditorAbilityYaml;
import dev.patric.dungeonsreborn.effects.editor.EditorAuditAction;
import dev.patric.dungeonsreborn.effects.editor.EditorAuditEvent;
import dev.patric.dungeonsreborn.effects.editor.EditorServices;
import dev.patric.dungeonsreborn.admin.AdminAuditStore;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.components.input.NumericInput;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class EditorAbilityDetailMenu extends Window {
  private static final int SIZE = 54;
  private static final MiniMessage MINI = MiniMessage.miniMessage();
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
  private static final DateTimeFormatter AUDIT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final EditorServices services;
  private final EditorAbilityDraft draft;
  private final Runnable onCloseRefresh;
  private boolean dirty;

  public EditorAbilityDetailMenu(EditorServices services, EditorAbilityDraft draft, Runnable onCloseRefresh) {
    super(SIZE, GuiI18n.tr("gui.effects.editor.detail.title"), true);
    this.services = Objects.requireNonNull(services, "services");
    this.draft = Objects.requireNonNull(draft, "draft");
    this.onCloseRefresh = onCloseRefresh == null ? () -> {
    } : onCloseRefresh;

    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, GuiI18n.tr(p, "gui.button.back"))));

    setFixedAt(0, 1, new Label(this::validationBannerItem));
    setFixedAt(0, 4, new Label(p -> GuiItems.named(Material.BOOK,
        GuiI18n.tr(p, "gui.effects.editor.detail.header.title",
            Placeholder.unparsed("id", draft.id())),
        List.of(GuiI18n.tr(p, "gui.effects.editor.detail.header.hint")))));
    setFixedAt(0, 6, new Label(this::auditItem));
    setFixedAt(0, 7, new Label(this::dirtyIndicatorItem));

    setFixedAt(1, 1, nameButton());
    setFixedAt(1, 3, descriptionButton());

    NumericInput cooldownInput = cooldownInput();
    cooldownInput.apply(this, Placement.FIXED);

    setFixedAt(2, 1, cooldownKeyButton());
    setFixedAt(2, 3, requirementsButton());
    setFixedAt(2, 5, costsButton());
    setFixedAt(2, 7, triggersButton());
    setFixedAt(3, 1, actionsButton());
    setFixedAt(3, 7, publishButton());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> {
      services.locks().release(draft.id(), ctx.player().getUniqueId());
      this.onCloseRefresh.run();
      GuiSounds.close(ctx.player());
    });
  }

  private TextButton nameButton() {
    return new TextButton(
        p -> GuiItems.named(Material.PAPER, GuiI18n.tr(p, "gui.effects.editor.detail.name.title"), List.of(
            GuiI18n.tr(p, "gui.effects.editor.detail.name.current")
                .append(render(EditorAbilityYaml.name(draft) == null
                    ? Locales.text(p, "gui.common.none")
                    : EditorAbilityYaml.name(draft))))),
        GuiI18n.tr("gui.effects.editor.detail.name.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
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
          Component preview = desc == null ? GuiI18n.tr(p, "gui.common.none") : render(desc);
          return GuiItems.named(Material.WRITABLE_BOOK, GuiI18n.tr(p, "gui.effects.editor.detail.description.title"), List.of(
              GuiI18n.tr(p, "gui.effects.editor.detail.description.current"),
              preview,
              GuiI18n.tr(p, "gui.effects.editor.detail.description.lineHint")));
        },
        GuiI18n.tr("gui.effects.editor.detail.description.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
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
            .label(GuiI18n.tr("gui.effects.editor.detail.cooldown.title"))
            .typingPrompt(GuiI18n.tr("gui.effects.editor.detail.cooldown.prompt"))
            .range(0, 72000)
            .step(5)
            .shiftStep(20);
  }

  private TextButton cooldownKeyButton() {
    return new TextButton(
        p -> GuiItems.named(Material.TRIPWIRE_HOOK, GuiI18n.tr(p, "gui.effects.editor.detail.cooldownKey.title"), List.of(
            GuiI18n.tr(p, "gui.effects.editor.detail.cooldownKey.current",
                Placeholder.unparsed("value", nullToNone(EditorAbilityYaml.cooldownKey(draft)))))),
        GuiI18n.tr("gui.effects.editor.detail.cooldownKey.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
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
    return new Button(p -> GuiItems.named(Material.LEATHER_BOOTS, GuiI18n.tr(p, "gui.effects.editor.detail.requirements.title"), List.of(
        GuiI18n.tr(p, "gui.effects.editor.detail.requirements.hint"))), ctx -> {
      openSubWindow(ctx.player(), new EditorRequirementsMenu(services, draft, this::refreshAfterChild));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button costsButton() {
    return new Button(p -> GuiItems.named(Material.AMETHYST_SHARD, GuiI18n.tr(p, "gui.effects.editor.detail.costs.title"), List.of(
        GuiI18n.tr(p, "gui.effects.editor.detail.costs.hint"))), ctx -> {
      openSubWindow(ctx.player(), new EditorCostsMenu(services, draft, this::refreshAfterChild));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button triggersButton() {
    return new Button(p -> GuiItems.named(Material.TRIPWIRE_HOOK, GuiI18n.tr(p, "gui.effects.editor.detail.bindings.title"), List.of(
        GuiI18n.tr(p, "gui.effects.editor.detail.bindings.hint"))), ctx -> {
      openSubWindow(ctx.player(), new EditorBindingsMenu(services, draft, this::refreshAfterChild));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button actionsButton() {
    return new Button(p -> GuiItems.named(Material.REPEATER, GuiI18n.tr(p, "gui.effects.editor.detail.actions.title"), actionSummaryLore(p)), ctx -> {
      Map<String, Object> root = dev.patric.dungeonsreborn.effects.editor.EditorActionTree.root(draft);
      List<Map<String, Object>> list = dev.patric.dungeonsreborn.effects.editor.EditorActionTree.rootActions(draft);
      openSubWindow(ctx.player(),
          new EditorActionGraphMenu(services, draft, root, list, GuiI18n.str(GuiI18n.defaultLocale(), "gui.effects.editor.detail.actions.graphTitle"),
              this::refreshAfterChild));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button publishButton() {
    return new Button(p -> GuiItems.named(Material.LIME_DYE, GuiI18n.tr(p, "gui.effects.editor.detail.publish.title"), List.of(
        GuiI18n.tr(p, "gui.effects.editor.detail.publish.hint1"),
        GuiI18n.tr(p, "gui.effects.editor.detail.publish.hint2"))), ctx -> {
      Player player = ctx.player();
      try {
        publishDraft();
        services.yaml().reload();
        services.audit().log(EditorAuditEvent.of(EditorAuditAction.PUBLISH, player.getUniqueId(), player.getName(), draft.id(), "publish"));
        dirty = false;
        redrawSlot(player, slotAt(0, 7));
        player.sendMessage(Locales.component(player, "messages.effects.editor.publish.success"));
      } catch (Exception ex) {
        player.sendMessage(Locales.component(player, "messages.effects.editor.publish.failure",
            Locales.placeholders("error", ex.getMessage())));
      }
      GuiSounds.click(player);
    }).autoDescribeInLore(false);
  }

  private void refreshAfterChild() {
    Player viewer = viewer() == null ? null : org.bukkit.Bukkit.getPlayer(viewer());
    if (viewer != null) {
      redrawSlot(viewer, slotAt(2, 3));
      redrawSlot(viewer, slotAt(2, 5));
      redrawSlot(viewer, slotAt(3, 1));
      redrawSlot(viewer, slotAt(0, 1));
    }
  }

  private void saveDraft(Player player, String detail) {
    services.drafts().save(draft);
    AdminAuditStore.get().record("ability:" + draft.id(), player.getName());
    services.audit().log(EditorAuditEvent.of(EditorAuditAction.EDIT, player.getUniqueId(), player.getName(), draft.id(), detail));
    dirty = true;
    redrawSlot(player, slotAt(0, 7));
  }

  private ItemStack auditItem(Player player) {
    AdminAuditStore.Entry entry = AdminAuditStore.get().entry("ability:" + draft.id());
    List<Component> lore = new ArrayList<>();
    if (entry == null || entry.timestamp() <= 0L) {
      lore.add(GuiI18n.tr(player, "gui.effects.editor.detail.audit.lastEdit.never"));
    } else {
      String when = AUDIT_FORMAT.format(Instant.ofEpochMilli(entry.timestamp()).atZone(ZoneId.systemDefault()));
      lore.add(GuiI18n.tr(player, "gui.effects.editor.detail.audit.lastEdit.value",
          Placeholder.unparsed("value", when)));
      lore.add(GuiI18n.tr(player, "gui.effects.editor.detail.audit.by",
          Placeholder.unparsed("value", entry.editor())));
    }
    return GuiItems.named(Material.CLOCK, GuiI18n.tr(player, "gui.effects.editor.detail.audit.title"), lore);
  }

  private ItemStack validationBannerItem(Player player) {
    List<String> issues = validationIssues();
    Material material = issues.isEmpty() ? Material.LIME_DYE : Material.RED_DYE;
    List<Component> lore = new ArrayList<>();
    if (issues.isEmpty()) {
      lore.add(GuiI18n.tr(player, "gui.effects.editor.detail.validation.ok"));
    } else {
      lore.add(GuiI18n.tr(player, "gui.effects.editor.detail.validation.issues"));
      for (String issue : issues) {
        lore.add(GuiI18n.tr(player, issue));
      }
    }
    return GuiItems.named(material, GuiI18n.tr(player, "gui.effects.editor.detail.validation.title"), lore);
  }

  private ItemStack dirtyIndicatorItem(Player player) {
    if (dirty) {
      return GuiItems.named(Material.ORANGE_DYE, GuiI18n.tr(player, "gui.effects.editor.detail.dirty.title"), List.of(
          GuiI18n.tr(player, "gui.effects.editor.detail.dirty.hint")));
    }
    return GuiItems.named(Material.LIME_DYE, GuiI18n.tr(player, "gui.effects.editor.detail.published.title"), List.of(
        GuiI18n.tr(player, "gui.effects.editor.detail.published.hint")));
  }

  private List<Component> actionSummaryLore(Player player) {
    List<Component> lore = new ArrayList<>();
    List<Map<String, Object>> rootActions = dev.patric.dungeonsreborn.effects.editor.EditorActionTree.rootActions(draft);
    int nodeCount = countNodes(rootActions);
    lore.add(GuiI18n.tr(player, "gui.effects.editor.detail.actions.summary.root",
        Placeholder.unparsed("value", String.valueOf(rootActions.size()))));
    lore.add(GuiI18n.tr(player, "gui.effects.editor.detail.actions.summary.nodes",
        Placeholder.unparsed("value", String.valueOf(nodeCount))));
    if (rootActions.isEmpty()) {
      lore.add(GuiI18n.tr(player, "gui.effects.editor.detail.actions.summary.empty"));
    }
    return lore;
  }

  private List<String> validationIssues() {
    List<String> issues = new ArrayList<>();
    String name = EditorAbilityYaml.name(draft);
    if (name == null || name.isBlank()) {
      issues.add("gui.effects.editor.detail.validation.issue.missingName");
    }
    List<Map<String, Object>> actions = dev.patric.dungeonsreborn.effects.editor.EditorActionTree.rootActions(draft);
    if (actions.isEmpty()) {
      issues.add("gui.effects.editor.detail.validation.issue.noActions");
    }
    if (EditorAbilityYaml.triggers(draft).isEmpty()) {
      issues.add("gui.effects.editor.detail.validation.issue.noBindings");
    }
    return issues;
  }

  private int countNodes(Object value) {
    if (value instanceof Map<?, ?> map) {
      int count = map.containsKey("type") ? 1 : 0;
      for (Object entry : map.values()) {
        count += countNodes(entry);
      }
      return count;
    }
    if (value instanceof List<?> list) {
      int count = 0;
      for (Object entry : list) {
        count += countNodes(entry);
      }
      return count;
    }
    return 0;
  }

  private void publishDraft() throws IOException {
    services.drafts().save(draft);
    File dir = services.yaml().abilitiesDir();
    dir.mkdirs();
    File file = new File(dir, draft.id() + ".yml");
    YamlConfiguration out = new YamlConfiguration();
    out.set("schemaVersion", 1);
    ConfigurationSection abilities = out.createSection("abilities");
    ConfigurationSection dest = abilities.createSection(draft.id());
    copySection(draft.abilitySection(), dest);
    out.save(file);
  }

  private void copySection(ConfigurationSection source, ConfigurationSection target) {
    for (String key : source.getKeys(false)) {
      Object value = source.get(key);
      if (value instanceof ConfigurationSection section) {
        ConfigurationSection child = target.createSection(key);
        copySection(section, child);
      } else {
        target.set(key, value);
      }
    }
  }

  private static String nullToNone(String value) {
    return value == null || value.isBlank()
        ? GuiI18n.str(GuiI18n.defaultLocale(), "gui.common.none")
        : value;
  }

  private static Component render(String raw) {
    if (raw == null) {
      return GuiI18n.tr(GuiI18n.defaultLocale(), "gui.common.none");
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
