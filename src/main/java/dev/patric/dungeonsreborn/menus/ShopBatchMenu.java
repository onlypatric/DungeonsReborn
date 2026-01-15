package dev.patric.dungeonsreborn.menus;

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
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;

public final class ShopBatchMenu extends Window {
  private static final int SIZE = 54;
  private static final Duration DELETE_TIMEOUT = Duration.ofSeconds(30);
  private static final String DELETE_WORD = "delete-all";

  private final ShopYamlRegistry registry;
  private final Runnable onCloseRefresh;

  public ShopBatchMenu(ShopYamlRegistry registry, Runnable onCloseRefresh) {
    super(SIZE, GuiI18n.tr("gui.shop.editor.batch.title"), true);
    this.registry = Objects.requireNonNull(registry, "registry");
    this.onCloseRefresh = onCloseRefresh == null ? () -> {
    } : onCloseRefresh;

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, GuiI18n.tr(p, "gui.button.back"))));

    setFixed(4, new Label(GuiItems.named(Material.COMPARATOR, GuiI18n.tr("gui.shop.editor.batch.header.title"), List.of(
        GuiI18n.tr("gui.shop.editor.batch.header.hint"),
        Locales.component(null, "gui.shop.editor.batch.header.count", Locales.placeholders("count", String.valueOf(shopCount())))))));

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
    TextButton button = new TextButton(p -> GuiItems.named(Material.NAME_TAG, GuiI18n.tr(p, "gui.shop.editor.batch.template.title"), List.of(
        GuiI18n.tr(p, "gui.shop.editor.batch.template.hint"),
        GuiI18n.tr(p, "gui.shop.editor.batch.template.example"))),
        GuiI18n.tr("gui.shop.editor.batch.template.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(45),
        (window, text) -> {
          Player player = viewerPlayer(window);
          if (player == null) {
            return;
          }
          String template = text == null ? "" : text.trim();
          if (template.isBlank()) {
            player.sendMessage(GuiI18n.tr(player, "gui.shop.editor.batch.template.error.required"));
            return;
          }
          if (!applyTitleTemplate(template)) {
            player.sendMessage(GuiI18n.tr(player, "gui.shop.editor.batch.template.error.failed"));
            return;
          }
          player.sendMessage(GuiI18n.tr(player, "gui.shop.editor.batch.template.success"));
          window.redraw(player);
        },
        true);
    button.autoDescribeInLore(false);
    return button;
  }

  private Button enableAllButton() {
    return new Button(p -> GuiItems.named(Material.LIME_DYE, GuiI18n.tr(p, "gui.shop.editor.batch.enable.title"), List.of(
        GuiI18n.tr(p, "gui.shop.editor.batch.enable.hint"))), ctx -> {
      if (!applyEnabled(true)) {
        ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "gui.shop.editor.batch.enable.error"));
        return;
      }
      ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "gui.shop.editor.batch.enable.success"));
      ctx.window().redraw(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button disableAllButton() {
    return new Button(p -> GuiItems.named(Material.RED_DYE, GuiI18n.tr(p, "gui.shop.editor.batch.disable.title"), List.of(
        GuiI18n.tr(p, "gui.shop.editor.batch.disable.hint"))), ctx -> {
      if (!applyEnabled(false)) {
        ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "gui.shop.editor.batch.disable.error"));
        return;
      }
      ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "gui.shop.editor.batch.disable.success"));
      ctx.window().redraw(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private TextButton deleteAllButton() {
    TextButton button = new TextButton(p -> GuiItems.named(Material.BARRIER, GuiI18n.tr(p, "gui.shop.editor.batch.delete.title"), List.of(
        Locales.component(p, "gui.shop.editor.batch.delete.hint", Locales.placeholders("word", DELETE_WORD)))),
        Locales.component(null, "gui.shop.editor.batch.delete.prompt", Locales.placeholders("word", DELETE_WORD)),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        DELETE_TIMEOUT,
        (window, text) -> {
          Player player = viewerPlayer(window);
          if (player == null) {
            return;
          }
          if (!DELETE_WORD.equalsIgnoreCase(text == null ? "" : text.trim())) {
            player.sendMessage(GuiI18n.tr(player, "gui.shop.editor.batch.delete.cancelled"));
            return;
          }
          if (!deleteAll()) {
            player.sendMessage(GuiI18n.tr(player, "gui.shop.editor.batch.delete.error"));
            return;
          }
          player.sendMessage(GuiI18n.tr(player, "gui.shop.editor.batch.delete.success"));
          window.redraw(player);
        },
        true);
    button.autoDescribeInLore(false);
    return button;
  }

  private int shopCount() {
    return registry.shops().size();
  }

  private boolean applyTitleTemplate(String template) {
    File file = registry.file();
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection shops = cfg.getConfigurationSection("shops");
    if (shops == null) {
      return false;
    }
    for (String id : shops.getKeys(false)) {
      String title = template.replace("{id}", id);
      shops.set(id + ".title", title);
    }
    return save(cfg, file);
  }

  private boolean applyEnabled(boolean enabled) {
    File file = registry.file();
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection shops = cfg.getConfigurationSection("shops");
    if (shops == null) {
      return false;
    }
    for (String id : shops.getKeys(false)) {
      shops.set(id + ".enabled", enabled ? null : false);
    }
    return save(cfg, file);
  }

  private boolean deleteAll() {
    File file = registry.file();
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection shops = cfg.getConfigurationSection("shops");
    if (shops == null) {
      return false;
    }
    for (String id : shops.getKeys(false)) {
      shops.set(id, null);
    }
    return save(cfg, file);
  }

  private boolean save(YamlConfiguration cfg, File file) {
    try {
      cfg.save(file);
    } catch (IOException ex) {
      return false;
    }
    registry.reload();
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
