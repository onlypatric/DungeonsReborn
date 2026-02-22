package dev.patric.dungeonsreborn;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.sql.SQLException;
import java.time.Duration;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.ConfigurationSection;

import dev.patric.dungeonsreborn.commands.DungeonsRebornCommand;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.crafting.CraftingYamlRegistry;
import dev.patric.dungeonsreborn.crafting.CraftingDiscoveryService;
import dev.patric.dungeonsreborn.crafting.vanilla.VanillaCraftingBridge;
import dev.patric.dungeonsreborn.dungeons.DungeonProgressJdbcRepository;
import dev.patric.dungeonsreborn.dungeons.DungeonProgressRepository;
import dev.patric.dungeonsreborn.dungeons.DungeonQueueService;
import dev.patric.dungeonsreborn.dungeons.DungeonSessionManager;
import dev.patric.dungeonsreborn.dungeons.DungeonSessionListener;
import dev.patric.dungeonsreborn.dungeons.DungeonYamlRegistry;
import dev.patric.dungeonsreborn.effects.integration.EffectsBindings;
import dev.patric.dungeonsreborn.effects.integration.ItemSyncListener;
import dev.patric.dungeonsreborn.effects.items.HeadRegistry;
import dev.patric.dungeonsreborn.effects.items.ItemHookListener;
import dev.patric.dungeonsreborn.effects.items.ItemTemplateCompiler;
import dev.patric.dungeonsreborn.textures.TextureBuildResult;
import dev.patric.dungeonsreborn.textures.TextureDeliveryListener;
import dev.patric.dungeonsreborn.textures.TextureService;
import dev.patric.dungeonsreborn.effects.mana.ManaDropListener;
import dev.patric.dungeonsreborn.effects.mana.ManaPickupListener;
import dev.patric.dungeonsreborn.effects.mana.ManaSessionListener;
import dev.patric.dungeonsreborn.effects.mana.ManaSourcesConfig;
import dev.patric.dungeonsreborn.effects.mana.ManaStorageService;
import dev.patric.dungeonsreborn.effects.mana.ManaUiConfig;
import dev.patric.dungeonsreborn.effects.mana.ManaUiSettings;
import dev.patric.dungeonsreborn.effects.mana.ResourceRuleSet;
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
import dev.patric.dungeonsreborn.logging.AdvancementAuditLog;
import dev.patric.dungeonsreborn.logging.PartyAuditLog;
import dev.patric.dungeonsreborn.logging.ServiceLogManager;
import dev.patric.dungeonsreborn.locale.LocaleService;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.mobs.MobSpawnManager;
import dev.patric.dungeonsreborn.mobs.MobYamlRegistry;
import dev.patric.dungeonsreborn.mobs.MobSpawnerBlockListener;
import dev.patric.dungeonsreborn.mobs.MobSpawnerBlockStore;
import dev.patric.dungeonsreborn.mobs.model.MobModelBridge;
import dev.patric.dungeonsreborn.mobs.model.NoopMobModelBridge;
import dev.patric.dungeonsreborn.mobs.ai.MobAiEngineMode;
import dev.patric.dungeonsreborn.mobs.TrialSpawnerBlockListener;
import dev.patric.dungeonsreborn.mobs.TrialSpawnerBlockStore;
import dev.patric.dungeonsreborn.mobs.TrialSpawnerManager;
import dev.patric.dungeonsreborn.mobs.VaultBlockListener;
import dev.patric.dungeonsreborn.mobs.VaultBlockStore;
import dev.patric.dungeonsreborn.mobs.VaultManager;
import dev.patric.dungeonsreborn.mobs.MobPersistenceStore;
import dev.patric.dungeonsreborn.mobs.editor.MobDebugOverlayService;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import dev.patric.dungeonsreborn.shops.ShopSessionManager;
import dev.patric.dungeonsreborn.shops.ShopOpenListener;
import dev.patric.dungeonsreborn.shops.ShopTradeListener;
import dev.patric.dungeonsreborn.shops.ShopTradeAuditLog;
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
import dev.patric.dungeonsreborn.progression.custom.CustomXpJdbcRepository;
import dev.patric.dungeonsreborn.progression.custom.CustomXpListener;
import dev.patric.dungeonsreborn.progression.custom.CustomXpRepository;
import dev.patric.dungeonsreborn.progression.custom.CustomXpService;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.style.GuiStyles;
import dev.patric.dungeonsreborn.system.SharedTickScheduler;
import dev.patric.dungeonsreborn.util.WorldAllowlist;
import dev.patric.dungeonsreborn.classes.ClassJdbcRepository;
import dev.patric.dungeonsreborn.classes.ClassBonusService;
import dev.patric.dungeonsreborn.classes.ClassAbilityBindings;
import dev.patric.dungeonsreborn.classes.ClassService;
import dev.patric.dungeonsreborn.classes.ClassYamlRegistry;
import dev.patric.dungeonsreborn.classes.skills.ClassSkillJdbcRepository;
import dev.patric.dungeonsreborn.classes.skills.ClassSkillPresetJdbcRepository;
import dev.patric.dungeonsreborn.classes.skills.ClassSkillService;
import dev.patric.dungeonsreborn.advancements.AdvancementService;
import dev.patric.dungeonsreborn.advancements.AdvancementXpListener;
import dev.patric.dungeonsreborn.advancements.AdvancementWorldListener;
import dev.patric.dungeonsreborn.advancements.BossAdvancementListener;
import dev.patric.dungeonsreborn.party.PartyAssistRules;
import dev.patric.dungeonsreborn.party.PartyAuraService;
import dev.patric.dungeonsreborn.party.PartyChatListener;
import dev.patric.dungeonsreborn.party.PartyLootShareMode;
import dev.patric.dungeonsreborn.party.PartyListener;
import dev.patric.dungeonsreborn.party.PartyJdbcRepository;
import dev.patric.dungeonsreborn.party.PartyRepository;
import dev.patric.dungeonsreborn.party.PartyService;
import dev.patric.dungeonsreborn.party.PartyShareMode;
import dev.patric.dungeonsreborn.quests.QuestRegion;
import dev.patric.dungeonsreborn.quests.QuestRewardShareMode;
import dev.patric.dungeonsreborn.quests.QuestGiverYamlRegistry;
import dev.patric.dungeonsreborn.quests.QuestJdbcRepository;
import dev.patric.dungeonsreborn.quests.QuestListener;
import dev.patric.dungeonsreborn.quests.QuestService;
import dev.patric.dungeonsreborn.quests.QuestYamlRegistry;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public final class DungeonsRebornPlugin extends JavaPlugin {
    private EffectsEngine effectsEngine;
    private EffectsBindings effectsBindings;
    private EffectsYamlAbilities yamlAbilities;
    private TextureService textureService;
    private HeadRegistry headRegistry;
    private EditorDraftStore editorDraftStore;
    private EditorLockManager editorLockManager;
    private EditorAuditLogger editorAuditLogger;
    private EditorAccessController editorAccessController;
    private EditorServices editorServices;
    private MobRegistry mobRegistry;
    private MobSpawnManager mobSpawnManager;
    private MobDebugOverlayService mobDebugOverlayService;
    private MobYamlRegistry mobYamlRegistry;
    private MobSpawnerBlockStore spawnerBlockStore;
    private TrialSpawnerBlockStore trialSpawnerBlockStore;
    private TrialSpawnerManager trialSpawnerManager;
    private VaultBlockStore vaultBlockStore;
    private VaultManager vaultManager;
    private MobPersistenceStore mobPersistenceStore;
    private boolean mobPersistenceEnabled;
    private MinionManager minionManager;
    private ServiceLogManager serviceLog;
    private LocaleService localeService;
    private SharedTickScheduler sharedTicks;
    private CraftingYamlRegistry craftingRecipes;
    private CraftingDiscoveryService craftingDiscovery;
    private VanillaCraftingBridge vanillaCraftingBridge;
    private UpgradeYamlRegistry upgradeRegistry;
    private UpgradeService upgradeService;
    private ShopYamlRegistry shopRegistry;
    private ShopSessionManager shopSessions;
    private ShopStockManager shopStocks;
    private ShopTradeMetrics shopMetrics;
    private ShopTradeAuditLog shopTradeAuditLog;
    private AdvancementAuditLog advancementAuditLog;
    private PartyAuditLog partyAuditLog;
    private QuestYamlRegistry questRegistry;
    private QuestGiverYamlRegistry questGiverRegistry;
    private QuestService questService;
    private WorldAllowlist worldAllowlist;
    private ProgressionDatabase progressionDatabase;
    private ProgressionService progressionService;
    private CustomXpService customXpService;
    private ProgressionStatService progressionStats;
    private ProgressionHudService progressionHud;
    private KitYamlRegistry kitRegistry;
    private KitService kitService;
    private ClassYamlRegistry classRegistry;
    private ClassService classService;
    private ClassSkillService classSkillService;
    private ClassBonusService classBonusService;
    private ClassAbilityBindings classAbilityBindings;
    private AdvancementService advancementService;
    private PartyService partyService;
    private PartyRepository partyRepository;
    private PartyAssistRules partyAssistRules;
    private PartyShareMode partyXpShareMode = PartyShareMode.NONE;
    private boolean partyXpRequireAssist = true;
    private PartyLootShareMode partyLootShareMode = PartyLootShareMode.NONE;
    private boolean partyLootRequireAssist = true;
    private PartyAuraService partyAuraService;
    private BukkitTask partyAuraTask;
    private ManaStorageService manaStorage;
    private ManaSourcesConfig manaSources;
    private DungeonYamlRegistry dungeonRegistry;
    private DungeonQueueService dungeonQueue;
    private DungeonProgressRepository dungeonProgress;
    private DungeonSessionManager dungeonSessions;
    private TextureDeliveryListener textureDeliveryListener;

    private AdvancementService initAdvancements() {
        try {
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
        GuiI18n.setDefaultLocale(Locale.forLanguageTag(localeService.defaultLocale()));
        advancementService = initAdvancements();
        advancementAuditLog = new AdvancementAuditLog(this, serviceLog.advancements());
        if (advancementService != null) {
            advancementService.setAuditLog(advancementAuditLog);
        }
        sharedTicks = new SharedTickScheduler(this, getLogger());
        sharedTicks.start();
        worldAllowlist = WorldAllowlist.fromConfig(getConfig());
        logStartupSummary();
        boolean customXpEnabled = getConfig().getBoolean("progression.customXp.enabled", true);
        if (advancementService != null && advancementService.isEnabled()) {
            Bukkit.getPluginManager().registerEvents(
                new AdvancementWorldListener(advancementService, worldAllowlist), this);
            if (!customXpEnabled) {
                Bukkit.getPluginManager().registerEvents(new AdvancementXpListener(advancementService), this);
            }
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
        double assistBase = getConfig().getDouble("party.assistRadius", 24.0);
        double assistScale = getConfig().getDouble("party.assistRadiusScalePerMember", 0.0);
        double assistMax = getConfig().getDouble("party.assistRadiusMax", 0.0);
        partyAssistRules = new PartyAssistRules(assistBase, assistScale, assistMax);
        partyXpShareMode = PartyShareMode.fromString(getConfig().getString("party.xpShare.mode", "NONE"),
            PartyShareMode.NONE);
        partyXpRequireAssist = getConfig().getBoolean("party.xpShare.requireAssist", true);
        partyLootShareMode = PartyLootShareMode.fromString(getConfig().getString("party.lootShare.mode", "NONE"),
            PartyLootShareMode.NONE);
        partyLootRequireAssist = getConfig().getBoolean("party.lootShare.requireAssist", true);
        String worldPolicyRaw = getConfig().getString("party.worldPolicy", "SAME_WORLD");
        PartyService.WorldPolicy worldPolicy = PartyService.WorldPolicy.fromString(worldPolicyRaw,
            PartyService.WorldPolicy.SAME_WORLD);
        List<QuestRegion> partyAllowRegions = readPartyRegions("party.regionRestrictions.allow");
        List<QuestRegion> partyDenyRegions = readPartyRegions("party.regionRestrictions.deny");
        partyService = new PartyService(this::isWorldAllowed, partyMaxSize, Duration.ofSeconds(inviteSeconds),
            worldPolicy, partyAllowRegions, partyDenyRegions, partyRepository);
        partyAuditLog = new PartyAuditLog(this, serviceLog.parties());
        partyService.setAuditLog(partyAuditLog);
        Bukkit.getPluginManager().registerEvents(new PartyListener(partyService), this);
        Bukkit.getPluginManager().registerEvents(new PartyChatListener(this, partyService), this);
        boolean partyBuffsEnabled = getConfig().getBoolean("party.buffs.enabled", false);
        if (partyBuffsEnabled) {
            long buffPeriod = getConfig().getLong("party.buffs.periodTicks", 40L);
            double buffRadius = getConfig().getDouble("party.buffs.radius", assistBase);
            double buffScale = getConfig().getDouble("party.buffs.radiusScalePerMember", 0.0);
            double buffMax = getConfig().getDouble("party.buffs.radiusMax", 0.0);
            boolean buffRequireAssist = getConfig().getBoolean("party.buffs.requireAssist", true);
            PartyAuraService.CenterMode centerMode = PartyAuraService.CenterMode.fromString(
                getConfig().getString("party.buffs.center", "LEADER"), PartyAuraService.CenterMode.LEADER);
            List<PotionEffect> buffEffects = readPartyBuffEffects(buffPeriod);
            partyAuraService = new PartyAuraService(partyService,
                new PartyAssistRules(buffRadius, buffScale, buffMax),
                buffEffects, buffRequireAssist, centerMode);
            if (!buffEffects.isEmpty() && buffPeriod > 0L) {
                partyAuraTask = Bukkit.getScheduler().runTaskTimer(this, partyAuraService, buffPeriod, buffPeriod);
            }
        }

        effectsEngine = EffectsEngine.init(this, serviceLog.effects());
        effectsEngine.configureCombat(
            getConfig().getBoolean("effects.combat.enabled", true),
            getConfig().getBoolean("effects.combat.debug", false),
            getConfig().getBoolean("effects.combat.asyncPlanner.enabled", true),
            getConfig().getInt("effects.combat.asyncPlanner.queueCapacity", 12_000),
            getConfig().getLong("effects.combat.asyncPlanner.planTtlTicks", 1L),
            getConfig().getInt("effects.combat.guardrails.maxEventDispatchPerTick", 2000),
            getConfig().getInt("effects.combat.guardrails.maxDamagePacketsPerTick", 4000),
            getConfig().getString("effects.combat.guardrails.degradePolicy", "DROP_LOW_PRIORITY"));
        textureService = new TextureService(this);
        ItemTemplateCompiler.setTextureService(textureService);
        TextureBuildResult startupTextureBuild = textureService.rebuildIfAutoEnabled();
        if (startupTextureBuild != null && startupTextureBuild.success() && startupTextureBuild.zipFile() != null) {
            getLogger().info("[Textures] Built resource pack: " + startupTextureBuild.zipFile().getPath()
                + " sha1=" + startupTextureBuild.zipSha1()
                + " textures=" + startupTextureBuild.texturesDiscovered());
        } else if (startupTextureBuild != null && startupTextureBuild.errorCount() > 0) {
            getLogger().warning("[Textures] Build completed with errors: " + String.join("; ", startupTextureBuild.errors()));
        }
        double baseMana = getConfig().getDouble("mana.base", 100.0);
        ResourceRuleSet manaRules = ResourceRuleSet.fromConfig(getConfig(), baseMana);
        manaSources = ManaSourcesConfig.fromConfig(getConfig());
        SessionManaProvider manaProvider = new SessionManaProvider(manaRules);
        effectsEngine.setManaProvider(manaProvider);
        long regenPeriod = getConfig().getLong("mana.regen.periodTicks", 20L);
        double regenAmount = getConfig().getDouble("mana.regen.baseAmount", 5.0);
        effectsEngine.enableManaRegen(regenPeriod, regenAmount);
        long regenDelay = getConfig().getLong("mana.regen.delayAfterCastTicks", 0L);
        long combatDelay = getConfig().getLong("mana.regen.combatDelayTicks", 0L);
        effectsEngine.setManaRegenDelays(regenDelay, combatDelay);
        double maxRegenPerTick = getConfig().getDouble("mana.antiExploit.maxRegenPerTick", 0.0);
        double maxGainPerTick = getConfig().getDouble("mana.antiExploit.maxGainPerTick", 0.0);
        effectsEngine.setManaAntiExploit(maxRegenPerTick, maxGainPerTick);
        effectsEngine.setManaTimedGrant(manaSources.timed().enabled(),
            manaSources.timed().periodTicks(), manaSources.timed().amount(), manaSources.timed().resourceId());
        ManaUiConfig manaUiConfig = ManaUiConfig.fromConfig(getConfig());
        effectsEngine.setManaUiConfig(manaUiConfig);
        ConfigurationSection manaUiDefaults = getConfig().getConfigurationSection("mana.ui.defaults");
        if (manaUiDefaults != null) {
            ManaUiSettings uiSettings = effectsEngine.manaUiSettings();
            uiSettings.setDefault(ManaUiSettings.Flag.ACTIONBAR, manaUiDefaults.getBoolean("actionbar", true));
            uiSettings.setDefault(ManaUiSettings.Flag.WARNINGS, manaUiDefaults.getBoolean("warnings", true));
            uiSettings.setDefault(ManaUiSettings.Flag.SCOREBOARD, manaUiDefaults.getBoolean("scoreboard", true));
        }
        boolean manaPersistence = getConfig().getBoolean("mana.persistence.enabled", true);
        manaStorage = new ManaStorageService(this);
        Bukkit.getPluginManager().registerEvents(new ManaSessionListener(manaProvider, manaStorage, manaPersistence), this);
        Bukkit.getPluginManager().registerEvents(new ManaDropListener(effectsEngine, manaSources.kills()), this);
        Bukkit.getPluginManager().registerEvents(new ManaPickupListener(effectsEngine, manaSources.pickups()), this);
        Bukkit.getPluginManager().registerEvents(new DamageMechanicsListener(effectsEngine), this);
        mobRegistry = new MobRegistry(effectsEngine);
        mobRegistry.setTextureService(textureService);
        mobRegistry.setCraftingDiscoveryService(craftingDiscovery);
        mobRegistry.setLogger(serviceLog.mobs());
        configureMobModelBridge();
        mobRegistry.setMaxActivePerTick(getConfig().getInt("mobs.performance.maxTickMobs", 0));
        mobRegistry.configureAi(
            getConfig().getBoolean("mobs.ai.enabled", true),
            parseMobAiEngineMode(getConfig().getString("mobs.ai.defaultEngine", "V3")),
            getConfig().getBoolean("mobs.ai.pathfinder.enabled", true),
            getConfig().getBoolean("mobs.ai.pathfinder.useMobGoalsApi", true),
            getConfig().getInt("mobs.ai.guardrails.maxAiStepsPerTick", 3000),
            getConfig().getInt("mobs.ai.guardrails.maxPathMutationsPerTick", 500),
            getConfig().getLong("mobs.ai.guardrails.retargetMinIntervalTicks", 5L),
            getConfig().getLong("mobs.ai.guardrails.pathRecalcMinIntervalTicks", 10L),
            getConfig().getLong("mobs.ai.metrics.sampleWindowTicks", 200L),
            getConfig().getBoolean("mobs.ai.async.enabled", true),
            resolveAiWorkerThreads(
                getConfig().getString("mobs.ai.async.workerThreads", "auto"),
                getConfig().getInt("mobs.ai.async.maxWorkerThreads", 8)),
            getConfig().getInt("mobs.ai.async.maxJobsPerTick", 2000),
            getConfig().getInt("mobs.ai.async.queueCapacity", 10_000),
            getConfig().getLong("mobs.ai.async.planTtlTicks", 1L));
        mobRegistry.setCustomXpService(customXpService);
        mobRegistry.configureXpGating(
            getConfig().getBoolean("mobs.xpGating.enabled", true),
            getConfig().getString("mobs.xpGating.bypassPermission", ""),
            getConfig().getInt("mobs.xpGating.messageCooldownTicks", 40));
        mobRegistry.setWorldAllowedPredicate(this::isWorldAllowed);
        mobRegistry.setPartyService(partyService);
        mobRegistry.setPartyShareRules(partyXpShareMode, partyXpRequireAssist, partyLootShareMode,
            partyLootRequireAssist, partyAssistRules);
        if (advancementService != null && advancementService.isEnabled()) {
            mobRegistry.setAdvancementService(advancementService);
        }
        Bukkit.getPluginManager().registerEvents(mobRegistry, this);
        mobSpawnManager = new MobSpawnManager(effectsEngine, mobRegistry, serviceLog.mobs());
        mobSpawnManager.setPartyService(partyService);
        mobSpawnManager.setCustomXpService(customXpService);
        mobRegistry.setSpawnManager(mobSpawnManager);
        mobDebugOverlayService = new MobDebugOverlayService(effectsEngine, mobRegistry);
        Bukkit.getPluginManager().registerEvents(mobSpawnManager, this);
        minionManager = new MinionManager(effectsEngine, mobRegistry);
        mobRegistry.setMinionManager(minionManager);
        minionManager.setMaxPerOwner(getConfig().getInt("minions.limits.maxPerOwner", 0));
        Bukkit.getPluginManager().registerEvents(minionManager, this);
        registerProgressionHooks();

        BuiltinTypes.registerAll(effectsEngine);

        effectsBindings = new EffectsBindings(effectsEngine);
        Bukkit.getPluginManager().registerEvents(effectsBindings, this);

        headRegistry = new HeadRegistry(this, getLogger());
        headRegistry.reload();
        ItemTemplateCompiler.setHeadRegistry(headRegistry);
        GuiItems.setHeadRegistry(headRegistry);

        yamlAbilities = new EffectsYamlAbilities(this, effectsEngine, effectsBindings, serviceLog.effects(), serviceLog.bindings());
        yamlAbilities.reload();
        Bukkit.getPluginManager().registerEvents(new YamlVarsSessionListener(yamlAbilities), this);
        Bukkit.getPluginManager().registerEvents(new ItemSyncListener(yamlAbilities), this);
        Bukkit.getPluginManager().registerEvents(new ItemHookListener(effectsEngine, yamlAbilities), this);
        yamlAbilities.syncOnlineItems();
        editorDraftStore = new EditorDraftStore(this, serviceLog.effects());
        editorAuditLogger = new EditorAuditLogger(serviceLog.effects());
        editorLockManager = new EditorLockManager();
        editorAccessController = new EditorAccessController();
        editorServices = new EditorServices(effectsEngine, yamlAbilities, editorDraftStore, editorAccessController, editorLockManager, editorAuditLogger);
        Bukkit.getPluginManager().registerEvents(new EditorLockListener(editorLockManager), this);

        shopRegistry = new ShopYamlRegistry(this, serviceLog.shops(), this::resolveShopItem);
        shopRegistry.reload();
        if (advancementService != null && advancementService.isEnabled()) {
            advancementService.setShopRegistry(shopRegistry);
        }
        mobRegistry.setShopRegistry(shopRegistry);
        boolean customXpActive = customXpEnabled && customXpService != null;
        int shopTradeXpReward = customXpActive
            ? getConfig().getInt("progression.customXp.gain.shopTradeReward", 1)
            : 0;
        shopStocks = new ShopStockManager(serviceLog.shops());
        shopSessions = new ShopSessionManager(shopRegistry, shopStocks, !customXpActive, serviceLog.shops());
        shopMetrics = new ShopTradeMetrics(this, serviceLog.shops());
        shopMetrics.load();
        shopSessions.setTradeServices(shopMetrics, advancementService, customXpService, shopTradeXpReward);
        shopTradeAuditLog = new ShopTradeAuditLog(this, serviceLog.shops());
        shopSessions.setAuditLog(shopTradeAuditLog);
        Bukkit.getPluginManager().registerEvents(new ShopOpenListener(shopSessions), this);
        Bukkit.getPluginManager().registerEvents(
            new ShopTradeListener(shopRegistry, shopSessions, shopStocks, shopMetrics, advancementService,
                customXpService, shopTradeXpReward, serviceLog.shops()), this);

        questRegistry = new QuestYamlRegistry(this, getLogger(), this::resolveQuestItem);
        questRegistry.reload();
        questGiverRegistry = new QuestGiverYamlRegistry(this, getLogger());
        questGiverRegistry.reload();
        if (progressionDatabase != null && progressionService != null) {
            questService = new QuestService(
                questRegistry,
                new QuestJdbcRepository(progressionDatabase, getLogger()),
                progressionService,
                customXpService,
                shopRegistry,
                this::resolveQuestItem,
                this::isWorldAllowed,
                effectsEngine.manaProvider(),
                manaSources == null ? null : manaSources.quests(),
                craftingDiscovery,
                mobRegistry,
                partyService);
            QuestRewardShareMode questRewardMode = QuestRewardShareMode.fromString(
                getConfig().getString("quests.party.rewards.mode", "NONE"),
                QuestRewardShareMode.NONE);
            boolean questRewardRequireAssist = getConfig().getBoolean("quests.party.rewards.requireAssist", true);
            boolean questLockRequireLeader = getConfig().getBoolean("quests.party.lock.requireLeader", true);
            boolean questLockAutoAcceptMembers = getConfig().getBoolean("quests.party.lock.autoAcceptMembers", true);
            boolean questLockShareCompletion = getConfig().getBoolean("quests.party.lock.shareCompletion", true);
            questService.setPartyRules(partyAssistRules, questRewardMode, questRewardRequireAssist,
                questLockRequireLeader, questLockAutoAcceptMembers, questLockShareCompletion);
            questService.loadOnlinePlayers();
            double questAssistRadius = partyAssistRules == null ? 0.0 : partyAssistRules.baseRadius();
            Bukkit.getPluginManager().registerEvents(new QuestListener(questService, partyService, questAssistRadius), this);
        }
        if (shopSessions != null) {
            shopSessions.setRequirementServices(questService, classService, null);
        }

        if (upgradeRegistry == null) {
            upgradeRegistry = new UpgradeYamlRegistry(this, effectsEngine, serviceLog.effects());
            upgradeRegistry.reload();
        }

        mobYamlRegistry = new MobYamlRegistry(this, effectsEngine, yamlAbilities, shopRegistry, mobRegistry, mobSpawnManager, serviceLog.mobs());
        mobYamlRegistry.setTextureService(textureService);
        mobYamlRegistry.setUpgradeRegistry(upgradeRegistry);
        mobYamlRegistry.reload();
        if (minionManager != null) {
            minionManager.restorePersistentMinions();
        }
        mobRegistry.setLootPoolResolver(mobYamlRegistry::lootPool);
        if (advancementService != null && advancementService.isEnabled()) {
            Bukkit.getPluginManager().registerEvents(new BossAdvancementListener(mobRegistry, advancementService), this);
        }
        spawnerBlockStore = new MobSpawnerBlockStore(getDataFolder(), serviceLog.mobs());
        spawnerBlockStore.load();
        spawnerBlockStore.rehydrateMarkers();
        mobSpawnManager.setSpawnerBlockStore(spawnerBlockStore);
        mobSpawnManager.setYamlRegistry(mobYamlRegistry);
        trialSpawnerBlockStore = new TrialSpawnerBlockStore(getDataFolder(), serviceLog.mobs());
        trialSpawnerBlockStore.load();
        trialSpawnerBlockStore.rehydrateMarkers();
        trialSpawnerManager = new TrialSpawnerManager(effectsEngine, mobRegistry, mobYamlRegistry, trialSpawnerBlockStore,
            serviceLog.mobs());
        vaultBlockStore = new VaultBlockStore(getDataFolder(), serviceLog.mobs());
        vaultBlockStore.load();
        vaultBlockStore.rehydrateMarkers();
        vaultManager = new VaultManager(effectsEngine, mobYamlRegistry, yamlAbilities, vaultBlockStore, serviceLog.mobs());
        Bukkit.getPluginManager().registerEvents(new MobEggListener(effectsEngine, mobRegistry, mobYamlRegistry), this);
        Bukkit.getPluginManager().registerEvents(trialSpawnerManager, this);
        Bukkit.getPluginManager().registerEvents(vaultManager, this);
        textureDeliveryListener = new TextureDeliveryListener(textureService);
        Bukkit.getPluginManager().registerEvents(textureDeliveryListener, this);
        boolean spawnerOwnershipEnabled = getConfig().getBoolean("mobs.spawners.ownership.enabled", false);
        boolean spawnerAdminOnly = getConfig().getBoolean("mobs.spawners.ownership.adminOnly", false);
        String spawnerAdminPermission = getConfig().getString("mobs.spawners.ownership.adminPermission", "dungeonsreborn.spawner.admin");
        Bukkit.getPluginManager().registerEvents(
            new MobSpawnerBlockListener(mobYamlRegistry, mobRegistry, mobSpawnManager, spawnerBlockStore, serviceLog.mobs(),
                spawnerOwnershipEnabled, spawnerAdminOnly, spawnerAdminPermission), this);
        Bukkit.getPluginManager().registerEvents(
            new TrialSpawnerBlockListener(mobYamlRegistry, trialSpawnerManager, trialSpawnerBlockStore, serviceLog.mobs(),
                spawnerOwnershipEnabled, spawnerAdminOnly, spawnerAdminPermission), this);
        Bukkit.getPluginManager().registerEvents(
            new VaultBlockListener(mobYamlRegistry, vaultBlockStore, vaultManager, serviceLog.mobs(),
                spawnerOwnershipEnabled, spawnerAdminOnly, spawnerAdminPermission), this);

        mobPersistenceEnabled = getConfig().getBoolean("mobs.persistence.enabled", false);
        mobPersistenceStore = new MobPersistenceStore(getDataFolder(), serviceLog.mobs());
        if (mobPersistenceEnabled) {
            int restored = mobRegistry.restoreSnapshots(mobPersistenceStore.load());
            if (restored > 0) {
                getLogger().info("[Mobs] Restored " + restored + " persistent mobs");
            }
        }

        kitRegistry = new KitYamlRegistry(this, getLogger());
        kitRegistry.reload();
        if (progressionDatabase != null) {
            kitService = new KitService(kitRegistry, new KitJdbcRepository(progressionDatabase, getLogger()),
                shopRegistry, yamlAbilities::itemTemplate, advancementService, customXpService, getLogger());
        }

        classRegistry = new ClassYamlRegistry(this, getLogger());
        classRegistry.reload();
        if (progressionDatabase != null && progressionService != null) {
            long classSwitchCooldown = getConfig().getLong("classes.switching.cooldownSeconds", 0L);
            java.util.Set<String> classLockouts = new java.util.HashSet<>();
            for (String world : getConfig().getStringList("classes.switching.lockoutWorlds")) {
                if (world != null && !world.isBlank()) {
                    classLockouts.add(world.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
            classService = new ClassService(
                classRegistry,
                new ClassJdbcRepository(progressionDatabase, getLogger()),
                progressionService,
                shopRegistry,
                questService,
                this::isWorldAllowed,
                getLogger(),
                classSwitchCooldown,
                classLockouts);
        }
        if (shopSessions != null) {
            shopSessions.setRequirementServices(questService, classService, null);
        }
        if (progressionDatabase != null && progressionService != null && classService != null) {
            boolean resetEnabled = getConfig().getBoolean("classes.reset.enabled", false);
            int resetTokenCost = Math.max(0, getConfig().getInt("classes.reset.tokenCost", 0));
            double resetRefund = Math.max(0.0, getConfig().getDouble("classes.reset.refundRatio", 1.0));
            boolean respecEnabled = getConfig().getBoolean("classes.respec.enabled", true);
            double respecTokenMultiplier = getConfig().getDouble("classes.respec.tokenMultiplier", 1.0);
            double respecPointMultiplier = getConfig().getDouble("classes.respec.pointMultiplier", 1.0);
            double respecRefund = getConfig().getDouble("classes.respec.refundRatio", 1.0);
            int respecMaxTokenCost = Math.max(0, getConfig().getInt("classes.respec.maxTokenCost", 0));
            int respecMaxPointCost = Math.max(0, getConfig().getInt("classes.respec.maxPointCost", 0));
            int respecMaxRefund = Math.max(0, getConfig().getInt("classes.respec.maxRefundPoints", 0));
            classSkillService = new ClassSkillService(
                classService,
                progressionService,
                new ClassSkillJdbcRepository(progressionDatabase, getLogger()),
                new ClassSkillPresetJdbcRepository(progressionDatabase, getLogger()),
                shopRegistry,
                this::isWorldAllowed,
                getLogger(),
                new ClassSkillService.ResetPolicy(resetEnabled, resetTokenCost, resetRefund),
                new ClassSkillService.RespecPolicy(respecEnabled, respecTokenMultiplier, respecPointMultiplier,
                    respecRefund, respecMaxTokenCost, respecMaxPointCost, respecMaxRefund));
        }
        if (questService != null) {
            questService.setRequirementServices(classService, classSkillService, null);
        }
        if (classService != null && effectsEngine != null) {
            classBonusService = new ClassBonusService(this, classService, classSkillService, effectsEngine,
                this::isWorldAllowed, getConfig());
            if (progressionHud != null) {
                progressionHud.setClassBonuses(classBonusService);
            }
        }
        if (classRegistry != null && classService != null && classSkillService != null && effectsBindings != null) {
            classAbilityBindings = new ClassAbilityBindings(classRegistry, classService, classSkillService, effectsBindings);
            classAbilityBindings.reload();
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
        mobSpawnManager.setDungeonSessions(dungeonSessions);
        dungeonQueue.setSessionManager(dungeonSessions);
        if (sharedTicks != null) {
            sharedTicks.schedule("dungeonQueue", 20L, dungeonQueue::tick);
        } else {
            Bukkit.getScheduler().runTaskTimer(this, dungeonQueue::tick, 20L, 20L);
        }
        Bukkit.getPluginManager().registerEvents(new DungeonSessionListener(dungeonSessions), this);
        applyDebugFlags();

        upgradeService = new UpgradeService(this, effectsEngine, effectsBindings, upgradeRegistry, shopRegistry,
            customXpService, serviceLog.upgrades());
        if (mobYamlRegistry != null) {
            mobYamlRegistry.setUpgradeRegistry(upgradeRegistry);
        }
        upgradeService.migrateOnlinePlayers();
        if (sharedTicks != null) {
            sharedTicks.schedule("upgradeAuras", 20L, upgradeService::tickInventoryAuras);
        } else {
            Bukkit.getScheduler().runTaskTimer(this, upgradeService::tickInventoryAuras, 20L, 20L);
        }
        Bukkit.getPluginManager().registerEvents(new UpgradeOnDamagedListener(upgradeService), this);

        craftingRecipes = new CraftingYamlRegistry(this, serviceLog.effects(), this::resolveCraftingItem);
        ensureDefaultCraftingRecipes();
        craftingRecipes.reload();
        craftingDiscovery = new CraftingDiscoveryService(this, serviceLog.effects(), craftingRecipes);
        craftingDiscovery.load();
        craftingDiscovery.loadOnlinePlayers();
        if (sharedTicks != null) {
            sharedTicks.schedule("craftingDiscovery", 20L, craftingDiscovery::tick);
        } else {
            Bukkit.getScheduler().runTaskTimer(this, craftingDiscovery::tick, 20L, 20L);
        }
        vanillaCraftingBridge = new VanillaCraftingBridge(this, craftingRecipes, craftingDiscovery);
        vanillaCraftingBridge.rebuild();
        Bukkit.getPluginManager().registerEvents(vanillaCraftingBridge, this);

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
                    mobDebugOverlayService,
                    spawnerBlockStore,
                    trialSpawnerBlockStore,
                    vaultBlockStore,
                    craftingRecipes,
                    craftingDiscovery,
                    advancementService,
                    upgradeService,
                    shopRegistry,
                    shopSessions,
                    kitService,
                    classRegistry,
                    classService,
                    classSkillService,
                    classAbilityBindings,
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
                    mobDebugOverlayService,
                    spawnerBlockStore,
                    trialSpawnerBlockStore,
                    vaultBlockStore,
                    craftingRecipes,
                    craftingDiscovery,
                    advancementService,
                    upgradeService,
                    shopRegistry,
                    shopSessions,
                    kitService,
                    classRegistry,
                    classService,
                    classSkillService,
                    classAbilityBindings,
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
                    mobDebugOverlayService,
                    spawnerBlockStore,
                    trialSpawnerBlockStore,
                    vaultBlockStore,
                    craftingRecipes,
                    craftingDiscovery,
                    advancementService,
                    upgradeService,
                    shopRegistry,
                    shopSessions,
                    kitService,
                    classRegistry,
                    classService,
                    classSkillService,
                    classAbilityBindings,
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

    private void configureMobModelBridge() {
        if (mobRegistry == null) {
            return;
        }
        // Mob custom model integration is intentionally disabled.
        MobModelBridge bridge = new NoopMobModelBridge();
        mobRegistry.configureModelBridge(
            bridge,
            false,
            "disabled",
            false,
            5L,
            "WARN_AND_FALLBACK");
        getLogger().info("[Mobs] Custom model bridge disabled; using vanilla mob visuals.");
    }

    private MobAiEngineMode parseMobAiEngineMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return MobAiEngineMode.V3;
        }
        try {
            return MobAiEngineMode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception ex) {
            getLogger().warning("[Mobs] Invalid mobs.ai.defaultEngine='" + raw + "', using V3");
            return MobAiEngineMode.V3;
        }
    }

    private int resolveAiWorkerThreads(String rawValue, int maxWorkerThreads) {
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        int max = Math.max(1, maxWorkerThreads);
        if (rawValue == null || rawValue.isBlank() || "auto".equalsIgnoreCase(rawValue.trim())) {
            return Math.max(1, Math.min(max, cores - 1));
        }
        try {
            return Math.max(1, Math.min(max, Integer.parseInt(rawValue.trim())));
        } catch (Exception ex) {
            getLogger().warning("[Mobs] Invalid mobs.ai.async.workerThreads='" + rawValue + "', using auto");
            return Math.max(1, Math.min(max, cores - 1));
        }
    }

    private ItemStack resolveCraftingItem(String id) {
        if (shopRegistry != null) {
            ItemStack token = shopRegistry.resolveTokenItem(id);
            if (token != null) {
                return token;
            }
        }
        if (upgradeRegistry != null) {
            ItemStack upgrade = upgradeRegistry.upgradeItem(id);
            if (upgrade != null) {
                return upgrade;
            }
        }
        return yamlAbilities == null ? null : yamlAbilities.itemTemplate(id);
    }

    private ItemStack resolveQuestItem(String id) {
        if (shopRegistry != null) {
            ItemStack token = shopRegistry.resolveTokenItem(id);
            if (token != null) {
                return token;
            }
        }
        if (upgradeRegistry != null) {
            ItemStack upgrade = upgradeRegistry.upgradeItem(id);
            if (upgrade != null) {
                return upgrade;
            }
        }
        return yamlAbilities == null ? null : yamlAbilities.itemTemplate(id);
    }

    private ItemStack resolveShopItem(String id) {
        if (shopRegistry != null) {
            ItemStack token = shopRegistry.resolveTokenItem(id);
            if (token != null) {
                return token;
            }
        }
        if (upgradeRegistry != null) {
            ItemStack upgrade = upgradeRegistry.upgradeItem(id);
            if (upgrade != null) {
                return upgrade;
            }
        }
        return yamlAbilities == null ? null : yamlAbilities.itemTemplate(id);
    }

    private void ensureDefaultCraftingRecipes() {
        File dir = new File(getDataFolder(), "recipes");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        List<String> entries = readResourceIndex("recipes/index.txt");
        if (entries.isEmpty()) {
            saveRecipeIfMissing("recipes/token_compress.yml");
            saveRecipeIfMissing("recipes/token_decompress.yml");
            saveRecipeIfMissing("recipes/token_pallet.yml");
            saveRecipeIfMissing("recipes/token_unpallet.yml");
            return;
        }
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (!trimmed.endsWith(".yml") && !trimmed.endsWith(".yaml")) {
                continue;
            }
            saveRecipeIfMissing("recipes/" + trimmed);
        }
    }

    private void saveRecipeIfMissing(String resourcePath) {
        File file = new File(getDataFolder(), resourcePath);
        if (file.exists()) {
            return;
        }
        if (getResource(resourcePath) == null) {
            getLogger().warning("[Crafting] Missing bundled recipe: " + resourcePath + " (skipping copy)");
            return;
        }
        saveResource(resourcePath, false);
    }

    private List<String> readResourceIndex(String path) {
        try (InputStream stream = getResource(path)) {
            if (stream == null) {
                return List.of();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                List<String> lines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
                return lines;
            }
        } catch (Exception ex) {
            getLogger().warning("[Crafting] Unable to read " + path + ": " + ex.getMessage());
            return List.of();
        }
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
        if (partyAuraTask != null) {
            partyAuraTask.cancel();
            partyAuraTask = null;
        }
        partyAuraService = null;
        if (customXpService != null) {
            customXpService.shutdown();
            customXpService = null;
        }
        if (progressionDatabase != null) {
            progressionDatabase.close();
            progressionDatabase = null;
        }
        if (effectsEngine != null) {
            effectsEngine.shutdown();
            effectsEngine = null;
        }
        if (textureService != null) {
            textureService.shutdown();
            textureService = null;
        }
        if (mobPersistenceStore != null && mobRegistry != null && mobPersistenceEnabled) {
            mobPersistenceStore.save(mobRegistry.snapshots());
        }
        if (manaStorage != null) {
            manaStorage.saveNow();
            manaStorage = null;
        }
        if (craftingDiscovery != null) {
            craftingDiscovery.save();
            craftingDiscovery = null;
        }
        if (vanillaCraftingBridge != null) {
            vanillaCraftingBridge.shutdown();
            vanillaCraftingBridge = null;
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
        mobPersistenceStore = null;
        minionManager = null;
        craftingRecipes = null;
        upgradeRegistry = null;
        upgradeService = null;
        shopRegistry = null;
        shopSessions = null;
        shopStocks = null;
        if (shopMetrics != null) {
            shopMetrics.saveNow();
        }
        shopMetrics = null;
        shopTradeAuditLog = null;
        partyAuditLog = null;
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
        partyRepository = null;
        partyAssistRules = null;
        partyXpShareMode = PartyShareMode.NONE;
        partyXpRequireAssist = true;
        partyLootShareMode = PartyLootShareMode.NONE;
        partyLootRequireAssist = true;
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
        partyRepository = new PartyJdbcRepository(progressionDatabase, getLogger());
        ProgressionRepository repository = new ProgressionJdbcRepository(progressionDatabase, getLogger());
        ProgressionCurve curve = ProgressionCurve.fromConfig(getConfig().getConfigurationSection("progression.levelCurve"));
        int skillPointsPerXp = getConfig().getInt("progression.skillPoints.perXp", 0);
        progressionService = new ProgressionService(this, repository, curve, this::isWorldAllowed, skillPointsPerXp, getLogger());
        progressionService.startAutoSave(sharedTicks);
        Bukkit.getPluginManager().registerEvents(new ProgressionListener(progressionService), this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            progressionService.load(player);
        }

        boolean customXpEnabled = getConfig().getBoolean("progression.customXp.enabled", true);
        if (customXpEnabled) {
            CustomXpRepository customRepository = new CustomXpJdbcRepository(progressionDatabase, getLogger());
            var customCurveSection = getConfig().getConfigurationSection("progression.customXp.levelCurve");
            ProgressionCurve customCurve = customCurveSection == null ? curve : ProgressionCurve.fromConfig(customCurveSection);
            customXpService = new CustomXpService(this, customRepository, customCurve, this::isWorldAllowed, getLogger());
            if (advancementService != null && advancementService.isEnabled()) {
                customXpService.setAdvancementService(advancementService);
            }
            customXpService.startAutoSave(sharedTicks);
            Bukkit.getPluginManager().registerEvents(new CustomXpListener(customXpService), this);
            for (Player player : Bukkit.getOnlinePlayers()) {
                customXpService.load(player);
            }
        } else {
            customXpService = null;
        }
    }

    private void registerProgressionHooks() {
        if (progressionService == null || mobRegistry == null) {
            return;
        }
        mobRegistry.setProgressionService(progressionService);
        if (progressionStats == null && effectsEngine != null) {
            progressionStats = new ProgressionStatService(this, progressionService, effectsEngine, this::isWorldAllowed, getConfig());
            for (Player player : Bukkit.getOnlinePlayers()) {
                progressionStats.apply(player);
            }
        }
        if (progressionHud == null && effectsEngine != null) {
            progressionHud = new ProgressionHudService(this, progressionService, customXpService,
                progressionStats, effectsEngine, this::isWorldAllowed);
            progressionHud.start(sharedTicks);
            Bukkit.getPluginManager().registerEvents(new ProgressionHudListener(progressionHud), this);
        }
        Bukkit.getPluginManager().registerEvents(
            new ProgressionMobKillListener(progressionService, customXpService, mobRegistry, partyService,
                partyAssistRules, partyXpShareMode, partyXpRequireAssist), this);
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

    public PartyService partyService() {
        return partyService;
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

    public CustomXpService customXpService() {
        return customXpService;
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

    private List<QuestRegion> readPartyRegions(String path) {
        Object raw = getConfig().get(path);
        if (raw == null) {
            return List.of();
        }
        List<QuestRegion> regions = new ArrayList<>();
        if (raw instanceof ConfigurationSection section) {
            QuestRegion region = parsePartyRegion(section.getValues(false));
            if (region != null) {
                regions.add(region);
            }
        } else if (raw instanceof Map<?, ?> map) {
            QuestRegion region = parsePartyRegion(map);
            if (region != null) {
                regions.add(region);
            }
        } else if (raw instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof ConfigurationSection entrySection) {
                    QuestRegion region = parsePartyRegion(entrySection.getValues(false));
                    if (region != null) {
                        regions.add(region);
                    }
                } else if (entry instanceof Map<?, ?> entryMap) {
                    QuestRegion region = parsePartyRegion(entryMap);
                    if (region != null) {
                        regions.add(region);
                    }
                }
            }
        }
        return List.copyOf(regions);
    }

    private List<PotionEffect> readPartyBuffEffects(long periodTicks) {
        List<Map<?, ?>> raw = getConfig().getMapList("party.buffs.effects");
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        int duration = (int) Math.min(Integer.MAX_VALUE, Math.max(40L, periodTicks * 2L));
        List<PotionEffect> effects = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            if (entry == null) {
                continue;
            }
            Object typeRaw = entry.get("type");
            PotionEffectType type = resolvePotionEffectType(typeRaw == null ? null : typeRaw.toString());
            if (type == null) {
                continue;
            }
            int amplifier = 0;
            Object ampRaw = entry.get("amplifier");
            if (ampRaw instanceof Number number) {
                amplifier = Math.max(0, number.intValue());
            }
            boolean ambient = entry.get("ambient") instanceof Boolean b ? b : true;
            boolean particles = entry.get("particles") instanceof Boolean b ? b : true;
            boolean icon = entry.get("icon") instanceof Boolean b ? b : true;
            effects.add(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
        }
        return effects;
    }

    private PotionEffectType resolvePotionEffectType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String id = raw.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(id);
        if (key == null) {
            key = NamespacedKey.minecraft(id);
        }
        return Registry.POTION_EFFECT_TYPE.get(key);
    }

    private QuestRegion parsePartyRegion(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        String world = stringValue(map.get("world"));
        if (world == null || world.isBlank()) {
            return null;
        }
        double x = doubleValue(map.get("x"), 0.0);
        double y = doubleValue(map.get("y"), 0.0);
        double z = doubleValue(map.get("z"), 0.0);
        double radius = doubleValue(map.get("radius"), 0.0);
        return new QuestRegion(world, x, y, z, radius);
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private double doubleValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
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
        if (textureService != null) {
            textureService.reloadConfig();
        }
        if (mobRegistry != null) {
            configureMobModelBridge();
        }
        applyDebugFlags();
    }

    public void reloadHeadRegistry() {
        if (headRegistry != null) {
            headRegistry.reload();
        }
    }

    public HeadRegistry headRegistry() {
        return headRegistry;
    }

    public TextureService textureService() {
        return textureService;
    }

    public TextureBuildResult reloadTextures() {
        if (textureService == null) {
            return TextureBuildResult.disabled();
        }
        textureService.reloadConfig();
        return textureService.rebuildIfAutoEnabled();
    }

    public EffectsEngine effectsEngine() {
        return effectsEngine;
    }

    public QuestService questService() {
        return questService;
    }

    public ClassService classService() {
        return classService;
    }

    public ShopYamlRegistry shopRegistry() {
        return shopRegistry;
    }

    public void rebuildVanillaCrafting() {
        if (vanillaCraftingBridge != null) {
            vanillaCraftingBridge.rebuild();
        }
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
