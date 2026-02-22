package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.crafting.CraftingDiscoveryService;
import dev.patric.dungeonsreborn.crafting.CraftingYamlRegistry;
import dev.patric.dungeonsreborn.dungeons.DungeonQueueService;
import dev.patric.dungeonsreborn.dungeons.DungeonSessionManager;
import dev.patric.dungeonsreborn.dungeons.DungeonYamlRegistry;
import dev.patric.dungeonsreborn.dungeons.menu.DungeonQueueMenu;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeService;
import dev.patric.dungeonsreborn.kits.KitService;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.InfoButton;
import dev.patric.dungeonsreborn.locale.LocaleService;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.party.PartyService;
import dev.patric.dungeonsreborn.quests.QuestGiverYamlRegistry;
import dev.patric.dungeonsreborn.quests.QuestService;
import dev.patric.dungeonsreborn.shops.ShopSessionManager;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import dev.patric.dungeonsreborn.effects.minions.MinionManager;

public final class UserHubMenu extends Window {
  private static final int[] HUB_COLUMNS = {1, 3, 5, 7};
  private final LocaleService locales;
  private final EffectsEngine engine;
  private final EffectsYamlAbilities items;
  private final UpgradeService upgrades;
  private final MinionManager minions;
  private final MobRegistry mobs;
  private final PartyService parties;
  private final QuestService quests;
  private final QuestGiverYamlRegistry questGivers;
  private final ShopYamlRegistry shops;
  private final ShopSessionManager shopSessions;
  private final CraftingYamlRegistry crafting;
  private final CraftingDiscoveryService craftingDiscovery;
  private final DungeonYamlRegistry dungeons;
  private final DungeonQueueService dungeonQueue;
  private final DungeonSessionManager dungeonSessions;
  private final KitService kits;

  public static void open(Player player, LocaleService locales, EffectsEngine engine,
      EffectsYamlAbilities items,
      UpgradeService upgrades,
      MinionManager minions,
      MobRegistry mobs,
      PartyService parties,
      QuestService quests,
      QuestGiverYamlRegistry questGivers,
      ShopYamlRegistry shops,
      ShopSessionManager shopSessions,
      CraftingYamlRegistry crafting,
      CraftingDiscoveryService craftingDiscovery,
      DungeonYamlRegistry dungeons,
      DungeonQueueService dungeonQueue,
      DungeonSessionManager dungeonSessions,
      KitService kits) {
    Objects.requireNonNull(player, "player");
    GuiManager.get().open(player, new UserHubMenu(locales, engine, items, upgrades, minions, mobs, parties, quests,
        questGivers, shops, shopSessions, crafting, craftingDiscovery, dungeons, dungeonQueue, dungeonSessions, kits));
  }

  public UserHubMenu(LocaleService locales, EffectsEngine engine,
      EffectsYamlAbilities items,
      UpgradeService upgrades,
      MinionManager minions,
      MobRegistry mobs,
      PartyService parties,
      QuestService quests,
      QuestGiverYamlRegistry questGivers,
      ShopYamlRegistry shops,
      ShopSessionManager shopSessions,
      CraftingYamlRegistry crafting,
      CraftingDiscoveryService craftingDiscovery,
      DungeonYamlRegistry dungeons,
      DungeonQueueService dungeonQueue,
      DungeonSessionManager dungeonSessions,
      KitService kits) {
    super(54, GuiI18n.tr("gui.userHub.title"));
    this.locales = Objects.requireNonNull(locales, "locales");
    this.engine = Objects.requireNonNull(engine, "engine");
    this.items = Objects.requireNonNull(items, "items");
    this.upgrades = Objects.requireNonNull(upgrades, "upgrades");
    this.minions = Objects.requireNonNull(minions, "minions");
    this.mobs = Objects.requireNonNull(mobs, "mobs");
    this.parties = Objects.requireNonNull(parties, "parties");
    this.quests = Objects.requireNonNull(quests, "quests");
    this.questGivers = Objects.requireNonNull(questGivers, "questGivers");
    this.shops = Objects.requireNonNull(shops, "shops");
    this.shopSessions = Objects.requireNonNull(shopSessions, "shopSessions");
    this.crafting = Objects.requireNonNull(crafting, "crafting");
    this.craftingDiscovery = Objects.requireNonNull(craftingDiscovery, "craftingDiscovery");
    this.dungeons = Objects.requireNonNull(dungeons, "dungeons");
    this.dungeonQueue = Objects.requireNonNull(dungeonQueue, "dungeonQueue");
    this.dungeonSessions = Objects.requireNonNull(dungeonSessions, "dungeonSessions");
    this.kits = Objects.requireNonNull(kits, "kits");
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));
    navRight(new CloseButton());
    nav(4, new InfoButton("gui.userHub.info.title", "gui.userHub.info.desc"));
    buildTiles();
  }

  private void buildTiles() {
    List<HubTile> tiles = new ArrayList<>();
    tiles.add(tile("ICON_CLASSES", "gui.userHub.classes.title", "gui.userHub.classes.desc", this::comingSoon));
    tiles.add(tile("ICON_PARTY", "gui.userHub.party.title", "gui.userHub.party.desc",
        ctx -> ctx.window().openSubWindow(ctx.player(), new PartyMenu(parties))));
    tiles.add(tile("ICON_QUESTS", "gui.userHub.quests.title", "gui.userHub.quests.desc",
        ctx -> ctx.window().openSubWindow(ctx.player(), new QuestLogMenu(quests, questGivers))));
    tiles.add(tile("ICON_SHOPS", "gui.userHub.shops.title", "gui.userHub.shops.desc",
        ctx -> ctx.window().openSubWindow(ctx.player(), new ShopIndexMenu(shops, shopSessions))));
    tiles.add(tile("ICON_CRAFTING", "gui.userHub.crafting.title", "gui.userHub.crafting.desc",
        ctx -> ctx.player().sendMessage(Locales.component(ctx.player(), "messages.command.crafting.vanillaOnly"))));
    tiles.add(tile("ICON_ITEMS", "gui.userHub.items.title", "gui.userHub.items.desc",
        ctx -> ctx.window().openSubWindow(ctx.player(), new ItemIndexMenu(items))));
    tiles.add(tile("ICON_UPGRADES", "gui.userHub.upgrades.title", "gui.userHub.upgrades.desc",
        ctx -> ctx.window().openSubWindow(ctx.player(), new UpgradeIndexMenu(upgrades))));
    tiles.add(tile("ICON_MINIONS", "gui.userHub.minions.title", "gui.userHub.minions.desc",
        ctx -> ctx.window().openSubWindow(ctx.player(), new MinionMenu(minions))));
    tiles.add(tile("ICON_MOBS", "gui.userHub.mobs.title", "gui.userHub.mobs.desc",
        ctx -> ctx.window().openSubWindow(ctx.player(), new MobIndexMenu(mobs))));
    tiles.add(tile("ICON_DUNGEONS", "gui.userHub.dungeons.title", "gui.userHub.dungeons.desc",
        ctx -> ctx.window().openSubWindow(ctx.player(),
            new DungeonQueueMenu(dungeons, dungeonQueue, dungeonSessions, parties))));
    tiles.add(tile("ICON_KITS", "gui.userHub.kits.title", "gui.userHub.kits.desc",
        ctx -> ctx.window().openSubWindow(ctx.player(), new KitsMenu(kits))));
    tiles.add(tile("ICON_SETTINGS", "gui.userHub.settings.title", "gui.userHub.settings.desc",
        ctx -> ctx.window().openSubWindow(ctx.player(), new UserSettingsMenu(locales, engine))));

    int row = 1;
    int colIndex = 0;
    for (HubTile tile : tiles) {
      int col = HUB_COLUMNS[colIndex];
      setFixedAt(row, col, tile.button());
      colIndex++;
      if (colIndex >= HUB_COLUMNS.length) {
        colIndex = 0;
        row++;
      }
    }
  }

  private HubTile tile(String headId, String titleKey, String descKey, Consumer<Window.ClickContext> onClick) {
    return new HubTile(headId, titleKey, descKey, onClick);
  }

  private void comingSoon(Window.ClickContext ctx) {
    ctx.player().sendMessage(Locales.component(ctx.player(), "messages.gui.comingSoon"));
  }

  private static final class HubTile {
    private final Button button;

    private HubTile(String headId, String titleKey, String descKey, Consumer<Window.ClickContext> onClick) {
      this.button = new Button(player -> GuiItems.head(headId,
          GuiI18n.tr(player, titleKey),
          List.of(GuiI18n.tr(player, descKey))))
              .left(GuiI18n.tr("gui.controls.action"), onClick);
      this.button.autoDescribeInLore(false);
      this.button.cachePerPlayer();
    }

    private Button button() {
      return button;
    }
  }
}
