package dev.patric.dungeonsreborn.mobs.editor.menu;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.mobs.MobYamlRegistry;
import dev.patric.dungeonsreborn.mobs.editor.MobEditorYaml;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import net.kyori.adventure.text.Component;

public final class MobEditorDetailMenu extends Window {
  private static final int SIZE = 54;
  private final MobYamlRegistry yaml;
  private final MobRegistry registry;
  private final String mobId;

  public MobEditorDetailMenu(MobYamlRegistry yaml, MobRegistry registry, String mobId) {
    super(SIZE, GuiMini.mm("<white><bold>Mob:</bold></white> <gray>" + mobId + "</gray>"), true);
    this.yaml = Objects.requireNonNull(yaml, "yaml");
    this.registry = Objects.requireNonNull(registry, "registry");
    this.mobId = Objects.requireNonNull(mobId, "mobId");

    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));

    setFixedAt(1, 1, nameButton());
    setFixedAt(1, 3, showNameButton());
    setFixedAt(1, 5, previewButton());
    setFixedAt(1, 6, giveEggButton());
    setFixedAt(1, 7, exportButton());

    setFixedAt(3, 1, mainAbilityButton());
    setFixedAt(3, 3, secondaryAbilityButton());

    setFixedAt(4, 1, statButton("maxHealth", "Max Health"));
    setFixedAt(4, 3, statButton("attackDamage", "Attack Damage"));
    setFixedAt(4, 5, statButton("movementSpeed", "Move Speed"));
    setFixedAt(4, 7, statButton("followRange", "Follow Range"));

    navLeft(new BackButton(p -> GuiItems.named(Material.BARRIER, GuiMini.mm("<red><bold>Back</bold></red>"), List.of())));
    nav(5, reloadButton());
    nav(6, errorsButton());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private TextButton nameButton() {
    return new TextButton(
        p -> GuiItems.named(Material.NAME_TAG, GuiMini.mm("<aqua><bold>Name</bold></aqua>"), List.of(
            GuiMini.mm("<gray>Current:</gray> <white>" + nullToNone(MobEditorYaml.name(file(), mobId)) + "</white>"),
            GuiMini.mm("<gray>Leave blank to clear.</gray>"))),
        GuiMini.mm("<gray>Enter a new name (or 'cancel')</gray>"),
        "cancel",
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = getViewer(w);
          if (player == null) {
            return;
          }
          MobEditorYaml.setName(file(), mobId, text == null ? "" : text.trim());
          reloadYaml(player, "name");
          w.redrawSlot(player, slotAt(1, 1));
        },
        true).inputMode(TextButton.InputMode.CHAT);
  }

  private Button showNameButton() {
    return new Button(p -> {
      Boolean value = MobEditorYaml.showName(file(), mobId);
      String text = value == null ? "(default)" : value ? "true" : "false";
      Material material = value == null ? Material.GRAY_DYE : value ? Material.LIME_DYE : Material.RED_DYE;
      return GuiItems.named(material, GuiMini.mm("<yellow><bold>Show Name</bold></yellow>"), List.of(
          GuiMini.mm("<gray>Current:</gray> <white>" + text + "</white>")));
    }, ctx -> {
      Player player = ctx.player();
      Boolean value = MobEditorYaml.showName(file(), mobId);
      boolean next = value == null || !value;
      MobEditorYaml.setShowName(file(), mobId, next);
      reloadYaml(player, "showName");
      ctx.window().redrawSlot(player, slotAt(1, 3));
    }).autoDescribeInLore(false);
  }

  private TextButton mainAbilityButton() {
    return new TextButton(
        p -> GuiItems.named(Material.BLAZE_POWDER, GuiMini.mm("<aqua><bold>Main Ability</bold></aqua>"), List.of(
            GuiMini.mm("<gray>Current:</gray> <white>" + nullToNone(MobEditorYaml.mainAbility(file(), mobId)) + "</white>"),
            GuiMini.mm("<gray>Blank clears the ability.</gray>"))),
        GuiMini.mm("<gray>Enter main ability id</gray>"),
        "cancel",
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = getViewer(w);
          if (player == null) {
            return;
          }
          String normalized = normalizeId(text);
          MobEditorYaml.setMainAbility(file(), mobId, normalized);
          reloadYaml(player, "main ability");
          w.redrawSlot(player, slotAt(3, 1));
        },
        true).inputMode(TextButton.InputMode.CHAT);
  }

  private TextButton secondaryAbilityButton() {
    return new TextButton(
        p -> GuiItems.named(Material.BLAZE_ROD, GuiMini.mm("<aqua><bold>Secondary Ability</bold></aqua>"), List.of(
            GuiMini.mm("<gray>Current:</gray> <white>" + nullToNone(MobEditorYaml.secondaryAbility(file(), mobId)) + "</white>"),
            GuiMini.mm("<gray>Blank clears the ability.</gray>"))),
        GuiMini.mm("<gray>Enter secondary ability id</gray>"),
        "cancel",
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = getViewer(w);
          if (player == null) {
            return;
          }
          String normalized = normalizeId(text);
          MobEditorYaml.setSecondaryAbility(file(), mobId, normalized);
          reloadYaml(player, "secondary ability");
          w.redrawSlot(player, slotAt(3, 3));
        },
        true).inputMode(TextButton.InputMode.CHAT);
  }

  private TextButton statButton(String key, String label) {
    return new TextButton(
        p -> {
          Double value = MobEditorYaml.stat(file(), mobId, key);
          return GuiItems.named(Material.PAPER, GuiMini.mm("<yellow><bold>" + label + "</bold></yellow>"), List.of(
              GuiMini.mm("<gray>Current:</gray> <white>" + (value == null ? "(default)" : value) + "</white>"),
              GuiMini.mm("<gray>Blank clears the stat.</gray>")));
        },
        GuiMini.mm("<gray>Enter a numeric value (or blank)</gray>"),
        "cancel",
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = getViewer(w);
          if (player == null) {
            return;
          }
          if (text == null || text.isBlank()) {
            MobEditorYaml.setStat(file(), mobId, key, null);
            reloadYaml(player, "stat " + key);
            w.redrawSlot(player, slotAt(4, 1));
            w.redrawSlot(player, slotAt(4, 3));
            w.redrawSlot(player, slotAt(4, 5));
            w.redrawSlot(player, slotAt(4, 7));
            return;
          }
          try {
            double value = Double.parseDouble(text.trim());
            if (!Double.isFinite(value)) {
              throw new NumberFormatException("not finite");
            }
            MobEditorYaml.setStat(file(), mobId, key, value);
            reloadYaml(player, "stat " + key);
            w.redrawSlot(player, slotAt(4, 1));
            w.redrawSlot(player, slotAt(4, 3));
            w.redrawSlot(player, slotAt(4, 5));
            w.redrawSlot(player, slotAt(4, 7));
          } catch (NumberFormatException ex) {
            player.sendMessage(Component.text("§cInvalid number: " + text));
          }
        },
        true).inputMode(TextButton.InputMode.CHAT);
  }

  private Button previewButton() {
    return new Button(p -> GuiItems.named(Material.SPAWNER, GuiMini.mm("<green><bold>Preview</bold></green>"), List.of(
        GuiMini.mm("<gray>Spawn this mob near you.</gray>"))), ctx -> {
      Player player = ctx.player();
      try {
        registry.spawn(mobId, player.getLocation(), player.getUniqueId());
        player.sendMessage(Component.text("§aPreview spawned: " + mobId));
      } catch (Exception ex) {
        player.sendMessage(Component.text("§cSpawn failed: " + ex.getMessage()));
      }
    }).autoDescribeInLore(false);
  }

  private Button giveEggButton() {
    return new Button(p -> GuiItems.named(Material.EGG, GuiMini.mm("<aqua><bold>Give Egg</bold></aqua>"), List.of(
        GuiMini.mm("<gray>Give yourself the spawn egg.</gray>"))), ctx -> {
      Player player = ctx.player();
      ItemStack egg = yaml.eggItemForMob(mobId);
      if (egg == null) {
        player.sendMessage(Component.text("§cNo spawn egg configured for " + mobId));
        return;
      }
      var leftovers = player.getInventory().addItem(egg);
      if (!leftovers.isEmpty()) {
        player.sendMessage(Component.text("§cInventory full."));
        return;
      }
      player.sendMessage(Component.text("§aGiven egg for " + mobId));
    }).autoDescribeInLore(false);
  }

  private Button exportButton() {
    return new Button(p -> GuiItems.named(Material.WRITABLE_BOOK, GuiMini.mm("<aqua><bold>Export</bold></aqua>"), List.of(
        GuiMini.mm("<gray>Save this mob to an export file.</gray>"))), ctx -> {
      Player player = ctx.player();
      File outDir = new File(file().getParentFile(), "mobs/exports");
      outDir.mkdirs();
      File out = new File(outDir, mobId + ".yml");
      try {
        MobEditorYaml.exportSingle(file(), mobId, out);
        player.sendMessage(Component.text("§aExported to " + out.getPath()));
      } catch (Exception ex) {
        player.sendMessage(Component.text("§cExport failed: " + ex.getMessage()));
      }
    }).autoDescribeInLore(false);
  }

  private Button reloadButton() {
    return new Button(p -> GuiItems.named(Material.CLOCK, GuiMini.mm("<yellow><bold>Reload</bold></yellow>"), List.of(
        GuiMini.mm("<gray>Reload mob YAML.</gray>"))), ctx -> {
      reloadYaml(ctx.player(), "manual reload");
    }).autoDescribeInLore(false);
  }

  private Button errorsButton() {
    return new Button(p -> GuiItems.named(Material.BOOK, GuiMini.mm("<aqua><bold>Errors</bold></aqua>"), List.of(
        GuiMini.mm("<gray>Show last YAML errors.</gray>"))), ctx -> {
      Player player = ctx.player();
      List<String> errors = yaml.lastErrors();
      if (errors.isEmpty()) {
        player.sendMessage(Component.text("§a[Mobs] No YAML errors."));
        return;
      }
      player.sendMessage(Component.text("§6[Mobs] YAML errors (" + errors.size() + "):"));
      for (String error : errors) {
        player.sendMessage(Component.text("§c- " + error));
      }
    }).autoDescribeInLore(false);
  }

  private void reloadYaml(Player player, String detail) {
    yaml.reload();
    yaml.plugin().getLogger().info("[Mobs][Editor] EDIT actor=" + player.getName() + " mob=" + mobId + " detail=" + detail);
  }

  private File file() {
    return yaml.file();
  }

  private static Player getViewer(Window window) {
    return window.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(window.viewer());
  }

  private static String nullToNone(String value) {
    return value == null || value.isBlank() ? "(none)" : value;
  }

  private static String normalizeId(String text) {
    if (text == null) {
      return null;
    }
    String trimmed = text.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    try {
      return Ids.normalize(trimmed);
    } catch (Exception ex) {
      return trimmed;
    }
  }
}
