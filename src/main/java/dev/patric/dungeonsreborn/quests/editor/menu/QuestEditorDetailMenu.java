package dev.patric.dungeonsreborn.quests.editor.menu;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.admin.AdminAuditStore;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.util.WorldAllowlist;
import dev.patric.dungeonsreborn.DungeonsRebornPlugin;
import dev.patric.dungeonsreborn.quests.QuestRewards;
import dev.patric.dungeonsreborn.quests.QuestSpec;
import dev.patric.dungeonsreborn.quests.QuestRotation;
import dev.patric.dungeonsreborn.quests.QuestYamlRegistry;
import dev.patric.dungeonsreborn.quests.editor.QuestEditorYaml;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class QuestEditorDetailMenu extends Window {
  private static final int SIZE = 54;
  private final QuestYamlRegistry yaml;
  private final String questId;

  public QuestEditorDetailMenu(QuestYamlRegistry yaml, String questId, Runnable onCloseRefresh) {
    super(SIZE, GuiI18n.tr("gui.quests.editor.detail.title"), true);
    this.yaml = Objects.requireNonNull(yaml, "yaml");
    this.questId = Objects.requireNonNull(questId, "questId");
    Runnable closeRefresh = Objects.requireNonNullElse(onCloseRefresh, () -> {
    });

    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiItems.named(Material.BARRIER, GuiI18n.tr(p, "gui.button.back"), List.of())));

    setFixedAt(0, 4, new Label(p -> GuiItems.named(Material.BOOK,
        GuiI18n.tr(p, "gui.quests.editor.detail.header.title", Placeholder.unparsed("id", questId)),
        List.of(GuiI18n.tr(p, "gui.quests.editor.detail.header.hint")))));

    setFixedAt(1, 0, enabledButton());
    setFixedAt(1, 1, nameButton());
    setFixedAt(1, 2, descriptionButton());
    setFixedAt(1, 3, cooldownButton());
    setFixedAt(1, 4, rotationButton());
    setFixedAt(1, 6, objectivesButton());
    setFixedAt(2, 6, deleteButton());
    setFixedAt(2, 0, new Label(this::auditItem));
    setFixedAt(2, 1, new Label(this::summaryItem));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> {
      closeRefresh.run();
      GuiSounds.close(ctx.player());
    });
  }

  private Button enabledButton() {
    return new Button(p -> {
      boolean enabled = QuestEditorYaml.enabled(yaml.file(), questId);
      Material material = enabled ? Material.LIME_DYE : Material.RED_DYE;
      Component state = enabled
          ? GuiI18n.tr(p, "gui.quests.editor.detail.enabled.state.enabled")
          : GuiI18n.tr(p, "gui.quests.editor.detail.enabled.state.disabled");
      return GuiItems.named(material, GuiI18n.tr(p, "gui.quests.editor.detail.enabled.title"), List.of(
          GuiI18n.tr(p, "gui.quests.editor.detail.enabled.state.line",
              Placeholder.component("value", state)),
          GuiI18n.tr(p, "gui.quests.editor.detail.enabled.hint")));
    }, ctx -> {
      boolean current = QuestEditorYaml.enabled(yaml.file(), questId);
      QuestEditorYaml.setEnabled(yaml.file(), questId, !current);
      yaml.reload();
      Player player = viewerPlayer(ctx.window());
      if (player != null) {
        ctx.window().redraw(player);
        AdminAuditStore.get().record("quest:" + questId, player.getName());
      }
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private TextButton nameButton() {
    TextButton button = new TextButton(p -> {
      String current = QuestEditorYaml.name(yaml.file(), questId);
      if (current == null) {
        current = GuiI18n.str(p, "gui.common.none");
      }
      return GuiItems.named(Material.NAME_TAG, GuiI18n.tr(p, "gui.quests.editor.detail.name.title"), List.of(
          GuiI18n.tr(p, "gui.quests.editor.detail.name.current", Placeholder.unparsed("value", current))));
    }, GuiI18n.tr("gui.quests.editor.detail.name.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"), Duration.ofSeconds(45),
        (window, text) -> {
          QuestEditorYaml.setName(yaml.file(), questId, text);
          yaml.reload();
          Player player = viewerPlayer(window);
          if (player != null) {
            window.redraw(player);
            AdminAuditStore.get().record("quest:" + questId, player.getName());
          }
        }, true);
    button.autoDescribeInLore(false);
    return button;
  }

  private TextButton descriptionButton() {
    TextButton button = new TextButton(p -> {
      List<String> lines = QuestEditorYaml.description(yaml.file(), questId);
      String value = lines.isEmpty() ? GuiI18n.str(p, "gui.common.none") : String.valueOf(lines.size());
      return GuiItems.named(Material.WRITABLE_BOOK, GuiI18n.tr(p, "gui.quests.editor.detail.description.title"), List.of(
          GuiI18n.tr(p, "gui.quests.editor.detail.description.lines", Placeholder.unparsed("value", value)),
          GuiI18n.tr(p, "gui.quests.editor.detail.description.hint")));
    }, GuiI18n.tr("gui.quests.editor.detail.description.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"), Duration.ofSeconds(60),
        (window, text) -> {
          if (text == null || text.isBlank()) {
            QuestEditorYaml.setDescription(yaml.file(), questId, List.of());
          } else {
            String normalized = text.replace("\\n", "\n");
            String[] split = normalized.split("\n", -1);
            List<String> lines = new ArrayList<>();
            for (String line : split) {
              lines.add(line);
            }
            QuestEditorYaml.setDescription(yaml.file(), questId, lines);
          }
          yaml.reload();
          Player player = viewerPlayer(window);
          if (player != null) {
            window.redraw(player);
            AdminAuditStore.get().record("quest:" + questId, player.getName());
          }
        }, true);
    button.autoDescribeInLore(false);
    return button;
  }

  private TextButton cooldownButton() {
    TextButton button = new TextButton(p -> {
      long current = QuestEditorYaml.cooldownSeconds(yaml.file(), questId);
      return GuiItems.named(Material.CLOCK, GuiI18n.tr(p, "gui.quests.editor.detail.cooldown.title"), List.of(
          GuiI18n.tr(p, "gui.quests.editor.detail.cooldown.seconds", Placeholder.unparsed("value", String.valueOf(current)))));
    }, GuiI18n.tr("gui.quests.editor.detail.cooldown.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"), Duration.ofSeconds(45),
        (window, text) -> {
          long value = parseLong(text, 0L);
          QuestEditorYaml.setCooldownSeconds(yaml.file(), questId, value);
          yaml.reload();
          Player player = viewerPlayer(window);
          if (player != null) {
            window.redraw(player);
            AdminAuditStore.get().record("quest:" + questId, player.getName());
          }
        }, true);
    button.validate((w, p, input) -> validateNonNegative(input));
    button.autoDescribeInLore(false);
    return button;
  }

  private Button rotationButton() {
    return new Button(p -> {
      QuestRotation rotation = QuestEditorYaml.rotation(yaml.file(), questId);
      return GuiItems.named(Material.COMPASS, GuiI18n.tr(p, "gui.quests.editor.detail.rotation.title"), List.of(
          GuiI18n.tr(p, "gui.quests.editor.detail.rotation.current", Placeholder.unparsed("value", rotation.name().toLowerCase())),
          GuiI18n.tr(p, "gui.quests.editor.detail.rotation.hint")));
    }, ctx -> {
      QuestRotation current = QuestEditorYaml.rotation(yaml.file(), questId);
      QuestRotation next = switch (current) {
        case NONE -> QuestRotation.DAILY;
        case DAILY -> QuestRotation.WEEKLY;
        case WEEKLY -> QuestRotation.NONE;
      };
      QuestEditorYaml.setRotation(yaml.file(), questId, next);
      yaml.reload();
      ctx.window().redraw(ctx.player());
      AdminAuditStore.get().record("quest:" + questId, ctx.player().getName());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button objectivesButton() {
    return new Button(p -> GuiItems.named(Material.PAPER, GuiI18n.tr(p, "gui.quests.editor.detail.objectives.title"), List.of(
        GuiI18n.tr(p, "gui.quests.editor.detail.objectives.hint"))), ctx -> {
      openSubWindow(ctx.player(), new QuestObjectiveListMenu(yaml, questId));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private TextButton deleteButton() {
    TextButton button = new TextButton(p -> GuiItems.named(Material.BARRIER, GuiI18n.tr(p, "gui.quests.editor.detail.delete.title"), List.of(
        GuiI18n.tr(p, "gui.quests.editor.detail.delete.hint"))),
        GuiI18n.tr("gui.quests.editor.detail.delete.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"), Duration.ofSeconds(30),
        (window, text) -> {
          if (!"delete".equalsIgnoreCase(text.trim())) {
            Player player = viewerPlayer(window);
            if (player != null) {
              player.sendMessage(GuiI18n.tr(player, "gui.quests.editor.detail.delete.error"));
            }
            return;
          }
          QuestEditorYaml.deleteQuest(yaml.file(), questId);
          yaml.reload();
          Player player = viewerPlayer(window);
          if (player != null) {
            player.closeInventory();
            AdminAuditStore.get().record("quest:" + questId, player.getName());
          }
        }, true);
    button.autoDescribeInLore(false);
    return button;
  }

  private ItemStack summaryItem(Player player) {
    QuestSpec spec = yaml.quest(questId);
    List<Component> lore = new ArrayList<>();
    List<QuestEditorYaml.ObjectiveData> objectives = QuestEditorYaml.objectives(yaml.file(), questId);
    lore.add(GuiI18n.tr(player, "gui.quests.editor.detail.summary.objectives",
        Placeholder.unparsed("count", String.valueOf(objectives.size()))));
    int shown = 0;
    for (QuestEditorYaml.ObjectiveData data : objectives) {
      if (shown >= 3) {
        lore.add(GuiI18n.tr(player, "gui.quests.editor.detail.summary.more",
            Placeholder.unparsed("count", String.valueOf(objectives.size() - shown))));
        break;
      }
      lore.add(GuiI18n.tr(player, "gui.quests.editor.detail.summary.objective",
          Placeholder.unparsed("value", QuestObjectiveSummary.describe(data))));
      shown++;
    }
    QuestRewards rewards = spec == null ? null : spec.rewards();
    lore.add(GuiI18n.tr(player, "gui.quests.editor.detail.summary.rewards",
        Placeholder.component("value", rewardSummary(player, rewards))));
    lore.add(GuiI18n.tr(player, "gui.quests.editor.detail.summary.allowlist",
        Placeholder.unparsed("value", allowlistLabel(player))));
    return GuiItems.named(Material.CHEST, GuiI18n.tr(player, "gui.quests.editor.detail.summary.title"), lore);
  }

  private ItemStack auditItem(Player player) {
    AdminAuditStore.Entry entry = AdminAuditStore.get().entry("quest:" + questId);
    String editor = entry == null ? GuiI18n.str(player, "gui.quests.editor.detail.audit.unknown") : entry.editor();
    String when = entry == null ? formatTimestamp(player, yaml.file().lastModified())
        : formatTimestamp(player, entry.timestamp());
    return GuiItems.named(Material.PAPER, GuiI18n.tr(player, "gui.quests.editor.detail.audit.title"), List.of(
        GuiI18n.tr(player, "gui.quests.editor.detail.audit.editor", Placeholder.unparsed("value", editor)),
        GuiI18n.tr(player, "gui.quests.editor.detail.audit.when", Placeholder.unparsed("value", when))));
  }

  private Component rewardSummary(Player player, QuestRewards rewards) {
    if (rewards == null) {
      return GuiI18n.tr(player, "gui.common.none");
    }
    List<Component> parts = new ArrayList<>();
    if (rewards.xp() > 0) {
      parts.add(GuiI18n.tr(player, "gui.quests.editor.detail.summary.reward.xp",
          Placeholder.unparsed("value", String.valueOf(rewards.xp()))));
    }
    if (rewards.tokens() > 0) {
      parts.add(GuiI18n.tr(player, "gui.quests.editor.detail.summary.reward.tokens",
          Placeholder.unparsed("value", String.valueOf(rewards.tokens()))));
    }
    if (rewards.compressed() > 0) {
      parts.add(GuiI18n.tr(player, "gui.quests.editor.detail.summary.reward.compressed",
          Placeholder.unparsed("value", String.valueOf(rewards.compressed()))));
    }
    if (rewards.pallet() > 0) {
      parts.add(GuiI18n.tr(player, "gui.quests.editor.detail.summary.reward.pallets",
          Placeholder.unparsed("value", String.valueOf(rewards.pallet()))));
    }
    if (!rewards.items().isEmpty()) {
      parts.add(GuiI18n.tr(player, "gui.quests.editor.detail.summary.reward.items",
          Placeholder.unparsed("value", String.valueOf(rewards.items().size()))));
    }
    if (parts.isEmpty()) {
      return GuiI18n.tr(player, "gui.common.none");
    }
    Component summary = parts.get(0);
    for (int i = 1; i < parts.size(); i++) {
      summary = summary.append(Component.text(", ")).append(parts.get(i));
    }
    return summary;
  }

  private String allowlistLabel(Player player) {
    DungeonsRebornPlugin plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(DungeonsRebornPlugin.class);
    WorldAllowlist allowlist = WorldAllowlist.fromConfig(plugin.getConfig());
    if (allowlist.allowAll()) {
      return GuiI18n.str(player, "gui.quests.editor.detail.summary.allowlist.all");
    }
    return String.join(", ", allowlist.worlds());
  }

  private String formatTimestamp(Player player, long timestamp) {
    if (timestamp <= 0L) {
      return GuiI18n.str(player, "gui.quests.editor.detail.audit.unknown");
    }
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    return formatter.format(Instant.ofEpochMilli(timestamp));
  }

  private Component validateNonNegative(String input) {
    if (input == null || input.isBlank()) {
      return null;
    }
    try {
      long value = Long.parseLong(input.trim());
      return value < 0 ? GuiI18n.tr("gui.quests.editor.detail.validation.nonNegative") : null;
    } catch (NumberFormatException ex) {
      return GuiI18n.tr("gui.quests.editor.detail.validation.integer");
    }
  }

  private long parseLong(String input, long def) {
    if (input == null || input.isBlank()) {
      return def;
    }
    try {
      return Long.parseLong(input.trim());
    } catch (NumberFormatException ex) {
      return def;
    }
  }

  private Player viewerPlayer(Window window) {
    if (window == null || window.viewer() == null) {
      return null;
    }
    return org.bukkit.Bukkit.getPlayer(window.viewer());
  }
}
