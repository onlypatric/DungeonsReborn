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
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.mobs.MobYamlRegistry;
import dev.patric.dungeonsreborn.mobs.editor.MobEditorYaml;
import net.kyori.adventure.text.Component;

public final class MobEditorLootMenu extends Window {
  private static final int SIZE = 54;

  public enum ListType {
    DROPS("drops", "Drops"),
    GUARANTEED("guaranteed", "Guaranteed");

    private final String key;
    private final String label;

    ListType(String key, String label) {
      this.key = key;
      this.label = label;
    }

    public String key() {
      return key;
    }

    public String label() {
      return label;
    }
  }

  private record LootEntry(int index, Map<String, Object> data) {
  }

  private final MobYamlRegistry yaml;
  private final String mobId;
  private final ListType type;
  private final VirtualList<LootEntry> list;

  public MobEditorLootMenu(MobYamlRegistry yaml, String mobId, ListType type) {
    super(SIZE, GuiMini.mm("<white><bold>Loot:</bold></white> <gray>" + mobId + "</gray>"), true);
    this.yaml = Objects.requireNonNull(yaml, "yaml");
    this.mobId = Objects.requireNonNull(mobId, "mobId");
    this.type = Objects.requireNonNull(type, "type");

    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(entry),
        (ctx, entry) -> openEntry(ctx.player(), entry.index()));
    list.apply(this, Placement.FIXED);

    navLeft(new BackButton(p -> GuiItems.named(Material.BARRIER, GuiMini.mm("<red><bold>Back</bold></red>"), List.of())));
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());
    nav(4, addButton());

    setFixedAt(0, 1, clearVanillaButton());
    if (type == ListType.DROPS) {
      setFixedAt(0, 3, rollsButton());
      setFixedAt(0, 4, bonusRollsButton());
      setFixedAt(0, 5, luckMultiplierButton());
      setFixedAt(0, 7, announceTiersButton());
      setFixedAt(0, 8, announceTemplateButton());
    }

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private List<LootEntry> entries(Player player) {
    List<Map<String, Object>> raw = MobEditorYaml.lootEntries(file(), mobId, type.key());
    List<LootEntry> entries = new ArrayList<>();
    for (int i = 0; i < raw.size(); i++) {
      entries.add(new LootEntry(i, raw.get(i)));
    }
    return entries;
  }

  private ItemStack entryItem(LootEntry entry) {
    ItemStack base = MobEditorYaml.previewLootItem(entry.data());
    List<Component> lore = new ArrayList<>();
    String tier = stringValue(entry.data(), "tier");
    if (tier != null) {
      lore.add(GuiMini.mm("<gray>Tier:</gray> <white>" + tier + "</white>"));
    }
    if (type == ListType.DROPS) {
      Double chance = numberValue(entry.data(), "chance");
      if (chance == null) {
        chance = 100.0;
      }
      double percent = chance <= 1.0 ? chance * 100.0 : chance;
      lore.add(GuiMini.mm("<gray>Chance:</gray> <white>" + formatPercent(percent) + "</white>"));
    }
    Integer min = intValue(entry.data(), "min");
    Integer max = intValue(entry.data(), "max");
    Integer amount = intValue(entry.data(), "amount");
    if (amount != null) {
      lore.add(GuiMini.mm("<gray>Amount:</gray> <white>" + amount + "</white>"));
    } else {
      lore.add(GuiMini.mm("<gray>Min:</gray> <white>" + (min == null ? "1" : min) + "</white>"));
      lore.add(GuiMini.mm("<gray>Max:</gray> <white>" + (max == null ? "1" : max) + "</white>"));
    }
    String token = stringValue(entry.data(), "token");
    if (token == null) {
      token = stringValue(entry.data(), "tokenTier");
    }
    if (token != null) {
      lore.add(GuiMini.mm("<gray>Token:</gray> <white>" + token + "</white>"));
    }
    return GuiItem.of(base)
        .displayName(GuiMini.mm("<yellow><bold>Drop #" + (entry.index() + 1) + "</bold></yellow>"))
        .lore(lore)
        .build();
  }

  private Button addButton() {
    return new Button(p -> GuiItems.named(Material.EMERALD, GuiMini.mm("<green><bold>Add Drop</bold></green>"), List.of(
        GuiMini.mm("<gray>Use the item in your hand.</gray>"))), ctx -> {
      Player player = ctx.player();
      ItemStack hand = player.getInventory().getItemInMainHand();
      if (hand == null || hand.getType().isAir()) {
        player.sendMessage(Component.text("§cHold an item to add."));
        return;
      }
      List<Map<String, Object>> entries = new ArrayList<>(MobEditorYaml.lootEntries(file(), mobId, type.key()));
      Map<String, Object> entry = new java.util.LinkedHashMap<>();
      entry.put("item", hand.clone());
      int amount = Math.max(1, hand.getAmount());
      entry.put("min", amount);
      entry.put("max", amount);
      if (type == ListType.DROPS) {
        entry.put("chance", 100.0);
      }
      entries.add(entry);
      MobEditorYaml.setLootEntries(file(), mobId, type.key(), entries);
      reloadYaml(player, "loot.add");
      refreshList();
      openEntry(player, entries.size() - 1);
    }).autoDescribeInLore(false);
  }

  private Button clearVanillaButton() {
    return new Button(p -> {
      boolean value = MobEditorYaml.lootClearVanilla(file(), mobId);
      Material mat = value ? Material.LIME_DYE : Material.RED_DYE;
      return GuiItems.named(mat, GuiMini.mm("<yellow><bold>Clear Vanilla</bold></yellow>"), List.of(
          GuiMini.mm("<gray>Current:</gray> <white>" + value + "</white>")));
    }, ctx -> {
      Player player = ctx.player();
      boolean next = !MobEditorYaml.lootClearVanilla(file(), mobId);
      MobEditorYaml.setLootValue(file(), mobId, "clearVanilla", next);
      reloadYaml(player, "loot.clearVanilla");
      ctx.window().redrawSlot(player, slotAt(0, 1));
    }).autoDescribeInLore(false);
  }

  private TextButton rollsButton() {
    return new TextButton(
        p -> GuiItems.named(Material.PAPER, GuiMini.mm("<yellow><bold>Rolls</bold></yellow>"), List.of(
            GuiMini.mm("<gray>Current:</gray> <white>" + MobEditorYaml.lootRolls(file(), mobId) + "</white>"))),
        GuiMini.mm("<gray>Enter rolls count</gray>"),
        "cancel",
        Duration.ofSeconds(30),
        (w, text) -> updateInt(w, text, "rolls", "loot.rolls", 1),
        true).inputMode(TextButton.InputMode.CHAT);
  }

  private TextButton bonusRollsButton() {
    return new TextButton(
        p -> GuiItems.named(Material.MAP, GuiMini.mm("<yellow><bold>Bonus Rolls</bold></yellow>"), List.of(
            GuiMini.mm("<gray>Current:</gray> <white>" + MobEditorYaml.lootBonusRolls(file(), mobId) + "</white>"))),
        GuiMini.mm("<gray>Enter bonus rolls</gray>"),
        "cancel",
        Duration.ofSeconds(30),
        (w, text) -> updateInt(w, text, "bonusRolls", "loot.bonusRolls", 0),
        true).inputMode(TextButton.InputMode.CHAT);
  }

  private TextButton luckMultiplierButton() {
    return new TextButton(
        p -> GuiItems.named(Material.GLOWSTONE_DUST, GuiMini.mm("<yellow><bold>Luck Mult</bold></yellow>"), List.of(
            GuiMini.mm("<gray>Current:</gray> <white>" + MobEditorYaml.lootLuckMultiplier(file(), mobId) + "</white>"),
            GuiMini.mm("<gray>Applied to player LUCK attribute.</gray>"))),
        GuiMini.mm("<gray>Enter luck multiplier</gray>"),
        "cancel",
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = viewerPlayer(w);
          if (player == null) {
            return;
          }
          try {
            double value = Double.parseDouble(text.trim());
            if (!Double.isFinite(value) || value < 0.0) {
              throw new NumberFormatException("invalid");
            }
            MobEditorYaml.setLootValue(file(), mobId, "luckMultiplier", value);
            reloadYaml(player, "loot.luckMultiplier");
            w.redrawSlot(player, slotAt(0, 5));
          } catch (NumberFormatException ex) {
            player.sendMessage(Component.text("§cInvalid number: " + text));
          }
        },
        true).inputMode(TextButton.InputMode.CHAT);
  }

  private TextButton announceTiersButton() {
    return new TextButton(
        p -> GuiItems.named(Material.BELL, GuiMini.mm("<yellow><bold>Announce Tiers</bold></yellow>"), List.of(
            GuiMini.mm("<gray>Current:</gray> <white>" + String.join(", ", MobEditorYaml.lootAnnounceTiers(file(), mobId)) + "</white>"),
            GuiMini.mm("<gray>Comma-separated tiers.</gray>"))),
        GuiMini.mm("<gray>Enter tiers (comma-separated)</gray>"),
        "cancel",
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = viewerPlayer(w);
          if (player == null) {
            return;
          }
          List<String> tiers = splitCsv(text);
          MobEditorYaml.setLootValue(file(), mobId, "announceTiers", tiers);
          reloadYaml(player, "loot.announceTiers");
          w.redrawSlot(player, slotAt(0, 7));
        },
        true).inputMode(TextButton.InputMode.CHAT);
  }

  private TextButton announceTemplateButton() {
    return new TextButton(
        p -> GuiItems.named(Material.WRITABLE_BOOK, GuiMini.mm("<yellow><bold>Announce Text</bold></yellow>"), List.of(
            GuiMini.mm("<gray>Current:</gray> <white>" + nullToNone(MobEditorYaml.lootAnnounceTemplate(file(), mobId)) + "</white>"),
            GuiMini.mm("<gray>Use {player} {mob} {item} {tier} {amount}</gray>"))),
        GuiMini.mm("<gray>Enter announcement template</gray>"),
        "cancel",
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = viewerPlayer(w);
          if (player == null) {
            return;
          }
          String value = text == null ? "" : text.trim();
          MobEditorYaml.setLootValue(file(), mobId, "announceTemplate", value.isBlank() ? null : value);
          reloadYaml(player, "loot.announceTemplate");
          w.redrawSlot(player, slotAt(0, 8));
        },
        true).inputMode(TextButton.InputMode.CHAT);
  }

  private void updateInt(Window w, String text, String key, String detail, int min) {
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
      if (value < min) {
        throw new NumberFormatException("too small");
      }
      MobEditorYaml.setLootValue(file(), mobId, key, value);
      reloadYaml(player, detail);
      w.redraw(player);
    } catch (NumberFormatException ex) {
      player.sendMessage(Component.text("§cInvalid number: " + text));
    }
  }

  private void openEntry(Player player, int index) {
    openSubWindow(player, new MobEditorLootEntryMenu(yaml, mobId, type, index, this::refreshList));
  }

  private void refreshList() {
    Player viewer = viewer() == null ? null : org.bukkit.Bukkit.getPlayer(viewer());
    if (viewer != null) {
      list.invalidate(viewer);
      list.redraw(this, viewer);
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

  private static String nullToNone(String value) {
    return value == null || value.isBlank() ? "(none)" : value;
  }

  private static String formatPercent(double value) {
    if (!Double.isFinite(value)) {
      return "0%";
    }
    return String.format(Locale.ROOT, "%.2f%%", value);
  }

  private static Integer intValue(Map<String, Object> map, String key) {
    Object raw = map.get(key);
    if (raw == null) {
      return null;
    }
    try {
      return Integer.parseInt(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static Double numberValue(Map<String, Object> map, String key) {
    Object raw = map.get(key);
    if (raw == null) {
      return null;
    }
    try {
      return Double.parseDouble(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static String stringValue(Map<String, Object> map, String key) {
    Object raw = map.get(key);
    if (raw == null) {
      return null;
    }
    String text = raw.toString();
    return text.isBlank() ? null : text;
  }

  private static List<String> splitCsv(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    String[] parts = text.split(",");
    List<String> out = new ArrayList<>();
    for (String part : parts) {
      String trimmed = part.trim();
      if (!trimmed.isBlank()) {
        out.add(trimmed);
      }
    }
    return out;
  }
}
