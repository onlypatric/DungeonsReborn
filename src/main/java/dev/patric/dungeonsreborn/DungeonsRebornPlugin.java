package dev.patric.dungeonsreborn;

import java.io.File;
import java.sql.SQLException;
import java.time.Duration;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.commands.DungeonsRebornCommand;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.crafting.CraftingGuiSessionManager;
import dev.patric.dungeonsreborn.crafting.CraftingYamlRegistry;
import dev.patric.dungeonsreborn.dungeons.DungeonProgressJdbcRepository;
import dev.patric.dungeonsreborn.dungeons.DungeonProgressRepository;
import dev.patric.dungeonsreborn.dungeons.DungeonQueueService;
import dev.patric.dungeonsreborn.dungeons.DungeonSessionManager;
import dev.patric.dungeonsreborn.dungeons.DungeonSessionListener;
import dev.patric.dungeonsreborn.dungeons.DungeonYamlRegistry;
import dev.patric.dungeonsreborn.effects.integration.EffectsBindings;
import dev.patric.dungeonsreborn.effects.integration.ItemSyncListener;
import dev.patric.dungeonsreborn.effects.mana.ManaSessionListener;
import dev.patric.dungeonsreborn.effects.mana.ManaDropListener;
import dev.patric.dungeonsreborn.effects.mana.SessionManaProvider;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;
import dev.patric.dungeonsreborn.effects.config.YamlVarsSessionListener;
import dev.patric.dungeonsreborn.effects.damage.DamageMechanicsListener;
import dev.patric.dungeonsreborn.effects.editor.EditorDraftStore;
import dev.patric.dungeonsreborn.effects.editor.EditorAccessController;
import dev.patric.dungeonsreborn.effects.editor.EditorAuditLogger;
import dev.patric.dungeonsreborn.effects.editor.EditorLockListener;
import dev.patric.dungeonsreborn.effects.editor.EditorLockManager;
import dev.patric.dungeonsreborn.effects.editor.EditorServices;
import dev.patric.dungeonsreborn.effects.registry.BuiltinTypes;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeService;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeOnDamagedListener;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeYamlRegistry;
import dev.patric.dungeonsreborn.kits.KitJdbcRepository;
import dev.patric.dungeonsreborn.kits.KitService;
import dev.patric.dungeonsreborn.kits.KitYamlRegistry;
import dev.patric.dungeonsreborn.logging.ServiceLogManager;
import dev.patric.dungeonsreborn.locale.LocaleService;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.mobs.MobSpawnManager;
import dev.patric.dungeonsreborn.mobs.MobYamlRegistry;
import dev.patric.dungeonsreborn.mobs.MobSpawnerBlockListener;
import dev.patric.dungeonsreborn.mobs.MobSpawnerBlockStore;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import dev.patric.dungeonsreborn.shops.ShopSessionManager;
import dev.patric.dungeonsreborn.shops.ShopOpenListener;
import dev.patric.dungeonsreborn.shops.ShopTradeListener;
import dev.patric.dungeonsreborn.shops.ShopTradeMetrics;
import dev.patric.dungeonsreborn.effects.minions.MinionManager;
import dev.patric.dungeonsreborn.shops.ShopStockManager;
import dev.patric.dungeonsreborn.mobs.MobEggListener;
import dev.patric.dungeonsreborn.progression.ProgressionDatabase;
import dev.patric.dungeonsreborn.progression.ProgressionCurve;
import dev.patric.dungeonsreborn.progression.ProgressionHudListener;
import dev.patric.dungeonsreborn.progression.ProgressionHudService;
import dev.patric.dungeonsreborn.progression.ProgressionJdbcRepository;
import dev.patric.dungeonsreborn.progression.ProgressionListener;
import dev.patric.dungeonsreborn.progression.ProgressionMobKillListener;
import dev.patric.dungeonsreborn.progression.ProgressionRepository;
import dev.patric.dungeonsreborn.progression.ProgressionService;
import dev.patric.dungeonsreborn.progression.ProgressionStatService;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.style.GuiStyles;
import dev.patric.dungeonsreborn.system.SharedTickScheduler;
import dev.patric.dungeonsreborn.util.WorldAllowlist;
import dev.patric.dungeonsreborn.classes.ClassJdbcRepository;
import dev.patric.dungeonsreborn.classes.ClassBonusService;
import dev.patric.dungeonsreborn.classes.ClassService;
import dev.patric.dungeonsreborn.classes.ClassYamlRegistry;
import dev.patric.dungeonsreborn.classes.skills.ClassSkillJdbcRepository;
import dev.patric.dungeonsreborn.classes.skills.ClassSkillService;
import dev.patric.dungeonsreborn.advancements.AdvancementService;
import dev.patric.dungeonsreborn.advancements.AdvancementXpListener;
import dev.patric.dungeonsreborn.advancements.AdvancementWorldListener;
import dev.patric.dungeonsreborn.advancements.BossAdvancementListener;
import dev.patric.dungeonsreborn.party.PartyChatListener;
import dev.patric.dungeonsreborn.party.PartyListener;
import dev.patric.dungeonsreborn.party.PartyService;
import dev.patric.dungeonsreborn.quests.QuestGiverYamlRegistry;
import dev.patric.dungeonsreborn.quests.QuestJdbcRepository;
import dev.patric.dungeonsreborn.quests.QuestListener;
import dev.patric.dungeonsreborn.quests.QuestService;
import dev.patric.dungeonsreborn.quests.QuestYamlRegistry;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;

public final class DungeonsRebornPlugin extends JavaPlugin {
    private EffectsEngine effectsEngine;
    private EffectsBindings effectsBindings;
    private EffectsYamlAbilities yamlAbilities;
    private EditorDraftStore editorDraftStore;
    private EditorLockManager editorLockManager;
    private EditorAuditLogger editorAuditLogger;
    private EditorAccessController editorAccessController;
    private EditorServices editorServices;
    private MobRegistry mobRegistry;
    private MobSpawnManager mobSpawnManager;
    private MobYamlRegistry mobYamlRegistry;
    private MobSpawnerBlockStore spawnerBlockStore;
    private MinionManager minionManager;
    private ServiceLogManager serviceLog;
    private LocaleService localeService;
    private SharedTickScheduler sharedTicks;
    private CraftingYamlRegistry craftingRecipes;
    private CraftingGuiSessionManager craftingSessions;
    private UpgradeYamlRegistry upgradeRegistry;
    private UpgradeService upgradeService;
    private ShopYamlRegistry shopRegistry;
    private ShopSessionManager shopSessions;
    private ShopStockManager shopStocks;
    private ShopTradeMetrics shopMetrics;
    private QuestYamlRegistry questRegistry;
    private QuestGiverYamlRegistry questGiverRegistry;
    private QuestService questService;
    private WorldAllowlist worldAllowlist;
    private ProgressionDatabase progressionDatabase;
    private ProgressionService progressionService;
    private ProgressionStatService progressionStats;
    private ProgressionHudService progressionHud;
    private KitYamlRegistry kitRegistry;
    private KitService kitService;
    private ClassYamlRegistry classRegistry;
    private ClassService classService;
    private ClassSkillService classSkillService;
    private ClassBonusService classBonusService;
    private AdvancementService advancementService;
    private PartyService partyService;
    private double partyAssistRadius;
    private DungeonYamlRegistry dungeonRegistry;
    private DungeonQueueService dungeonQueue;
    private DungeonProgressRepository dungeonProgress;
    private DungeonSessionManager dungeonSessions;

    private AdvancementService initAdvancements() {
        try {
            if (Bukkit.getPluginManager().getPlugin("UltimateAdvancementAPI") == null) {
                getLogger().warning("[Advancements] UltimateAdvancementAPI not installed, skipping.");
                return null;
            }
            AdvancementService service = new AdvancementService(this);
            if (service.enable()) {
                return service;
            }
        } catch (NoClassDefFoundError | Exception ex) {
            getLogger().log(java.util.logging.Level.WARNING,
                "[Advancements] Failed to initialize, continuing without advancements", ex);
        }
        return null;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        serviceLog = ServiceLogManager.fromConfig(this);
        localeService = new LocaleService(this, serviceLog.locales());
        localeService.reload();
        Locales.install(localeService);
        advancementService = initAdvancements();
        sharedTicks = new SharedTickScheduler(this, getLogger());
        sharedTicks.start();
        worldAllowlist = WorldAllowlist.fromConfig(getConfig());
        logStartupSummary();
        if (advancementService != null && advancementService.isEnabled()) {
            Bukkit.getPluginManager().registerEvents(
                new AdvancementWorldListener(advancementService, worldAllowlist), this);
            Bukkit.getPluginManager().registerEvents(new AdvancementXpListener(advancementService), this);
        }
        getLogger().info("DungeonsReborn enabled");
        initProgression();
        if (advancementService != null && advancementService.isEnabled()) {
            advancementService.setProgressionService(progressionService);
        }
        GuiManager.init(this, serviceLog.gui());
        GuiStyles.installButtonDefaults();
        int partyMaxSize = getConfig().getInt("party.maxSize", 5);
        int inviteSeconds = getConfig().getInt("party.inviteSeconds", 30);
        partyAssistRadius = getConfig().getDouble("party.assistRadius", 24.0);
        partyService = new PartyService(this::isWorldAllowed, partyMaxSize, Duration.ofSeconds(inviteSeconds));
        Bukkit.getPluginManager().registerEvents(new PartyListener(partyService), this);
        Bukkit.getPluginManager().registerEvents(new PartyChatListener(this, partyService), this);

        effectsEngine = EffectsEngine.init(this, serviceLog.effects());
        SessionManaProvider manaProvider = new SessionManaProvider(100.0);
        effectsEngine.setManaProvider(manaProvider);
        effectsEngine.enableManaRegen(20L, 5.0);
        Bukkit.getPluginManager().registerEvents(new ManaSessionListener(manaProvider), this);
        Bukkit.getPluginManager().registerEvents(new ManaDropListener(effectsEngine), this);
        Bukkit.getPluginManager().registerEvents(new DamageMechanicsListener(effectsEngine), this);
        mobRegistry = new MobRegistry(effectsEngine);
        mobRegistry.setMaxActivePerTick(getConfig().getInt("mobs.performance.maxTickMobs", 0));
        mobRegistry.configureXpGating(
            getConfig().getBoolean("mobs.xpGating.enabled", true),
            getConfig().getString("mobs.xpGating.bypassPermission", ""),
            getConfig().getInt("mobs.xpGating.messageCooldownTicks", 40));
        mobRegistry.setWorldAllowedPredicate(this::isWorldAllowed);
        if (advancementService != null && advancementService.isEnabled()) {
            mobRegistry.setAdvancementService(advancementService);
        }
        Bukkit.getPluginManager().registerEvents(mobRegistry, this);
        mobSpawnManager = new MobSpawnManager(effectsEngine, mobRegistry, serviceLog.mobs());
        mobRegistry.setSpawnManager(mobSpawnManager);
        Bukkit.getPluginManager().registerEvents(mobSpawnManager, this);
        minionManager = new MinionManager(effectsEngine, mobRegistry);
        mobRegistry.setMinionManager(minionManager);
        Bukkit.getPluginManager().registerEvents(minionManager, this);
        registerProgressionHooks();

        BuiltinTypes.registerAll(effectsEngine);

        effectsBindings = new EffectsBindings(effectsEngine);
        Bukkit.getPluginManager().registerEvents(effectsBindings, this);

        yamlAbilities = new EffectsYamlAbilities(this, effectsEngine, effectsBindings, serviceLog.effects(), serviceLog.bindings());
        yamlAbilities.reload();
        Bukkit.getPluginManager().registerEvents(new YamlVarsSessionListener(yamlAbilities), this);
        Bukkit.getPluginManager().registerEvents(new ItemSyncListener(yamlAbilities), this);
        yamlAbilities.syncOnlineItems();
        editorDraftStore = new EditorDraftStore(this, serviceLog.effects());
        editorAuditLogger = new EditorAuditLogger(serviceLog.effects());
        editorLockManager = new EditorLockManager();
        editorAccessController = new EditorAccessController();
        editorServices = new EditorServices(effectsEngine, yamlAbilities, editorDraftStore, editorAccessController, editorLockManager, editorAuditLogger);
        Bukkit.getPluginManager().registerEvents(new EditorLockListener(editorLockManager), this);

        shopRegistry = new ShopYamlRegistry(this, serviceLog.shops(), yamlAbilities::itemTemplate);
        shopRegistry.reload();
        if (advancementService != null && advancementService.isEnabled()) {
            advancementService.setShopRegistry(shopRegistry);
        }
        mobRegistry.setShopRegistry(shopRegistry);
        shopStocks = new ShopStockManager(serviceLog.shops());
        shopSessions = new ShopSessionManager(shopRegistry, shopStocks, serviceLog.shops());
        shopMetrics = new ShopTradeMetrics(this, serviceLog.shops());
        shopMetrics.load();
        Bukkit.getPluginManager().registerEvents(new ShopOpenListener(shopSessions), this);
        Bukkit.getPluginManager().registerEvents(
            new ShopTradeListener(shopRegistry, shopSessions, shopStocks, shopMetrics, advancementService, serviceLog.shops()), this);

        questRegistry = new QuestYamlRegistry(this, getLogger(), yamlAbilities::itemTemplate);
        questRegistry.reload();
        questGiverRegistry = new QuestGiverYamlRegistry(this, getLogger());
        questGiverRegistry.reload();
        if (progressionDatabase != null && progressionService != null) {
            questService = new QuestService(
                questRegistry,
                new QuestJdbcRepository(progressionDatabase, getLogger()),
                progressionService,
                shopRegistry,
                yamlAbilities::itemTemplate,
                this::isWorldAllowed);
            questService.loadOnlinePlayers();
            Bukkit.getPluginManager().registerEvents(new QuestListener(questService, partyService, partyAssistRadius), this);
        }

        mobYamlRegistry = new MobYamlRegistry(this, effectsEngine, yamlAbilities, shopRegistry, mobRegistry, mobSpawnManager, serviceLog.mobs());
        mobYamlRegistry.reload();
        if (advancementService != null && advancementService.isEnabled()) {
            Bukkit.getPluginManager().registerEvents(new BossAdvancementListener(mobRegistry, advancementService), this);
        }
        spawnerBlockStore = new MobSpawnerBlockStore(getDataFolder(), serviceLog.mobs());
        spawnerBlockStore.load();
        spawnerBlockStore.rehydrateMarkers();
        Bukkit.getPluginManager().registerEvents(new MobEggListener(effectsEngine, mobRegistry, mobYamlRegistry), this);
        Bukkit.getPluginManager().registerEvents(
            new MobSpawnerBlockListener(mobYamlRegistry, mobRegistry, mobSpawnManager, spawnerBlockStore, serviceLog.mobs()), this);

        craftingRecipes = new CraftingYamlRegistry(this, serviceLog.effects(), this::resolveCraftingItem);
        ensureDefaultCraftingRecipes();
        craftingRecipes.reload();
        craftingSessions = new CraftingGuiSessionManager();
        Bukkit.getPluginManager().registerEvents(craftingSessions, this);

        kitRegistry = new KitYamlRegistry(this, getLogger());
        kitRegistry.reload();
        if (progressionDatabase != null) {
            kitService = new KitService(kitRegistry, new KitJdbcRepository(progressionDatabase, getLogger()),
                shopRegistry, yamlAbilities::itemTemplate, advancementService, getLogger());
        }

        classRegistry = new ClassYamlRegistry(this, getLogger());
        classRegistry.reload();
        if (progressionDatabase != null && progressionService != null) {
            classService = new ClassService(
                classRegistry,
                new ClassJdbcRepository(progressionDatabase, getLogger()),
                progressionService,
                shopRegistry,
                this::isWorldAllowed,
                getLogger());
        }
        if (progressionDatabase != null && progressionService != null && classService != null) {
            classSkillService = new ClassSkillService(
                classService,
                progressionService,
                new ClassSkillJdbcRepository(progressionDatabase, getLogger()),
                shopRegistry,
                this::isWorldAllowed,
                getLogger());
        }
        if (classService != null && effectsEngine != null) {
            classBonusService = new ClassBonusService(this, classService, classSkillService, effectsEngine,
                this::isWorldAllowed, getConfig());
            if (progressionHud != null) {
                progressionHud.setClassBonuses(classBonusService);
            }
        }

        dungeonRegistry = new DungeonYamlRegistry(this, serviceLog.dungeons(), worldAllowlist);
        dungeonRegistry.reload();
        if (advancementService != null && advancementService.isEnabled()) {
            advancementService.rebuildAll(mobRegistry, dungeonRegistry);
        }
        if (progressionDatabase != null) {
            dungeonProgress = new DungeonProgressJdbcRepository(progressionDatabase, serviceLog.dungeons());
        }
        dungeonQueue = new DungeonQueueService(this, dungeonRegistry, dungeonProgress, worldAllowlist, serviceLog.dungeons());
        dungeonSessions = new DungeonSessionManager(this, dungeonRegistry, dungeonProgress, dungeonQueue, mobRegistry,
            shopRegistry, progressionService, advancementService, serviceLog.dungeons());
        dungeonQueue.setSessionManager(dungeonSessions);
        if (sharedTicks != null) {
            sharedTicks.schedule("dungeonQueue", 20L, dungeonQueue::tick);
        } else {
            Bukkit.getScheduler().runTaskTimer(this, dungeonQueue::tick, 20L, 20L);
        }
        Bukkit.getPluginManager().registerEvents(new DungeonSessionListener(dungeonSessions), this);
        applyDebugFlags();

        upgradeRegistry = new UpgradeYamlRegistry(this, effectsEngine, serviceLog.effects());
        upgradeRegistry.reload();
        upgradeService = new UpgradeService(this, effectsEngine, effectsBindings, upgradeRegistry, shopRegistry,
            serviceLog.upgrades());
        upgradeService.migrateOnlinePlayers();
        if (sharedTicks != null) {
            sharedTicks.schedule("upgradeAuras", 20L, upgradeService::tickInventoryAuras);
        } else {
            Bukkit.getScheduler().runTaskTimer(this, upgradeService::tickInventoryAuras, 20L, 20L);
        }
        Bukkit.getPluginManager().registerEvents(new UpgradeOnDamagedListener(upgradeService), this);

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(
                DungeonsRebornCommand.createCommand(
                    "dr",
                    this,
                    effectsEngine,
                    yamlAbilities,
                    effectsBindings,
                    editorServices,
                    minionManager,
                    mobYamlRegistry,
                    mobRegistry,
                    mobSpawnManager,
                    spawnerBlockStore,
                    craftingRecipes,
                    craftingSessions,
                    advancementService,
                    upgradeService,
                    shopRegistry,
                    shopSessions,
                    kitService,
                    classRegistry,
                    classService,
                    classSkillService,
                    dungeonRegistry,
                    dungeonQueue,
                    dungeonSessions,
                    questRegistry,
                    questService,
                    questGiverRegistry,
                    partyService,
                    localeService
                ).build()
            );
            commands.registrar().register(
                DungeonsRebornCommand.createCommand(
                    "droam",
                    this,
                    effectsEngine,
                    yamlAbilities,
                    effectsBindings,
                    editorServices,
                    minionManager,
                    mobYamlRegistry,
                    mobRegistry,
                    mobSpawnManager,
                    spawnerBlockStore,
                    craftingRecipes,
                    craftingSessions,
                    advancementService,
                    upgradeService,
                    shopRegistry,
                    shopSessions,
                    kitService,
                    classRegistry,
                    classService,
                    classSkillService,
                    dungeonRegistry,
                    dungeonQueue,
                    dungeonSessions,
                    questRegistry,
                    questService,
                    questGiverRegistry,
                    partyService,
                    localeService
                ).build()
            );
            commands.registrar().register(
                DungeonsRebornCommand.createCommand(
                    "dungeonroam",
                    this,
                    effectsEngine,
                    yamlAbilities,
                    effectsBindings,
                    editorServices,
                    minionManager,
                    mobYamlRegistry,
                    mobRegistry,
                    mobSpawnManager,
                    spawnerBlockStore,
                    craftingRecipes,
                    craftingSessions,
                    advancementService,
                    upgradeService,
                    shopRegistry,
                    shopSessions,
                    kitService,
                    classRegistry,
                    classService,
                    classSkillService,
                    dungeonRegistry,
                    dungeonQueue,
                    dungeonSessions,
                    questRegistry,
                    questService,
                    questGiverRegistry,
                    partyService,
                    localeService
                ).build()
            );
        });
    }

    private ItemStack resolveCraftingItem(String id) {
        if (shopRegistry != null) {
            ItemStack token = shopRegistry.resolveTokenItem(id);
            if (token != null) {
                return token;
            }
        }
        return yamlAbilities == null ? null : yamlAbilities.itemTemplate(id);
    }

    private void ensureDefaultCraftingRecipes() {
        File dir = new File(getDataFolder(), "recipes");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        saveRecipeIfMissing("recipes/token_compress.yml");
        saveRecipeIfMissing("recipes/token_decompress.yml");
        saveRecipeIfMissing("recipes/token_pallet.yml");
        saveRecipeIfMissing("recipes/token_unpallet.yml");
    }

    private void saveRecipeIfMissing(String resourcePath) {
        File file = new File(getDataFolder(), resourcePath);
        if (file.exists()) {
            return;
        }
        saveResource(resourcePath, false);
    }

    @Override
    public void onDisable() {
        getLogger().info("DungeonsReborn disabled");
        if (sharedTicks != null) {
            sharedTicks.stop();
            sharedTicks = null;
        }
        if (progressionHud != null) {
            progressionHud.stop();
            progressionHud = null;
        }
        if (progressionStats != null) {
            progressionStats.clearAll();
            progressionStats = null;
        }
        if (progressionService != null) {
            progressionService.shutdown();
            progressionService = null;
        }
        if (progressionDatabase != null) {
            progressionDatabase.close();
            progressionDatabase = null;
        }
        if (effectsEngine != null) {
            effectsEngine.shutdown();
            effectsEngine = null;
        }
        effectsBindings = null;
        yamlAbilities = null;
        editorDraftStore = null;
        editorLockManager = null;
        editorAuditLogger = null;
        editorAccessController = null;
        editorServices = null;
        mobRegistry = null;
        mobSpawnManager = null;
        mobYamlRegistry = null;
        minionManager = null;
        craftingRecipes = null;
        craftingSessions = null;
        upgradeRegistry = null;
        upgradeService = null;
        shopRegistry = null;
        shopSessions = null;
        shopStocks = null;
        if (shopMetrics != null) {
            shopMetrics.saveNow();
        }
        shopMetrics = null;
        questRegistry = null;
        questGiverRegistry = null;
        questService = null;
        kitRegistry = null;
        kitService = null;
        dungeonRegistry = null;
        dungeonQueue = null;
        dungeonProgress = null;
        dungeonSessions = null;
        classRegistry = null;
        classService = null;
        worldAllowlist = null;
        partyService = null;
        partyAssistRadius = 0.0;
        if (advancementService != null) {
            advancementService.disable();
            advancementService = null;
        }
        serviceLog = null;
    }

    private void initProgression() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            getLogger().warning("[Progression] Failed to create data folder");
        }
        File dbFile = new File(dataFolder, "progression.db");
        progressionDatabase = new ProgressionDatabase(dbFile, getLogger());
        try {
            progressionDatabase.open();
            progressionDatabase.migrate();
        } catch (SQLException ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "[Progression] Failed to initialize database", ex);
            progressionDatabase.close();
            progressionDatabase = null;
            return;
        }
        ProgressionRepository repository = new ProgressionJdbcRepository(progressionDatabase, getLogger());
        ProgressionCurve curve = ProgressionCurve.fromConfig(getConfig().getConfigurationSection("progression.levelCurve"));
        int skillPointsPerXp = getConfig().getInt("progression.skillPoints.perXp", 0);
        progressionService = new ProgressionService(this, repository, curve, this::isWorldAllowed, skillPointsPerXp, getLogger());
        progressionService.startAutoSave(sharedTicks);
        Bukkit.getPluginManager().registerEvents(new ProgressionListener(progressionService), this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            progressionService.load(player);
        }
    }

    private void registerProgressionHooks() {
        if (progressionService == null || mobRegistry == null) {
            return;
        }
        if (progressionStats == null && effectsEngine != null) {
            progressionStats = new ProgressionStatService(this, progressionService, effectsEngine, this::isWorldAllowed, getConfig());
            for (Player player : Bukkit.getOnlinePlayers()) {
                progressionStats.apply(player);
            }
        }
        if (progressionHud == null && effectsEngine != null) {
            progressionHud = new ProgressionHudService(this, progressionService, progressionStats, effectsEngine, this::isWorldAllowed);
            progressionHud.start(sharedTicks);
            Bukkit.getPluginManager().registerEvents(new ProgressionHudListener(progressionHud), this);
        }
        Bukkit.getPluginManager().registerEvents(
            new ProgressionMobKillListener(progressionService, mobRegistry, partyService, partyAssistRadius), this);
    }

    public MobRegistry mobRegistry() {
        return mobRegistry;
    }

    public MobYamlRegistry mobYamlRegistry() {
        return mobYamlRegistry;
    }

    public MinionManager minionManager() {
        return minionManager;
    }

    public ServiceLogManager serviceLog() {
        return serviceLog;
    }

    public WorldAllowlist worldAllowlist() {
        return worldAllowlist;
    }

    public ProgressionService progressionService() {
        return progressionService;
    }

    public boolean isWorldAllowed(org.bukkit.World world) {
        return worldAllowlist != null && worldAllowlist.isAllowed(world);
    }

    public void reloadWorldAllowlist() {
        worldAllowlist = WorldAllowlist.fromConfig(getConfig());
    }

    public void reloadLogging() {
        if (serviceLog != null) {
            serviceLog.reloadFromConfig(this);
        }
    }

    public void reloadScoreboardConfig() {
        if (progressionHud != null) {
            progressionHud.reloadConfig();
        }
    }

    private void logStartupSummary() {
        if (worldAllowlist == null) {
            return;
        }
        if (worldAllowlist.allowAll()) {
            getLogger().info("[RPG] World allowlist: ALL (no restriction)");
        } else {
            getLogger().info("[RPG] World allowlist: " + String.join(", ", worldAllowlist.worlds()));
        }
        StringBuilder worldSummary = new StringBuilder();
        StringBuilder blockedSummary = new StringBuilder();
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            String name = world.getName();
            String key = world.getKey().toString();
            boolean allowed = worldAllowlist.isAllowed(world);
            if (worldSummary.length() > 0) {
                worldSummary.append(", ");
            }
            worldSummary.append(name).append(" (").append(key).append(") -> ")
                .append(allowed ? "allowed" : "blocked");
            if (!allowed) {
                if (blockedSummary.length() > 0) {
                    blockedSummary.append(", ");
                }
                blockedSummary.append(name).append(" (").append(key).append(")");
            }
        }
        getLogger().info("[RPG] Worlds: " + worldSummary);
        if (blockedSummary.length() > 0) {
            getLogger().info("[RPG] Worlds blocked by allowlist: " + blockedSummary);
        }
        String defaultLocale = getConfig().getString("locales.default", "en");
        var enabledLocales = getConfig().getStringList("locales.enabled");
        getLogger().info("[Locales] default=" + defaultLocale + " enabled=" + enabledLocales);
        boolean advEnabled = advancementService != null && advancementService.isEnabled();
        getLogger().info("[Advancements] " + (advEnabled ? "enabled" : "disabled"));
    }

    public void reloadRuntimeConfig() {
        reloadWorldAllowlist();
        applyDebugFlags();
    }

    private void applyDebugFlags() {
        boolean waveLogs = getConfig().getBoolean("debug.dungeons.waveLogs", false);
        boolean spawnLogs = getConfig().getBoolean("debug.mobs.spawnLogs", false);
        if (dungeonSessions != null) {
            dungeonSessions.setDebugWaveLogs(waveLogs);
        }
        if (mobSpawnManager != null) {
            mobSpawnManager.setDebugSpawns(spawnLogs);
        }
    }
}
