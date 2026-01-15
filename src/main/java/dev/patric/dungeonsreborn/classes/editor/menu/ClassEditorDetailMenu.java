package dev.patric.dungeonsreborn.classes.editor.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.admin.AdminAuditStore;
import dev.patric.dungeonsreborn.classes.ClassSpec;
import dev.patric.dungeonsreborn.classes.ClassYamlRegistry;
import dev.patric.dungeonsreborn.classes.editor.ClassEditorYaml;
import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.item.ItemPreview;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.classes.ClassBonusSpec;
import dev.patric.dungeonsreborn.classes.ClassUnlockSpec;
import dev.patric.dungeonsreborn.classes.skills.SkillTreeSpec;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ClassEditorDetailMenu extends Window {
  private static final int SIZE = 54;
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

  private final ClassYamlRegistry yaml;
  private final String classId;

  public ClassEditorDetailMenu(ClassYamlRegistry yaml, String classId, Runnable onCloseRefresh) {
    super(SIZE, GuiI18n.tr("gui.classes.editor.detail.title"), true);
    this.yaml = Objects.requireNonNull(yaml, "yaml");
    this.classId = Objects.requireNonNull(classId, "classId");
    Runnable closeRefresh = onCloseRefresh == null ? () -> {
    } : onCloseRefresh;

    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiItems.named(Material.BARRIER, GuiI18n.tr(p, "gui.button.back"), List.of())));

    setFixedAt(0, 4, new Label(p -> GuiItems.named(Material.BOOK,
        GuiI18n.tr(p, "gui.classes.editor.detail.header.title", Placeholder.unparsed("id", classId)),
        List.of(GuiI18n.tr(p, "gui.classes.editor.detail.header.hint")))));

    setFixedAt(1, 4, new ItemPreview(this::iconPreview)
        .placeholder(GuiItems.named(Material.GRAY_STAINED_GLASS_PANE, GuiI18n.tr("gui.classes.editor.detail.icon.none"))));

    setFixedAt(1, 0, enabledButton());
    setFixedAt(1, 1, nameButton());
    setFixedAt(1, 2, descriptionButton());
    setFixedAt(1, 6, iconButton());
    setFixedAt(2, 1, unlockLevelButton());
    setFixedAt(2, 2, unlockTokensButton());
    setFixedAt(2, 3, unlockQuestsButton());
    setFixedAt(2, 6, reloadButton());
    setFixedAt(2, 0, new Label(p -> auditItem()));
    setFixedAt(3, 1, new Label(p -> summaryItem()));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> {
      closeRefresh.run();
      GuiSounds.close(ctx.player());
    });
  }

  private Button enabledButton() {
    return new Button(p -> {
      boolean enabled = ClassEditorYaml.enabled(yaml.file(), classId);
      Material material = enabled ? Material.LIME_DYE : Material.RED_DYE;
      Component state = enabled
          ? GuiI18n.tr(p, "gui.classes.editor.detail.enabled.state.enabled")
          : GuiI18n.tr(p, "gui.classes.editor.detail.enabled.state.disabled");
      return GuiItems.named(material, GuiI18n.tr(p, "gui.classes.editor.detail.enabled.title"), List.of(
          GuiI18n.tr(p, "gui.classes.editor.detail.enabled.state", Placeholder.component("state", state)),
          GuiI18n.tr(p, "gui.classes.editor.detail.enabled.hint")));
    }, ctx -> {
      boolean current = ClassEditorYaml.enabled(yaml.file(), classId);
      ClassEditorYaml.setEnabled(yaml.file(), classId, !current);
      yaml.reload();
      Player player = viewerPlayer(ctx.window());
      if (player != null) {
        ctx.window().redraw(player);
        AdminAuditStore.get().record("class:" + classId, player.getName());
      }
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private ItemStack iconPreview(Player player) {
    ClassSpec spec = yaml.classSpec(classId);
    return spec == null || spec.icon() == null ? null : spec.icon().clone();
  }

  private Button reloadButton() {
    return new Button(p -> GuiItems.named(Material.CLOCK, GuiI18n.tr(p, "gui.classes.editor.detail.reload.title"), List.of(
        GuiI18n.tr(p, "gui.classes.editor.detail.reload.hint"))), ctx -> {
      yaml.reload();
      ctx.window().redraw(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private TextButton nameButton() {
    TextButton button = new TextButton(p -> {
      ClassSpec spec = yaml.classSpec(classId);
      String current = spec == null ? Locales.text(p, "gui.common.none") : PLAIN.serialize(spec.displayName());
      return GuiItems.named(Material.NAME_TAG, GuiI18n.tr(p, "gui.classes.editor.detail.name.title"), List.of(
          GuiI18n.tr(p, "gui.classes.editor.detail.name.current", Placeholder.unparsed("value", current))));
    }, GuiI18n.tr("gui.classes.editor.detail.name.prompt"), "cancel", Duration.ofSeconds(45),
        (window, text) -> {
          ClassEditorYaml.setName(yaml.file(), classId, text);
          yaml.reload();
          Player player = viewerPlayer(window);
          if (player != null) {
            window.redraw(player);
            AdminAuditStore.get().record("class:" + classId, player.getName());
          }
        }, true);
    button.autoDescribeInLore(false);
    return button;
  }

  private TextButton descriptionButton() {
    TextButton button = new TextButton(p -> {
      List<String> lines = ClassEditorYaml.description(yaml.file(), classId);
      String count = lines.isEmpty() ? Locales.text(p, "gui.common.none") : String.valueOf(lines.size());
      return GuiItems.named(Material.WRITABLE_BOOK, GuiI18n.tr(p, "gui.classes.editor.detail.description.title"), List.of(
          GuiI18n.tr(p, "gui.classes.editor.detail.description.lines", Placeholder.unparsed("count", count)),
          GuiI18n.tr(p, "gui.classes.editor.detail.description.hint")));
    }, GuiI18n.tr("gui.classes.editor.detail.description.prompt"), "cancel", Duration.ofSeconds(60),
        (window, text) -> {
          if (text == null || text.isBlank()) {
            ClassEditorYaml.setDescription(yaml.file(), classId, List.of());
          } else {
            String normalized = text.replace("\\\\n", "\n");
            String[] split = normalized.split("\n", -1);
            List<String> lines = new ArrayList<>();
            for (String line : split) {
              lines.add(line);
            }
            ClassEditorYaml.setDescription(yaml.file(), classId, lines);
          }
          yaml.reload();
          Player player = viewerPlayer(window);
          if (player != null) {
            window.redraw(player);
            AdminAuditStore.get().record("class:" + classId, player.getName());
          }
        }, true);
    button.autoDescribeInLore(false);
    return button;
  }

  private Button iconButton() {
    return new Button(p -> GuiItems.named(Material.ITEM_FRAME, GuiI18n.tr(p, "gui.classes.editor.detail.icon.title"), List.of(
        GuiI18n.tr(p, "gui.classes.editor.detail.icon.hint"))), ctx -> {
      ItemStack hand = ctx.player().getInventory().getItemInMainHand();
      if (hand == null || hand.getType().isAir()) {
        ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "messages.classes.editor.icon.missing"));
        return;
      }
      ClassEditorYaml.setIcon(yaml.file(), classId, hand);
      yaml.reload();
      ctx.window().redraw(ctx.player());
      AdminAuditStore.get().record("class:" + classId, ctx.player().getName());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private TextButton unlockLevelButton() {
    TextButton button = new TextButton(p -> {
      int level = ClassEditorYaml.unlockLevel(yaml.file(), classId);
      return GuiItems.named(Material.EXPERIENCE_BOTTLE, GuiI18n.tr(p, "gui.classes.editor.detail.unlockLevel.title"), List.of(
          GuiI18n.tr(p, "gui.classes.editor.detail.unlockLevel.current", Placeholder.unparsed("value", String.valueOf(level)))));
    }, GuiI18n.tr("gui.classes.editor.detail.unlockLevel.prompt"), "cancel", Duration.ofSeconds(45),
        (window, text) -> {
          int level = parseInt(text, 0);
          ClassEditorYaml.setUnlockLevel(yaml.file(), classId, level);
          yaml.reload();
          Player player = viewerPlayer(window);
          if (player != null) {
            window.redraw(player);
            AdminAuditStore.get().record("class:" + classId, player.getName());
          }
        }, true);
    button.validate((w, p, input) -> validateNonNegative(input));
    button.autoDescribeInLore(false);
    return button;
  }

  private TextButton unlockTokensButton() {
    TextButton button = new TextButton(p -> {
      int tokens = ClassEditorYaml.unlockTokens(yaml.file(), classId);
      return GuiItems.named(Material.SUNFLOWER, GuiI18n.tr(p, "gui.classes.editor.detail.unlockTokens.title"), List.of(
          GuiI18n.tr(p, "gui.classes.editor.detail.unlockTokens.current", Placeholder.unparsed("value", String.valueOf(tokens)))));
    }, GuiI18n.tr("gui.classes.editor.detail.unlockTokens.prompt"), "cancel", Duration.ofSeconds(45),
        (window, text) -> {
          int tokens = parseInt(text, 0);
          ClassEditorYaml.setUnlockTokens(yaml.file(), classId, tokens);
          yaml.reload();
          Player player = viewerPlayer(window);
          if (player != null) {
            window.redraw(player);
            AdminAuditStore.get().record("class:" + classId, player.getName());
          }
        }, true);
    button.validate((w, p, input) -> validateNonNegative(input));
    button.autoDescribeInLore(false);
    return button;
  }

  private TextButton unlockQuestsButton() {
    TextButton button = new TextButton(p -> {
      List<String> quests = ClassEditorYaml.unlockQuests(yaml.file(), classId);
      String current = quests.isEmpty() ? Locales.text(p, "gui.common.none") : String.join(", ", quests);
      return GuiItems.named(Material.PAPER, GuiI18n.tr(p, "gui.classes.editor.detail.unlockQuests.title"), List.of(
          GuiI18n.tr(p, "gui.classes.editor.detail.unlockQuests.current", Placeholder.unparsed("value", current)),
          GuiI18n.tr(p, "gui.classes.editor.detail.unlockQuests.hint")));
    }, GuiI18n.tr("gui.classes.editor.detail.unlockQuests.prompt"), "cancel", Duration.ofSeconds(60),
        (window, text) -> {
          if (text == null || text.isBlank()) {
            ClassEditorYaml.setUnlockQuests(yaml.file(), classId, List.of());
          } else {
            String[] parts = text.split(",");
            List<String> quests = new ArrayList<>();
            for (String part : parts) {
              String trimmed = part.trim();
              if (trimmed.isEmpty()) {
                continue;
              }
              try {
                quests.add(Ids.normalize(trimmed));
              } catch (IllegalArgumentException ex) {
                Player player = viewerPlayer(window);
                if (player != null) {
                  player.sendMessage(GuiI18n.tr(player, "messages.classes.editor.invalidQuestId",
                      Placeholder.unparsed("id", trimmed)));
                }
              }
            }
            ClassEditorYaml.setUnlockQuests(yaml.file(), classId, quests);
          }
          yaml.reload();
          Player player = viewerPlayer(window);
          if (player != null) {
            window.redraw(player);
            AdminAuditStore.get().record("class:" + classId, player.getName());
          }
        }, true);
    button.autoDescribeInLore(false);
    return button;
  }

  private Component validateNonNegative(String input) {
    if (input == null || input.isBlank()) {
      return null;
    }
    try {
      int value = Integer.parseInt(input.trim());
      return value < 0 ? Locales.component(null, "gui.classes.editor.detail.validation.nonNegative") : null;
    } catch (NumberFormatException ex) {
      return Locales.component(null, "gui.classes.editor.detail.validation.integer");
    }
  }

  private int parseInt(String input, int def) {
    if (input == null || input.isBlank()) {
      return def;
    }
    try {
      return Integer.parseInt(input.trim());
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

  private ItemStack summaryItem() {
    ClassSpec spec = yaml.classSpec(classId);
    ClassBonusSpec bonuses = spec == null ? null : spec.bonusesOrEmpty();
    SkillTreeSpec tree = spec == null ? SkillTreeSpec.empty() : spec.skillTreeOrEmpty();
    ClassUnlockSpec unlock = spec == null ? ClassUnlockSpec.none() : spec.unlock();
    List<Component> lore = new ArrayList<>();
    lore.add(GuiI18n.tr("gui.classes.editor.detail.summary.stats",
        Placeholder.unparsed("value", statSummary(bonuses))));
    lore.add(GuiI18n.tr("gui.classes.editor.detail.summary.skillPath",
        Placeholder.unparsed("nodes", String.valueOf(tree.nodes().size())),
        Placeholder.unparsed("links", String.valueOf(tree.edges().size()))));
    if (tree.respecPoints() > 0 || tree.respecTokens() > 0) {
      lore.add(GuiI18n.tr("gui.classes.editor.detail.summary.respec",
          Placeholder.unparsed("points", String.valueOf(tree.respecPoints())),
          Placeholder.unparsed("tokens", String.valueOf(tree.respecTokens()))));
    }
    lore.add(GuiI18n.tr("gui.classes.editor.detail.summary.unlock",
        Placeholder.unparsed("level", String.valueOf(unlock.level())),
        Placeholder.unparsed("tokens", String.valueOf(unlock.tokens())),
        Placeholder.unparsed("quests", String.valueOf(unlock.quests().size()))));
    return GuiItems.named(Material.LECTERN, GuiI18n.tr("gui.classes.editor.detail.summary.title"), lore);
  }

  private String statSummary(ClassBonusSpec bonuses) {
    if (bonuses == null) {
      return Locales.text(null, "gui.common.none");
    }
    List<String> parts = new ArrayList<>();
    if (bonuses.strength() > 0) {
      parts.add(Locales.text(null, "gui.classes.editor.detail.stats.str",
          Locales.placeholders("value", bonuses.strength())));
    }
    if (bonuses.dexterity() > 0) {
      parts.add(Locales.text(null, "gui.classes.editor.detail.stats.dex",
          Locales.placeholders("value", bonuses.dexterity())));
    }
    if (bonuses.intelligence() > 0) {
      parts.add(Locales.text(null, "gui.classes.editor.detail.stats.int",
          Locales.placeholders("value", bonuses.intelligence())));
    }
    if (bonuses.vitality() > 0) {
      parts.add(Locales.text(null, "gui.classes.editor.detail.stats.vit",
          Locales.placeholders("value", bonuses.vitality())));
    }
    if (bonuses.manaMaxBonus() > 0.0) {
      parts.add(Locales.text(null, "gui.classes.editor.detail.stats.mana",
          Locales.placeholders("value", bonuses.manaMaxBonus())));
    }
    if (bonuses.manaRegenBonus() > 0.0) {
      parts.add(Locales.text(null, "gui.classes.editor.detail.stats.regen",
          Locales.placeholders("value", bonuses.manaRegenBonus())));
    }
    if (!bonuses.attributesOrEmpty().isEmpty()) {
      parts.add(Locales.text(null, "gui.classes.editor.detail.stats.attributes",
          Locales.placeholders("count", bonuses.attributesOrEmpty().size())));
    }
    if (!bonuses.potionsOrEmpty().isEmpty()) {
      parts.add(Locales.text(null, "gui.classes.editor.detail.stats.potions",
          Locales.placeholders("count", bonuses.potionsOrEmpty().size())));
    }
    if (!bonuses.resistancesOrEmpty().isEmpty()) {
      parts.add(Locales.text(null, "gui.classes.editor.detail.stats.resists",
          Locales.placeholders("count", bonuses.resistancesOrEmpty().size())));
    }
    if (parts.isEmpty()) {
      return Locales.text(null, "gui.common.none");
    }
    return String.join(", ", parts);
  }

  private ItemStack auditItem() {
    AdminAuditStore.Entry entry = AdminAuditStore.get().entry("class:" + classId);
    String editor = entry == null ? Locales.text(null, "gui.common.unknown") : entry.editor();
    String when = entry == null ? formatTimestamp(yaml.file().lastModified()) : formatTimestamp(entry.timestamp());
    return GuiItems.named(Material.PAPER, GuiI18n.tr("gui.classes.editor.detail.audit.title"), List.of(
        GuiI18n.tr("gui.classes.editor.detail.audit.editor", Placeholder.unparsed("value", editor)),
        GuiI18n.tr("gui.classes.editor.detail.audit.change", Placeholder.unparsed("value", when))));
  }

  private String formatTimestamp(long timestamp) {
    if (timestamp <= 0L) {
      return Locales.text(null, "gui.common.unknown");
    }
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    return formatter.format(Instant.ofEpochMilli(timestamp));
  }
}
