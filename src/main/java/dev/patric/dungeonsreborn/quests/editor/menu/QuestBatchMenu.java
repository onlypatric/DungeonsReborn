package dev.patric.dungeonsreborn.quests.editor.menu;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.quests.QuestYamlRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class QuestBatchMenu extends Window {
  private static final int SIZE = 54;
  private static final Duration DELETE_TIMEOUT = Duration.ofSeconds(30);
  private static final String DELETE_WORD = "delete-all";

  private final QuestYamlRegistry yaml;
  private final Runnable onCloseRefresh;

  public QuestBatchMenu(QuestYamlRegistry yaml, Runnable onCloseRefresh) {
    super(SIZE, GuiI18n.tr("gui.quests.batch.title"), true);
    this.yaml = Objects.requireNonNull(yaml, "yaml");
    this.onCloseRefresh = onCloseRefresh == null ? () -> {
    } : onCloseRefresh;

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, GuiI18n.tr(p, "gui.button.back"))));

    setFixed(4, new Label(GuiItems.named(Material.COMPARATOR, GuiI18n.tr("gui.quests.batch.header.title"), List.of(
        GuiI18n.tr("gui.quests.batch.header.hint"),
        GuiI18n.tr("gui.quests.batch.header.count", Placeholder.unparsed("count", String.valueOf(questCount())))))));

    setFixedAt(2, 2, renameButton());
    setFixedAt(2, 4, enableAllButton());
    setFixedAt(2, 5, disableAllButton());
    setFixedAt(3, 4, deleteAllButton());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> {
      runCloseRefresh();
      GuiSounds.close(ctx.player());
    });
  }

  private TextButton renameButton() {
    TextButton button = new TextButton(p -> GuiItems.named(Material.NAME_TAG, GuiI18n.tr(p, "gui.quests.batch.nameTemplate.title"), List.of(
        GuiI18n.tr(p, "gui.quests.batch.nameTemplate.hint1"),
        GuiI18n.tr(p, "gui.quests.batch.nameTemplate.hint2"))),
        GuiI18n.tr("gui.quests.batch.nameTemplate.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(45),
        (window, text) -> {
          Player player = viewerPlayer(window);
          if (player == null) {
            return;
          }
          String template = text == null ? "" : text.trim();
          if (template.isBlank()) {
            player.sendMessage(GuiI18n.tr(player, "gui.quests.batch.nameTemplate.error.required"));
            return;
          }
          if (!applyNameTemplate(template)) {
            player.sendMessage(GuiI18n.tr(player, "gui.quests.batch.nameTemplate.error.apply"));
            return;
          }
          player.sendMessage(GuiI18n.tr(player, "gui.quests.batch.nameTemplate.success"));
          window.redraw(player);
        },
        true);
    button.autoDescribeInLore(false);
    return button;
  }

  private Button enableAllButton() {
    return new Button(p -> GuiItems.named(Material.LIME_DYE, GuiI18n.tr(p, "gui.quests.batch.enable.title"), List.of(
        GuiI18n.tr(p, "gui.quests.batch.enable.hint"))), ctx -> {
      if (!applyEnabled(true)) {
        ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "gui.quests.batch.enable.error"));
        return;
      }
      ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "gui.quests.batch.enable.success"));
      ctx.window().redraw(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button disableAllButton() {
    return new Button(p -> GuiItems.named(Material.RED_DYE, GuiI18n.tr(p, "gui.quests.batch.disable.title"), List.of(
        GuiI18n.tr(p, "gui.quests.batch.disable.hint"))), ctx -> {
      if (!applyEnabled(false)) {
        ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "gui.quests.batch.disable.error"));
        return;
      }
      ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "gui.quests.batch.disable.success"));
      ctx.window().redraw(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private TextButton deleteAllButton() {
    TextButton button = new TextButton(p -> GuiItems.named(Material.BARRIER, GuiI18n.tr(p, "gui.quests.batch.delete.title"), List.of(
        GuiI18n.tr(p, "gui.quests.batch.delete.hint", Placeholder.unparsed("word", DELETE_WORD)))),
        GuiI18n.tr("gui.quests.batch.delete.prompt", Placeholder.unparsed("word", DELETE_WORD)),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        DELETE_TIMEOUT,
        (window, text) -> {
          Player player = viewerPlayer(window);
          if (player == null) {
            return;
          }
          if (!DELETE_WORD.equalsIgnoreCase(text == null ? "" : text.trim())) {
            player.sendMessage(GuiI18n.tr(player, "gui.quests.batch.delete.cancel"));
            return;
          }
          if (!deleteAll()) {
            player.sendMessage(GuiI18n.tr(player, "gui.quests.batch.delete.error"));
            return;
          }
          player.sendMessage(GuiI18n.tr(player, "gui.quests.batch.delete.success"));
          window.redraw(player);
        },
        true);
    button.autoDescribeInLore(false);
    return button;
  }

  private int questCount() {
    return yaml.quests().size();
  }

  private boolean applyNameTemplate(String template) {
    File file = yaml.file();
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection quests = cfg.getConfigurationSection("quests");
    if (quests == null) {
      return false;
    }
    for (String id : quests.getKeys(false)) {
      String name = template.replace("{id}", id);
      quests.set(id + ".name", name);
    }
    return save(cfg, file);
  }

  private boolean applyEnabled(boolean enabled) {
    File file = yaml.file();
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection quests = cfg.getConfigurationSection("quests");
    if (quests == null) {
      return false;
    }
    for (String id : quests.getKeys(false)) {
      quests.set(id + ".enabled", enabled ? null : false);
    }
    return save(cfg, file);
  }

  private boolean deleteAll() {
    File file = yaml.file();
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection quests = cfg.getConfigurationSection("quests");
    if (quests == null) {
      return false;
    }
    for (String id : quests.getKeys(false)) {
      quests.set(id, null);
    }
    return save(cfg, file);
  }

  private boolean save(YamlConfiguration cfg, File file) {
    try {
      cfg.save(file);
    } catch (IOException ex) {
      return false;
    }
    yaml.reload();
    runCloseRefresh();
    return true;
  }

  private Player viewerPlayer(Window window) {
    if (window == null || window.viewer() == null) {
      return null;
    }
    return org.bukkit.Bukkit.getPlayer(window.viewer());
  }

  private void runCloseRefresh() {
    if (onCloseRefresh != null) {
      onCloseRefresh.run();
    }
  }
}
