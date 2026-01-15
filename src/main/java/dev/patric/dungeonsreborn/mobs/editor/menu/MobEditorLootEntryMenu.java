package dev.patric.dungeonsreborn.mobs.editor.menu;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.mobs.MobYamlRegistry;
import dev.patric.dungeonsreborn.mobs.editor.MobEditorYaml;
import net.kyori.adventure.text.Component;

public final class MobEditorLootEntryMenu extends Window {
  private static final int SIZE = 54;

  private final MobYamlRegistry yaml;
  private final String mobId;
  private final MobEditorLootMenu.ListType type;
  private final int index;
  private final Runnable onCloseRefresh;

  public MobEditorLootEntryMenu(MobYamlRegistry yaml, String mobId,
      MobEditorLootMenu.ListType type, int index, Runnable onCloseRefresh) {
    super(SIZE, GuiMini.mm("<white><bold>Loot Entry</bold></white>"), true);
    this.yaml = Objects.requireNonNull(yaml, "yaml");
    this.mobId = Objects.requireNonNull(mobId, "mobId");
    this.type = Objects.requireNonNull(type, "type");
    this.index = index;
    this.onCloseRefresh = onCloseRefresh == null ? () -> {
    } : onCloseRefresh;

    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));

    setFixedAt(1, 1, itemButton());
    if (type == MobEditorLootMenu.ListType.DROPS) {
      setFixedAt(1, 3, chanceButton());
    }
    setFixedAt(1, 5, minButton());
    setFixedAt(1, 7, maxButton());
    setFixedAt(2, 1, tierButton());
    setFixedAt(2, 3, tokenButton());
    setFixedAt(2, 7, deleteButton());

    navLeft(new BackButton(p -> GuiItems.named(Material.BARRIER, GuiMini.mm("<red><bold>Back</bold></red>"), List.of())));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> {
      this.onCloseRefresh.run();
      GuiSounds.close(ctx.player());
    });
  }

  private Button itemButton() {
    return new Button(p -> {
      Map<String, Object> entry = entry();
      ItemStack preview = MobEditorYaml.previewLootItem(entry);
      return GuiItem.of(preview)
          .displayName(GuiMini.mm("<yellow><bold>Item</bold></yellow>"))
          .lore(List.of(
              GuiMini.mm("<gray>Click to use held item.</gray>")))
          .build();
    }, ctx -> {
      Player player = ctx.player();
      ItemStack hand = player.getInventory().getItemInMainHand();
      if (hand == null || hand.getType().isAir()) {
        player.sendMessage(Component.text("§cHold an item first."));
        return;
      }
      Map<String, Object> entry = entry();
      entry.put("item", hand.clone());
      entry.remove("material");
      entry.remove("itemId");
      entry.remove("id");
      entry.remove("token");
      entry.remove("tokenTier");
      int amount = Math.max(1, hand.getAmount());
      entry.put("min", amount);
      entry.put("max", amount);
      entry.remove("amount");
      saveEntry(entry, "loot.item");
      ctx.window().redraw(ctx.player());
    }).autoDescribeInLore(false);
  }

  private TextButton chanceButton() {
    return new TextButton(
        p -> GuiItems.named(Material.PAPER, GuiMini.mm("<yellow><bold>Chance</bold></yellow>"), List.of(
            GuiMini.mm("<gray>Current:</gray> <white>" + chanceDisplay() + "</white>"),
            GuiMini.mm("<gray>Use 0-1 or 0-100%</gray>"))),
        GuiMini.mm("<gray>Enter chance</gray>"),
        "cancel",
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = viewerPlayer(w);
          if (player == null) {
            return;
          }
          if (text == null || text.isBlank()) {
            player.sendMessage(Component.text("§cInvalid number."));
            return;
          }
          try {
            double value = Double.parseDouble(text.trim());
            Map<String, Object> entry = entry();
            entry.put("chance", value);
            saveEntry(entry, "loot.chance");
            w.redraw(player);
          } catch (NumberFormatException ex) {
            player.sendMessage(Component.text("§cInvalid number: " + text));
          }
        },
        true).inputMode(TextButton.InputMode.CHAT);
  }

  private TextButton minButton() {
    return new TextButton(
        p -> GuiItems.named(Material.MAP, GuiMini.mm("<yellow><bold>Min</bold></yellow>"), List.of(
            GuiMini.mm("<gray>Current:</gray> <white>" + intValue(entry(), "min", 1) + "</white>"))),
        GuiMini.mm("<gray>Enter min amount</gray>"),
        "cancel",
        Duration.ofSeconds(30),
        (w, text) -> updateAmount(w, text, "min", "loot.min"),
        true).inputMode(TextButton.InputMode.CHAT);
  }

  private TextButton maxButton() {
    return new TextButton(
        p -> GuiItems.named(Material.FILLED_MAP, GuiMini.mm("<yellow><bold>Max</bold></yellow>"), List.of(
            GuiMini.mm("<gray>Current:</gray> <white>" + intValue(entry(), "max", 1) + "</white>"))),
        GuiMini.mm("<gray>Enter max amount</gray>"),
        "cancel",
        Duration.ofSeconds(30),
        (w, text) -> updateAmount(w, text, "max", "loot.max"),
        true).inputMode(TextButton.InputMode.CHAT);
  }

  private TextButton tierButton() {
    return new TextButton(
        p -> GuiItems.named(Material.NAME_TAG, GuiMini.mm("<yellow><bold>Tier</bold></yellow>"), List.of(
            GuiMini.mm("<gray>Current:</gray> <white>" + nullToNone(stringValue(entry(), "tier")) + "</white>"),
            GuiMini.mm("<gray>Leave blank to clear.</gray>"))),
        GuiMini.mm("<gray>Enter tier</gray>"),
        "cancel",
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = viewerPlayer(w);
          if (player == null) {
            return;
          }
          Map<String, Object> entry = entry();
          if (text == null || text.isBlank()) {
            entry.remove("tier");
          } else {
            entry.put("tier", text.trim());
          }
          saveEntry(entry, "loot.tier");
          w.redraw(player);
        },
        true).inputMode(TextButton.InputMode.CHAT);
  }

  private TextButton tokenButton() {
    return new TextButton(
        p -> GuiItems.named(Material.SUNFLOWER, GuiMini.mm("<gold><bold>Token Tier</bold></gold>"), List.of(
            GuiMini.mm("<gray>Current:</gray> <white>" + nullToNone(tokenValue(entry())) + "</white>"),
            GuiMini.mm("<gray>Example:</gray> <white>token/compressed/pallet</white>"))),
        GuiMini.mm("<gray>Enter token tier</gray>"),
        "cancel",
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = viewerPlayer(w);
          if (player == null) {
            return;
          }
          Map<String, Object> entry = entry();
          if (text == null || text.isBlank()) {
            entry.remove("token");
            entry.remove("tokenTier");
          } else {
            entry.put("token", text.trim());
            entry.remove("item");
            entry.remove("material");
            entry.remove("itemId");
            entry.remove("id");
          }
          saveEntry(entry, "loot.token");
          w.redraw(player);
        },
        true).inputMode(TextButton.InputMode.CHAT);
  }

  private TextButton deleteButton() {
    return new TextButton(
        p -> GuiItems.named(Material.BARRIER, GuiMini.mm("<red><bold>Delete</bold></red>"), List.of(
            GuiMini.mm("<gray>Type delete to confirm.</gray>"))),
        GuiMini.mm("<red>Type delete to remove</red>"),
        "cancel",
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = viewerPlayer(w);
          if (player == null) {
            return;
          }
          if (!"delete".equalsIgnoreCase(text == null ? "" : text.trim())) {
            player.sendMessage(Component.text("§cType delete to confirm."));
            return;
          }
          List<Map<String, Object>> entries = new ArrayList<>(MobEditorYaml.lootEntries(file(), mobId, type.key()));
          if (index >= 0 && index < entries.size()) {
            entries.remove(index);
            MobEditorYaml.setLootEntries(file(), mobId, type.key(), entries);
            reloadYaml(player, "loot.delete");
            player.closeInventory();
          }
        },
        true).inputMode(TextButton.InputMode.CHAT);
  }

  private void updateAmount(Window w, String text, String key, String detail) {
    Player player = viewerPlayer(w);
    if (player == null) {
      return;
    }
    if (text == null || text.isBlank()) {
      player.sendMessage(Component.text("§cInvalid number."));
      return;
    }
    try {
      int value = Integer.parseInt(text.trim());
      if (value <= 0) {
        throw new NumberFormatException("too small");
      }
      Map<String, Object> entry = entry();
      entry.put(key, value);
      entry.remove("amount");
      saveEntry(entry, detail);
      w.redraw(player);
    } catch (NumberFormatException ex) {
      player.sendMessage(Component.text("§cInvalid number: " + text));
    }
  }

  private void saveEntry(Map<String, Object> entry, String detail) {
    List<Map<String, Object>> entries = new ArrayList<>(MobEditorYaml.lootEntries(file(), mobId, type.key()));
    if (index < 0 || index >= entries.size()) {
      return;
    }
    entries.set(index, entry);
    MobEditorYaml.setLootEntries(file(), mobId, type.key(), entries);
    Player viewer = viewerPlayer(this);
    if (viewer != null) {
      reloadYaml(viewer, detail);
    }
  }

  private Map<String, Object> entry() {
    List<Map<String, Object>> entries = MobEditorYaml.lootEntries(file(), mobId, type.key());
    if (index < 0 || index >= entries.size()) {
      return new java.util.LinkedHashMap<>();
    }
    return new java.util.LinkedHashMap<>(entries.get(index));
  }

  private String chanceDisplay() {
    Map<String, Object> entry = entry();
    Object raw = entry.get("chance");
    if (raw == null) {
      return "100%";
    }
    try {
      double value = Double.parseDouble(String.valueOf(raw));
      double percent = value <= 1.0 ? value * 100.0 : value;
      return String.format(Locale.ROOT, "%.2f%%", percent);
    } catch (NumberFormatException ex) {
      return String.valueOf(raw);
    }
  }

  private void reloadYaml(Player player, String detail) {
    yaml.reload();
    yaml.logger().info("[Mobs][Editor] EDIT actor=" + player.getName() + " mob=" + mobId + " detail=" + detail);
  }

  private File file() {
    return yaml.file();
  }

  private static Player viewerPlayer(Window window) {
    return window.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(window.viewer());
  }

  private static int intValue(Map<String, Object> map, String key, int fallback) {
    Object raw = map.get(key);
    if (raw == null) {
      return fallback;
    }
    try {
      return Integer.parseInt(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static String stringValue(Map<String, Object> map, String key) {
    Object raw = map.get(key);
    if (raw == null) {
      return null;
    }
    String value = raw.toString();
    return value.isBlank() ? null : value;
  }

  private static String tokenValue(Map<String, Object> map) {
    String token = stringValue(map, "token");
    if (token != null) {
      return token;
    }
    return stringValue(map, "tokenTier");
  }

  private static String nullToNone(String value) {
    return value == null || value.isBlank() ? "(none)" : value;
  }
}
