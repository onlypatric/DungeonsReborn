package dev.patric.dungeonsreborn.menus;

import java.util.List;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.classes.ClassYamlRegistry;
import dev.patric.dungeonsreborn.classes.editor.menu.ClassEditorListMenu;
import dev.patric.dungeonsreborn.crafting.CraftingGuiSessionManager;
import dev.patric.dungeonsreborn.crafting.CraftingYamlRegistry;
import dev.patric.dungeonsreborn.dungeons.DungeonQueueService;
import dev.patric.dungeonsreborn.dungeons.DungeonSessionManager;
import dev.patric.dungeonsreborn.dungeons.DungeonYamlRegistry;
import dev.patric.dungeonsreborn.dungeons.menu.DungeonAdminMenu;
import dev.patric.dungeonsreborn.effects.editor.EditorServices;
import dev.patric.dungeonsreborn.effects.editor.menu.EditorAbilityListMenu;
import dev.patric.dungeonsreborn.effects.editor.menu.EditorItemListMenu;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeService;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.mobs.MobYamlRegistry;
import dev.patric.dungeonsreborn.mobs.editor.menu.MobEditorListMenu;
import dev.patric.dungeonsreborn.quests.QuestYamlRegistry;
import dev.patric.dungeonsreborn.quests.editor.menu.QuestEditorListMenu;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import dev.patric.dungeonsreborn.system.SystemStatusStore;

public final class AdminHubMenu extends Window {
  private static final int SIZE = 54;

  private final EditorServices editor;
  private final MobYamlRegistry mobsYaml;
  private final MobRegistry mobsRegistry;
  private final QuestYamlRegistry quests;
  private final ShopYamlRegistry shops;
  private final CraftingYamlRegistry crafting;
  private final CraftingGuiSessionManager craftingSessions;
  private final ClassYamlRegistry classes;
  private final UpgradeService upgrades;
  private final DungeonYamlRegistry dungeons;
  private final DungeonQueueService dungeonQueue;
  private final DungeonSessionManager dungeonSessions;

  public AdminHubMenu(EditorServices editor,
      MobYamlRegistry mobsYaml,
      MobRegistry mobsRegistry,
      QuestYamlRegistry quests,
      ShopYamlRegistry shops,
      CraftingYamlRegistry crafting,
      CraftingGuiSessionManager craftingSessions,
      ClassYamlRegistry classes,
      UpgradeService upgrades,
      DungeonYamlRegistry dungeons,
      DungeonQueueService dungeonQueue,
      DungeonSessionManager dungeonSessions) {
    super(SIZE, GuiI18n.tr("gui.adminHub.title"), true);
    this.editor = editor;
    this.mobsYaml = mobsYaml;
    this.mobsRegistry = mobsRegistry;
    this.quests = quests;
    this.shops = shops;
    this.crafting = crafting;
    this.craftingSessions = craftingSessions;
    this.classes = classes;
    this.upgrades = upgrades;
    this.dungeons = dungeons;
    this.dungeonQueue = dungeonQueue;
    this.dungeonSessions = dungeonSessions;

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));
    navRight(new CloseButton(p -> GuiButtons.item(GuiButtons.Type.CLOSE, GuiI18n.tr(p, "gui.button.close"))).autoDescribeInLore(false));

    setFixed(4, new Label(GuiItems.named(Material.COMPASS, GuiI18n.tr("gui.adminHub.header.title"), List.of(
        GuiI18n.tr("gui.adminHub.header.subtitle")))));

    setFixed(19, button(Material.BLAZE_POWDER, "gui.adminHub.effectsEditor.title",
        "gui.adminHub.effectsEditor.hint",
        player -> {
          if (this.editor == null) {
            sendUnavailable(player, "labels.system.effectsEditor");
            return;
          }
          new EditorAbilityListMenu(this.editor).open(player);
        }));
    setFixed(20, button(Material.CHEST, "gui.adminHub.itemsEditor.title",
        "gui.adminHub.itemsEditor.hint",
        player -> {
          if (this.editor == null) {
            sendUnavailable(player, "labels.system.itemsEditor");
            return;
          }
          new EditorItemListMenu(this.editor).open(player);
        }));
    setFixed(21, button(Material.ENCHANTED_BOOK, "gui.adminHub.upgrades.title",
        "gui.adminHub.upgrades.hint",
        player -> {
          if (this.upgrades == null) {
            sendUnavailable(player, "labels.system.upgrades");
            return;
          }
          new UpgradeAdminListMenu(this.upgrades).open(player);
        }));
    setFixed(22, button(Material.ZOMBIE_HEAD, "gui.adminHub.mobEditor.title",
        "gui.adminHub.mobEditor.hint",
        player -> {
          if (this.mobsYaml == null || this.mobsRegistry == null) {
            sendUnavailable(player, "labels.system.mobsEditor");
            return;
          }
          new MobEditorListMenu(this.mobsYaml, this.mobsRegistry).open(player);
        }));
    setFixed(23, button(Material.COMPARATOR, "gui.adminHub.systemStatus.title",
        "gui.adminHub.systemStatus.hint",
        player -> new SystemStatusMenu(SystemStatusStore.get()).open(player)));
    setFixed(24, button(Material.WRITABLE_BOOK, "gui.adminHub.questEditor.title",
        "gui.adminHub.questEditor.hint",
        player -> {
          if (this.quests == null) {
            sendUnavailable(player, "labels.system.questEditor");
            return;
          }
          new QuestEditorListMenu(this.quests).open(player);
        }));
    setFixed(25, button(Material.EMERALD, "gui.adminHub.shopEditor.title",
        "gui.adminHub.shopEditor.hint",
        player -> {
          if (this.shops == null) {
            sendUnavailable(player, "labels.system.shops");
            return;
          }
          new ShopEditorListMenu(this.shops).open(player);
        }));
    setFixed(30, button(Material.COMMAND_BLOCK, "gui.adminHub.giveTools.title",
        "gui.adminHub.giveTools.hint",
        player -> new AdminGiveMenu().open(player)));
    setFixed(31, button(Material.CRAFTING_TABLE, "gui.adminHub.craftingEditor.title",
        "gui.adminHub.craftingEditor.hint",
        player -> {
          if (this.crafting == null || this.craftingSessions == null) {
            sendUnavailable(player, "labels.system.crafting");
            return;
          }
          new CraftingRecipeEditorMenu(this.crafting, this.craftingSessions).open(player);
        }));
    setFixed(32, button(Material.BOOK, "gui.adminHub.classEditor.title",
        "gui.adminHub.classEditor.hint",
        player -> {
          if (this.classes == null) {
            sendUnavailable(player, "labels.system.classes");
            return;
          }
          new ClassEditorListMenu(this.classes).open(player);
        }));
    setFixed(33, button(Material.DIAMOND_SWORD, "gui.adminHub.dungeonAdmin.title",
        "gui.adminHub.dungeonAdmin.hint",
        player -> {
          if (this.dungeons == null || this.dungeonQueue == null || this.dungeonSessions == null) {
            sendUnavailable(player, "labels.system.dungeons");
            return;
          }
          new DungeonAdminMenu(this.dungeons, this.dungeonQueue, this.dungeonSessions).open(player);
        }));

    setFixed(34, commandButton(Material.MAP, "gui.adminHub.dungeonValidate.title",
        "gui.adminHub.dungeonValidate.hint",
        "dr admin dungeon validate"));
    setFixed(35, commandButton(Material.REDSTONE, "gui.adminHub.dungeonReload.title",
        "gui.adminHub.dungeonReload.hint",
        "dr admin dungeon reload"));

    setFixed(37, commandButton(Material.NETHER_STAR, "gui.adminHub.reloadAll.title",
        "gui.adminHub.reloadAll.hint",
        "dr admin reload"));
    setFixed(38, commandButton(Material.BLAZE_POWDER, "gui.adminHub.reloadEffects.title",
        "gui.adminHub.reloadEffects.hint",
        "dr admin effects reload"));
    setFixed(39, commandButton(Material.ZOMBIE_HEAD, "gui.adminHub.reloadMobs.title",
        "gui.adminHub.reloadMobs.hint",
        "dr admin mobs reload"));
    setFixed(40, commandButton(Material.WRITABLE_BOOK, "gui.adminHub.reloadQuests.title",
        "gui.adminHub.reloadQuests.hint",
        "dr admin quests reload"));
    setFixed(41, commandButton(Material.EMERALD, "gui.adminHub.reloadShops.title",
        "gui.adminHub.reloadShops.hint",
        "dr admin shop reload"));
    setFixed(42, commandButton(Material.CRAFTING_TABLE, "gui.adminHub.reloadCrafting.title",
        "gui.adminHub.reloadCrafting.hint",
        "dr admin crafting reload"));
    setFixed(43, commandButton(Material.ENCHANTED_BOOK, "gui.adminHub.reloadUpgrades.title",
        "gui.adminHub.reloadUpgrades.hint",
        "dr admin upgrades reload"));

    setFixed(46, new Label(p -> statusItem(p)));
    setFixed(47, new Button(p -> errorItem(p), ctx -> {
      GuiManager.get().push(ctx.player(), new SystemStatusErrorsMenu(SystemStatusStore.get(), null,
          Locales.text(ctx.player(), "gui.systemStatus.errors.allSystems")));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private Button button(Material material, String nameKey, String hintKey, Consumer<Player> action) {
    return new Button(p -> GuiItems.named(material, GuiI18n.tr(p, nameKey), List.of(GuiI18n.tr(p, hintKey))), ctx -> {
      Player player = ctx.player();
      if (action != null && player != null) {
        action.accept(player);
      }
      GuiSounds.click(player);
    }).autoDescribeInLore(false);
  }

  private Button commandButton(Material material, String nameKey, String hintKey, String command) {
    return new Button(p -> GuiItems.named(material, GuiI18n.tr(p, nameKey), List.of(GuiI18n.tr(p, hintKey))), ctx -> {
      Player player = ctx.player();
      if (player != null) {
        player.performCommand(command);
      }
      GuiSounds.click(player);
    }).autoDescribeInLore(false);
  }

  private void sendUnavailable(Player player, String systemKey) {
    if (player == null) {
      return;
    }
    String system = Locales.text(player, systemKey);
    player.sendMessage(Locales.component(player, "messages.command.systemUnavailable", Locales.placeholders("system", system)));
  }

  private org.bukkit.inventory.ItemStack statusItem(Player player) {
    var entries = SystemStatusStore.get().entries();
    int systems = entries.size();
    long latest = 0L;
    for (var entry : entries) {
      latest = Math.max(latest, entry.timestampMs());
    }
    String lastReload = latest <= 0L
        ? Locales.text(player, "gui.adminHub.status.never")
        : formatAgo(player, latest);
    return GuiItems.named(Material.PAPER, GuiI18n.tr(player, "gui.adminHub.status.title"), List.of(
        Locales.component(player, "gui.adminHub.status.systems", Locales.placeholders("count", systems)),
        Locales.component(player, "gui.adminHub.status.lastReload", Locales.placeholders("value", lastReload))));
  }

  private org.bukkit.inventory.ItemStack errorItem(Player player) {
    int errors = SystemStatusStore.get().errors().size();
    Material material = errors > 0 ? Material.BARRIER : Material.LIME_DYE;
    return GuiItems.named(material, GuiI18n.tr(player, "gui.adminHub.errors.title"), List.of(
        Locales.component(player, "gui.adminHub.errors.count", Locales.placeholders("count", errors)),
        GuiI18n.tr(player, errors > 0 ? "gui.adminHub.errors.hint" : "gui.adminHub.errors.clear")));
  }

  private static String formatAgo(Player player, long timestampMs) {
    long delta = Math.max(0L, System.currentTimeMillis() - timestampMs);
    long seconds = delta / 1000L;
    long minutes = seconds / 60L;
    long hours = minutes / 60L;
    long days = hours / 24L;
    if (days > 0) {
      return Locales.text(player, "gui.common.time.daysHours", Locales.placeholders(
          "days", days,
          "hours", hours % 24));
    }
    if (hours > 0) {
      return Locales.text(player, "gui.common.time.hoursMinutes", Locales.placeholders(
          "hours", hours,
          "minutes", minutes % 60));
    }
    if (minutes > 0) {
      return Locales.text(player, "gui.common.time.minutesSeconds", Locales.placeholders(
          "minutes", minutes,
          "seconds", seconds % 60));
    }
    return Locales.text(player, "gui.common.time.seconds", Locales.placeholders(
        "seconds", seconds));
  }
}
