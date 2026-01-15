package dev.patric.dungeonsreborn.classes.editor.menu;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.classes.ClassYamlRegistry;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.locale.Locales;

public final class ClassBatchMenu extends Window {
  private static final int SIZE = 54;
  private static final Duration DELETE_TIMEOUT = Duration.ofSeconds(30);
  private static final String DELETE_WORD = "delete-all";

  private final ClassYamlRegistry yaml;
  private final Runnable onCloseRefresh;

  public ClassBatchMenu(ClassYamlRegistry yaml, Runnable onCloseRefresh) {
    super(SIZE, GuiI18n.tr("gui.classes.editor.batch.title"), true);
    this.yaml = Objects.requireNonNull(yaml, "yaml");
    this.onCloseRefresh = onCloseRefresh == null ? () -> {
    } : onCloseRefresh;

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, GuiI18n.tr(p, "gui.button.back"))));

    setFixed(4, new Label(GuiItems.named(Material.COMPARATOR, GuiI18n.tr("gui.classes.editor.batch.header.title"), List.of(
        GuiI18n.tr("gui.classes.editor.batch.header.hint"),
        Locales.component(null, "gui.classes.editor.batch.header.count", Locales.placeholders("count", String.valueOf(classCount())))))));

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
    TextButton button = new TextButton(p -> GuiItems.named(Material.NAME_TAG, GuiI18n.tr(p, "gui.classes.editor.batch.template.title"), List.of(
        GuiI18n.tr(p, "gui.classes.editor.batch.template.hint"),
        GuiI18n.tr(p, "gui.classes.editor.batch.template.example"))),
        GuiI18n.tr("gui.classes.editor.batch.template.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(45),
        (window, text) -> {
          Player player = viewerPlayer(window);
          if (player == null) {
            return;
          }
          String template = text == null ? "" : text.trim();
          if (template.isBlank()) {
            player.sendMessage(GuiI18n.tr(player, "gui.classes.editor.batch.template.error.required"));
            return;
          }
          if (!applyNameTemplate(template)) {
            player.sendMessage(GuiI18n.tr(player, "gui.classes.editor.batch.template.error.failed"));
            return;
          }
          player.sendMessage(GuiI18n.tr(player, "gui.classes.editor.batch.template.success"));
          window.redraw(player);
        },
        true);
    button.autoDescribeInLore(false);
    return button;
  }

  private Button enableAllButton() {
    return new Button(p -> GuiItems.named(Material.LIME_DYE, GuiI18n.tr(p, "gui.classes.editor.batch.enable.title"), List.of(
        GuiI18n.tr(p, "gui.classes.editor.batch.enable.hint"))), ctx -> {
      if (!applyEnabled(true)) {
        ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "gui.classes.editor.batch.enable.error"));
        return;
      }
      ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "gui.classes.editor.batch.enable.success"));
      ctx.window().redraw(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button disableAllButton() {
    return new Button(p -> GuiItems.named(Material.RED_DYE, GuiI18n.tr(p, "gui.classes.editor.batch.disable.title"), List.of(
        GuiI18n.tr(p, "gui.classes.editor.batch.disable.hint"))), ctx -> {
      if (!applyEnabled(false)) {
        ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "gui.classes.editor.batch.disable.error"));
        return;
      }
      ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "gui.classes.editor.batch.disable.success"));
      ctx.window().redraw(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private TextButton deleteAllButton() {
    TextButton button = new TextButton(p -> GuiItems.named(Material.BARRIER, GuiI18n.tr(p, "gui.classes.editor.batch.delete.title"), List.of(
        Locales.component(p, "gui.classes.editor.batch.delete.hint", Locales.placeholders("word", DELETE_WORD)))),
        Locales.component(null, "gui.classes.editor.batch.delete.prompt", Locales.placeholders("word", DELETE_WORD)),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        DELETE_TIMEOUT,
        (window, text) -> {
          Player player = viewerPlayer(window);
          if (player == null) {
            return;
          }
          if (!DELETE_WORD.equalsIgnoreCase(text == null ? "" : text.trim())) {
            player.sendMessage(GuiI18n.tr(player, "gui.classes.editor.batch.delete.cancelled"));
            return;
          }
          if (!deleteAll()) {
            player.sendMessage(GuiI18n.tr(player, "gui.classes.editor.batch.delete.error"));
            return;
          }
          player.sendMessage(GuiI18n.tr(player, "gui.classes.editor.batch.delete.success"));
          window.redraw(player);
        },
        true);
    button.autoDescribeInLore(false);
    return button;
  }

  private int classCount() {
    return yaml.classes().size();
  }

  private boolean applyNameTemplate(String template) {
    File file = yaml.file();
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection classes = cfg.getConfigurationSection("classes");
    if (classes == null) {
      return false;
    }
    for (String id : classes.getKeys(false)) {
      String name = template.replace("{id}", id);
      classes.set(id + ".name", name);
    }
    return save(cfg, file);
  }

  private boolean applyEnabled(boolean enabled) {
    File file = yaml.file();
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection classes = cfg.getConfigurationSection("classes");
    if (classes == null) {
      return false;
    }
    for (String id : classes.getKeys(false)) {
      classes.set(id + ".enabled", enabled ? null : false);
    }
    return save(cfg, file);
  }

  private boolean deleteAll() {
    File file = yaml.file();
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection classes = cfg.getConfigurationSection("classes");
    if (classes == null) {
      return false;
    }
    for (String id : classes.getKeys(false)) {
      classes.set(id, null);
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
