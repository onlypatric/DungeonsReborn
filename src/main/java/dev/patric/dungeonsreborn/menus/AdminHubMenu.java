package dev.patric.dungeonsreborn.menus;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.advancements.AdvancementService;
import dev.patric.dungeonsreborn.advancements.menu.AdvancementIndexMenu;
import dev.patric.dungeonsreborn.classes.ClassYamlRegistry;
import dev.patric.dungeonsreborn.crafting.CraftingDiscoveryService;
import dev.patric.dungeonsreborn.crafting.CraftingYamlRegistry;
import dev.patric.dungeonsreborn.dungeons.DungeonQueueService;
import dev.patric.dungeonsreborn.dungeons.DungeonSessionManager;
import dev.patric.dungeonsreborn.dungeons.DungeonYamlRegistry;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeService;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.InfoButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.kits.KitService;
import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.mobs.MobYamlRegistry;
import dev.patric.dungeonsreborn.quests.QuestGiverYamlRegistry;
import dev.patric.dungeonsreborn.quests.QuestService;
import dev.patric.dungeonsreborn.shops.ShopSessionManager;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import dev.patric.dungeonsreborn.party.PartyService;

public final class AdminHubMenu extends Window {
    @SuppressWarnings("unused")
    private final EffectsYamlAbilities items;
    @SuppressWarnings("unused")
    private final UpgradeService upgrades;
    @SuppressWarnings("unused")
    private final MobRegistry mobs;
    @SuppressWarnings("unused")
    private final MobYamlRegistry mobsYaml;
    @SuppressWarnings("unused")
    private final KitService kits;
    @SuppressWarnings("unused")
    private final ShopYamlRegistry shops;
    @SuppressWarnings("unused")
    private final ShopSessionManager shopSessions;
    @SuppressWarnings("unused")
    private final QuestService quests;
    @SuppressWarnings("unused")
    private final QuestGiverYamlRegistry questGivers;
    @SuppressWarnings("unused")
    private final CraftingYamlRegistry crafting;
    @SuppressWarnings("unused")
    private final CraftingDiscoveryService craftingDiscovery;
    @SuppressWarnings("unused")
    private final DungeonYamlRegistry dungeons;
    @SuppressWarnings("unused")
    private final DungeonQueueService dungeonQueue;
    @SuppressWarnings("unused")
    private final DungeonSessionManager dungeonSessions;
    @SuppressWarnings("unused")
    private final PartyService parties;
    @SuppressWarnings("unused")
    private final AdvancementService advancements;
    @SuppressWarnings("unused")
    private final ClassYamlRegistry classes;

    public static void open(Player player, EffectsYamlAbilities items, UpgradeService upgrades, MobRegistry mobs,
            MobYamlRegistry mobsYaml, KitService kits, ShopYamlRegistry shops, ShopSessionManager shopSessions,
            QuestService quests, QuestGiverYamlRegistry questGivers, CraftingYamlRegistry crafting,
            CraftingDiscoveryService craftingDiscovery, DungeonYamlRegistry dungeons, DungeonQueueService dungeonQueue,
            DungeonSessionManager dungeonSessions, PartyService parties, AdvancementService advancements,
            ClassYamlRegistry classes) {
        Objects.requireNonNull(player, "player");
        GuiManager.get().open(player, new AdminHubMenu(items, upgrades, mobs, mobsYaml, kits, shops, shopSessions,
                quests, questGivers, crafting, craftingDiscovery, dungeons, dungeonQueue, dungeonSessions, parties,
                advancements, classes));
    }

    public AdminHubMenu(EffectsYamlAbilities items, UpgradeService upgrades, MobRegistry mobs, MobYamlRegistry mobsYaml,
            KitService kits, ShopYamlRegistry shops, ShopSessionManager shopSessions, QuestService quests,
            QuestGiverYamlRegistry questGivers, CraftingYamlRegistry crafting,
            CraftingDiscoveryService craftingDiscovery,
            DungeonYamlRegistry dungeons, DungeonQueueService dungeonQueue, DungeonSessionManager dungeonSessions,
            PartyService parties,
            AdvancementService advancements, ClassYamlRegistry classes) {
        super(54, GuiI18n.tr("gui.adminHub.title"));
        this.items = Objects.requireNonNull(items, "items");
        this.upgrades = Objects.requireNonNull(upgrades, "upgrades");
        this.mobs = Objects.requireNonNull(mobs, "mobs");
        this.mobsYaml = Objects.requireNonNull(mobsYaml, "mobsYaml");
        this.kits = Objects.requireNonNull(kits, "kits");
        this.shops = Objects.requireNonNull(shops, "shops");
        this.shopSessions = Objects.requireNonNull(shopSessions, "shopSessions");
        this.quests = Objects.requireNonNull(quests, "quests");
        this.questGivers = Objects.requireNonNull(questGivers, "questGivers");
        this.crafting = Objects.requireNonNull(crafting, "crafting");
        this.craftingDiscovery = Objects.requireNonNull(craftingDiscovery, "craftingDiscovery");
        this.dungeons = Objects.requireNonNull(dungeons, "dungeons");
        this.dungeonQueue = Objects.requireNonNull(dungeonQueue, "dungeonQueue");
        this.dungeonSessions = Objects.requireNonNull(dungeonSessions, "dungeonSessions");
        this.parties = Objects.requireNonNull(parties, "parties");
        this.advancements = Objects.requireNonNull(advancements, "advancements");
        this.classes = Objects.requireNonNull(classes, "classes");

        background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));
        GuiNav.applyDetail(this, new BackButton(), new CloseButton());
        nav(4, new InfoButton("gui.adminHub.info.title", "gui.adminHub.info.desc"));
        setFixedAt(0, 4, new Label(this::headerItem));

        setFixedAt(1, 1, menuButton("ICON_ITEMS", "gui.adminHub.items.title", "gui.adminHub.items.desc",
                ctx -> ctx.window().openSubWindow(ctx.player(), new ItemIndexMenu(items, true))));
        setFixedAt(1, 2, menuButton("ICON_UPGRADES", "gui.adminHub.upgrades.title", "gui.adminHub.upgrades.desc",
                ctx -> ctx.window().openSubWindow(ctx.player(), new UpgradeIndexMenu(upgrades, true))));
        setFixedAt(1, 3, menuButton("ICON_MOBS", "gui.adminHub.mobs.title", "gui.adminHub.mobs.desc",
                ctx -> ctx.window().openSubWindow(ctx.player(), new MobIndexMenu(mobs, mobsYaml, true))));
        setFixedAt(1, 4, menuButton("ICON_KITS", "gui.adminHub.kits.title", "gui.adminHub.kits.desc",
                ctx -> ctx.window().openSubWindow(ctx.player(), new KitsMenu(kits, true))));
        setFixedAt(1, 5, menuButton("ICON_SHOPS", "gui.adminHub.shops.title", "gui.adminHub.shops.desc",
                ctx -> ctx.window().openSubWindow(ctx.player(), new ShopIndexMenu(shops, shopSessions))));
        setFixedAt(1, 6, menuButton("ICON_QUESTS", "gui.adminHub.quests.title", "gui.adminHub.quests.desc",
                ctx -> ctx.window().openSubWindow(ctx.player(), new QuestLogMenu(quests, questGivers))));
        setFixedAt(1, 7, menuButton("ICON_CRAFTING", "gui.adminHub.crafting.title", "gui.adminHub.crafting.desc",
                ctx -> ctx.window().openSubWindow(ctx.player(),
                        new CraftingDiscoveryMenu(crafting, craftingDiscovery, true, true))));

        setFixedAt(2, 1, menuButton("ICON_QUESTS", "gui.adminHub.questGivers.title", "gui.adminHub.questGivers.desc",
                ctx -> ctx.window().openSubWindow(ctx.player(), new QuestGiverIndexMenu(quests, questGivers))));
        setFixedAt(2, 2, menuButton("ICON_DUNGEONS", "gui.adminHub.dungeons.title", "gui.adminHub.dungeons.desc",
                ctx -> ctx.window().openSubWindow(ctx.player(),
                        new dev.patric.dungeonsreborn.dungeons.menu.DungeonQueueMenu(
                                dungeons, dungeonQueue, dungeonSessions, parties))));
        setFixedAt(2, 3, menuButton("ICON_GIVE", "gui.adminHub.give.title", "gui.adminHub.give.desc",
                ctx -> ctx.window().openSubWindow(ctx.player(), new AdminGiveMenu(shops))));
        setFixedAt(2, 4, menuButton("ICON_STATUS", "gui.adminHub.systemStatus.title",
                "gui.adminHub.systemStatus.desc",
                ctx -> ctx.window().openSubWindow(ctx.player(), new SystemStatusMenu())));
        setFixedAt(2, 5, menuButton("ICON_CLASSES", "gui.adminHub.classes.title", "gui.adminHub.classes.desc",
                ctx -> ctx.window().openSubWindow(ctx.player(), new ClassIndexMenu(classes))));
        setFixedAt(2, 6, menuButton("ICON_ADVANCEMENTS", "gui.adminHub.advancements.title",
                "gui.adminHub.advancements.desc",
                ctx -> ctx.window().openSubWindow(ctx.player(), new AdvancementIndexMenu(advancements))));
    }

    private Button menuButton(String headId, String titleKey, String descKey, Consumer<Window.ClickContext> action) {
        Button button = new Button(player -> GuiItems.head(headId,
                GuiI18n.tr(player, titleKey),
                List.of(GuiI18n.tr(player, descKey))));
        if (action != null) {
            button.left(action);
        }
        button.cachePerPlayer();
        return button;
    }

    private org.bukkit.inventory.ItemStack headerItem(Player player) {
        return GuiItems.head("ICON_ADMIN", GuiI18n.tr(player, "gui.adminHub.header"), List.of());
    }
}
