package dev.patric.dungeonsreborn.mobs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityBreakDoorEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.entity.ArmorStand;
import dev.patric.dungeonsreborn.party.Party;
import dev.patric.dungeonsreborn.party.PartyAssistRules;
import dev.patric.dungeonsreborn.party.PartyLootShareMode;
import dev.patric.dungeonsreborn.party.PartyShareMode;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Vehicle;

import dev.patric.dungeonsreborn.advancements.AdvancementService;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.Vars;
import dev.patric.dungeonsreborn.effects.damage.DamageType;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.effects.mana.ManaProvider;
import dev.patric.dungeonsreborn.effects.minions.MinionManager;
import dev.patric.dungeonsreborn.effects.minions.MinionMode;
import dev.patric.dungeonsreborn.effects.minions.MinionTargetRules;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeModifierType;
import dev.patric.dungeonsreborn.crafting.CraftingDiscoveryService;
import dev.patric.dungeonsreborn.logging.ServiceLogger;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.progression.ProgressionAwardSource;
import dev.patric.dungeonsreborn.progression.ProgressionService;
import dev.patric.dungeonsreborn.progression.custom.CustomXpProfile;
import dev.patric.dungeonsreborn.progression.custom.CustomXpService;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import dev.patric.dungeonsreborn.mobs.ai.MobAiEngineMode;
import dev.patric.dungeonsreborn.mobs.ai.MobAiNavigationDriver;
import dev.patric.dungeonsreborn.mobs.ai.MobAiProfile;
import dev.patric.dungeonsreborn.mobs.ai.MobAiRuntimeMetrics;
import dev.patric.dungeonsreborn.mobs.ai.MobGoalsNavigationDriver;
import dev.patric.dungeonsreborn.mobs.ai.VelocityNavigationDriver;
import dev.patric.dungeonsreborn.mobs.ai.v3.MobAiGuardrailController;
import dev.patric.dungeonsreborn.mobs.ai.v3.MobAiPlan;
import dev.patric.dungeonsreborn.mobs.ai.v3.MobAiPlannerService;
import dev.patric.dungeonsreborn.mobs.ai.v3.MobAiSnapshot;
import dev.patric.dungeonsreborn.mobs.ai.v3.MobAiV3Resolver;
import dev.patric.dungeonsreborn.mobs.ai.v3.MobAiV3Spec;
import dev.patric.dungeonsreborn.mobs.model.ModelRuntimeSpec;
import dev.patric.dungeonsreborn.mobs.model.MobModelBridge;
import dev.patric.dungeonsreborn.mobs.model.NoopMobModelBridge;
import dev.patric.dungeonsreborn.textures.TextureService;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class MobRegistry implements Listener {
  private record MobInstance(String specId, UUID ownerId) {
  }

  public record MobSnapshot(UUID entityId, String mobId, String variantId, String traitId, UUID ownerId, String world,
      double x, double y, double z, double health, double maxHealth) {
  }

  public record MobMetricsSnapshot(String mobId, long spawns, long deaths, double spawnsPerMinute, int active) {
  }

  public record MobDebugSnapshot(String mobId, String phaseId, String behaviorState, String targetName,
      String cooldownSummary, String pathInfo, long stateAgeTicks) {
  }

  public record ModelBridgeStatus(
      boolean enabled,
      String provider,
      boolean available,
      int activeModeledEntities,
      long fallbackCount,
      long lastReloadEpochMs,
      long syncPeriodTicks,
      String missingProviderPolicy,
      boolean debug) {
  }

  public record AiStatusSnapshot(
      boolean enabled,
      String defaultEngine,
      boolean pathfinderEnabled,
      boolean useMobGoalsApi,
      boolean fullOverrideEnabled,
      boolean fullOverrideHardDisableVanilla,
      boolean asyncEnabled,
      int asyncQueueSize,
      int asyncWorkers,
      int activeMobs,
      int activeFullOverrideMobs,
      int aiStepsLastTick,
      int pathMutationsLastTick,
      int selectorEvaluationsLastTick,
      int overrideCastsLastTick,
      long guardrailTrips,
      long fallbackTicks,
      long stalePlanDiscards,
      String degradeTier) {
  }

  public record AiAsyncStatusSnapshot(
      boolean enabled,
      int workers,
      int queueSize,
      int queueCapacity,
      int maxJobsPerTick,
      long planTtlTicks,
      long submitted,
      long completed,
      long staleDiscards,
      long droppedBackpressure,
      long failed) {
  }

  private static final long TICK_PERIOD = 1L;
  private final Map<String, MobSpec> specs = new LinkedHashMap<>();
  private final Map<UUID, MobInstance> active = new java.util.HashMap<>();
  private final Map<UUID, MobState> states = new java.util.HashMap<>();
  private final Map<UUID, ManaKillStreak> manaKillStreaks = new java.util.HashMap<>();
  private final Map<String, Long> spawnCounts = new java.util.HashMap<>();
  private final Map<String, Long> deathCounts = new java.util.HashMap<>();
  private final long metricsStartMs = System.currentTimeMillis();
  private final Random rng = new Random();
  private final EffectsEngine engine;
  private ServiceLogger logger;
  private TextureService textureService;
  private MinionManager minionManager;
  private MobSpawnManager spawnManager;
  private ShopYamlRegistry shopRegistry;
  private AdvancementService advancementService;
  private CustomXpService customXpService;
  private ProgressionService progressionService;
  private CraftingDiscoveryService craftingDiscovery;
  private java.util.function.Function<String, MobLootSpec> lootPoolResolver;
  private dev.patric.dungeonsreborn.party.PartyService partyService;
  private PartyAssistRules partyAssistRules = new PartyAssistRules(0.0, 0.0, 0.0);
  private PartyShareMode partyXpShareMode = PartyShareMode.NONE;
  private boolean partyXpRequireAssist = true;
  private PartyLootShareMode partyLootShareMode = PartyLootShareMode.NONE;
  private boolean partyLootRequireAssist = true;
  private int maxActivePerTick;
  private boolean xpGatingEnabled = true;
  private String xpGatingBypassPermission = "";
  private long xpGatingMessageCooldownMs = 2000L;
  private MinionKillCredit minionLootCredit = MinionKillCredit.OWNER;
  private MinionKillCredit minionXpCredit = MinionKillCredit.OWNER;
  private final Map<UUID, Long> nextXpGateMessageAt = new java.util.HashMap<>();
  private Predicate<World> worldAllowed = world -> true;
  private MobModelBridge modelBridge = new NoopMobModelBridge();
  private boolean modelBridgeEnabled = true;
  private String modelBridgeProvider = "model_engine";
  private boolean modelBridgeDebug;
  private long modelSyncPeriodTicks = 5L;
  private String modelMissingProviderPolicy = "WARN_AND_FALLBACK";
  private long modelFallbackCount;
  private final Set<String> modelFallbackWarnings = new HashSet<>();
  private long modelLastReloadEpochMs;
  private static final double MODEL_MOVE_THRESHOLD_SQ = 0.02D * 0.02D;
  private boolean aiEnabled = true;
  private MobAiEngineMode aiDefaultEngine = MobAiEngineMode.LEGACY;
  private boolean aiPathfinderEnabled = true;
  private boolean aiUseMobGoalsApi = true;
  private int aiMaxStepsPerTick = 3000;
  private int aiMaxPathMutationsPerTick = 500;
  private long aiRetargetMinIntervalTicks = 5L;
  private long aiPathRecalcMinIntervalTicks = 10L;
  private long aiSampleWindowTicks = 200L;
  private boolean aiFullOverrideEnabled = true;
  private boolean aiFullOverrideHardDisableVanilla = true;
  private int aiMaxSelectorEvaluationsPerTick = 4000;
  private int aiMaxOverrideCastsPerTick = 1000;
  private boolean aiV4NaturalModelEnabled = true;
  private String aiV4NaturalOptInMode = "PACK_PREFIX";
  private final List<String> aiV4NaturalPackPrefixes = new ArrayList<>();
  private MobAiMovementPolicy aiV4DefaultMovementPolicy = MobAiMovementPolicy.PATHFINDER_FIRST;
  private boolean aiAsyncEnabled = true;
  private int aiAsyncWorkerThreads = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() - 1));
  private int aiAsyncMaxJobsPerTick = 2000;
  private int aiAsyncQueueCapacity = 10_000;
  private long aiAsyncPlanTtlTicks = 1L;
  private int aiStepsThisTick;
  private int aiPathMutationsThisTick;
  private int aiStepsLastTick;
  private int aiPathMutationsLastTick;
  private int aiSelectorEvaluationsThisTick;
  private int aiSelectorEvaluationsLastTick;
  private int aiOverrideCastsThisTick;
  private int aiOverrideCastsLastTick;
  private int aiActiveFullOverrideThisTick;
  private int aiActiveFullOverrideLastTick;
  private long aiTotalSteps;
  private long aiTotalPathMutations;
  private long aiGuardrailTrips;
  private long aiFallbackTicks;
  private long aiStalePlanDiscards;
  private MobAiGuardrailController.DegradeTier aiCurrentDegradeTier = MobAiGuardrailController.DegradeTier.NONE;
  private final MobAiGuardrailController aiGuardrailController = new MobAiGuardrailController();
  private MobAiPlannerService aiPlannerService;
  private final MobAiNavigationDriver mobGoalsNavigationDriver = new MobGoalsNavigationDriver();
  private final MobAiNavigationDriver velocityNavigationDriver = new VelocityNavigationDriver();
  private final Set<String> aiLegacyOverrideWarned = new HashSet<>();

  private static final class MobState {
    private UUID lastAttacker;
    private final Map<UUID, Double> threat = new java.util.HashMap<>();
    private long nextMainTick;
    private long nextSecondaryTick;
    private final Map<String, Long> nextPassiveTick = new java.util.HashMap<>();
    private long nextNearTick;
    private long nextSpawnTick;
    private long nextIdleTick;
    private long nextStuckTick;
    private BossBar bossBar;
    private long nextBossBarAudienceTick;
    private org.bukkit.Location home;
    private UUID currentTarget;
    private long lastTargetSwitchTick;
    private long nextWanderTick;
    private org.bukkit.Location wanderTarget;
    private String variantId;
    private String traitId;
    private String phaseId;
    private long nextManaDrainTick;
    private int patrolIndex;
    private MobBehaviorState behaviorState = MobBehaviorState.IDLE;
    private long lastStateChangeTick;
    private org.bukkit.Location lastPosition;
    private long lastMoveTick;
    private long nextBlockTick;
    private final Map<String, Long> nextAttackCooldown = new java.util.HashMap<>();
    private double baseScale = 1.0;
    private UUID compositePartner;
    private boolean compositeKeepAlive;
    private long nextModelSyncTick;
    private boolean modelMoving;
    private long nextCallHelpTick;
    private long nextRetargetTick;
    private long nextPathRecalcTick;
    private final Map<String, Long> nextSelectorCastTick = new java.util.HashMap<>();
    private String lastSelectorId;
    private MobAiIntentType lastIntentType = MobAiIntentType.HOLD_POSITION;
    private final Map<MobAiTargetSourceType, UUID> targetSourceMemory = new EnumMap<>(MobAiTargetSourceType.class);
    private final Map<MobAiTargetSourceType, Long> targetSourceMemoryExpiry = new EnumMap<>(MobAiTargetSourceType.class);
    private final Map<MobAiTargetSourceType, Long> targetSourceCooldownExpiry = new EnumMap<>(MobAiTargetSourceType.class);
    private MobAiTargetSourceType lastResolvedTargetSource;
    private String lastNavigationDriver;
    private long movementSuppressedUntilTick;
  }

  private record ManaKillStreak(long lastKillTick, int streak) {
  }

  public MobRegistry(EffectsEngine engine) {
    this.engine = Objects.requireNonNull(engine, "engine");
    this.aiPlannerService = new MobAiPlannerService(aiAsyncWorkerThreads, aiAsyncQueueCapacity);
    engine.runRepeating(TICK_PERIOD, TICK_PERIOD, this::tick);
  }

  public void setMinionManager(MinionManager minionManager) {
    this.minionManager = minionManager;
  }

  public void setSpawnManager(MobSpawnManager spawnManager) {
    this.spawnManager = spawnManager;
  }

  public void setCraftingDiscoveryService(CraftingDiscoveryService craftingDiscovery) {
    this.craftingDiscovery = craftingDiscovery;
  }

  public void setLogger(ServiceLogger logger) {
    this.logger = logger;
  }

  public void setTextureService(TextureService textureService) {
    this.textureService = textureService;
  }

  public void configureModelBridge(MobModelBridge bridge, boolean enabled, String provider, boolean debug,
      long syncPeriodTicks, String missingProviderPolicy) {
    this.modelBridge = bridge == null ? new NoopMobModelBridge() : bridge;
    this.modelBridgeEnabled = enabled;
    this.modelBridgeProvider = provider == null || provider.isBlank() ? "model_engine" : provider;
    this.modelBridgeDebug = debug;
    this.modelSyncPeriodTicks = Math.max(1L, syncPeriodTicks);
    this.modelMissingProviderPolicy = missingProviderPolicy == null || missingProviderPolicy.isBlank()
        ? "WARN_AND_FALLBACK"
        : missingProviderPolicy;
    this.modelFallbackCount = 0L;
    this.modelFallbackWarnings.clear();
    this.modelLastReloadEpochMs = System.currentTimeMillis();
  }

  public ModelBridgeStatus modelBridgeStatus() {
    return new ModelBridgeStatus(
        modelBridgeEnabled,
        modelBridgeProvider,
        modelBridge != null && modelBridge.available(),
        modelBridge == null ? 0 : modelBridge.activeCount(),
        modelFallbackCount,
        modelLastReloadEpochMs,
        modelSyncPeriodTicks,
        modelMissingProviderPolicy,
        modelBridgeDebug);
  }

  public void configureAi(
      boolean enabled,
      MobAiEngineMode defaultEngine,
      boolean pathfinderEnabled,
      boolean useMobGoalsApi,
      int maxAiStepsPerTick,
      int maxPathMutationsPerTick,
      long retargetMinIntervalTicks,
      long pathRecalcMinIntervalTicks,
      long sampleWindowTicks,
      boolean asyncEnabled,
      int asyncWorkerThreads,
      int asyncMaxJobsPerTick,
      int asyncQueueCapacity,
      long asyncPlanTtlTicks,
      boolean fullOverrideEnabled,
      boolean fullOverrideHardDisableVanilla,
      int maxSelectorEvaluationsPerTick,
      int maxOverrideCastsPerTick,
      boolean naturalModelEnabled,
      String naturalOptInMode,
      List<String> naturalPackPrefixes,
      MobAiMovementPolicy defaultMovementPolicy) {
    this.aiEnabled = enabled;
    this.aiDefaultEngine = defaultEngine == null ? MobAiEngineMode.LEGACY : defaultEngine;
    this.aiPathfinderEnabled = pathfinderEnabled;
    this.aiUseMobGoalsApi = useMobGoalsApi;
    this.aiMaxStepsPerTick = Math.max(0, maxAiStepsPerTick);
    this.aiMaxPathMutationsPerTick = Math.max(0, maxPathMutationsPerTick);
    this.aiRetargetMinIntervalTicks = Math.max(0L, retargetMinIntervalTicks);
    this.aiPathRecalcMinIntervalTicks = Math.max(0L, pathRecalcMinIntervalTicks);
    this.aiSampleWindowTicks = Math.max(1L, sampleWindowTicks);
    this.aiAsyncEnabled = asyncEnabled;
    this.aiAsyncWorkerThreads = Math.max(1, asyncWorkerThreads);
    this.aiAsyncMaxJobsPerTick = Math.max(1, asyncMaxJobsPerTick);
    this.aiAsyncQueueCapacity = Math.max(128, asyncQueueCapacity);
    this.aiAsyncPlanTtlTicks = Math.max(1L, asyncPlanTtlTicks);
    this.aiFullOverrideEnabled = fullOverrideEnabled;
    this.aiFullOverrideHardDisableVanilla = fullOverrideHardDisableVanilla;
    this.aiMaxSelectorEvaluationsPerTick = Math.max(1, maxSelectorEvaluationsPerTick);
    this.aiMaxOverrideCastsPerTick = Math.max(1, maxOverrideCastsPerTick);
    this.aiV4NaturalModelEnabled = naturalModelEnabled;
    this.aiV4NaturalOptInMode = naturalOptInMode == null || naturalOptInMode.isBlank()
        ? "PACK_PREFIX"
        : naturalOptInMode.trim().toUpperCase(Locale.ROOT);
    this.aiV4NaturalPackPrefixes.clear();
    if (naturalPackPrefixes != null) {
      for (String prefix : naturalPackPrefixes) {
        if (prefix == null || prefix.isBlank()) {
          continue;
        }
        this.aiV4NaturalPackPrefixes.add(prefix.trim().toLowerCase(Locale.ROOT));
      }
    }
    this.aiV4DefaultMovementPolicy = defaultMovementPolicy == null
        ? MobAiMovementPolicy.PATHFINDER_FIRST
        : defaultMovementPolicy;
    if (aiPlannerService != null) {
      aiPlannerService.shutdown();
    }
    aiPlannerService = new MobAiPlannerService(aiAsyncWorkerThreads, aiAsyncQueueCapacity);
  }

  public AiStatusSnapshot aiStatus() {
    return new AiStatusSnapshot(
        aiEnabled,
        aiDefaultEngine.name(),
        aiPathfinderEnabled,
        aiUseMobGoalsApi,
        aiFullOverrideEnabled,
        aiFullOverrideHardDisableVanilla,
        aiAsyncEnabled,
        aiPlannerService == null ? 0 : aiPlannerService.queueSize(),
        aiAsyncWorkerThreads,
        active.size(),
        aiActiveFullOverrideLastTick,
        aiStepsLastTick,
        aiPathMutationsLastTick,
        aiSelectorEvaluationsLastTick,
        aiOverrideCastsLastTick,
        aiGuardrailTrips,
        aiFallbackTicks,
        aiStalePlanDiscards,
        aiCurrentDegradeTier.name());
  }

  public MobAiRuntimeMetrics aiMetrics() {
    return new MobAiRuntimeMetrics(
        aiStepsLastTick,
        aiPathMutationsLastTick,
        aiTotalSteps,
        aiTotalPathMutations,
        aiGuardrailTrips,
        aiFallbackTicks,
        aiSampleWindowTicks);
  }

  public AiAsyncStatusSnapshot aiAsyncStatus() {
    long submitted = 0L;
    long completed = 0L;
    long dropped = 0L;
    long failed = 0L;
    if (aiPlannerService != null && aiPlannerService.metrics() != null) {
      submitted = aiPlannerService.metrics().submitted();
      completed = aiPlannerService.metrics().completed();
      dropped = aiPlannerService.metrics().droppedBackpressure();
      failed = aiPlannerService.metrics().failed();
    }
    return new AiAsyncStatusSnapshot(
        aiAsyncEnabled,
        aiAsyncWorkerThreads,
        aiPlannerService == null ? 0 : aiPlannerService.queueSize(),
        aiAsyncQueueCapacity,
        aiAsyncMaxJobsPerTick,
        aiAsyncPlanTtlTicks,
        submitted,
        completed,
        aiStalePlanDiscards,
        dropped,
        failed);
  }

  public String aiDegradeStatus() {
    return aiCurrentDegradeTier.name();
  }

  public boolean tuneAiAsync(String key, String value) {
    if (key == null || value == null) {
      return false;
    }
    String k = key.trim().toLowerCase(Locale.ROOT);
    try {
      switch (k) {
        case "enabled" -> aiAsyncEnabled = Boolean.parseBoolean(value);
        case "workers" -> aiAsyncWorkerThreads = Math.max(1, Integer.parseInt(value));
        case "maxjobspetick", "maxjobspertick", "max_jobs_per_tick" -> aiAsyncMaxJobsPerTick = Math.max(1, Integer.parseInt(value));
        case "queuecapacity", "queue_capacity" -> aiAsyncQueueCapacity = Math.max(128, Integer.parseInt(value));
        case "planttl", "plan_ttl", "planttlticks", "plan_ttl_ticks" -> aiAsyncPlanTtlTicks = Math.max(1L, Long.parseLong(value));
        default -> {
          return false;
        }
      }
      if (k.equals("workers") || k.equals("queuecapacity") || k.equals("queue_capacity")) {
        if (aiPlannerService != null) {
          aiPlannerService.shutdown();
        }
        aiPlannerService = new MobAiPlannerService(aiAsyncWorkerThreads, aiAsyncQueueCapacity);
      }
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  public String simulateAi(String mobId, int ticks) {
    if (mobId == null || mobId.isBlank() || ticks <= 0) {
      return "invalid";
    }
    MobSpec spec = specs.get(Ids.normalize(mobId));
    if (spec == null) {
      return "missing";
    }
    MobAiSpec ai = spec.aiSpec();
    if (ai == null || !ai.enabled()) {
      return "mode=DISABLED";
    }
    MobAiEngineMode engine = ai.engineMode() == null ? aiDefaultEngine : ai.engineMode();
    if (!ai.isFullOverride()) {
      MobAiV3Spec resolved = MobAiV3Resolver.resolve(ai);
      return "mode=DEFAULT, schema=" + ai.schemaVersion().name()
          + ", engine=" + engine.name()
          + ", profile=" + resolved.profile().name()
          + ", goals=" + resolved.goals().size()
          + ", ticks=" + ticks;
    }
    List<MobAiSelectorSpec> selectors = new ArrayList<>(ai.selectors());
    selectors.sort(java.util.Comparator.comparingInt(MobAiSelectorSpec::priority));
    int castSelectors = 0;
    long estimatedCasts = 0L;
    List<String> top = new ArrayList<>();
    for (MobAiSelectorSpec selector : selectors) {
      if (selector == null || selector.intent() == null) {
        continue;
      }
      MobAiIntentSpec intent = selector.intent();
      if (top.size() < 3) {
        top.add(selector.id() + ":" + intent.type().name());
      }
      if (intent.hasCastAbility()) {
        castSelectors++;
        long cooldown = Math.max(1L, intent.castCooldownTicks());
        estimatedCasts += Math.max(1L, ticks / cooldown);
      }
    }
    String firstNoTarget = selectSimulatedSelector(selectors, false);
    String firstWithTarget = selectSimulatedSelector(selectors, true);
    MobAiRuntimeModel runtimeModel = resolveRuntimeModel(spec, ai);
    MobAiMovementPolicy movementPolicy = resolveMovementPolicy(spec, ai, runtimeModel);
    int sources = ai.targetSources() == null ? 0 : ai.targetSources().size();
    return "mode=FULL_OVERRIDE, schema=" + ai.schemaVersion().name()
        + ", engine=" + engine.name()
        + ", runtimeModel=" + runtimeModel.name()
        + ", movementPolicy=" + movementPolicy.name()
        + ", authority=" + ai.combatAuthority().name()
        + ", targetSources=" + sources
        + ", selectors=" + selectors.size()
        + ", castSelectors=" + castSelectors
        + ", estCasts@" + ticks + "t=" + estimatedCasts
        + ", first(noTarget)=" + firstNoTarget
        + ", first(withTarget)=" + firstWithTarget
        + ", top=" + (top.isEmpty() ? "-" : String.join("|", top));
  }

  private String selectSimulatedSelector(List<MobAiSelectorSpec> selectors, boolean hasTarget) {
    for (MobAiSelectorSpec selector : selectors) {
      if (selector == null || selector.intent() == null) {
        continue;
      }
      if (evaluateSimulatedCondition(selector.condition(), hasTarget)) {
        return selector.id() + ":" + selector.intent().type().name();
      }
    }
    return "none";
  }

  private boolean evaluateSimulatedCondition(MobAiConditionSpec condition, boolean hasTarget) {
    if (condition == null) {
      return true;
    }
    return switch (condition.kind()) {
      case ALWAYS -> true;
      case ALL -> {
        boolean ok = true;
        for (MobAiConditionSpec child : condition.children()) {
          if (!evaluateSimulatedCondition(child, hasTarget)) {
            ok = false;
            break;
          }
        }
        yield ok;
      }
      case ANY -> {
        boolean ok = false;
        for (MobAiConditionSpec child : condition.children()) {
          if (evaluateSimulatedCondition(child, hasTarget)) {
            ok = true;
            break;
          }
        }
        yield ok;
      }
      case NOT -> condition.children().isEmpty() || !evaluateSimulatedCondition(condition.children().get(0), hasTarget);
      case HAS_TARGET -> {
        boolean expected = condition.booleanValue() == null || condition.booleanValue();
        yield expected == hasTarget;
      }
      case HEALTH_RATIO_LTE, HEALTH_RATIO_GTE, TARGET_DISTANCE_LTE, TARGET_DISTANCE_GTE, BEHAVIOR_STATE, RANDOM_CHANCE -> true;
    };
  }

  public void markModelRegistryReload() {
    modelLastReloadEpochMs = System.currentTimeMillis();
    modelFallbackCount = 0L;
    modelFallbackWarnings.clear();
    if (modelBridge == null) {
      return;
    }
    int refreshed = 0;
    for (Map.Entry<UUID, MobInstance> entry : active.entrySet()) {
      if (entry == null || entry.getKey() == null || entry.getValue() == null) {
        continue;
      }
      Entity entity = Bukkit.getEntity(entry.getKey());
      if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
        continue;
      }
      MobSpec spec = specs.get(entry.getValue().specId());
      if (spec == null) {
        detachModelBridge(living);
        continue;
      }
      MobState state = states.get(entry.getKey());
      MobPhaseSpec phase = currentPhase(state, spec);
      MobModelSpec model = phase != null && phase.modelSpec() != null ? phase.modelSpec() : spec.modelSpec();
      applyModelBridgeUpdate(living, model, spec.id());
      refreshed++;
    }
    if (modelBridgeDebug && logger != null) {
      logger.info("[Mobs] model bridge refresh on reload: refreshed=" + refreshed);
    }
  }

  private MobPhaseSpec currentPhase(MobState state, MobSpec spec) {
    if (state == null || spec == null || state.phaseId == null || state.phaseId.isBlank()) {
      return null;
    }
    for (MobPhaseSpec phase : spec.phases()) {
      if (phase != null && state.phaseId.equals(phase.id())) {
        return phase;
      }
    }
    return null;
  }

  public void setMaxActivePerTick(int maxActivePerTick) {
    this.maxActivePerTick = Math.max(0, maxActivePerTick);
  }

  public void setShopRegistry(ShopYamlRegistry shopRegistry) {
    this.shopRegistry = shopRegistry;
  }

  public void setPartyService(dev.patric.dungeonsreborn.party.PartyService partyService) {
    this.partyService = partyService;
  }

  public void setPartyShareRules(PartyShareMode xpShareMode, boolean xpRequireAssist,
      PartyLootShareMode lootShareMode, boolean lootRequireAssist, PartyAssistRules assistRules) {
    this.partyXpShareMode = xpShareMode == null ? PartyShareMode.NONE : xpShareMode;
    this.partyXpRequireAssist = xpRequireAssist;
    this.partyLootShareMode = lootShareMode == null ? PartyLootShareMode.NONE : lootShareMode;
    this.partyLootRequireAssist = lootRequireAssist;
    this.partyAssistRules = assistRules == null ? new PartyAssistRules(0.0, 0.0, 0.0) : assistRules;
  }

  public void setCustomXpService(CustomXpService customXpService) {
    this.customXpService = customXpService;
  }

  public void setProgressionService(ProgressionService progressionService) {
    this.progressionService = progressionService;
  }

  public void setLootPoolResolver(java.util.function.Function<String, MobLootSpec> lootPoolResolver) {
    this.lootPoolResolver = lootPoolResolver;
  }

  public void setMinionKillCredit(MinionKillCredit lootCredit, MinionKillCredit xpCredit) {
    this.minionLootCredit = lootCredit == null ? MinionKillCredit.OWNER : lootCredit;
    this.minionXpCredit = xpCredit == null ? MinionKillCredit.OWNER : xpCredit;
  }

  public void setAdvancementService(AdvancementService advancementService) {
    this.advancementService = advancementService;
  }

  public void setWorldAllowedPredicate(Predicate<World> worldAllowed) {
    this.worldAllowed = worldAllowed == null ? world -> true : worldAllowed;
  }

  public void configureXpGating(boolean enabled, String bypassPermission, int messageCooldownTicks) {
    this.xpGatingEnabled = enabled;
    this.xpGatingBypassPermission = bypassPermission == null ? "" : bypassPermission;
    int cooldown = Math.max(0, messageCooldownTicks);
    this.xpGatingMessageCooldownMs = cooldown == 0 ? 0L : cooldown * 50L;
  }

  public void register(MobSpec spec) {
    Objects.requireNonNull(spec, "spec");
    String id = spec.id();
    if (specs.containsKey(id)) {
      throw new IllegalArgumentException("Duplicate mob id: " + id);
    }
    specs.put(id, spec);
  }

  public boolean unregister(String id) {
    Objects.requireNonNull(id, "id");
    return specs.remove(id) != null;
  }

  public MobSpec get(String id) {
    Objects.requireNonNull(id, "id");
    return specs.get(id);
  }

  public String getPhaseId(org.bukkit.entity.Entity entity) {
    Objects.requireNonNull(entity, "entity");
    MobState state = states.get(entity.getUniqueId());
    return state == null ? null : state.phaseId;
  }

  public boolean has(String id) {
    Objects.requireNonNull(id, "id");
    return specs.containsKey(id);
  }

  public Set<String> ids() {
    return Collections.unmodifiableSet(specs.keySet());
  }

  public List<MobMetricsSnapshot> metrics() {
    Map<String, Integer> activeCounts = new java.util.HashMap<>();
    for (MobInstance instance : active.values()) {
      activeCounts.merge(instance.specId(), 1, (left, right) -> (left == null ? 0 : left) + (right == null ? 0 : right));
    }
    long elapsedMs = Math.max(1L, System.currentTimeMillis() - metricsStartMs);
    double elapsedMinutes = elapsedMs / 60000.0;
    List<MobMetricsSnapshot> snapshots = new ArrayList<>();
    for (String id : specs.keySet()) {
      long spawns = spawnCounts.getOrDefault(id, 0L);
      long deaths = deathCounts.getOrDefault(id, 0L);
      double perMinute = elapsedMinutes <= 0.0 ? 0.0 : spawns / elapsedMinutes;
      int activeCount = activeCounts.getOrDefault(id, 0);
      snapshots.add(new MobMetricsSnapshot(id, spawns, deaths, perMinute, activeCount));
    }
    return snapshots;
  }

  public LivingEntity findClosestOwnedMob(String mobId, UUID ownerId, Location origin, double radius) {
    if (mobId == null || origin == null) {
      return null;
    }
    double maxDistSq = radius <= 0.0 ? Double.MAX_VALUE : radius * radius;
    LivingEntity closest = null;
    double closestDist = Double.MAX_VALUE;
    for (var entry : active.entrySet()) {
      MobInstance instance = entry.getValue();
      if (!mobId.equals(instance.specId())) {
        continue;
      }
      if (ownerId != null && (instance.ownerId() == null || !ownerId.equals(instance.ownerId()))) {
        continue;
      }
      Entity entity = Bukkit.getEntity(entry.getKey());
      if (!(entity instanceof LivingEntity living)) {
        continue;
      }
      if (!living.isValid()) {
        continue;
      }
      if (!living.getWorld().equals(origin.getWorld())) {
        continue;
      }
      double dist = living.getLocation().distanceSquared(origin);
      if (dist > maxDistSq || dist >= closestDist) {
        continue;
      }
      closest = living;
      closestDist = dist;
    }
    return closest;
  }

  public MobDebugSnapshot debugSnapshot(LivingEntity entity) {
    if (entity == null) {
      return null;
    }
    String mobId = MobMarkers.getMobId(entity);
    if (mobId == null) {
      return null;
    }
    MobState state = states.get(entity.getUniqueId());
    String phase = state == null ? null : state.phaseId;
    String behavior = state == null || state.behaviorState == null ? null : state.behaviorState.name();
    String targetName = null;
    if (state != null && state.currentTarget != null) {
      Entity target = Bukkit.getEntity(state.currentTarget);
      if (target instanceof LivingEntity living) {
        if (living.customName() != null) {
          targetName = PlainTextComponentSerializer.plainText().serialize(living.customName());
        } else {
          targetName = living.getType().name().toLowerCase(Locale.ROOT);
        }
      }
    }
    long now = engine.tickNow();
    MobSpec spec = specs.get(mobId);
    MobPhaseSpec phaseSpec = currentPhase(state, spec);
    MobAiSpec ai = resolveEffectiveAi(spec, phaseSpec);
    String cooldownSummary = state == null ? null : formatCooldowns(state, now);
    String pathInfo = state == null ? null : formatPathInfo(state, entity, ai, now);
    long ageTicks = state == null ? 0L : Math.max(0L, now - state.lastStateChangeTick);
    return new MobDebugSnapshot(mobId, phase, behavior, targetName, cooldownSummary, pathInfo, ageTicks);
  }

  public LivingEntity spawn(String id, Location location) {
    return spawnInternal(id, location, null, true);
  }

  public LivingEntity spawn(String id, Location location, UUID ownerId) {
    return spawnInternal(id, location, ownerId, true);
  }

  private LivingEntity spawnInternal(String id, Location location, UUID ownerId, boolean allowComposite) {
    Objects.requireNonNull(location, "location");
    MobSpec spec = get(id);
    if (spec == null) {
      throw new IllegalArgumentException("Unknown mob id: " + id);
    }
    if (location.getWorld() == null) {
      throw new IllegalArgumentException("Location has no world");
    }
    Entity entity = location.getWorld().spawnEntity(location, spec.entityType());
    if (!(entity instanceof LivingEntity living)) {
      entity.remove();
      throw new IllegalArgumentException("Entity type is not a LivingEntity: " + spec.entityType());
    }
    MobVariantSpec variant = chooseVariant(spec);
    MobTraitSpec trait = chooseTrait(spec);
    try {
      applySpec(spec, living, ownerId, variant, trait);
    } catch (RuntimeException ex) {
      living.remove();
      throw ex;
    }
    recordSpawn(spec.id());
    logMobEvent("spawn", spec, living, ownerId, null, 0.0);
    active.put(living.getUniqueId(), new MobInstance(spec.id(), ownerId));
    MobState state = new MobState();
    state.home = living.getLocation().clone();
    state.lastPosition = state.home.clone();
    state.lastMoveTick = engine.tickNow();
    state.variantId = variant == null ? null : variant.id();
    state.traitId = trait == null ? null : trait.id();
    state.baseScale = readScale(living);
    states.put(living.getUniqueId(), state);
    MobContext ctx = new MobContext(spec, living, ownerId);
    playSpawnFx(spec, living);
    spec.onSpawn().accept(ctx);
    if (allowComposite) {
      spawnComposite(spec, living, ownerId);
    }
    return living;
  }

  private void applySpec(MobSpec spec, LivingEntity entity, UUID ownerId, MobVariantSpec variant, MobTraitSpec trait) {
    boolean hasVariantName = variant != null && (variant.name() != null || variant.namePrefix() != null || variant.nameSuffix() != null);
    boolean hasTraitName = trait != null && (trait.name() != null || trait.namePrefix() != null || trait.nameSuffix() != null);
    applyNameplate(spec, entity, variant, trait, spec.style(), hasVariantName, hasTraitName);
    MobMarkers.setMobId(entity, spec.id());
    MobMarkers.setOwner(entity, ownerId);
    MobMarkers.setVariant(entity, variant == null ? null : variant.id());
    MobMarkers.setTrait(entity, trait == null ? null : trait.id());
    MobModelSpec effectiveModel = spec.modelSpec();
    applyModelSpec(entity, effectiveModel);
    applyCollidable(entity, resolveCollidable(spec, variant, null));
    applyInvulnerable(entity, spec.invulnerable());
    entity.setSilent(spec.silent());

    var equipment = entity.getEquipment();
    if (equipment != null) {
      ItemStack mainHand = spec.mainHand();
      ItemStack offHand = spec.offHand();
      ItemStack head = spec.head();
      equipment.setItemInMainHand(mainHand);
      equipment.setItemInOffHand(offHand);
      equipment.setHelmet(head);
      equipment.setChestplate(spec.chest());
      equipment.setLeggings(spec.legs());
      equipment.setBoots(spec.feet());
      MobVisualSpec visual = resolveVisualSpec(spec.visualSpec(), effectiveModel, spec.id());
      if (!isFullModelReplacement(effectiveModel)) {
        applyVisualEquipment(equipment, visual, mainHand, offHand, head, spec.id());
      }
    }

    applyAttributes(entity, spec.attributes());
    if (variant != null) {
      applyVariant(entity, variant);
    }
    if (trait != null) {
      applyTrait(entity, trait);
    }
    syncHealthToMax(entity);
    applyScaleVariance(entity, spec.scaleVariance());
    applyResistances(entity, spec.resistances());
    if (trait != null && trait.resistances() != null && !trait.resistances().isEmpty()) {
      applyResistances(entity, trait.resistances());
    }
    applyModelBridgeAttach(entity, effectiveModel, spec.id());
  }

  private void syncHealthToMax(LivingEntity entity) {
    AttributeInstance inst = entity.getAttribute(Attribute.MAX_HEALTH);
    if (inst == null) {
      return;
    }
    double max = inst.getValue();
    if (!Double.isFinite(max) || max <= 0.0) {
      return;
    }
    double current = entity.getHealth();
    if (!Double.isFinite(current) || current != max) {
      entity.setHealth(max);
    }
  }

  private void spawnComposite(MobSpec spec, LivingEntity primary, UUID ownerId) {
    MobCompositeSpec composite = spec.composite();
    if (composite == null) {
      return;
    }
    boolean primaryIsMount = composite.role() == MobCompositeRole.PRIMARY_MOUNT;
    String secondaryId = primaryIsMount ? composite.riderMobId() : composite.mountMobId();
    LivingEntity secondary = spawnInternal(secondaryId, primary.getLocation(), ownerId, false);
    if (secondary == null) {
      return;
    }
    LivingEntity mount = primaryIsMount ? primary : secondary;
    LivingEntity rider = primaryIsMount ? secondary : primary;
    mount.addPassenger(rider);
    linkComposite(primary.getUniqueId(), secondary.getUniqueId(), composite.keepAliveTogether());
  }

  private void linkComposite(UUID firstId, UUID secondId, boolean keepAliveTogether) {
    MobState firstState = states.get(firstId);
    if (firstState != null) {
      firstState.compositePartner = secondId;
      firstState.compositeKeepAlive = keepAliveTogether;
    }
    MobState secondState = states.get(secondId);
    if (secondState != null) {
      secondState.compositePartner = firstId;
      secondState.compositeKeepAlive = keepAliveTogether;
    }
  }

  private void handleCompositeRemoval(MobState state) {
    if (state == null || state.compositePartner == null || !state.compositeKeepAlive) {
      return;
    }
    UUID partnerId = state.compositePartner;
    state.compositePartner = null;
    state.compositeKeepAlive = false;
    MobState partnerState = states.get(partnerId);
    if (partnerState != null) {
      partnerState.compositePartner = null;
      partnerState.compositeKeepAlive = false;
    }
    Entity partner = Bukkit.getEntity(partnerId);
    if (partner != null) {
      partner.remove();
    }
  }

  private void applyPhaseOverrides(MobSpec spec, LivingEntity entity, MobState state, MobPhaseSpec phase) {
    var equipment = entity.getEquipment();
    if (equipment != null) {
      ItemStack mainHand = phase != null && phase.mainHand() != null ? phase.mainHand() : spec.mainHand();
      ItemStack offHand = phase != null && phase.offHand() != null ? phase.offHand() : spec.offHand();
      ItemStack head = phase != null && phase.head() != null ? phase.head() : spec.head();
      ItemStack chest = phase != null && phase.chest() != null ? phase.chest() : spec.chest();
      ItemStack legs = phase != null && phase.legs() != null ? phase.legs() : spec.legs();
      ItemStack feet = phase != null && phase.feet() != null ? phase.feet() : spec.feet();
      equipment.setItemInMainHand(mainHand == null ? null : mainHand.clone());
      equipment.setItemInOffHand(offHand == null ? null : offHand.clone());
      equipment.setHelmet(head == null ? null : head.clone());
      equipment.setChestplate(chest == null ? null : chest.clone());
      equipment.setLeggings(legs == null ? null : legs.clone());
      equipment.setBoots(feet == null ? null : feet.clone());
      MobVisualSpec phaseVisual = phase != null && phase.visualSpec() != null ? phase.visualSpec() : spec.visualSpec();
      MobModelSpec modelForVisual = phase != null && phase.modelSpec() != null ? phase.modelSpec() : spec.modelSpec();
      MobVisualSpec visual = resolveVisualSpec(phaseVisual, modelForVisual, spec.id());
      if (!isFullModelReplacement(modelForVisual)) {
        applyVisualEquipment(equipment, visual, mainHand, offHand, head, spec.id());
      }
    }
    Double phaseScaleMultiplier = phase == null ? null : phase.scaleMultiplier();
    AttributeInstance scale = entity.getAttribute(Attribute.SCALE);
    if (scale != null) {
      double base = state != null ? state.baseScale : readScale(entity);
      double next = phaseScaleMultiplier == null ? base : base * phaseScaleMultiplier;
      scale.setBaseValue(Math.max(0.1, next));
    }
    MobVariantSpec variant = resolveVariant(spec, state == null ? null : state.variantId);
    MobTraitSpec trait = resolveTrait(spec, state == null ? null : state.traitId);
    MobStyleSpec style = phase != null && phase.style() != null ? phase.style() : spec.style();
    boolean hasVariantName = variant != null && (variant.name() != null || variant.namePrefix() != null || variant.nameSuffix() != null);
    boolean hasTraitName = trait != null && (trait.name() != null || trait.namePrefix() != null || trait.nameSuffix() != null);
    applyNameplate(spec, entity, variant, trait, style, hasVariantName, hasTraitName);
    MobModelSpec model = phase != null && phase.modelSpec() != null ? phase.modelSpec() : spec.modelSpec();
    applyModelSpec(entity, model);
    applyModelBridgeUpdate(entity, model, spec.id());
    applyCollidable(entity, resolveCollidable(spec, variant, phase));
  }

  private void applyVisualEquipment(org.bukkit.inventory.EntityEquipment equipment, MobVisualSpec visual,
      ItemStack explicitMainHand, ItemStack explicitOffHand, ItemStack explicitHead, String mobId) {
    if (equipment == null || visual == null || textureService == null) {
      return;
    }
    ItemStack visualItem = createVisualItem(visual);
    if (visualItem == null) {
      return;
    }
    switch (visual.slot()) {
      case HEAD -> {
        if (explicitHead != null) {
          logVisualConflict(mobId, visual, "head");
          return;
        }
        equipment.setHelmet(visualItem);
        equipment.setHelmetDropChance(0.0f);
      }
      case MAIN_HAND -> {
        if (explicitMainHand != null) {
          logVisualConflict(mobId, visual, "mainHand");
          return;
        }
        equipment.setItemInMainHand(visualItem);
        equipment.setItemInMainHandDropChance(0.0f);
      }
      case OFF_HAND -> {
        if (explicitOffHand != null) {
          logVisualConflict(mobId, visual, "offHand");
          return;
        }
        equipment.setItemInOffHand(visualItem);
        equipment.setItemInOffHandDropChance(0.0f);
      }
    }
  }

  private ItemStack createVisualItem(MobVisualSpec visual) {
    if (visual == null || visual.material() == null || visual.modelKey() == null || visual.modelKey().isBlank()) {
      return null;
    }
    try {
      ItemStack item = new ItemStack(visual.material());
      item.setData(DataComponentTypes.ITEM_MODEL, Key.key(visual.modelKey()));
      if (textureService.config().compatWriteCustomModelData()) {
        int cmd = textureService.assignCompatCustomModelData(visual.modelKey());
        if (cmd > 0) {
          var meta = item.getItemMeta();
          if (meta != null) {
            meta.setCustomModelData(cmd);
            item.setItemMeta(meta);
          }
        }
      }
      return item;
    } catch (Exception ex) {
      if (logger != null) {
        logger.warn("[Mobs] visual item build failed for model " + visual.modelKey() + ": " + ex.getMessage());
      }
      return null;
    }
  }

  private void logVisualConflict(String mobId, MobVisualSpec visual, String slot) {
    if (logger != null) {
      logger.warn("[Mobs] visual skipped due to explicit equipment slot: mob=" + mobId
          + " slot=" + slot + " texture=" + visual.texturePath());
    }
  }

  private double readScale(LivingEntity entity) {
    AttributeInstance inst = entity.getAttribute(Attribute.SCALE);
    if (inst == null) {
      return 1.0;
    }
    double base = inst.getBaseValue();
    if (!Double.isFinite(base) || base <= 0.0) {
      return 1.0;
    }
    return base;
  }

  @EventHandler
  public void onDeath(EntityDeathEvent event) {
    LivingEntity entity = event.getEntity();
    String id = MobMarkers.getMobId(entity);
    if (id == null) {
      return;
    }
    MobSpec spec = specs.get(id);
    if (spec == null) {
      return;
    }
    playModelAnimation(entity, "death");
    if (aiPlannerService != null) {
      aiPlannerService.cancelEntity(entity.getUniqueId());
    }
    MobInstance inst = active.remove(entity.getUniqueId());
    MobState state = states.remove(entity.getUniqueId());
    UUID ownerId = inst == null ? MobMarkers.getOwner(entity) : inst.ownerId();
    recordDeath(spec.id());
    logMobEvent("death", spec, entity, ownerId, entity.getKiller(), 0.0);
    MobContext ctx = new MobContext(spec, entity, ownerId);
    if (state != null) {
      handleCompositeRemoval(state);
      removeBossBar(state);
    }
    if (state != null) {
      LivingEntity killer = resolveTarget(state.lastAttacker);
      if (killer != null) {
        String killerMobId = MobMarkers.getMobId(killer);
        if (killerMobId != null) {
          MobSpec killerSpec = specs.get(killerMobId);
          if (killerSpec != null && killerSpec.events() != null) {
            MobState killerState = states.get(killer.getUniqueId());
            triggerEventAbility(killerSpec, killer, killerState, MobMarkers.getOwner(killer),
                killerSpec.events().onKill(), entity);
          }
        }
      }
    }
    playDeathFx(spec, entity);
    KillCredit credit = resolveKillCredit(entity, state);
    if (!credit.skipLoot()) {
      applyLoot(spec, entity, event, credit.lootKiller(), credit.xpKiller());
    }
    applyManaDrops(spec, entity, credit.xpKiller());
    if (advancementService != null && credit.xpKiller() != null) {
      advancementService.recordMobKill(credit.xpKiller(), spec);
    }
    broadcastBossKill(spec, entity);
    spec.onDeath().accept(ctx);
    spec.onRemove().accept(ctx, MobRemovalReason.DEATH);
    detachModelBridge(entity);
  }

  private enum MinionKillCredit {
    OWNER,
    NONE
  }

  private record KillCredit(Player lootKiller, Player xpKiller, boolean skipLoot) {
  }

  private KillCredit resolveKillCredit(LivingEntity entity, MobState state) {
    LivingEntity killer = entity.getKiller();
    if (killer == null && state != null) {
      killer = resolveTarget(state.lastAttacker);
    }
    boolean minionKill = killer != null && MobMarkers.getMinionId(killer) != null;
    if (!minionKill) {
      Player player = killer instanceof Player ? (Player) killer : null;
      return new KillCredit(player, player, false);
    }
    UUID ownerId = MobMarkers.getOwner(killer);
    Player owner = ownerId == null ? null : Bukkit.getPlayer(ownerId);
    Player lootKiller = minionLootCredit == MinionKillCredit.OWNER ? owner : null;
    Player xpKiller = minionXpCredit == MinionKillCredit.OWNER ? owner : null;
    boolean skipLoot = minionLootCredit == MinionKillCredit.NONE;
    return new KillCredit(lootKiller, xpKiller, skipLoot);
  }

  @EventHandler
  public void onRemove(EntityRemoveFromWorldEvent event) {
    Entity entity = event.getEntity();
    if (entity instanceof LivingEntity living && MobMarkers.getMobId(entity) != null) {
      detachModelBridge(living);
    }
    UUID uuid = entity.getUniqueId();
    if (aiPlannerService != null) {
      aiPlannerService.cancelEntity(uuid);
    }
    MobInstance inst = active.remove(uuid);
    MobState state = states.remove(uuid);
    if (inst == null) {
      return;
    }
    MobSpec spec = specs.get(inst.specId());
    if (spec == null || !(entity instanceof LivingEntity living)) {
      return;
    }
    engine.clearResistances(uuid);
    engine.clearReflect(uuid);
    logMobEvent("despawn", spec, living, inst.ownerId(), null, 0.0);
    MobContext ctx = new MobContext(spec, living, inst.ownerId());
    if (state != null) {
      handleCompositeRemoval(state);
      removeBossBar(state);
    }
    if (spec.events() != null) {
      triggerEventAbility(spec, living, state, inst.ownerId(), spec.events().onDespawn(), null);
    }
    spec.onRemove().accept(ctx, MobRemovalReason.REMOVED);
  }

  private void broadcastBossKill(MobSpec spec, LivingEntity entity) {
    if (spec == null || entity == null) {
      return;
    }
    MobBroadcastSpec broadcast = spec.bossBroadcast();
    if (broadcast == null || !broadcast.enabled()) {
      return;
    }
    Player killer = entity.getKiller();
    if (killer == null) {
      return;
    }
    String template = broadcast.message();
    if (template == null || template.isBlank()) {
      return;
    }
    String mobName = resolveMobName(spec, entity);
    String rendered = template
        .replace("{player}", killer.getName())
        .replace("{mob}", mobName)
        .replace("{mob_id}", spec.id());
    var message = MobText.parse(rendered);
    for (Player player : Bukkit.getOnlinePlayers()) {
      player.sendMessage(message);
    }
  }

  private String resolveMobName(MobSpec spec, LivingEntity entity) {
    if (entity.customName() != null) {
      return PlainTextComponentSerializer.plainText().serialize(entity.customName());
    }
    if (spec != null && spec.displayName() != null) {
      return PlainTextComponentSerializer.plainText().serialize(spec.displayName());
    }
    return entity.getType().name().toLowerCase(Locale.ROOT);
  }

  private void recordSpawn(String mobId) {
    if (mobId == null) {
      return;
    }
    spawnCounts.merge(mobId, 1L, (left, right) -> (left == null ? 0L : left) + (right == null ? 0L : right));
  }

  private void recordDeath(String mobId) {
    if (mobId == null) {
      return;
    }
    deathCounts.merge(mobId, 1L, (left, right) -> (left == null ? 0L : left) + (right == null ? 0L : right));
  }

  private void logMobEvent(String event, MobSpec spec, LivingEntity entity, UUID ownerId, LivingEntity other,
      double amount) {
    if (logger == null || spec == null || entity == null || event == null) {
      return;
    }
    String otherName = other == null ? "none" : other.getType().name().toLowerCase(Locale.ROOT);
    String owner = ownerId == null ? "none" : ownerId.toString();
    logger.debug("event=mob_" + event
        + " mob=" + spec.id()
        + " entity=" + entity.getUniqueId()
        + " type=" + entity.getType().name().toLowerCase(Locale.ROOT)
        + " world=" + entity.getWorld().getName()
        + " x=" + String.format(Locale.ROOT, "%.2f", entity.getLocation().getX())
        + " y=" + String.format(Locale.ROOT, "%.2f", entity.getLocation().getY())
        + " z=" + String.format(Locale.ROOT, "%.2f", entity.getLocation().getZ())
        + " owner=" + owner
        + " other=" + otherName
        + " amount=" + String.format(Locale.ROOT, "%.2f", amount));
  }

  private String formatCooldowns(MobState state, long now) {
    if (state == null || state.nextAttackCooldown.isEmpty()) {
      return null;
    }
    List<String> entries = new ArrayList<>();
    for (Map.Entry<String, Long> entry : state.nextAttackCooldown.entrySet()) {
      long remaining = entry.getValue() - now;
      if (remaining <= 0L) {
        continue;
      }
      entries.add(entry.getKey() + ":" + remaining);
      if (entries.size() >= 3) {
        break;
      }
    }
    if (entries.isEmpty()) {
      return null;
    }
    return String.join(",", entries);
  }

  private String formatPathInfo(MobState state, LivingEntity entity, MobAiSpec ai, long now) {
    if (state == null || entity == null) {
      return null;
    }
    if (state.home == null) {
      return "home=unknown";
    }
    double distance = entity.getLocation().distance(state.home);
    String base = "homeDist=" + String.format(Locale.ROOT, "%.1f", distance);
    if (aiDefaultEngine == MobAiEngineMode.V3) {
      base = base + " asyncQ=" + (aiPlannerService == null ? 0 : aiPlannerService.queueSize())
          + " tier=" + aiCurrentDegradeTier.name();
    }
    String mode = ai != null && ai.isFullOverride() ? "FULL_OVERRIDE" : "DEFAULT";
    String authority = ai == null ? "VANILLA" : ai.combatAuthority().name();
    String runtimeModel = ai == null ? "LEGACY_V4" : ai.runtimeModel().name();
    String selector = state.lastSelectorId == null ? "-" : state.lastSelectorId;
    String intent = state.lastIntentType == null ? "-" : state.lastIntentType.name();
    String source = state.lastResolvedTargetSource == null ? "-" : state.lastResolvedTargetSource.name();
    String driver = state.lastNavigationDriver == null ? "-" : state.lastNavigationDriver;
    String castCooldown = formatSelectorCooldowns(state, now);
    base = base + " mode=" + mode
        + " auth=" + authority
        + " model=" + runtimeModel
        + " sel=" + selector
        + " intent=" + intent
        + " src=" + source
        + " nav=" + driver;
    if (castCooldown != null) {
      base = base + " castCd=" + castCooldown;
    }
    if (state.patrolIndex > 0) {
      return base + " patrol=" + state.patrolIndex;
    }
    return base;
  }

  private String formatSelectorCooldowns(MobState state, long now) {
    if (state == null || state.nextSelectorCastTick.isEmpty()) {
      return null;
    }
    List<String> entries = new ArrayList<>();
    for (Map.Entry<String, Long> entry : state.nextSelectorCastTick.entrySet()) {
      long remaining = entry.getValue() - now;
      if (remaining <= 0L) {
        continue;
      }
      entries.add(entry.getKey() + ":" + remaining);
      if (entries.size() >= 2) {
        break;
      }
    }
    if (entries.isEmpty()) {
      return null;
    }
    return String.join(",", entries);
  }

  @EventHandler
  public void onDamage(EntityDamageByEntityEvent event) {
    Entity entity = event.getEntity();
    String id = MobMarkers.getMobId(entity);
    if (id == null) {
      return;
    }
    if (!(entity instanceof LivingEntity)) {
      return;
    }
    LivingEntity attacker = resolveDamager(event.getDamager());
    if (attacker == null) {
      return;
    }
    if (attacker instanceof Player player) {
      MobSpec spec = resolveSpecFromEntity(entity);
      if (spec != null && shouldBlockDamage(player, spec)) {
        event.setCancelled(true);
        return;
      }
    }
    MobSpec spec = specs.get(id);
    MobState state = states.get(entity.getUniqueId());
    if (state != null) {
      state.lastAttacker = attacker.getUniqueId();
      state.targetSourceMemory.put(MobAiTargetSourceType.LAST_ATTACKER, attacker.getUniqueId());
      state.targetSourceMemoryExpiry.put(MobAiTargetSourceType.LAST_ATTACKER, engine.tickNow() + 80L);
      state.movementSuppressedUntilTick = Math.max(state.movementSuppressedUntilTick, engine.tickNow() + 6L);
      if (event.getFinalDamage() > 0.0) {
        state.threat.merge(attacker.getUniqueId(), event.getFinalDamage(),
            (left, right) -> (left == null ? 0.0 : left) + (right == null ? 0.0 : right));
      }
      if (spec != null) {
        MobAiSpec ai = spec.aiSpec();
        if (ai != null
            && ai.isFullOverride()
            && isValidTarget((LivingEntity) entity, attacker, ai.aggroRadius())
            && !isFriendlyTarget((LivingEntity) entity, attacker)) {
          setTargetTracked((LivingEntity) entity, state, attacker, engine.tickNow());
        }
      }
    }
    if (spec != null && spec.events() != null) {
      triggerEventAbility(spec, (LivingEntity) entity, state, MobMarkers.getOwner(entity),
          spec.events().onHurt(), attacker);
    }
    playModelAnimation((LivingEntity) entity, "hurt");
    if (spec != null) {
      logMobEvent("hurt", spec, (LivingEntity) entity, MobMarkers.getOwner(entity), attacker, event.getFinalDamage());
      applyCombatMitigation(spec, (LivingEntity) entity, state, event);
      applyCombatCleanse(spec, (LivingEntity) entity);
    }
  }

  @EventHandler
  public void onMobHit(EntityDamageByEntityEvent event) {
    LivingEntity attacker = resolveDamager(event.getDamager());
    if (attacker == null) {
      return;
    }
    String mobId = MobMarkers.getMobId(attacker);
    if (mobId == null) {
      return;
    }
    MobSpec spec = specs.get(mobId);
    if (spec == null || spec.events() == null) {
      return;
    }
    MobState state = states.get(attacker.getUniqueId());
    LivingEntity target = event.getEntity() instanceof LivingEntity living ? living : null;
    logMobEvent("attack", spec, attacker, MobMarkers.getOwner(attacker), target, event.getFinalDamage());
    playModelAnimation(attacker, "attack");
    triggerEventAbility(spec, attacker, state, MobMarkers.getOwner(attacker), spec.events().onHit(), target);
  }

  @EventHandler(ignoreCancelled = true)
  public void onDamagePlayer(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Player player)) {
      return;
    }
    LivingEntity attacker = resolveDamager(event.getDamager());
    if (attacker == null) {
      return;
    }
    MobSpec spec = resolveSpecFromEntity(attacker);
    if (spec == null) {
      return;
    }
    MobManaDrainSpec drain = spec.manaDrain();
    if (drain == null || drain.isEmpty()) {
      return;
    }
    if (drain.chance() < 1.0 && rng.nextDouble() > drain.chance()) {
      return;
    }
    long now = engine.tickNow();
    MobState state = states.get(attacker.getUniqueId());
    if (state != null && now < state.nextManaDrainTick) {
      return;
    }
    if (state != null) {
      state.nextManaDrainTick = now + drain.cooldownTicks();
    }
    dev.patric.dungeonsreborn.effects.mana.ManaProvider provider = engine.manaProvider();
    if (provider == null) {
      return;
    }
    double amount = drain.amount();
    if (!(amount > 0.0)) {
      return;
    }
    provider.tryConsume(player, drain.resourceId(), amount);
  }

  @EventHandler(ignoreCancelled = true)
  public void onExplode(EntityExplodeEvent event) {
    Entity entity = event.getEntity();
    if (shouldPreventBlockDamage(entity)) {
      event.blockList().clear();
      event.setYield(0.0f);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onChangeBlock(EntityChangeBlockEvent event) {
    if (shouldPreventBlockDamage(event.getEntity())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onHangingBreak(HangingBreakByEntityEvent event) {
    if (shouldPreventBlockDamage(event.getRemover())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onEntityDamageNonBlock(EntityDamageByEntityEvent event) {
    if (!isNonBlockGriefTarget(event.getEntity())) {
      return;
    }
    if (shouldPreventBlockDamage(event.getDamager())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onBlockIgnite(BlockIgniteEvent event) {
    Entity igniter = event.getIgnitingEntity();
    if (shouldPreventBlockDamage(igniter)) {
      event.setCancelled(true);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onBlockForm(EntityBlockFormEvent event) {
    if (shouldPreventBlockDamage(event.getEntity())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onBreakDoor(EntityBreakDoorEvent event) {
    if (shouldPreventBlockDamage(event.getEntity())) {
      event.setCancelled(true);
    }
  }

  private LivingEntity resolveDamager(Entity damager) {
    if (damager instanceof LivingEntity living) {
      return living;
    }
    if (damager instanceof Projectile projectile) {
      Object shooter = projectile.getShooter();
      if (shooter instanceof LivingEntity living) {
        return living;
      }
    }
    return null;
  }

  private MobSpec resolveSpecFromEntity(Entity entity) {
    if (entity == null) {
      return null;
    }
    String id = MobMarkers.getMobId(entity);
    if (id != null) {
      return specs.get(id);
    }
    if (entity instanceof Projectile projectile) {
      Object shooter = projectile.getShooter();
      if (shooter instanceof Entity shooterEntity) {
        id = MobMarkers.getMobId(shooterEntity);
        if (id != null) {
          return specs.get(id);
        }
      }
    }
    return null;
  }

  private MobSpawnSpec resolveSpawnSpecFromEntity(Entity entity) {
    if (entity == null || spawnManager == null) {
      return null;
    }
    MobSpawnSpec spec = spawnManager.spawnSpecForEntity(entity);
    if (spec != null) {
      return spec;
    }
    if (entity instanceof Projectile projectile) {
      Object shooter = projectile.getShooter();
      if (shooter instanceof Entity shooterEntity) {
        return spawnManager.spawnSpecForEntity(shooterEntity);
      }
    }
    return null;
  }

  private boolean shouldBlockDamage(Player player, MobSpec spec) {
    if (!xpGatingEnabled || spec == null) {
      return false;
    }
    if (!worldAllowed.test(player.getWorld())) {
      return false;
    }
    String mobId = spec.id();
    if (mobId.startsWith("undead_t1_") || mobId.startsWith("hostile_t1_") || mobId.startsWith("corrupted_t1_")) {
      return false;
    }
    MobAiSpec ai = spec.aiSpec();
    if (ai != null && ai.aggroTargetMode() == MobTargetMode.NEAREST_HOSTILE) {
      return false;
    }
    int minLevel = spec.minXpLevel();
    if (minLevel <= 0) {
      return false;
    }
    if (!xpGatingBypassPermission.isBlank() && player.hasPermission(xpGatingBypassPermission)) {
      return false;
    }
    int playerLevel = player.getLevel();
    if (customXpService != null) {
      CustomXpProfile profile = customXpService.getOrCreate(player.getUniqueId());
      if (profile != null) {
        playerLevel = profile.level();
      }
    }
    if (playerLevel >= minLevel) {
      return false;
    }
    maybeWarnXpGate(player, minLevel);
    return true;
  }

  private void maybeWarnXpGate(Player player, int minLevel) {
    long now = System.currentTimeMillis();
    long nextAt = nextXpGateMessageAt.getOrDefault(player.getUniqueId(), 0L);
    if (now < nextAt) {
      return;
    }
    if (xpGatingMessageCooldownMs > 0L) {
      nextXpGateMessageAt.put(player.getUniqueId(), now + xpGatingMessageCooldownMs);
    }
    player.sendMessage(Locales.component(player, "messages.mobs.damage.xpLocked",
        Locales.placeholders("level", minLevel)));
  }

  private boolean shouldPreventBlockDamage(Entity entity) {
    MobSpec spec = resolveSpecFromEntity(entity);
    if (spec != null && !spec.allowBlockDamage()) {
      return true;
    }
    MobSpawnSpec spawnSpec = resolveSpawnSpecFromEntity(entity);
    return spawnSpec != null && !spawnSpec.allowBlockDamage();
  }

  private boolean isNonBlockGriefTarget(Entity entity) {
    if (entity == null) {
      return false;
    }
    if (entity instanceof Hanging) {
      return true;
    }
    if (entity instanceof ArmorStand) {
      return true;
    }
    if (entity instanceof Vehicle) {
      return true;
    }
    return entity instanceof EnderCrystal;
  }

  private void tick() {
    aiStepsThisTick = 0;
    aiPathMutationsThisTick = 0;
    aiSelectorEvaluationsThisTick = 0;
    aiOverrideCastsThisTick = 0;
    aiActiveFullOverrideThisTick = 0;
    double overloadRatio = 1.0;
    if (aiPlannerService != null && aiAsyncQueueCapacity > 0) {
      overloadRatio = Math.max(1.0, (double) aiPlannerService.queueSize() / (double) aiAsyncQueueCapacity);
    }
    aiCurrentDegradeTier = aiGuardrailController.tierForOverload(overloadRatio);
    if (active.isEmpty()) {
      aiStepsLastTick = 0;
      aiPathMutationsLastTick = 0;
      aiSelectorEvaluationsLastTick = 0;
      aiOverrideCastsLastTick = 0;
      aiActiveFullOverrideLastTick = 0;
      return;
    }
    long now = engine.tickNow();
    List<UUID> ids = new ArrayList<>(active.keySet());
    int limit = maxActivePerTick > 0 ? Math.min(maxActivePerTick, ids.size()) : ids.size();
    List<UUID> toRemove = null;
    int processed = 0;
    for (UUID entityId : ids) {
      if (processed++ >= limit) {
        break;
      }
      MobInstance inst = active.get(entityId);
      if (inst == null) {
        continue;
      }
      MobSpec spec = specs.get(inst.specId());
      Entity entity = org.bukkit.Bukkit.getEntity(entityId);
      if (!(entity instanceof LivingEntity living) || !entity.isValid() || living.isDead()) {
        if (entity instanceof LivingEntity stale) {
          detachModelBridge(stale);
        }
        if (toRemove == null) {
          toRemove = new ArrayList<>();
        }
        toRemove.add(entityId);
        continue;
      }
      if (spec == null) {
        continue;
      }
      MobState state = states.computeIfAbsent(entityId, k -> new MobState());
      if (state.home == null) {
        state.home = living.getLocation().clone();
      }
      if (!tickSummon(spec, living, state, inst.ownerId())) {
        continue;
      }
      MobPhaseSpec phase = resolvePhase(spec, living, state, now);
      tickAi(spec, phase, living, state, inst.ownerId(), now);
      tickEventHooks(spec, living, state, inst.ownerId(), now);
      if (spec.bossBar() != null || (spec.style() != null && spec.style().bossBar() != null)
          || (phase != null && phase.style() != null && phase.style().bossBar() != null)) {
        updateBossBar(spec, phase, living, state, inst.ownerId(), now);
      }
      tickPassives(spec, phase, living, state, inst.ownerId(), now);
      MobAttackSpec mainAttack = phase != null && phase.mainAttack() != null ? phase.mainAttack() : spec.mainAttack();
      MobAttackSpec secondaryAttack = phase != null && phase.secondaryAttack() != null ? phase.secondaryAttack() : spec.secondaryAttack();
      MobAiSpec effectiveAi = resolveEffectiveAi(spec, phase);
      boolean syncVanillaAttackTarget = !(effectiveAi != null && effectiveAi.isFullOverride() && aiFullOverrideEnabled);
      tickAttacks(spec, inst, mainAttack, secondaryAttack, living, state, now, syncVanillaAttackTarget);
      tickModelAnimations(living, state, now);
    }
    if (toRemove != null) {
      for (UUID id : toRemove) {
        if (aiPlannerService != null) {
          aiPlannerService.cancelEntity(id);
        }
        active.remove(id);
        states.remove(id);
      }
    }
    aiStepsLastTick = aiStepsThisTick;
    aiPathMutationsLastTick = aiPathMutationsThisTick;
    aiSelectorEvaluationsLastTick = aiSelectorEvaluationsThisTick;
    aiOverrideCastsLastTick = aiOverrideCastsThisTick;
    aiActiveFullOverrideLastTick = aiActiveFullOverrideThisTick;
  }

  private void tickModelAnimations(LivingEntity entity, MobState state, long now) {
    if (entity == null || state == null || modelBridge == null || !modelBridgeEnabled || !modelBridge.available()) {
      return;
    }
    if (now < state.nextModelSyncTick) {
      return;
    }
    state.nextModelSyncTick = now + modelSyncPeriodTicks;
    Vector velocity = entity.getVelocity();
    boolean moving = velocity != null && velocity.lengthSquared() > MODEL_MOVE_THRESHOLD_SQ;
    if (moving == state.modelMoving) {
      return;
    }
    boolean previous = state.modelMoving;
    state.modelMoving = moving;
    if (modelBridgeDebug && logger != null) {
      logger.debug("[Mobs] state-transition entity=" + entity.getUniqueId()
          + " from=" + (previous ? "walk" : "idle")
          + " to=" + (moving ? "walk" : "idle"));
    }
    playModelAnimation(entity, moving ? "walk" : "idle");
  }

  private void tickPassives(MobSpec spec, MobPhaseSpec phase, LivingEntity entity, MobState state, UUID ownerId, long now) {
    if (isMinion(entity) && minionManager != null && minionManager.disableBasePassives(entity.getUniqueId())) {
      return;
    }
    List<MobPassiveSpec> passives = phase != null && phase.passives() != null ? phase.passives() : spec.passives();
    if (passives.isEmpty()) {
      return;
    }
    for (MobPassiveSpec passive : passives) {
      long next = state.nextPassiveTick.getOrDefault(passive.abilityId(), 0L);
      if (now < next) {
        continue;
      }
      state.nextPassiveTick.put(passive.abilityId(), now + passive.periodTicks());
      tryCast(entity, passive.abilityId(), spec, null, null, ownerId);
    }
  }

  private void tickAi(MobSpec spec, MobPhaseSpec phase, LivingEntity entity, MobState state, UUID ownerId, long now) {
    if (minionManager != null && minionManager.disableBaseAi(entity.getUniqueId())) {
      if (aiFullOverrideHardDisableVanilla) {
        setVanillaAiState(entity, true);
      }
      return;
    }
    if (!aiEnabled) {
      if (aiFullOverrideHardDisableVanilla) {
        setVanillaAiState(entity, true);
      }
      return;
    }
    MobAiSpec ai = resolveEffectiveAi(spec, phase);
    if (ai == null || !ai.enabled()) {
      if (aiFullOverrideHardDisableVanilla) {
        setVanillaAiState(entity, true);
      }
      return;
    }
    MobAiEngineMode mode = ai.engineMode() == null ? aiDefaultEngine : ai.engineMode();
    boolean hardDisableVanilla = shouldHardDisableVanillaAi(spec, ai);
    if (ai.isFullOverride()) {
      if (!aiFullOverrideEnabled) {
        if (aiFullOverrideHardDisableVanilla) {
          setVanillaAiState(entity, true);
        }
      } else {
        aiActiveFullOverrideThisTick++;
        if (hardDisableVanilla) {
          setVanillaAiState(entity, false);
        } else {
          setVanillaAiState(entity, true);
        }
        if (mode == MobAiEngineMode.V3) {
          tickAiFullOverride(spec, phase, entity, state, ai, ownerId, now, true);
          return;
        }
        if (mode == MobAiEngineMode.V2) {
          tickAiFullOverride(spec, phase, entity, state, ai, ownerId, now, false);
          return;
        }
        if (spec != null && aiLegacyOverrideWarned.add(spec.id())) {
          if (logger != null) {
            logger.warn("[Mobs] FULL_OVERRIDE requested with LEGACY engine for mob " + spec.id()
                + "; falling back to legacy AI.");
          }
        }
      }
    } else if (aiFullOverrideHardDisableVanilla) {
      setVanillaAiState(entity, true);
    }
    if (mode == MobAiEngineMode.V3) {
      tickAiV3(spec, phase, entity, state, ai, ownerId, now);
      return;
    }
    if (mode == MobAiEngineMode.V2) {
      tickAiV2(spec, phase, entity, state, ai, ownerId, now);
      return;
    }
    tickAiLegacy(spec, phase, entity, state, ai, ownerId, now);
  }

  private boolean shouldHardDisableVanillaAi(MobSpec spec, MobAiSpec ai) {
    if (!aiFullOverrideHardDisableVanilla || ai == null || !ai.isFullOverride()) {
      return false;
    }
    MobAiRuntimeModel runtimeModel = resolveRuntimeModel(spec, ai);
    // Passive fauna in natural model relies on vanilla awareness for stable idle/flee movement.
    if (runtimeModel == MobAiRuntimeModel.NATURAL_V1 && ai.profile() == MobAiProfile.PASSIVE) {
      return false;
    }
    return true;
  }

  private MobAiSpec resolveEffectiveAi(MobSpec spec, MobPhaseSpec phase) {
    if (phase != null && phase.aiSpec() != null) {
      return phase.aiSpec();
    }
    return spec == null ? null : spec.aiSpec();
  }

  private void tickAiFullOverride(
      MobSpec spec,
      MobPhaseSpec phase,
      LivingEntity entity,
      MobState state,
      MobAiSpec ai,
      UUID ownerId,
      long now,
      boolean asyncCapable) {
    if (!consumeAiStepBudget()) {
      aiFallbackTicks++;
      return;
    }
    if (state.home == null) {
      state.home = entity.getLocation().clone();
    }
    applyLocomotion(entity, ai);
    if (applyTerrainAvoidanceV2(entity, state, ai)) {
      return;
    }
    if (ai.openDoors()) {
      tryOpenDoorAhead(entity);
    }
    if (!applyLeash(state, entity, ai, false)) {
      clearTargetTracked(entity, state);
      updateBehaviorStateWithHooks(spec, entity, state, ai, null, ownerId, now);
      return;
    }
    MobAiRuntimeModel runtimeModel = resolveRuntimeModel(spec, ai);

    LivingEntity target = resolveTarget(state.currentTarget);
    if (target != null && !isValidTarget(entity, target, ai.aggroRadius())) {
      clearTargetTracked(entity, state);
      target = null;
    }
    LivingEntity desired = runtimeModel == MobAiRuntimeModel.NATURAL_V1
        ? selectAggroTargetNatural(spec, entity, state, ai, now)
        : selectAggroTargetV2(entity, state, ai);
    if (desired != null && (target == null || !desired.getUniqueId().equals(target.getUniqueId()))) {
      long switchCooldown = Math.max(ai.targetSwitchCooldownTicks(), aiRetargetMinIntervalTicks);
      if (now >= state.nextRetargetTick && (switchCooldown <= 0L || now - state.lastTargetSwitchTick >= switchCooldown)) {
        setTargetTracked(entity, state, desired, now);
        state.nextRetargetTick = now + Math.max(1L, aiRetargetMinIntervalTicks);
        target = desired;
      }
    }
    if (runtimeModel != MobAiRuntimeModel.NATURAL_V1 && target == null && ai.profile() == MobAiProfile.PASSIVE) {
      clearTargetTracked(entity, state);
    }

    target = resolveTarget(state.currentTarget);
    ensureAerialAggroState(entity, target);
    updateBehaviorStateWithHooks(spec, entity, state, ai, target, ownerId, now);

    if (aiCurrentDegradeTier == MobAiGuardrailController.DegradeTier.LIGHTWEIGHT_FALLBACK) {
      if (target != null) {
        if (runtimeModel == MobAiRuntimeModel.NATURAL_V1) {
          navigateTowardNatural(spec, entity, target.getLocation(), Math.max(0.1, ai.chaseSpeed()), ai, state);
        } else {
          navigateToward(entity, target.getLocation(), Math.max(0.1, ai.chaseSpeed()), ai);
        }
        state.lastIntentType = MobAiIntentType.CHASE;
      } else {
        stopNavigation(entity);
        state.lastIntentType = MobAiIntentType.HOLD_POSITION;
      }
      state.lastSelectorId = "degrade_lightweight";
      return;
    }
    if (aiCurrentDegradeTier == MobAiGuardrailController.DegradeTier.SLOW_RECALC) {
      if (now < state.nextPathRecalcTick) {
        return;
      }
      state.nextPathRecalcTick = now + 2L;
    }

    MobAiSelectorSpec selector = selectOverrideSelector(ai, entity, state, target, now);
    if (selector != null) {
      state.lastSelectorId = selector.id();
      state.lastIntentType = selector.intent().type();
      executeOverrideIntent(spec, phase, entity, state, ai, ownerId, now, target, selector, runtimeModel);
      if (ai.controller() != null) {
        ai.controller().tick(new ContextImpl(spec, entity, state, ownerId, now));
      }
      return;
    }

    if (asyncCapable && aiAsyncEnabled && aiPlannerService != null) {
      MobAiV3Spec resolved = MobAiV3Resolver.resolve(ai);
      MobAiSnapshot snapshot = snapshotForV3(spec, phase, entity, state, ownerId, now, resolved);
      if (aiPlannerService.queueSize() < aiAsyncMaxJobsPerTick) {
        aiPlannerService.submit(snapshot);
      } else {
        aiFallbackTicks++;
      }
      MobAiPlan plan = aiPlannerService.poll(entity.getUniqueId());
      if (plan != null && now - plan.tick() <= aiAsyncPlanTtlTicks && plan.tick() == now) {
        applyV3Plan(spec, phase, entity, state, ai, ownerId, now, plan);
        state.lastSelectorId = "async:" + (plan.debugSelector() == null ? "plan" : plan.debugSelector());
        state.lastIntentType = mapPlanIntent(plan.intent());
        return;
      }
      if (plan != null) {
        aiStalePlanDiscards++;
      }
    }

    if (target != null) {
      state.wanderTarget = null;
      if (runtimeModel == MobAiRuntimeModel.NATURAL_V1) {
        navigateTowardNatural(spec, entity, target.getLocation(), Math.max(0.1, ai.chaseSpeed()), ai, state);
      } else {
        navigateToward(entity, target.getLocation(), Math.max(0.1, ai.chaseSpeed()), ai);
      }
      state.lastSelectorId = null;
      state.lastIntentType = MobAiIntentType.CHASE;
    } else if (ai.idleWanderRadius() > 0.0 && now >= state.nextWanderTick) {
      state.nextWanderTick = now + Math.max(1L, ai.idleWanderIntervalTicks());
      state.wanderTarget = randomHomeOffset(state.home, ai.idleWanderRadius());
      if (runtimeModel == MobAiRuntimeModel.NATURAL_V1) {
        navigateTowardNatural(spec, entity, state.wanderTarget, Math.max(0.08, ai.chaseSpeed() * 0.5), ai, state);
      } else {
        navigateToward(entity, state.wanderTarget, Math.max(0.08, ai.chaseSpeed() * 0.5), ai);
      }
      state.lastSelectorId = null;
      state.lastIntentType = MobAiIntentType.WANDER;
    } else if (state.wanderTarget != null) {
      if (runtimeModel == MobAiRuntimeModel.NATURAL_V1) {
        navigateTowardNatural(spec, entity, state.wanderTarget, Math.max(0.08, ai.chaseSpeed() * 0.5), ai, state);
      } else {
        navigateToward(entity, state.wanderTarget, Math.max(0.08, ai.chaseSpeed() * 0.5), ai);
      }
    } else {
      stopNavigation(entity);
      state.lastSelectorId = null;
      state.lastIntentType = MobAiIntentType.HOLD_POSITION;
    }
  }

  private MobAiSelectorSpec selectOverrideSelector(
      MobAiSpec ai,
      LivingEntity entity,
      MobState state,
      LivingEntity target,
      long now) {
    List<MobAiSelectorSpec> selectors = ai == null ? List.of() : ai.selectors();
    if (selectors.isEmpty()) {
      return null;
    }
    List<MobAiSelectorSpec> ordered = new ArrayList<>(selectors);
    ordered.sort(java.util.Comparator.comparingInt(MobAiSelectorSpec::priority));
    for (MobAiSelectorSpec selector : ordered) {
      if (!consumeSelectorEvaluationBudget()) {
        return null;
      }
      if (aiCurrentDegradeTier == MobAiGuardrailController.DegradeTier.DROP_LOW_PRIORITY && selector.priority() > 100) {
        continue;
      }
      if (aiCurrentDegradeTier == MobAiGuardrailController.DegradeTier.DISABLE_SOCIAL_ECONOMY
          && (selector.intent().type() == MobAiIntentType.ASSIST || selector.intent().type() == MobAiIntentType.CALL_HELP)) {
        continue;
      }
      if (evaluateOverrideCondition(selector.condition(), entity, state, target, now)) {
        return selector;
      }
    }
    return null;
  }

  private boolean evaluateOverrideCondition(
      MobAiConditionSpec condition,
      LivingEntity entity,
      MobState state,
      LivingEntity target,
      long now) {
    MobAiConditionSpec effective = condition == null ? MobAiConditionSpec.always() : condition;
    return switch (effective.kind()) {
      case ALWAYS -> true;
      case ALL -> {
        boolean ok = true;
        for (MobAiConditionSpec child : effective.children()) {
          if (!evaluateOverrideCondition(child, entity, state, target, now)) {
            ok = false;
            break;
          }
        }
        yield ok;
      }
      case ANY -> {
        boolean ok = false;
        for (MobAiConditionSpec child : effective.children()) {
          if (evaluateOverrideCondition(child, entity, state, target, now)) {
            ok = true;
            break;
          }
        }
        yield ok;
      }
      case NOT -> effective.children().isEmpty() || !evaluateOverrideCondition(effective.children().get(0), entity, state, target, now);
      case HAS_TARGET -> {
        boolean expected = effective.booleanValue() == null || effective.booleanValue();
        yield expected == (target != null && target.isValid() && !target.isDead());
      }
      case HEALTH_RATIO_LTE -> {
        double max = maxHealth(entity);
        double ratio = max <= 0.0 ? 0.0 : (entity.getHealth() / max);
        double limit = effective.numberValue() == null ? 1.0 : effective.numberValue();
        yield ratio <= limit;
      }
      case HEALTH_RATIO_GTE -> {
        double max = maxHealth(entity);
        double ratio = max <= 0.0 ? 0.0 : (entity.getHealth() / max);
        double limit = effective.numberValue() == null ? 0.0 : effective.numberValue();
        yield ratio >= limit;
      }
      case TARGET_DISTANCE_LTE -> {
        if (target == null) {
          yield false;
        }
        double limit = effective.numberValue() == null ? 0.0 : effective.numberValue();
        yield entity.getLocation().distanceSquared(target.getLocation()) <= limit * limit;
      }
      case TARGET_DISTANCE_GTE -> {
        if (target == null) {
          yield false;
        }
        double limit = effective.numberValue() == null ? 0.0 : effective.numberValue();
        yield entity.getLocation().distanceSquared(target.getLocation()) >= limit * limit;
      }
      case BEHAVIOR_STATE -> effective.behaviorState() == null || effective.behaviorState() == state.behaviorState;
      case RANDOM_CHANCE -> {
        double chance = effective.numberValue() == null ? 1.0 : effective.numberValue();
        yield chance >= 1.0 || rng.nextDouble() <= chance;
      }
    };
  }

  private void executeOverrideIntent(
      MobSpec spec,
      MobPhaseSpec phase,
      LivingEntity entity,
      MobState state,
      MobAiSpec ai,
      UUID ownerId,
      long now,
      LivingEntity target,
      MobAiSelectorSpec selector,
      MobAiRuntimeModel runtimeModel) {
    MobAiIntentSpec intent = selector.intent();
    boolean natural = runtimeModel == MobAiRuntimeModel.NATURAL_V1;
    boolean suppressMovement = natural && now < state.movementSuppressedUntilTick;
    switch (intent.type()) {
      case CHASE -> {
        if (!suppressMovement && target != null) {
          state.wanderTarget = null;
          if (natural) {
            navigateTowardNatural(spec, entity, target.getLocation(), intent.speed() > 0.0 ? intent.speed() : ai.chaseSpeed(), ai, state);
          } else {
            navigateToward(entity, target.getLocation(), intent.speed() > 0.0 ? intent.speed() : ai.chaseSpeed(), ai);
          }
        }
      }
      case FLEE -> {
        if (!suppressMovement && target != null) {
          state.wanderTarget = null;
          if (natural) {
            navigateAwayFromNatural(spec, entity, target, intent.speed() > 0.0 ? intent.speed() : ai.fleeSpeed(), ai, state);
          } else {
            navigateAwayFrom(entity, target, intent.speed() > 0.0 ? intent.speed() : ai.fleeSpeed(), ai);
          }
        }
      }
      case HOLD_RANGE -> {
        if (suppressMovement || target == null) {
          return;
        }
        double min = intent.minRange() > 0.0 ? intent.minRange() : ai.kiteMinRange();
        double max = intent.maxRange() > 0.0 ? intent.maxRange() : Math.max(min + 2.0, intent.radius());
        double distSq = entity.getLocation().distanceSquared(target.getLocation());
        if (min > 0.0 && distSq < min * min) {
          if (natural) {
            navigateAwayFromNatural(spec, entity, target, intent.speed() > 0.0 ? intent.speed() : ai.kiteSpeed(), ai, state);
          } else {
            navigateAwayFrom(entity, target, intent.speed() > 0.0 ? intent.speed() : ai.kiteSpeed(), ai);
          }
        } else if (max > 0.0 && distSq > max * max) {
          if (natural) {
            navigateTowardNatural(spec, entity, target.getLocation(), intent.speed() > 0.0 ? intent.speed() : ai.chaseSpeed(), ai, state);
          } else {
            navigateToward(entity, target.getLocation(), intent.speed() > 0.0 ? intent.speed() : ai.chaseSpeed(), ai);
          }
        } else {
          stopNavigation(entity);
        }
      }
      case HOLD_POSITION -> stopNavigation(entity);
      case WANDER -> {
        if (suppressMovement) {
          return;
        }
        long interval = intent.intervalTicks() > 0 ? intent.intervalTicks() : ai.idleWanderIntervalTicks();
        double radius = intent.radius() > 0.0 ? intent.radius() : ai.idleWanderRadius();
        if (radius <= 0.0) {
          return;
        }
        double stopDistance = Math.max(0.1, ai.movementStopDistance());
        double stopDistanceSq = stopDistance * stopDistance;
        if (state.wanderTarget == null
            || now >= state.nextWanderTick
            || entity.getLocation().distanceSquared(state.wanderTarget) <= stopDistanceSq) {
          state.nextWanderTick = now + Math.max(1L, interval);
          state.wanderTarget = randomHomeOffset(state.home, radius);
        }
        if (natural) {
          navigateTowardNatural(spec, entity, state.wanderTarget, intent.speed() > 0.0 ? intent.speed() : 0.18, ai, state);
        } else {
          navigateToward(entity, state.wanderTarget, intent.speed() > 0.0 ? intent.speed() : 0.18, ai);
        }
      }
      case RETURN -> {
        if (!suppressMovement && state.home != null) {
          if (natural) {
            navigateTowardNatural(spec, entity, state.home, intent.speed() > 0.0 ? intent.speed() : ai.chaseSpeed(), ai, state);
          } else {
            navigateToward(entity, state.home, intent.speed() > 0.0 ? intent.speed() : ai.chaseSpeed(), ai);
          }
        }
      }
      case GUARD, PATROL -> {
        org.bukkit.Location guard = resolveGuardPoint(ai, entity, state);
        if (guard == null) {
          guard = state.home;
        }
        if (guard != null) {
          double guardRadius = intent.radius() > 0.0 ? intent.radius() : 2.0;
          if (entity.getLocation().distanceSquared(guard) > guardRadius * guardRadius) {
            if (!suppressMovement) {
              if (natural) {
                navigateTowardNatural(spec, entity, guard, intent.speed() > 0.0 ? intent.speed() : ai.chaseSpeed(), ai, state);
              } else {
                navigateToward(entity, guard, intent.speed() > 0.0 ? intent.speed() : ai.chaseSpeed(), ai);
              }
            }
          } else {
            stopNavigation(entity);
          }
        }
      }
      case ASSIST -> {
        LivingEntity assistTarget = resolveAssistTarget(entity, state, intent.radius());
        if (assistTarget != null) {
          setTargetTracked(entity, state, assistTarget, now);
          if (!suppressMovement) {
            if (natural) {
              navigateTowardNatural(spec, entity, assistTarget.getLocation(), intent.speed() > 0.0 ? intent.speed() : ai.chaseSpeed(), ai, state);
            } else {
              navigateToward(entity, assistTarget.getLocation(), intent.speed() > 0.0 ? intent.speed() : ai.chaseSpeed(), ai);
            }
          }
        }
      }
      case CALL_HELP -> {
        if (target != null) {
          applyCallForHelp(spec, entity, state, target, ai, now, !ai.isFullOverride());
        }
      }
      case CAST_ONLY -> trySelectorCast(spec, entity, state, ownerId, target, selector.id(), intent, now);
      case CHASE_AND_CAST -> {
        if (!suppressMovement && target != null) {
          if (natural) {
            navigateTowardNatural(spec, entity, target.getLocation(), intent.speed() > 0.0 ? intent.speed() : ai.chaseSpeed(), ai, state);
          } else {
            navigateToward(entity, target.getLocation(), intent.speed() > 0.0 ? intent.speed() : ai.chaseSpeed(), ai);
          }
        }
        trySelectorCast(spec, entity, state, ownerId, target, selector.id(), intent, now);
      }
    }
  }

  private boolean trySelectorCast(
      MobSpec spec,
      LivingEntity entity,
      MobState state,
      UUID ownerId,
      LivingEntity target,
      String selectorId,
      MobAiIntentSpec intent,
      long now) {
    if (intent == null || !intent.hasCastAbility()) {
      return false;
    }
    if (intent.requireTarget() && (target == null || !target.isValid() || target.isDead())) {
      return false;
    }
    if (!consumeOverrideCastBudget()) {
      return false;
    }
    String key = (selectorId == null ? "selector" : selectorId) + ":" + intent.abilityId();
    long next = state.nextSelectorCastTick.getOrDefault(key, 0L);
    if (now < next) {
      return false;
    }
    long cooldown = Math.max(1L, intent.castCooldownTicks());
    state.nextSelectorCastTick.put(key, now + cooldown);
    tryCast(entity, intent.abilityId(), spec, null, target, ownerId);
    return true;
  }

  private MobAiIntentType mapPlanIntent(MobAiPlan.Intent intent) {
    if (intent == null) {
      return MobAiIntentType.HOLD_POSITION;
    }
    return switch (intent) {
      case CHASE -> MobAiIntentType.CHASE;
      case FLEE -> MobAiIntentType.FLEE;
      case HOLD_RANGE -> MobAiIntentType.HOLD_RANGE;
      case HOLD_POSITION, NONE -> MobAiIntentType.HOLD_POSITION;
      case WANDER -> MobAiIntentType.WANDER;
      case CALL_HELP -> MobAiIntentType.CALL_HELP;
      case ASSIST -> MobAiIntentType.ASSIST;
    };
  }

  private boolean consumeSelectorEvaluationBudget() {
    aiSelectorEvaluationsThisTick++;
    if (aiMaxSelectorEvaluationsPerTick > 0 && aiSelectorEvaluationsThisTick > aiMaxSelectorEvaluationsPerTick) {
      aiGuardrailTrips++;
      aiFallbackTicks++;
      return false;
    }
    return true;
  }

  private boolean consumeOverrideCastBudget() {
    if (aiMaxOverrideCastsPerTick > 0 && aiOverrideCastsThisTick >= aiMaxOverrideCastsPerTick) {
      aiGuardrailTrips++;
      aiFallbackTicks++;
      return false;
    }
    aiOverrideCastsThisTick++;
    return true;
  }

  private void tickAiLegacy(MobSpec spec, MobPhaseSpec phase, LivingEntity entity, MobState state, MobAiSpec ai, UUID ownerId, long now) {
    LivingEntity owner = null;
    if (isMinion(entity)) {
      owner = resolveOwner(entity);
      if (owner != null && !ai.overrideDefault()) {
        state.home = owner.getLocation().clone();
      }
    }

    if (state.home == null) {
      state.home = entity.getLocation().clone();
    }

    if (isMinion(entity)) {
      MinionMode mode = minionMode(entity, owner);
      if (mode == MinionMode.PASSIVE || mode == MinionMode.HOLD || mode == MinionMode.AVOID) {
        clearTarget(entity, state);
        return;
      }
      if (owner != null) {
        double followRadius = mode == MinionMode.GUARD ? 6.0 : 4.0;
        if ((mode == MinionMode.FOLLOW || mode == MinionMode.GUARD) &&
            entity.getLocation().distanceSquared(owner.getLocation()) > followRadius * followRadius) {
          clearTarget(entity, state);
          moveToward(entity, owner.getLocation(), ai.chaseSpeed() > 0.0 ? ai.chaseSpeed() : 0.2);
          return;
        }
      }
    }

    applyLocomotion(entity, ai);
    if (applyTerrainAvoidance(entity, state, ai)) {
      return;
    }

    if (ai.overrideDefault() && ai.controller() != null) {
      ai.controller().tick(new ContextImpl(spec, entity, state, ownerId, now));
      return;
    }

    double leashRadius = ai.leashRadius();
    if (leashRadius > 0.0) {
      double distHome = entity.getLocation().distanceSquared(state.home);
      double leash = leashRadius * leashRadius;
      if (distHome > leash) {
        clearTarget(entity, state);
        if (ai.leashTeleportRadius() > 0.0 && distHome > ai.leashTeleportRadius() * ai.leashTeleportRadius()) {
          entity.teleport(state.home);
        } else {
          moveToward(entity, state.home, 0.2);
        }
        return;
      }
    }

    LivingEntity current = resolveTarget(state.currentTarget);
    if (current != null && !isValidTarget(entity, current, ai.aggroRadius())) {
      current = null;
      clearTarget(entity, state);
    }

    LivingEntity desired = selectAggroTarget(entity, state, ai);
    if (current != null && desired != null && !current.getUniqueId().equals(desired.getUniqueId())) {
      long cooldown = ai.targetSwitchCooldownTicks();
      if (cooldown > 0 && now - state.lastTargetSwitchTick < cooldown) {
        desired = current;
      }
    }

    if (desired != null && (current == null || !desired.getUniqueId().equals(current.getUniqueId()))) {
      setTarget(entity, state, desired, now);
    }

    if (state.currentTarget == null) {
      if (owner != null) {
        double followRadius = 3.5;
        double distOwner = entity.getLocation().distanceSquared(owner.getLocation());
        if (distOwner > followRadius * followRadius) {
          moveToward(entity, owner.getLocation(), 0.25);
        }
      } else {
        if (ai.roamRadius() > 0.0 && state.home != null) {
          double distHome = entity.getLocation().distanceSquared(state.home);
          if (distHome > ai.roamRadius() * ai.roamRadius()) {
            moveToward(entity, state.home, ai.chaseSpeed());
            return;
          }
        }
        boolean handled = applyAiGoals(ai, entity, state, now);
        if (!handled) {
          long interval = ai.idleWanderIntervalTicks();
          if (ai.idleWanderRadius() > 0.0 && now >= state.nextWanderTick) {
            state.nextWanderTick = now + interval;
            org.bukkit.Location wander = randomHomeOffset(state.home, ai.idleWanderRadius());
            moveToward(entity, wander, 0.18);
          }
        }
      }
    }

    LivingEntity target = resolveTarget(state.currentTarget);
    if (target == null) {
      updateBehaviorState(entity, state, ai, null, now);
      return;
    }
    ensureAerialAggroState(entity, target);

    updateBehaviorState(entity, state, ai, target, now);

    if (ai.fleeHealthRatio() > 0.0) {
      double max = maxHealth(entity);
      double ratio = max <= 0.0 ? 0.0 : (entity.getHealth() / max);
      if (ratio <= ai.fleeHealthRatio()) {
        moveAwayFrom(entity, target, ai.fleeSpeed());
        return;
      }
    }

    if (ai.kiteMinRange() > 0.0 && hasRangedAttack(spec, phase)) {
      double dist = entity.getLocation().distanceSquared(target.getLocation());
      if (dist < ai.kiteMinRange() * ai.kiteMinRange()) {
        moveAwayFrom(entity, target, ai.kiteSpeed());
      }
    }

    if (ai.controller() != null) {
      ai.controller().tick(new ContextImpl(spec, entity, state, ownerId, now));
    }
  }

  private void tickAiV2(MobSpec spec, MobPhaseSpec phase, LivingEntity entity, MobState state, MobAiSpec ai, UUID ownerId, long now) {
    if (!consumeAiStepBudget()) {
      aiFallbackTicks++;
      return;
    }
    LivingEntity owner = null;
    if (isMinion(entity)) {
      owner = resolveOwner(entity);
      if (owner != null && !ai.overrideDefault()) {
        state.home = owner.getLocation().clone();
      }
    }
    if (state.home == null) {
      state.home = entity.getLocation().clone();
    }
    applyLocomotion(entity, ai);
    if (applyTerrainAvoidanceV2(entity, state, ai)) {
      return;
    }
    if (ai.openDoors()) {
      tryOpenDoorAhead(entity);
    }

    if (!applyLeash(state, entity, ai)) {
      return;
    }

    LivingEntity target = resolveTarget(state.currentTarget);
    if (target != null && !isValidTarget(entity, target, ai.aggroRadius())) {
      clearTarget(entity, state);
      target = null;
    }
    LivingEntity desired = selectAggroTargetV2(entity, state, ai);
    if (desired != null && (target == null || !desired.getUniqueId().equals(target.getUniqueId()))) {
      long switchCooldown = Math.max(ai.targetSwitchCooldownTicks(), aiRetargetMinIntervalTicks);
      if (now >= state.nextRetargetTick && (switchCooldown <= 0L || now - state.lastTargetSwitchTick >= switchCooldown)) {
        setTarget(entity, state, desired, now);
        state.nextRetargetTick = now + Math.max(1L, aiRetargetMinIntervalTicks);
        target = desired;
      }
    }

    if (target == null && ai.profile() == MobAiProfile.PASSIVE) {
      clearTarget(entity, state);
    }

    if (target == null) {
      if (owner != null) {
        double followRadius = 3.5;
        double distOwner = entity.getLocation().distanceSquared(owner.getLocation());
        if (distOwner > followRadius * followRadius) {
          navigateToward(entity, owner.getLocation(), ai.chaseSpeed(), ai);
        }
      } else {
        boolean handled = applyAiGoalsV2(spec, phase, ai, entity, state, null, now);
        if (!handled && ai.idleWanderRadius() > 0.0 && now >= state.nextWanderTick) {
          state.nextWanderTick = now + Math.max(1L, ai.idleWanderIntervalTicks());
          navigateToward(entity, randomHomeOffset(state.home, ai.idleWanderRadius()), Math.max(0.12, ai.chaseSpeed() * 0.7), ai);
        }
      }
      updateBehaviorStateWithHooks(spec, entity, state, ai, null, ownerId, now);
      return;
    }
    ensureAerialAggroState(entity, target);

    updateBehaviorStateWithHooks(spec, entity, state, ai, target, ownerId, now);
    if (ai.callForHelpRadius() > 0.0) {
      applyCallForHelp(spec, entity, state, target, ai, now);
    }

    if (applyAiGoalsV2(spec, phase, ai, entity, state, target, now)) {
      if (ai.controller() != null) {
        ai.controller().tick(new ContextImpl(spec, entity, state, ownerId, now));
      }
      return;
    }

    if (state.behaviorState == MobBehaviorState.RETREAT) {
      navigateAwayFrom(entity, target, ai.fleeSpeed(), ai);
      return;
    }
    if (ai.kiteMinRange() > 0.0 && hasRangedAttack(spec, phase)) {
      double dist = entity.getLocation().distanceSquared(target.getLocation());
      if (dist < ai.kiteMinRange() * ai.kiteMinRange()) {
        navigateAwayFrom(entity, target, ai.kiteSpeed(), ai);
        return;
      }
    }
    if (now >= state.nextPathRecalcTick) {
      state.nextPathRecalcTick = now + Math.max(1L, aiPathRecalcMinIntervalTicks);
      navigateToward(entity, target.getLocation(), ai.chaseSpeed(), ai);
    }
    if (ai.controller() != null) {
      ai.controller().tick(new ContextImpl(spec, entity, state, ownerId, now));
    }
  }

  private void tickAiV3(MobSpec spec, MobPhaseSpec phase, LivingEntity entity, MobState state, MobAiSpec ai, UUID ownerId, long now) {
    if (!consumeAiStepBudget()) {
      aiFallbackTicks++;
      return;
    }
    MobAiV3Spec resolved = MobAiV3Resolver.resolve(ai);
    MobAiPlan plan = null;
    if (aiAsyncEnabled && aiPlannerService != null) {
      MobAiSnapshot snapshot = snapshotForV3(spec, phase, entity, state, ownerId, now, resolved);
      if (aiPlannerService.queueSize() < aiAsyncMaxJobsPerTick) {
        aiPlannerService.submit(snapshot);
      } else {
        aiFallbackTicks++;
      }
      plan = aiPlannerService.poll(entity.getUniqueId());
    }
    if (plan != null) {
      if (now - plan.tick() > aiAsyncPlanTtlTicks || plan.tick() != now) {
        aiStalePlanDiscards++;
      } else {
        applyV3Plan(spec, phase, entity, state, ai, ownerId, now, plan);
        return;
      }
    }
    // Synchronous fallback keeps behavior stable if async has no fresh plan.
    tickAiV2(spec, phase, entity, state, ai, ownerId, now);
  }

  private MobAiSnapshot snapshotForV3(
      MobSpec spec,
      MobPhaseSpec phase,
      LivingEntity entity,
      MobState state,
      UUID ownerId,
      long now,
      MobAiV3Spec resolved) {
    LivingEntity target = resolveTarget(state.currentTarget);
    double targetX = target == null ? entity.getLocation().getX() : target.getLocation().getX();
    double targetY = target == null ? entity.getLocation().getY() : target.getLocation().getY();
    double targetZ = target == null ? entity.getLocation().getZ() : target.getLocation().getZ();
    double targetDistSq = target == null ? Double.MAX_VALUE : entity.getLocation().distanceSquared(target.getLocation());
    Vector velocity = entity.getVelocity();
    return new MobAiSnapshot(
        now,
        entity.getUniqueId(),
        spec == null ? "" : spec.id(),
        state == null ? null : state.phaseId,
        ownerId,
        entity.getLocation().getX(),
        entity.getLocation().getY(),
        entity.getLocation().getZ(),
        velocity.getX(),
        velocity.getY(),
        velocity.getZ(),
        entity.getHealth(),
        maxHealth(entity),
        target == null ? null : target.getUniqueId(),
        targetX,
        targetY,
        targetZ,
        targetDistSq,
        state == null ? MobBehaviorState.IDLE : state.behaviorState,
        resolved);
  }

  private void applyV3Plan(
      MobSpec spec,
      MobPhaseSpec phase,
      LivingEntity entity,
      MobState state,
      MobAiSpec ai,
      UUID ownerId,
      long now,
      MobAiPlan plan) {
    LivingEntity target = null;
    if (plan.targetId() != null) {
      Entity targetEntity = Bukkit.getEntity(plan.targetId());
      if (targetEntity instanceof LivingEntity livingTarget) {
        target = livingTarget;
      }
    }
    ensureAerialAggroState(entity, target);
    if (plan.desiredState() != null) {
      updateBehaviorStateWithHooks(spec, entity, state, ai, target, ownerId, now);
    }
    switch (plan.intent()) {
      case CHASE, ASSIST -> {
        Location goal = new Location(entity.getWorld(), plan.moveX(), plan.moveY(), plan.moveZ());
        navigateToward(entity, goal, plan.speed(), ai);
      }
      case FLEE -> {
        if (target != null) {
          navigateAwayFrom(entity, target, plan.speed(), ai);
        }
      }
      case HOLD_RANGE -> {
        if (target != null) {
          double dist = entity.getLocation().distanceSquared(target.getLocation());
          double min = ai.kiteMinRange() > 0.0 ? ai.kiteMinRange() : 5.0;
          if (dist < min * min) {
            navigateAwayFrom(entity, target, Math.max(plan.speed(), ai.kiteSpeed()), ai);
          } else {
            navigateToward(entity, target.getLocation(), Math.max(plan.speed(), ai.chaseSpeed()), ai);
          }
        }
      }
      case CALL_HELP -> {
        if (target != null) {
          applyCallForHelp(spec, entity, state, target, ai, now, false);
        }
      }
      case HOLD_POSITION, NONE -> stopNavigation(entity);
      case WANDER -> {
        Location wander = randomHomeOffset(state.home == null ? entity.getLocation() : state.home, ai.idleWanderRadius());
        navigateToward(entity, wander, Math.max(0.1, plan.speed()), ai);
      }
    }
  }

  private MobAiRuntimeModel resolveRuntimeModel(MobSpec spec, MobAiSpec ai) {
    if (ai == null) {
      return MobAiRuntimeModel.LEGACY_V4;
    }
    if (ai.runtimeModel() == MobAiRuntimeModel.NATURAL_V1) {
      return MobAiRuntimeModel.NATURAL_V1;
    }
    if (!aiV4NaturalModelEnabled || ai.schemaVersion() != MobAiSchemaVersion.V4) {
      return ai.runtimeModel();
    }
    if ("ALL".equals(aiV4NaturalOptInMode)) {
      return MobAiRuntimeModel.NATURAL_V1;
    }
    if ("PACK_PREFIX".equals(aiV4NaturalOptInMode)) {
      String id = spec == null ? null : spec.id();
      if (id == null || id.isBlank()) {
        return ai.runtimeModel();
      }
      String normalized = id.toLowerCase(Locale.ROOT);
      for (String prefix : aiV4NaturalPackPrefixes) {
        if (prefix != null && !prefix.isBlank() && normalized.startsWith(prefix)) {
          return MobAiRuntimeModel.NATURAL_V1;
        }
      }
    }
    return ai.runtimeModel();
  }

  private MobAiMovementPolicy resolveMovementPolicy(MobSpec spec, MobAiSpec ai, MobAiRuntimeModel runtimeModel) {
    if (runtimeModel == MobAiRuntimeModel.NATURAL_V1) {
      return ai == null ? aiV4DefaultMovementPolicy : (ai.movementPolicy() == null ? aiV4DefaultMovementPolicy : ai.movementPolicy());
    }
    return ai == null ? MobAiMovementPolicy.PATHFINDER_FIRST : ai.movementPolicy();
  }

  private List<MobAiTargetSourceSpec> naturalDefaultTargetSources(MobAiSpec ai) {
    double radius = ai == null ? 10.0 : Math.max(0.0, ai.aggroRadius());
    List<MobAiTargetSourceSpec> defaults = new ArrayList<>();
    defaults.add(new MobAiTargetSourceSpec(MobAiTargetSourceType.LAST_ATTACKER, radius, 80L, 0L, 10));
    defaults.add(new MobAiTargetSourceSpec(MobAiTargetSourceType.PROXIMITY_PLAYER, radius, 40L, 0L, 20));
    defaults.add(new MobAiTargetSourceSpec(MobAiTargetSourceType.CURRENT_TARGET, radius, 20L, 0L, 50));
    return defaults;
  }

  private LivingEntity selectAggroTargetNatural(
      MobSpec spec,
      LivingEntity entity,
      MobState state,
      MobAiSpec ai,
      long now) {
    List<MobAiTargetSourceSpec> sources = ai == null ? List.of() : ai.targetSources();
    List<MobAiTargetSourceSpec> ordered = new ArrayList<>(sources.isEmpty() ? naturalDefaultTargetSources(ai) : sources);
    ordered.sort(java.util.Comparator.comparingInt(MobAiTargetSourceSpec::priority));
    for (MobAiTargetSourceSpec source : ordered) {
      if (source == null || source.type() == null) {
        continue;
      }
      long blockedUntil = state.targetSourceCooldownExpiry.getOrDefault(source.type(), 0L);
      if (blockedUntil > now) {
        continue;
      }
      LivingEntity candidate = resolveTargetFromSource(entity, state, ai, source);
      if (candidate != null) {
        if (source.memoryTicks() > 0L) {
          state.targetSourceMemory.put(source.type(), candidate.getUniqueId());
          state.targetSourceMemoryExpiry.put(source.type(), now + source.memoryTicks());
        }
        if (source.cooldownTicks() > 0L) {
          state.targetSourceCooldownExpiry.put(source.type(), now + source.cooldownTicks());
        }
        state.lastResolvedTargetSource = source.type();
        return candidate;
      }
      UUID remembered = state.targetSourceMemory.get(source.type());
      long memoryExpiry = state.targetSourceMemoryExpiry.getOrDefault(source.type(), 0L);
      if (remembered != null && memoryExpiry > now) {
        LivingEntity rememberedTarget = resolveTarget(remembered);
        if (isValidSourceTarget(entity, rememberedTarget, source, ai) && !isFriendlyTarget(entity, rememberedTarget)) {
          state.lastResolvedTargetSource = source.type();
          return rememberedTarget;
        }
      }
    }
    state.lastResolvedTargetSource = null;
    return null;
  }

  private LivingEntity resolveTargetFromSource(
      LivingEntity entity,
      MobState state,
      MobAiSpec ai,
      MobAiTargetSourceSpec source) {
    double radius = source.radius() > 0.0 ? source.radius() : (ai == null ? 12.0 : ai.aggroRadius());
    LivingEntity candidate = switch (source.type()) {
      case LAST_ATTACKER -> resolveTarget(state.lastAttacker);
      case PROXIMITY_PLAYER -> MobTargeting.nearestPlayer(entity, radius);
      case PROXIMITY_HOSTILE -> MobTargeting.nearestHostile(entity, radius);
      case CURRENT_TARGET -> resolveTarget(state.currentTarget);
    };
    if (!isValidSourceTarget(entity, candidate, source, ai)) {
      return null;
    }
    if (isFriendlyTarget(entity, candidate)) {
      return null;
    }
    return candidate;
  }

  private boolean isValidSourceTarget(
      LivingEntity entity,
      LivingEntity candidate,
      MobAiTargetSourceSpec source,
      MobAiSpec ai) {
    if (candidate == null || !candidate.isValid() || candidate.isDead()) {
      return false;
    }
    double radius = source.radius() > 0.0 ? source.radius() : (ai == null ? 12.0 : ai.aggroRadius());
    return isValidTarget(entity, candidate, radius);
  }

  private LivingEntity selectAggroTargetV2(LivingEntity entity, MobState state, MobAiSpec ai) {
    if (ai.profile() == MobAiProfile.PASSIVE) {
      return null;
    }
    if (ai.profile() == MobAiProfile.NEUTRAL || ai.profile() == MobAiProfile.DEFENSIVE) {
      LivingEntity attacker = resolveTarget(state.lastAttacker);
      if (attacker != null && isValidTarget(entity, attacker, ai.aggroRadius())) {
        return attacker;
      }
      LivingEntity current = resolveTarget(state.currentTarget);
      if (current != null && isValidTarget(entity, current, ai.aggroRadius())) {
        return current;
      }
      if (ai.profile() == MobAiProfile.NEUTRAL) {
        return null;
      }
    }
    return selectAggroTarget(entity, state, ai);
  }

  private boolean applyLeash(MobState state, LivingEntity entity, MobAiSpec ai) {
    return applyLeash(state, entity, ai, true);
  }

  private boolean applyLeash(MobState state, LivingEntity entity, MobAiSpec ai, boolean syncVanillaTarget) {
    if (state == null || state.home == null) {
      return true;
    }
    double leashRadius = ai.leashRadius();
    if (leashRadius <= 0.0) {
      return true;
    }
    double distHome = entity.getLocation().distanceSquared(state.home);
    double leash = leashRadius * leashRadius;
    if (distHome <= leash) {
      return true;
    }
    if (syncVanillaTarget) {
      clearTarget(entity, state);
    } else {
      clearTargetTracked(entity, state);
    }
    if (ai.leashTeleportRadius() > 0.0 && distHome > ai.leashTeleportRadius() * ai.leashTeleportRadius()) {
      entity.teleport(state.home);
    } else {
      navigateToward(entity, state.home, Math.max(0.15, ai.chaseSpeed()), ai);
    }
    return false;
  }

  private void updateBehaviorStateWithHooks(
      MobSpec spec,
      LivingEntity entity,
      MobState state,
      MobAiSpec ai,
      LivingEntity target,
      UUID ownerId,
      long now) {
    MobBehaviorState desired = MobBehaviorState.IDLE;
    double max = maxHealth(entity);
    double ratio = max <= 0.0 ? 0.0 : (entity.getHealth() / max);
    if (ai.rageHealthRatio() > 0.0 && ratio <= ai.rageHealthRatio()) {
      desired = MobBehaviorState.RAGE;
    } else if (ai.fleeHealthRatio() > 0.0 && ratio <= ai.fleeHealthRatio()) {
      desired = MobBehaviorState.RETREAT;
    } else if (target != null) {
      desired = MobBehaviorState.ENGAGE;
    }
    if (state.behaviorState == desired) {
      return;
    }
    long minCooldown = Math.max(0L, ai.stateTransitionCooldownTicks());
    if (state.lastStateChangeTick > 0L && minCooldown > 0L && now - state.lastStateChangeTick < minCooldown) {
      return;
    }
    state.behaviorState = desired;
    state.lastStateChangeTick = now;
    if (ai.hooks() == null || ai.hooks().isEmpty()) {
      return;
    }
    String hook = ai.hooks().forState(desired.name());
    if (hook != null && !hook.isBlank()) {
      triggerEventAbility(spec, entity, state, ownerId, hook, target);
    }
  }

  private boolean applyAiGoalsV2(
      MobSpec spec,
      MobPhaseSpec phase,
      MobAiSpec ai,
      LivingEntity entity,
      MobState state,
      LivingEntity target,
      long now) {
    List<MobAiGoalSpec> goals = ai.goals();
    if (goals.isEmpty()) {
      return false;
    }
    List<MobAiGoalSpec> ordered = new ArrayList<>(goals);
    ordered.sort(java.util.Comparator.comparingInt(MobAiGoalSpec::priority));
    for (MobAiGoalSpec goal : ordered) {
      if (!consumeAiStepBudget()) {
        aiFallbackTicks++;
        return true;
      }
      switch (goal.type()) {
        case CHASE -> {
          if (target != null) {
            return navigateToward(entity, target.getLocation(), goal.speed() > 0.0 ? goal.speed() : ai.chaseSpeed(), ai);
          }
        }
        case HOLD_RANGE -> {
          if (target == null) {
            continue;
          }
          double min = goal.minRange() > 0.0 ? goal.minRange() : ai.kiteMinRange();
          double max = goal.maxRange() > 0.0 ? goal.maxRange() : Math.max(min + 2.0, goal.radius());
          double dist = entity.getLocation().distanceSquared(target.getLocation());
          if (min > 0.0 && dist < min * min) {
            return navigateAwayFrom(entity, target, goal.speed() > 0.0 ? goal.speed() : ai.kiteSpeed(), ai);
          }
          if (max > 0.0 && dist > max * max) {
            return navigateToward(entity, target.getLocation(), goal.speed() > 0.0 ? goal.speed() : ai.chaseSpeed(), ai);
          }
          return true;
        }
        case FLEE -> {
          if (target != null) {
            return navigateAwayFrom(entity, target, goal.speed() > 0.0 ? goal.speed() : ai.fleeSpeed(), ai);
          }
        }
        case ASSIST -> {
          LivingEntity assistTarget = resolveAssistTarget(entity, state, goal.radius());
          if (assistTarget != null) {
            setTarget(entity, state, assistTarget, now);
            return navigateToward(entity, assistTarget.getLocation(), goal.speed() > 0.0 ? goal.speed() : ai.chaseSpeed(), ai);
          }
        }
        case CALL_HELP -> {
          if (target != null) {
            applyCallForHelp(spec, entity, state, target, ai, now);
            return true;
          }
        }
        case HOLD_POSITION -> {
          if (state.home == null) {
            continue;
          }
          double holdRadius = goal.radius() > 0.0 ? goal.radius() : 2.0;
          if (entity.getLocation().distanceSquared(state.home) > holdRadius * holdRadius) {
            return navigateToward(entity, state.home, goal.speed() > 0.0 ? goal.speed() : ai.chaseSpeed(), ai);
          }
          stopNavigation(entity);
          return true;
        }
        case AVOID -> {
          LivingEntity avoid = MobTargeting.nearestPlayer(entity, goal.radius());
          if (avoid != null) {
            return navigateAwayFrom(entity, avoid, goal.speed() > 0.0 ? goal.speed() : ai.fleeSpeed(), ai);
          }
        }
        case GUARD -> {
          if (state.home == null) {
            continue;
          }
          org.bukkit.Location guard = resolveGuardPoint(ai, entity, state);
          if (guard == null) {
            guard = state.home;
          }
          double dist = entity.getLocation().distanceSquared(guard);
          if (goal.radius() <= 0.0 || dist > goal.radius() * goal.radius()) {
            return navigateToward(entity, guard, goal.speed() > 0.0 ? goal.speed() : ai.chaseSpeed(), ai);
          }
        }
        case RETURN -> {
          if (state.home != null) {
            return navigateToward(entity, state.home, goal.speed() > 0.0 ? goal.speed() : ai.chaseSpeed(), ai);
          }
        }
        case PATROL -> {
          if (state.home == null || goal.points().isEmpty()) {
            continue;
          }
          int index = Math.max(0, Math.min(state.patrolIndex, goal.points().size() - 1));
          org.bukkit.Location waypoint = state.home.clone().add(goal.points().get(index));
          if (entity.getLocation().distanceSquared(waypoint) <= 1.0) {
            state.patrolIndex = (index + 1) % goal.points().size();
            waypoint = state.home.clone().add(goal.points().get(state.patrolIndex));
          }
          return navigateToward(entity, waypoint, goal.speed() > 0.0 ? goal.speed() : ai.chaseSpeed(), ai);
        }
        case WANDER -> {
          long interval = goal.intervalTicks() > 0 ? goal.intervalTicks() : ai.idleWanderIntervalTicks();
          if (goal.radius() > 0.0 && now >= state.nextWanderTick) {
            state.nextWanderTick = now + interval;
            org.bukkit.Location wander = randomHomeOffset(state.home, goal.radius());
            return navigateToward(entity, wander, goal.speed() > 0.0 ? goal.speed() : 0.18, ai);
          }
        }
        default -> {
        }
      }
    }
    return false;
  }

  private LivingEntity resolveAssistTarget(LivingEntity entity, MobState state, double radius) {
    if (entity == null || entity.getWorld() == null) {
      return null;
    }
    double search = radius > 0.0 ? radius : 12.0;
    LivingEntity best = null;
    double bestDist = Double.MAX_VALUE;
    for (LivingEntity nearby : entity.getWorld().getNearbyLivingEntities(entity.getLocation(), search, search, search)) {
      if (nearby == entity || !nearby.isValid() || nearby.isDead()) {
        continue;
      }
      MobState otherState = states.get(nearby.getUniqueId());
      if (otherState == null || otherState.currentTarget == null) {
        continue;
      }
      LivingEntity candidate = resolveTarget(otherState.currentTarget);
      if (candidate == null || !candidate.isValid() || candidate.isDead()) {
        continue;
      }
      double dist = entity.getLocation().distanceSquared(candidate.getLocation());
      if (dist < bestDist) {
        best = candidate;
        bestDist = dist;
      }
    }
    return best;
  }

  private void applyCallForHelp(MobSpec spec, LivingEntity entity, MobState state, LivingEntity target, MobAiSpec ai, long now) {
    applyCallForHelp(spec, entity, state, target, ai, now, true);
  }

  private void applyCallForHelp(
      MobSpec spec,
      LivingEntity entity,
      MobState state,
      LivingEntity target,
      MobAiSpec ai,
      long now,
      boolean syncVanillaTarget) {
    if (target == null || entity.getWorld() == null || ai.callForHelpRadius() <= 0.0 || now < state.nextCallHelpTick) {
      return;
    }
    state.nextCallHelpTick = now + 20L;
    double radius = ai.callForHelpRadius();
    double assistRadiusSq = ai.assistRadius() <= 0.0 ? Double.MAX_VALUE : ai.assistRadius() * ai.assistRadius();
    for (LivingEntity nearby : entity.getWorld().getNearbyLivingEntities(entity.getLocation(), radius, radius, radius)) {
      if (nearby == entity || !nearby.isValid() || nearby.isDead()) {
        continue;
      }
      String nearbyId = MobMarkers.getMobId(nearby);
      if (nearbyId == null) {
        continue;
      }
      MobState nearbyState = states.get(nearby.getUniqueId());
      if (nearbyState == null) {
        continue;
      }
      nearbyState.threat.merge(target.getUniqueId(), 1.0, Double::sum);
      if (nearby.getLocation().distanceSquared(target.getLocation()) <= assistRadiusSq) {
        if (syncVanillaTarget) {
          setTarget(nearby, nearbyState, target, now);
        } else {
          setTargetTracked(nearby, nearbyState, target, now);
        }
      }
    }
  }

  private void updateBehaviorState(LivingEntity entity, MobState state, MobAiSpec ai, LivingEntity target, long now) {
    MobBehaviorState desired = MobBehaviorState.IDLE;
    double max = maxHealth(entity);
    double ratio = max <= 0.0 ? 0.0 : (entity.getHealth() / max);
    if (ai.rageHealthRatio() > 0.0 && ratio <= ai.rageHealthRatio()) {
      desired = MobBehaviorState.RAGE;
    } else if (ai.fleeHealthRatio() > 0.0 && ratio <= ai.fleeHealthRatio()) {
      desired = MobBehaviorState.RETREAT;
    } else if (target != null) {
      desired = MobBehaviorState.ENGAGE;
    }
    if (state.behaviorState != desired) {
      state.behaviorState = desired;
      state.lastStateChangeTick = now;
    }
  }

  private void tickEventHooks(MobSpec spec, LivingEntity entity, MobState state, UUID ownerId, long now) {
    MobEventSpec events = spec.events();
    if (events == null || events.isEmpty()) {
      return;
    }
    if (events.onSpawnTick() != null && now >= state.nextSpawnTick) {
      state.nextSpawnTick = now + events.onSpawnTickIntervalTicks();
      triggerEventAbility(spec, entity, state, ownerId, events.onSpawnTick(), null);
    }
    if (events.onNear() != null && now >= state.nextNearTick) {
      LivingEntity nearby = MobTargeting.nearestPlayer(entity, events.onNearRadius());
      if (nearby != null) {
        state.nextNearTick = now + events.onNearCooldownTicks();
        triggerEventAbility(spec, entity, state, ownerId, events.onNear(), nearby);
      }
    }
    if (events.onIdle() != null && state.behaviorState == MobBehaviorState.IDLE && now >= state.nextIdleTick) {
      state.nextIdleTick = now + events.onIdleIntervalTicks();
      triggerEventAbility(spec, entity, state, ownerId, events.onIdle(), null);
    }
    updateStuckState(entity, state, now);
    if (events.onStuck() != null && now >= state.nextStuckTick && isStuck(entity, state, events.onStuckDistance(), now)) {
      state.nextStuckTick = now + events.onStuckIntervalTicks();
      triggerEventAbility(spec, entity, state, ownerId, events.onStuck(), null);
    }
    MobCombatSpec combat = spec.combatSpec();
    if (combat != null && !combat.immuneEffects().isEmpty()) {
      for (var type : combat.immuneEffects()) {
        entity.removePotionEffect(type);
      }
    }
  }

  private void updateStuckState(LivingEntity entity, MobState state, long now) {
    if (state.lastPosition == null) {
      state.lastPosition = entity.getLocation().clone();
      state.lastMoveTick = now;
      return;
    }
    double dist = entity.getLocation().distanceSquared(state.lastPosition);
    if (dist > 0.09) {
      state.lastPosition = entity.getLocation().clone();
      state.lastMoveTick = now;
    }
  }

  private boolean isStuck(LivingEntity entity, MobState state, double distance, long now) {
    if (state.lastPosition == null) {
      return false;
    }
    long idleTicks = now - state.lastMoveTick;
    if (idleTicks < 60L) {
      return false;
    }
    double limit = distance <= 0.0 ? 0.25 : distance;
    return entity.getLocation().distanceSquared(state.lastPosition) <= limit * limit;
  }

  private boolean applyAiGoals(MobAiSpec ai, LivingEntity entity, MobState state, long now) {
    List<MobAiGoalSpec> goals = ai.goals();
    if (goals.isEmpty()) {
      return false;
    }
    List<MobAiGoalSpec> ordered = new ArrayList<>(goals);
    ordered.sort(java.util.Comparator.comparingInt(MobAiGoalSpec::priority));
    for (MobAiGoalSpec goal : ordered) {
      switch (goal.type()) {
        case AVOID -> {
          LivingEntity target = MobTargeting.nearestPlayer(entity, goal.radius());
          if (target != null) {
            moveAwayFrom(entity, target, goal.speed());
            return true;
          }
        }
        case GUARD -> {
          if (state.home == null) {
            continue;
          }
          org.bukkit.Location guard = resolveGuardPoint(ai, entity, state);
          if (guard == null) {
            guard = state.home;
          }
          double dist = entity.getLocation().distanceSquared(guard);
          if (goal.radius() <= 0.0 || dist > goal.radius() * goal.radius()) {
            moveToward(entity, guard, goal.speed());
            return true;
          }
        }
        case RETURN -> {
          if (state.home != null) {
            moveToward(entity, state.home, goal.speed());
            return true;
          }
        }
        case PATROL -> {
          if (state.home == null || goal.points().isEmpty()) {
            continue;
          }
          int index = Math.max(0, Math.min(state.patrolIndex, goal.points().size() - 1));
          org.bukkit.Location target = state.home.clone().add(goal.points().get(index));
          if (entity.getLocation().distanceSquared(target) <= 1.0) {
            state.patrolIndex = (index + 1) % goal.points().size();
            target = state.home.clone().add(goal.points().get(state.patrolIndex));
          }
          moveToward(entity, target, goal.speed());
          return true;
        }
        case WANDER -> {
          long interval = goal.intervalTicks() > 0 ? goal.intervalTicks() : ai.idleWanderIntervalTicks();
          if (goal.radius() > 0.0 && now >= state.nextWanderTick) {
            state.nextWanderTick = now + interval;
            org.bukkit.Location wander = randomHomeOffset(state.home, goal.radius());
            moveToward(entity, wander, goal.speed() > 0.0 ? goal.speed() : 0.18);
            return true;
          }
        }
        default -> {
          continue;
        }
      }
    }
    return false;
  }

  private org.bukkit.Location resolveGuardPoint(MobAiSpec ai, LivingEntity entity, MobState state) {
    if (ai.guardPoints().isEmpty() || state.home == null) {
      return null;
    }
    org.bukkit.Location best = null;
    double bestDist = Double.MAX_VALUE;
    for (org.bukkit.util.Vector point : ai.guardPoints()) {
      org.bukkit.Location location = state.home.clone().add(point);
      double dist = entity.getLocation().distanceSquared(location);
      if (dist < bestDist) {
        bestDist = dist;
        best = location;
      }
    }
    return best;
  }

  private void applyLocomotion(LivingEntity entity, MobAiSpec ai) {
    if (usesAirNavigation(entity, ai)) {
      entity.setGravity(false);
      return;
    }
    entity.setGravity(true);
  }

  private void setVanillaAiState(LivingEntity entity, boolean enabled) {
    if (!(entity instanceof Mob mob)) {
      return;
    }
    if (enabled) {
      try {
        mob.setAI(true);
      } catch (Throwable ignored) {
      }
      try {
        mob.getClass().getMethod("setAware", boolean.class).invoke(mob, true);
      } catch (Throwable ignored) {
      }
      return;
    }
    // Keep AI flag enabled to preserve physics/knockback/velocity response on passive mobs.
    // setAware(false) is enough to suppress vanilla behavior while custom override drives motion.
    try {
      mob.setAI(true);
    } catch (Throwable ignored) {
    }
    try {
      mob.getClass().getMethod("setAware", boolean.class).invoke(mob, false);
    } catch (Throwable ignored) {
    }
  }

  private boolean applyTerrainAvoidance(LivingEntity entity, MobState state, MobAiSpec ai) {
    if (state.home == null) {
      return false;
    }
    if (ai.avoidLava() && entity.getLocation().getBlock().isLiquid()
        && entity.getLocation().getBlock().getType().name().contains("LAVA")) {
      moveToward(entity, state.home, ai.chaseSpeed());
      return true;
    }
    if (ai.avoidWater() && entity.getLocation().getBlock().isLiquid()
        && entity.getLocation().getBlock().getType().name().contains("WATER")) {
      moveToward(entity, state.home, ai.chaseSpeed());
      return true;
    }
    return false;
  }

  private void tickAttack(MobSpec spec, MobInstance inst, MobAttackSpec attack, LivingEntity entity, MobState state, long now, boolean main,
      List<LivingEntity> targets, boolean syncVanillaTarget) {
    if (attack == null) {
      return;
    }
    String abilityId = attack.abilityId();
    if (isMinion(entity) && minionManager != null) {
      abilityId = minionManager.resolveAttackOverride(entity.getUniqueId(), main, abilityId);
    }
    long next = main ? state.nextMainTick : state.nextSecondaryTick;
    if (now < next) {
      return;
    }
    long internalNext = state.nextAttackCooldown.getOrDefault(abilityId, 0L);
    if (internalNext > now) {
      return;
    }
    List<LivingEntity> resolved = targets != null ? targets : selectTargets(entity, state, attack);
    if ((resolved == null || resolved.isEmpty()) && attack.requireTarget()) {
      return;
    }
    List<LivingEntity> effective = resolved == null ? List.of() : resolved;
    if (attack.chance() < 1.0 && rng.nextDouble() > attack.chance()) {
      scheduleNext(state, attack, now, main);
      return;
    }
    if (syncVanillaTarget && entity instanceof Mob mob) {
      LivingEntity target = effective.isEmpty() ? null : effective.get(0);
      if (target != null) {
        mob.setTarget(target);
      }
    }
    UUID ownerId = inst == null ? MobMarkers.getOwner(entity) : inst.ownerId();
    if (effective.isEmpty()) {
      MobCastContext ctx = new MobCastContext(spec, attack, entity, null, ownerId);
      attack.beforeCast().accept(ctx);
      tryCast(entity, abilityId, spec, attack, null, ownerId);
      attack.afterCast().accept(ctx);
    } else {
      for (LivingEntity target : effective) {
        if (target != null && !canTrigger(attack, entity, target)) {
          continue;
        }
        MobCastContext ctx = new MobCastContext(spec, attack, entity, target, ownerId);
        attack.beforeCast().accept(ctx);
        tryCast(entity, abilityId, spec, attack, target, ownerId);
        attack.afterCast().accept(ctx);
      }
    }
    scheduleNext(state, attack, now, main);
    if (attack.internalCooldownTicks() > 0) {
      state.nextAttackCooldown.put(abilityId, now + attack.internalCooldownTicks());
    }
  }

  private void tickAttacks(MobSpec spec, MobInstance inst, MobAttackSpec mainAttack, MobAttackSpec secondaryAttack,
      LivingEntity entity, MobState state, long now, boolean syncVanillaTarget) {
    if (isMinion(entity) && minionManager != null && minionManager.disableBaseAttacks(entity.getUniqueId())) {
      return;
    }
    List<AttackChoice> choices = new ArrayList<>();
    AttackChoice mainChoice = buildAttackChoice(entity, state, mainAttack, now, true);
    if (mainChoice != null) {
      choices.add(mainChoice);
    }
    AttackChoice secondaryChoice = buildAttackChoice(entity, state, secondaryAttack, now, false);
    if (secondaryChoice != null) {
      choices.add(secondaryChoice);
    }
    if (choices.isEmpty()) {
      return;
    }
    if (choices.size() == 1) {
      AttackChoice choice = choices.get(0);
      tickAttack(spec, inst, choice.attack, entity, state, now, choice.main, choice.targets, syncVanillaTarget);
      return;
    }
    AttackChoice choice = pickWeightedAttack(choices);
    tickAttack(spec, inst, choice.attack, entity, state, now, choice.main, choice.targets, syncVanillaTarget);
  }

  private AttackChoice buildAttackChoice(LivingEntity entity, MobState state, MobAttackSpec attack, long now, boolean main) {
    if (attack == null) {
      return null;
    }
    long next = main ? state.nextMainTick : state.nextSecondaryTick;
    if (now < next) {
      return null;
    }
    long internalNext = state.nextAttackCooldown.getOrDefault(attack.abilityId(), 0L);
    if (internalNext > now) {
      return null;
    }
    List<LivingEntity> targets = selectTargets(entity, state, attack);
    if ((targets == null || targets.isEmpty()) && attack.requireTarget()) {
      return null;
    }
    return new AttackChoice(attack, targets, main);
  }

  private AttackChoice pickWeightedAttack(List<AttackChoice> choices) {
    double total = 0.0;
    for (AttackChoice choice : choices) {
      total += Math.max(0.0, choice.attack.priorityWeight());
    }
    if (total <= 0.0) {
      return choices.get(0);
    }
    double roll = rng.nextDouble() * total;
    double running = 0.0;
    for (AttackChoice choice : choices) {
      running += Math.max(0.0, choice.attack.priorityWeight());
      if (roll <= running) {
        return choice;
      }
    }
    return choices.get(0);
  }

  private record AttackChoice(MobAttackSpec attack, List<LivingEntity> targets, boolean main) {
  }

  private void scheduleNext(MobState state, MobAttackSpec attack, long now, boolean main) {
    long next = now + Math.max(0L, attack.cooldownTicks());
    if (main) {
      state.nextMainTick = next;
    } else {
      state.nextSecondaryTick = next;
    }
  }

  private List<LivingEntity> selectTargets(LivingEntity entity, MobState state, MobAttackSpec attack) {
    if (attack == null) {
      return List.of();
    }
    MobAttackAoESpec aoe = attack.aoeSpec();
    if (aoe == null) {
      LivingEntity target = selectSingleTarget(entity, state, attack);
      return target == null ? List.of() : List.of(target);
    }
    return selectAoETargets(entity, state, attack, aoe);
  }

  private LivingEntity selectSingleTarget(LivingEntity entity, MobState state, MobAttackSpec attack) {
    double range = attack.range();
    if (isMinion(entity)) {
      LivingEntity owner = resolveOwner(entity);
      MinionMode mode = minionMode(entity, owner);
      if (mode == MinionMode.PASSIVE) {
        return null;
      }
      LivingEntity ownerTarget = resolveOwnerTarget(owner);
      if (ownerTarget != null
          && !isFriendlyTarget(entity, ownerTarget)
          && (range <= 0 || ownerTarget.getLocation().distanceSquared(entity.getLocation()) <= range * range)) {
        return ownerTarget;
      }
      if (mode == MinionMode.DEFENSIVE) {
        LivingEntity last = resolveOwnerLastAttacker(owner);
        if (last != null
            && !isFriendlyTarget(entity, last)
            && (range <= 0 || last.getLocation().distanceSquared(entity.getLocation()) <= range * range)) {
          return last;
        }
        return null;
      }
    }
    if (attack.targetMode() == MobTargetMode.LAST_ATTACKER) {
      LivingEntity last = resolveTarget(state.lastAttacker);
      if (last != null
          && !isFriendlyTarget(entity, last)
          && (range <= 0 || last.getLocation().distanceSquared(entity.getLocation()) <= range * range)) {
        return last;
      }
      return null;
    }
    LivingEntity current = resolveTarget(state.currentTarget);
    if (current != null
        && !isFriendlyTarget(entity, current)
        && (range <= 0 || current.getLocation().distanceSquared(entity.getLocation()) <= range * range)) {
      return current;
    }
    LivingEntity candidate = switch (attack.targetMode()) {
      case NEAREST_HOSTILE -> MobTargeting.nearestHostile(entity, range);
      case NEAREST_PLAYER -> MobTargeting.nearestPlayer(entity, range);
      case LAST_ATTACKER -> null;
      case WEIGHT_DISTANCE -> MobTargeting.weightedByDistance(entity, range);
      case WEIGHT_THREAT -> MobTargeting.weightedByThreat(entity, range, state.threat);
      case PARTY_LEADER -> partyService == null ? MobTargeting.nearestPlayer(entity, range)
          : MobTargeting.nearestPartyLeader(entity, partyService, range);
    };
    MobSpec spec = resolveSpecFromEntity(entity);
    candidate = normalizePartyTarget(candidate, spec == null ? null : spec.aiSpec());
    if (isFriendlyTarget(entity, candidate)) {
      return null;
    }
    return isAllowedSpawnTarget(entity, candidate) ? candidate : null;
  }

  private List<LivingEntity> selectAoETargets(LivingEntity entity, MobState state, MobAttackSpec attack, MobAttackAoESpec aoe) {
    if (entity.getWorld() == null) {
      return List.of();
    }
    double radius = aoe.radius();
    double height = aoe.height() > 0.0 ? aoe.height() : radius;
    org.bukkit.Location origin = entity.getLocation();
    List<LivingEntity> results = new ArrayList<>();
    for (LivingEntity target : entity.getWorld().getNearbyLivingEntities(origin, radius, height, radius)) {
      if (target == entity || !target.isValid() || target.isDead()) {
        continue;
      }
      if (!aoe.filter().matches(target)) {
        continue;
      }
      if (isFriendlyTarget(entity, target)) {
        continue;
      }
      if (attack.requireLineOfSight() && !entity.hasLineOfSight(target)) {
        continue;
      }
      if (!isAllowedSpawnTarget(entity, target)) {
        continue;
      }
      if (!matchesAoEShape(entity, target, aoe)) {
        continue;
      }
      results.add(target);
    }
    if (results.isEmpty()) {
      return results;
    }
    results.sort(java.util.Comparator.comparingDouble(t -> t.getLocation().distanceSquared(origin)));
    int max = aoe.maxTargets();
    if (max > 0 && results.size() > max) {
      return new ArrayList<>(results.subList(0, max));
    }
    return results;
  }

  private boolean matchesAoEShape(LivingEntity origin, LivingEntity target, MobAttackAoESpec aoe) {
    double radius = aoe.radius();
    org.bukkit.Location o = origin.getLocation();
    org.bukkit.Location t = target.getLocation();
    return switch (aoe.shape()) {
      case SPHERE -> o.distanceSquared(t) <= radius * radius;
      case BOX -> {
        double dx = Math.abs(t.getX() - o.getX());
        double dy = Math.abs(t.getY() - o.getY());
        double dz = Math.abs(t.getZ() - o.getZ());
        yield dx <= radius && dz <= radius && dy <= aoe.height();
      }
      case CONE -> {
        double angle = aoe.angleDegrees();
        if (angle <= 0.0 || angle > 180.0) {
          yield false;
        }
        org.bukkit.util.Vector forward = o.getDirection().clone();
        forward.setY(0);
        if (forward.lengthSquared() < 1e-9) {
          forward = new org.bukkit.util.Vector(0, 0, 1);
        }
        forward.normalize();
        org.bukkit.util.Vector to = t.toVector().subtract(o.toVector());
        to.setY(0);
        if (to.lengthSquared() < 1e-9) {
          yield false;
        }
        to.normalize();
        double cos = Math.cos(Math.toRadians(angle) / 2.0);
        yield forward.dot(to) >= cos && o.distanceSquared(t) <= radius * radius;
      }
    };
  }

  private boolean canTrigger(MobAttackSpec attack, LivingEntity entity, LivingEntity target) {
    double range = attack.range();
    if (!isAllowedSpawnTarget(entity, target)) {
      return false;
    }
    if (range > 0 && target.getLocation().distanceSquared(entity.getLocation()) > range * range) {
      return false;
    }
    if (attack.requireLineOfSight() && !entity.hasLineOfSight(target)) {
      return false;
    }
    if (attack.trigger() == MobAttackTrigger.MELEE) {
      double meleeRange = Math.min(3.0, Math.max(1.5, range <= 0 ? 2.5 : Math.min(range, 3.0)));
      return target.getLocation().distanceSquared(entity.getLocation()) <= meleeRange * meleeRange;
    }
    return true;
  }

  private void tryCast(LivingEntity caster, String abilityId, MobSpec spec, MobAttackSpec attack, LivingEntity target, UUID ownerId) {
    org.bukkit.Location origin = caster.getEyeLocation();
    org.bukkit.util.Vector direction;
    if (target != null) {
      direction = target.getEyeLocation().toVector().subtract(origin.toVector());
      if (direction.lengthSquared() < 1e-9) {
        direction = origin.getDirection();
      } else {
        direction.normalize();
      }
    } else {
      direction = origin.getDirection();
    }
    try {
      engine.castWithContext(abilityId, caster, origin, direction, null, ctx -> {
        ctx.state().put(Vars.MOB_ID, spec.id());
        if (ownerId != null) {
          ctx.state().put(Vars.MOB_OWNER, ownerId);
        }
        if (target != null) {
          ctx.state().put(Vars.MOB_TARGET, target);
        }
        if (attack != null) {
          ctx.state().put(Vars.MOB_ATTACK, attack.abilityId());
        }
        applyDamageBonusVars(spec, target, ctx);
      });
    } catch (IllegalArgumentException ignored) {
    }
  }

  private void applyDamageBonusVars(MobSpec spec, LivingEntity target, dev.patric.dungeonsreborn.effects.CastContext ctx) {
    if (spec == null || spec.damageBonuses().isEmpty()) {
      return;
    }
    double best = 1.0;
    DamageType bestType = null;
    for (MobDamageBonusSpec bonus : spec.damageBonuses()) {
      if (!bonus.matches(target)) {
        continue;
      }
      if (bonus.multiplier() > best) {
        best = bonus.multiplier();
        bestType = bonus.damageType();
      }
    }
    if (bestType != null) {
      ctx.state().put(Vars.MOB_DAMAGE_MULTIPLIER, best);
      ctx.state().put(Vars.MOB_DAMAGE_TYPE, bestType.name());
    }
  }

  private void triggerEventAbility(MobSpec spec, LivingEntity caster, MobState state, UUID ownerId, String abilityId,
      LivingEntity target) {
    if (abilityId == null || abilityId.isBlank() || caster == null) {
      return;
    }
    tryCast(caster, abilityId, spec, null, target, ownerId);
  }

  private MobVariantSpec chooseVariant(MobSpec spec) {
    if (spec.variants().isEmpty()) {
      return null;
    }
    double total = 0.0;
    for (MobVariantSpec variant : spec.variants()) {
      total += Math.max(0.0, variant.weight());
    }
    if (total <= 0.0) {
      return spec.variants().get(0);
    }
    double roll = rng.nextDouble() * total;
    double sum = 0.0;
    for (MobVariantSpec variant : spec.variants()) {
      sum += Math.max(0.0, variant.weight());
      if (roll <= sum) {
        return variant;
      }
    }
    return spec.variants().get(spec.variants().size() - 1);
  }

  private MobTraitSpec chooseTrait(MobSpec spec) {
    if (spec.traits().isEmpty()) {
      return null;
    }
    double total = 0.0;
    for (MobTraitSpec trait : spec.traits()) {
      total += Math.max(0.0, trait.weight());
    }
    if (total <= 0.0) {
      return spec.traits().get(0);
    }
    double roll = rng.nextDouble() * total;
    double sum = 0.0;
    for (MobTraitSpec trait : spec.traits()) {
      sum += Math.max(0.0, trait.weight());
      if (roll <= sum) {
        return trait;
      }
    }
    return spec.traits().get(spec.traits().size() - 1);
  }

  private void applyAttributes(LivingEntity entity, Map<Attribute, Double> attrs) {
    if (attrs.isEmpty()) {
      return;
    }
    for (Map.Entry<Attribute, Double> entry : attrs.entrySet()) {
      AttributeInstance inst = entity.getAttribute(entry.getKey());
      if (inst == null) {
        continue;
      }
      inst.setBaseValue(entry.getValue());
    }
    AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
    if (maxHealth != null) {
      double value = maxHealth.getBaseValue();
      entity.setHealth(value);
    }
  }

  private void applyVariant(LivingEntity entity, MobVariantSpec variant) {
    if (variant.healthMultiplier() != 1.0) {
      multiplyAttribute(entity, Attribute.MAX_HEALTH, variant.healthMultiplier(), true);
    }
    if (variant.damageMultiplier() != 1.0) {
      multiplyAttribute(entity, Attribute.ATTACK_DAMAGE, variant.damageMultiplier(), false);
    }
    if (variant.speedMultiplier() != 1.0) {
      multiplyAttribute(entity, Attribute.MOVEMENT_SPEED, variant.speedMultiplier(), false);
    }
    if (variant.followRangeMultiplier() != 1.0) {
      multiplyAttribute(entity, Attribute.FOLLOW_RANGE, variant.followRangeMultiplier(), false);
    }
    if (variant.scaleMultiplier() != 1.0) {
      multiplyAttribute(entity, Attribute.SCALE, variant.scaleMultiplier(), false);
    }
  }

  private void applyTrait(LivingEntity entity, MobTraitSpec trait) {
    if (trait.healthMultiplier() != 1.0) {
      multiplyAttribute(entity, Attribute.MAX_HEALTH, trait.healthMultiplier(), true);
    }
    if (trait.damageMultiplier() != 1.0) {
      multiplyAttribute(entity, Attribute.ATTACK_DAMAGE, trait.damageMultiplier(), false);
    }
    if (trait.speedMultiplier() != 1.0) {
      multiplyAttribute(entity, Attribute.MOVEMENT_SPEED, trait.speedMultiplier(), false);
    }
    if (trait.followRangeMultiplier() != 1.0) {
      multiplyAttribute(entity, Attribute.FOLLOW_RANGE, trait.followRangeMultiplier(), false);
    }
    if (trait.scaleMultiplier() != 1.0) {
      multiplyAttribute(entity, Attribute.SCALE, trait.scaleMultiplier(), false);
    }
  }

  private void applyNameplate(MobSpec spec, LivingEntity entity, MobVariantSpec variant, MobTraitSpec trait,
      MobStyleSpec style, boolean hasVariantName, boolean hasTraitName) {
    Component styleName = style == null ? null : style.nameplate();
    Component base = styleName != null ? styleName : spec.displayName();
    if (trait != null && trait.name() != null) {
      base = MobText.parse(trait.name());
    } else if (variant != null && variant.name() != null) {
      base = MobText.parse(variant.name());
    } else if (base == null) {
      base = Component.text(entity.getType().name());
    }
    if (variant != null && variant.namePrefix() != null) {
      base = MobText.parse(variant.namePrefix()).append(base);
    }
    if (trait != null && trait.namePrefix() != null) {
      base = MobText.parse(trait.namePrefix()).append(base);
    }
    if (variant != null && variant.nameSuffix() != null) {
      base = base.append(MobText.parse(variant.nameSuffix()));
    }
    if (trait != null && trait.nameSuffix() != null) {
      base = base.append(MobText.parse(trait.nameSuffix()));
    }
    boolean showName = style != null && style.showName() != null ? style.showName() : spec.showName();
    if (spec.displayName() != null || styleName != null || hasVariantName || hasTraitName) {
      entity.customName(base);
      entity.setCustomNameVisible(showName
          || (variant != null && variant.name() != null)
          || (trait != null && trait.name() != null));
    } else {
      entity.customName(null);
      entity.setCustomNameVisible(false);
    }
  }

  private void applyModelSpec(LivingEntity entity, MobModelSpec modelSpec) {
    if (modelSpec == null) {
      MobMarkers.setModelId(entity, null);
      MobMarkers.setAnimationId(entity, null);
      MobMarkers.setAnimationSpeed(entity, null);
      return;
    }
    MobMarkers.setModelId(entity, modelSpec.modelId());
    MobMarkers.setAnimationId(entity, modelSpec.animationId());
    MobMarkers.setAnimationSpeed(entity, modelSpec.animationSpeed());
  }

  private boolean isFullModelReplacement(MobModelSpec modelSpec) {
    return modelSpec != null
        && modelSpec.replaceVisual()
        && modelSpec.modelId() != null
        && !modelSpec.modelId().isBlank();
  }

  private MobVisualSpec resolveVisualSpec(MobVisualSpec explicitVisual, MobModelSpec modelSpec, String mobId) {
    return explicitVisual;
  }

  private ModelRuntimeSpec toRuntimeSpec(MobModelSpec modelSpec) {
    if (modelSpec == null || !isFullModelReplacement(modelSpec)) {
      return null;
    }
    return ModelRuntimeSpec.from(modelSpec);
  }

  private void applyModelBridgeAttach(LivingEntity entity, MobModelSpec modelSpec, String mobId) {
    if (entity == null) {
      return;
    }
    if (!modelBridgeEnabled || modelBridge == null) {
      if (entity.isInvisible()) {
        entity.setInvisible(false);
      }
      return;
    }
    ModelRuntimeSpec runtimeSpec = toRuntimeSpec(modelSpec);
    if (runtimeSpec == null) {
      modelBridge.detach(entity);
      if (entity.isInvisible()) {
        entity.setInvisible(false);
      }
      return;
    }
    if (!modelBridge.available() || !modelBridge.attach(entity, runtimeSpec)) {
      modelFallbackCount++;
      entity.setInvisible(false);
      if (modelFallbackWarnings.add(mobId)) {
        logModelFallback(mobId, runtimeSpec.modelId());
      }
    }
  }

  private void applyModelBridgeUpdate(LivingEntity entity, MobModelSpec modelSpec, String mobId) {
    if (entity == null) {
      return;
    }
    if (!modelBridgeEnabled || modelBridge == null) {
      if (entity.isInvisible()) {
        entity.setInvisible(false);
      }
      return;
    }
    ModelRuntimeSpec runtimeSpec = toRuntimeSpec(modelSpec);
    if (runtimeSpec == null) {
      modelBridge.detach(entity);
      if (entity.isInvisible()) {
        entity.setInvisible(false);
      }
      return;
    }
    if (!modelBridge.available()) {
      modelFallbackCount++;
      entity.setInvisible(false);
      if (modelFallbackWarnings.add(mobId)) {
        logModelFallback(mobId, runtimeSpec.modelId());
      }
      return;
    }
    modelBridge.update(entity, runtimeSpec);
  }

  private void detachModelBridge(LivingEntity entity) {
    if (entity == null || modelBridge == null) {
      return;
    }
    modelBridge.detach(entity);
    if (entity.isInvisible()) {
      entity.setInvisible(false);
    }
  }

  private void playModelAnimation(LivingEntity entity, String animationKey) {
    if (entity == null || modelBridge == null || !modelBridgeEnabled || !modelBridge.available()) {
      return;
    }
    modelBridge.play(entity, animationKey);
  }

  private void logModelFallback(String mobId, String modelId) {
    if (logger == null) {
      return;
    }
    logger.warn("[Mobs] model replacement fallback: mob=" + mobId
        + " model=" + modelId
        + " provider=" + modelBridgeProvider
        + " policy=" + modelMissingProviderPolicy);
  }

  private void applyCollidable(LivingEntity entity, Boolean collidable) {
    if (collidable == null) {
      return;
    }
    entity.setCollidable(collidable.booleanValue());
  }

  private void applyInvulnerable(LivingEntity entity, Boolean invulnerable) {
    if (invulnerable == null) {
      return;
    }
    entity.setInvulnerable(invulnerable.booleanValue());
  }

  private Boolean resolveCollidable(MobSpec spec, MobVariantSpec variant, MobPhaseSpec phase) {
    if (phase != null && phase.collidable() != null) {
      return phase.collidable();
    }
    if (variant != null && variant.collidable() != null) {
      return variant.collidable();
    }
    return spec == null ? null : spec.collidable();
  }

  private MobBossBarSpec resolveBossBar(MobSpec spec, MobPhaseSpec phase) {
    if (phase != null && phase.style() != null && phase.style().bossBar() != null) {
      return phase.style().bossBar();
    }
    if (spec != null && spec.style() != null && spec.style().bossBar() != null) {
      return spec.style().bossBar();
    }
    return spec == null ? null : spec.bossBar();
  }

  private void applyScaleVariance(LivingEntity entity, double variance) {
    if (variance <= 0.0) {
      return;
    }
    AttributeInstance inst = entity.getAttribute(Attribute.SCALE);
    if (inst == null) {
      return;
    }
    double base = inst.getBaseValue();
    if (base <= 0.0) {
      base = 1.0;
    }
    double delta = (rng.nextDouble() * 2.0 - 1.0) * variance;
    double next = Math.max(0.85, base + delta);
    inst.setBaseValue(next);
  }

  private void multiplyAttribute(LivingEntity entity, Attribute attribute, double multiplier, boolean clampHealth) {
    if (multiplier <= 0.0) {
      return;
    }
    AttributeInstance inst = entity.getAttribute(attribute);
    if (inst == null) {
      return;
    }
    inst.setBaseValue(inst.getBaseValue() * multiplier);
    if (clampHealth && attribute == Attribute.MAX_HEALTH) {
      double max = inst.getBaseValue();
      entity.setHealth(max);
    }
  }

  private void applyResistances(LivingEntity entity, Map<DamageType, Double> resistances) {
    if (resistances.isEmpty()) {
      return;
    }
    UUID id = entity.getUniqueId();
    for (Map.Entry<DamageType, Double> entry : resistances.entrySet()) {
      engine.setResistance(id, entry.getKey(), entry.getValue());
    }
  }

  private void applyCombatMitigation(MobSpec spec, LivingEntity entity, MobState state, EntityDamageByEntityEvent event) {
    MobCombatSpec combat = spec.combatSpec();
    if (combat == null || combat.isEmpty()) {
      return;
    }
    double damage = event.getDamage();
    if (combat.armorMultiplier() != 1.0) {
      damage *= combat.armorMultiplier();
    }
    if (combat.blockChance() > 0.0) {
      long now = engine.tickNow();
      if (state == null || now >= state.nextBlockTick) {
        if (rng.nextDouble() <= combat.blockChance()) {
          damage *= combat.blockMultiplier();
          if (state != null) {
            state.nextBlockTick = now + combat.blockCooldownTicks();
          }
        }
      }
    }
    event.setDamage(damage);
  }

  private void applyCombatCleanse(MobSpec spec, LivingEntity entity) {
    MobCombatSpec combat = spec.combatSpec();
    if (combat == null) {
      return;
    }
    if (!combat.cleanseEffects().isEmpty()) {
      for (var type : combat.cleanseEffects()) {
        entity.removePotionEffect(type);
      }
    }
  }

  private boolean tickSummon(MobSpec spec, LivingEntity entity, MobState state, UUID ownerId) {
    MobSummonSpec summon = spec.summonSpec();
    if (summon == null || !summon.enabled()) {
      return true;
    }
    if (ownerId == null) {
      return true;
    }
    org.bukkit.entity.Player owner = Bukkit.getPlayer(ownerId);
    if (owner == null || !owner.isOnline()) {
      if (summon.despawnWhenOwnerOffline()) {
        entity.remove();
        return false;
      }
      return true;
    }
    Location ownerLoc = owner.getLocation();
    double distSq = entity.getLocation().distanceSquared(ownerLoc);
    double despawn = summon.despawnDistance();
    if (despawn > 0.0 && distSq > despawn * despawn) {
      entity.remove();
      return false;
    }
    double teleport = summon.teleportDistance();
    if (teleport > 0.0 && distSq > teleport * teleport) {
      entity.teleport(ownerLoc);
      state.home = ownerLoc.clone();
    }
    return true;
  }

  private void applyLoot(MobSpec spec, LivingEntity entity, EntityDeathEvent event, Player lootKiller,
      Player xpKiller) {
    MobLootSpec loot = spec.loot();
    if (loot == null) {
      return;
    }
    if (loot.clearVanilla()) {
      event.getDrops().clear();
    }
    Location loc = entity.getLocation();
    LootModifiers modifiers = buildLootModifiers(loot, lootKiller);
    Random dropRng = loot.deterministic() ? buildDeterministicLootRandom(spec, loot, loc, lootKiller) : rng;
    applyLootSpec(spec, loot, loc, lootKiller, modifiers, dropRng);
    applyLootPools(spec, loot, loc, lootKiller);
    applyLootBundles(spec, loot, loc, lootKiller, modifiers, dropRng);
    applyLootRewards(spec, loot, loc, lootKiller, xpKiller);
  }

  private void applyLootSpec(MobSpec spec, MobLootSpec loot, Location loc, Player killer, LootModifiers modifiers,
      Random dropRng) {
    for (MobDropSpec drop : loot.guaranteed()) {
      dropLootItem(spec, loot, drop, loc, killer, modifiers, false, dropRng);
    }
    int totalRolls = loot.rolls() + loot.bonusRolls();
    for (int i = 0; i < totalRolls; i++) {
      for (MobDropSpec drop : loot.drops()) {
        dropLootItem(spec, loot, drop, loc, killer, modifiers, true, dropRng);
      }
    }
  }

  private void applyLootPools(MobSpec spec, MobLootSpec loot, Location loc, Player killer) {
    applyLootPools(spec, loot, loc, killer, new java.util.HashSet<>());
  }

  private void applyLootPools(MobSpec spec, MobLootSpec loot, Location loc, Player killer,
      Set<String> visitedPools) {
    if (loot.pools().isEmpty() || lootPoolResolver == null) {
      return;
    }
    for (MobLootPoolRef ref : loot.pools()) {
      if (ref == null) {
        continue;
      }
      if (!visitedPools.add(ref.poolId())) {
        continue;
      }
      if (ref.conditions() != null && !ref.conditions().matches(spec, loc, killer)) {
        continue;
      }
      if (ref.chance() <= 0.0) {
        continue;
      }
      if (ref.chance() < 1.0 && rng.nextDouble() > ref.chance()) {
        continue;
      }
      MobLootSpec poolSpec = lootPoolResolver.apply(ref.poolId());
      if (poolSpec == null) {
        continue;
      }
      int rolls = ref.rolls() != null ? ref.rolls() : poolSpec.rolls();
      int bonus = ref.bonusRolls() != null ? ref.bonusRolls() : poolSpec.bonusRolls();
      double luckMultiplier = ref.luckMultiplier() != null ? ref.luckMultiplier() : poolSpec.luckMultiplier();
      boolean deterministic = ref.deterministic() != null ? ref.deterministic() : poolSpec.deterministic();
      long seedSalt = ref.seedSalt() != null ? ref.seedSalt() : poolSpec.seedSalt();
      MobLootSpec effective = new MobLootSpec(false, poolSpec.guaranteed(), poolSpec.drops(), poolSpec.pools(),
          poolSpec.bundles(), poolSpec.rewards(), rolls, bonus, luckMultiplier, poolSpec.announceTiers(),
          poolSpec.announceTemplate(), deterministic, seedSalt);
      LootModifiers modifiers = buildLootModifiers(luckMultiplier, killer);
      Random dropRng = deterministic ? buildDeterministicLootRandom(spec, effective, loc, killer) : rng;
      applyLootSpec(spec, effective, loc, killer, modifiers, dropRng);
      applyLootPools(spec, effective, loc, killer, visitedPools);
      applyLootBundles(spec, effective, loc, killer, modifiers, dropRng);
      applyLootRewards(spec, effective, loc, killer, killer);
    }
  }

  private void applyLootBundles(MobSpec spec, MobLootSpec loot, Location loc, Player killer, LootModifiers modifiers,
      Random dropRng) {
    if (loot.bundles().isEmpty()) {
      return;
    }
    Random rngToUse = dropRng == null ? rng : dropRng;
    for (MobLootBundleSpec bundle : loot.bundles()) {
      if (bundle == null) {
        continue;
      }
      if (bundle.conditions() != null && !bundle.conditions().matches(spec, loc, killer)) {
        continue;
      }
      int totalRolls = bundle.rolls() + bundle.bonusRolls();
      for (int i = 0; i < totalRolls; i++) {
        if (bundle.chance() < 1.0 && rngToUse.nextDouble() > bundle.chance()) {
          continue;
        }
        for (MobDropSpec drop : bundle.drops()) {
          dropLootItem(spec, loot, drop, loc, killer, modifiers, true, rngToUse);
        }
      }
    }
  }

  private void applyLootRewards(MobSpec spec, MobLootSpec loot, Location loc, Player lootKiller, Player xpKiller) {
    MobLootRewardSpec rewards = loot.rewards();
    if (rewards == null) {
      return;
    }
    if (xpKiller != null) {
      List<Player> xpRecipients = resolvePartyRecipients(xpKiller, loc, partyXpShareMode, partyXpRequireAssist);
      int xpTotal = rewards.xp();
      int skillTotal = rewards.skillPoints();
      if (xpTotal > 0 && !xpRecipients.isEmpty()) {
        distributeXp(spec, xpRecipients, xpTotal);
      }
      if (skillTotal > 0 && progressionService != null && !xpRecipients.isEmpty()) {
        distributeSkillPoints(xpRecipients, skillTotal);
      }
    }
    if (rewards.tokens() > 0) {
      distributeTokens(loc, lootKiller, rewards.tokens());
    }
    for (ItemStack item : rewards.items()) {
      if (item == null || item.getType().isAir()) {
        continue;
      }
      distributeRewardItem(loc, lootKiller, item);
    }
  }

  private List<Player> resolvePartyRecipients(Player killer, Location loc, PartyShareMode mode, boolean requireAssist) {
    List<Player> recipients = new ArrayList<>();
    if (killer == null) {
      return recipients;
    }
    recipients.add(killer);
    if (partyService == null || mode == PartyShareMode.NONE) {
      return recipients;
    }
    Party party = partyService.partyOf(killer);
    if (party == null || party.size() <= 1) {
      return recipients;
    }
    double radius = requireAssist ? partyAssistRules.radiusForParty(party) : 0.0;
    double radiusSquared = radius * radius;
    for (UUID memberId : party.members()) {
      if (memberId == null || memberId.equals(killer.getUniqueId())) {
        continue;
      }
      Player member = Bukkit.getPlayer(memberId);
      if (member == null) {
        continue;
      }
      if (!member.getWorld().equals(loc.getWorld())) {
        continue;
      }
      if (requireAssist && radius > 0.0 && member.getLocation().distanceSquared(loc) > radiusSquared) {
        continue;
      }
      recipients.add(member);
    }
    return recipients;
  }

  private void distributeXp(MobSpec spec, List<Player> recipients, int total) {
    if (recipients.isEmpty() || total <= 0) {
      return;
    }
    int per = total;
    int remainder = 0;
    if (partyXpShareMode == PartyShareMode.SPLIT && recipients.size() > 1) {
      per = total / recipients.size();
      remainder = total % recipients.size();
    }
    for (int i = 0; i < recipients.size(); i++) {
      int amount = per + (remainder > 0 && i < remainder ? 1 : 0);
      if (amount <= 0) {
        continue;
      }
      Player recipient = recipients.get(i);
      if (customXpService != null) {
        customXpService.awardXp(recipient, amount);
      } else if (progressionService != null) {
        progressionService.awardXp(recipient, amount, ProgressionAwardSource.MOB_KILL, spec.id());
      } else {
        recipient.giveExp(amount);
      }
    }
  }

  private void distributeSkillPoints(List<Player> recipients, int total) {
    int per = total;
    int remainder = 0;
    if (partyXpShareMode == PartyShareMode.SPLIT && recipients.size() > 1) {
      per = total / recipients.size();
      remainder = total % recipients.size();
    }
    for (int i = 0; i < recipients.size(); i++) {
      int amount = per + (remainder > 0 && i < remainder ? 1 : 0);
      if (amount <= 0) {
        continue;
      }
      progressionService.awardSkillPoints(recipients.get(i), amount);
    }
  }

  private void distributeTokens(Location loc, Player lootKiller, int total) {
    if (partyLootShareMode == PartyLootShareMode.NONE || partyService == null || lootKiller == null) {
      if (lootKiller != null && advancementService != null) {
        advancementService.recordTokensEarned(lootKiller, total);
      }
      dropTokenBundle(loc, total);
      return;
    }
    if (partyLootShareMode == PartyLootShareMode.LEADER_ONLY) {
      if (lootKiller != null && advancementService != null) {
        advancementService.recordTokensEarned(lootKiller, total);
      }
      dropTokenBundle(loc, total);
      return;
    }
    List<Player> recipients = resolvePartyRecipients(lootKiller, loc, PartyShareMode.FULL, partyLootRequireAssist);
    if (recipients.isEmpty()) {
      dropTokenBundle(loc, total);
      return;
    }
    if (partyLootShareMode == PartyLootShareMode.DUPLICATE) {
      for (Player recipient : recipients) {
        if (advancementService != null) {
          advancementService.recordTokensEarned(recipient, total);
        }
        dropTokenBundle(recipient.getLocation(), total);
      }
      return;
    }
    if (partyLootShareMode == PartyLootShareMode.SPLIT) {
      int per = total / recipients.size();
      int remainder = total % recipients.size();
      for (int i = 0; i < recipients.size(); i++) {
        int amount = per + (remainder > 0 && i < remainder ? 1 : 0);
        if (amount <= 0) {
          continue;
        }
        Player recipient = recipients.get(i);
        if (advancementService != null) {
          advancementService.recordTokensEarned(recipient, amount);
        }
        dropTokenBundle(recipient.getLocation(), amount);
      }
    }
  }

  private void distributeRewardItem(Location loc, Player lootKiller, ItemStack item) {
    if (partyLootShareMode == PartyLootShareMode.NONE || partyService == null || lootKiller == null) {
      if (lootKiller != null) {
        giveItemOrDrop(lootKiller, item.clone());
      } else {
        dropStackedItem(loc, item, item.getAmount());
      }
      return;
    }
    if (partyLootShareMode == PartyLootShareMode.LEADER_ONLY) {
      giveItemOrDrop(lootKiller, item.clone());
      return;
    }
    List<Player> recipients = resolvePartyRecipients(lootKiller, loc, PartyShareMode.FULL, partyLootRequireAssist);
    if (recipients.isEmpty()) {
      giveItemOrDrop(lootKiller, item.clone());
      return;
    }
    if (partyLootShareMode == PartyLootShareMode.DUPLICATE) {
      for (Player recipient : recipients) {
        giveItemOrDrop(recipient, item.clone());
      }
      return;
    }
    if (partyLootShareMode == PartyLootShareMode.SPLIT) {
      int total = item.getAmount();
      int per = total / recipients.size();
      int remainder = total % recipients.size();
      for (int i = 0; i < recipients.size(); i++) {
        int amount = per + (remainder > 0 && i < remainder ? 1 : 0);
        if (amount <= 0) {
          continue;
        }
        ItemStack portion = item.clone();
        portion.setAmount(amount);
        giveItemOrDrop(recipients.get(i), portion);
      }
    }
  }

  private void dropLootItem(MobSpec spec, MobLootSpec loot, MobDropSpec drop, Location loc, Player killer,
      LootModifiers modifiers, boolean applyModifiers, Random dropRng) {
    Random rngToUse = dropRng == null ? rng : dropRng;
    if (drop.conditions() != null && !drop.conditions().matches(spec, loc, killer)) {
      return;
    }
    int amount = rollAmount(drop, modifiers, applyModifiers, rngToUse);
    if (amount <= 0) {
      return;
    }
    if (drop.tokenDrop()) {
      if (shopRegistry == null) {
        dropStackedItem(loc, drop.item(), amount);
        maybeAnnounceDrop(loot, spec, killer, drop, amount, drop.item());
        return;
      }
      if (advancementService != null && killer != null) {
        advancementService.recordTokensEarned(killer, amount);
      }
      dropTokenBundle(loc, amount);
      maybeAnnounceDrop(loot, spec, killer, drop, amount, defaultTokenItem());
      return;
    }
    ItemStack item = drop.item().clone();
    if (item.getType().isAir()) {
      return;
    }
    item.setAmount(amount);
    applyRandomDurability(item, drop, rngToUse);
    loc.getWorld().dropItemNaturally(loc, item);
    if (craftingDiscovery != null && killer != null) {
      craftingDiscovery.unlockFromDrop(killer, item);
    }
    maybeAnnounceDrop(loot, spec, killer, drop, amount, item);
  }

  private static void applyRandomDurability(ItemStack item, MobDropSpec drop, Random rng) {
    int maxDurability = item.getType().getMaxDurability();
    if (maxDurability <= 0) {
      return;
    }
    if (!(item.getItemMeta() instanceof Damageable damageable)) {
      return;
    }
    int maxDamage = maxDurability - 1;
    int minDamage = 0;
    if (drop != null && (drop.minDamage() != null || drop.maxDamage() != null)) {
      minDamage = Math.max(0, drop.minDamage() == null ? 0 : drop.minDamage());
      maxDamage = drop.maxDamage() == null ? maxDamage : drop.maxDamage();
    }
    if (maxDamage < minDamage) {
      int swap = minDamage;
      minDamage = maxDamage;
      maxDamage = swap;
    }
    maxDamage = Math.max(0, Math.min(maxDamage, maxDurability - 1));
    minDamage = Math.max(0, Math.min(minDamage, maxDamage));
    int range = maxDamage - minDamage + 1;
    int damage = range <= 1 ? minDamage : minDamage + rng.nextInt(range);
    damageable.setDamage(damage);
    item.setItemMeta(damageable);
  }

  private void dropTokenBundle(Location loc, int amount) {
    if (amount <= 0 || shopRegistry == null) {
      return;
    }
    ItemStack palletItem = shopRegistry.resolveTokenItem("pallet");
    ItemStack compressedItem = shopRegistry.resolveTokenItem("compressed");
    ItemStack normalItem = shopRegistry.resolveTokenItem("token");
    int remaining = amount;
    if (palletItem != null && !palletItem.getType().isAir()) {
      int pallets = remaining / 4096;
      remaining %= 4096;
      dropTokenTier(loc, palletItem, pallets);
    }
    if (compressedItem != null && !compressedItem.getType().isAir()) {
      int compressed = remaining / 64;
      remaining %= 64;
      dropTokenTier(loc, compressedItem, compressed);
    }
    if (normalItem != null && !normalItem.getType().isAir()) {
      dropTokenTier(loc, normalItem, remaining);
    }
  }

  private static void dropTokenTier(Location loc, ItemStack item, int amount) {
    if (amount <= 0 || item == null || item.getType().isAir()) {
      return;
    }
    int maxStack = item.getMaxStackSize();
    int remaining = amount;
    while (remaining > 0) {
      int stackAmount = Math.min(remaining, maxStack);
      ItemStack stack = item.clone();
      stack.setAmount(stackAmount);
      loc.getWorld().dropItemNaturally(loc, stack);
      remaining -= stackAmount;
    }
  }

  private static void dropStackedItem(Location loc, ItemStack item, int amount) {
    if (amount <= 0 || item == null || item.getType().isAir()) {
      return;
    }
    int maxStack = item.getMaxStackSize();
    int remaining = amount;
    while (remaining > 0) {
      int stackAmount = Math.min(remaining, maxStack);
      ItemStack stack = item.clone();
      stack.setAmount(stackAmount);
      loc.getWorld().dropItemNaturally(loc, stack);
      remaining -= stackAmount;
    }
  }

  private void giveItemOrDrop(Player player, ItemStack item) {
    if (player == null || item == null || item.getType().isAir()) {
      return;
    }
    if (craftingDiscovery != null) {
      craftingDiscovery.unlockFromDrop(player, item);
    }
    Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
    if (leftover.isEmpty()) {
      return;
    }
    Location loc = player.getLocation();
    for (ItemStack stack : leftover.values()) {
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      loc.getWorld().dropItemNaturally(loc, stack);
    }
  }

  private record LootModifiers(double multiplier, double add) {
  }

  private LootModifiers buildLootModifiers(MobLootSpec loot, Player killer) {
    return buildLootModifiers(loot == null ? 0.0 : loot.luckMultiplier(), killer);
  }

  private LootModifiers buildLootModifiers(double luckMultiplier, Player killer) {
    double multiplier = 1.0;
    double add = 0.0;
    if (killer != null) {
      AttributeInstance luck = killer.getAttribute(Attribute.LUCK);
      if (luck != null && luckMultiplier > 0.0) {
        multiplier *= 1.0 + luck.getValue() * luckMultiplier;
      }
      for (ItemStack item : killer.getInventory().getContents()) {
        LootModifiers modifiers = lootModifiersForItem(item);
        multiplier *= modifiers.multiplier();
        add += modifiers.add();
      }
      LootModifiers offhand = lootModifiersForItem(killer.getInventory().getItemInOffHand());
      multiplier *= offhand.multiplier();
      add += offhand.add();
      LootModifiers helmet = lootModifiersForItem(killer.getInventory().getHelmet());
      multiplier *= helmet.multiplier();
      add += helmet.add();
      LootModifiers chest = lootModifiersForItem(killer.getInventory().getChestplate());
      multiplier *= chest.multiplier();
      add += chest.add();
      LootModifiers legs = lootModifiersForItem(killer.getInventory().getLeggings());
      multiplier *= legs.multiplier();
      add += legs.add();
      LootModifiers boots = lootModifiersForItem(killer.getInventory().getBoots());
      multiplier *= boots.multiplier();
      add += boots.add();
    }
    return new LootModifiers(multiplier, add);
  }

  private static LootModifiers lootModifiersForItem(ItemStack item) {
    if (item == null || item.getType().isAir()) {
      return new LootModifiers(1.0, 0.0);
    }
    java.util.Map<String, Double> mods = ItemMarkers.getUpgradeModifiers(item);
    if (mods.isEmpty()) {
      return new LootModifiers(1.0, 0.0);
    }
    double mult = mods.getOrDefault(UpgradeModifierType.LOOT_MULT.key(),
        UpgradeModifierType.LOOT_MULT.defaultValue());
    double add = mods.getOrDefault(UpgradeModifierType.LOOT_ADD.key(),
        UpgradeModifierType.LOOT_ADD.defaultValue());
    if (mult == UpgradeModifierType.LOOT_MULT.defaultValue() && add == 0.0) {
      return new LootModifiers(1.0, 0.0);
    }
    return new LootModifiers(mult, add);
  }

  private int rollAmount(MobDropSpec drop, LootModifiers modifiers, boolean applyModifiers, Random rng) {
    double chance = drop.chance();
    if (applyModifiers && modifiers != null) {
      chance = chance * modifiers.multiplier() + modifiers.add();
    }
    if (chance <= 0.0) {
      return 0;
    }
    if (chance < 1.0 && rng.nextDouble() > chance) {
      return 0;
    }
    if (drop.minAmount() == drop.maxAmount()) {
      return drop.minAmount();
    }
    return drop.minAmount() + rng.nextInt(drop.maxAmount() - drop.minAmount() + 1);
  }

  private static Random buildDeterministicLootRandom(MobSpec spec, MobLootSpec loot, Location loc, Player killer) {
    long seed = loot == null ? 0L : loot.seedSalt();
    if (spec != null && spec.id() != null) {
      seed = seed * 31L + spec.id().hashCode();
    }
    if (loc != null) {
      seed = seed * 31L + loc.getBlockX();
      seed = seed * 31L + loc.getBlockY();
      seed = seed * 31L + loc.getBlockZ();
      if (loc.getWorld() != null) {
        seed = seed * 31L + loc.getWorld().getUID().hashCode();
      }
    }
    if (killer != null) {
      seed = seed * 31L + killer.getUniqueId().hashCode();
    }
    return new Random(seed);
  }

  private void maybeAnnounceDrop(MobLootSpec loot, MobSpec spec, Player killer, MobDropSpec drop, int amount,
      ItemStack displayItem) {
    if (loot == null || loot.announceTiers().isEmpty()) {
      return;
    }
    String tier = drop.tier();
    if (tier == null || !loot.announceTiers().contains(tier.toLowerCase(Locale.ROOT))) {
      return;
    }
    String template = loot.announceTemplate();
    if (template == null || template.isBlank()) {
      template = "<gold>{player}</gold> found <yellow>{item}</yellow> from <red>{mob}</red>!";
    }
    String playerName = killer == null ? "Unknown" : killer.getName();
    String mobName = displayName(spec);
    String itemName = displayName(displayItem);
    String message = template
        .replace("{player}", playerName)
        .replace("{mob}", mobName)
        .replace("{item}", itemName)
        .replace("{tier}", tier == null ? "" : tier)
        .replace("{amount}", String.valueOf(amount));
    Bukkit.broadcast(MobText.parse(message));
  }

  private static String displayName(ItemStack item) {
    if (item == null) {
      return "Unknown";
    }
    var meta = item.getItemMeta();
    if (meta != null && meta.hasDisplayName()) {
      return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
    }
    return item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
  }

  private static String displayName(MobSpec spec) {
    if (spec == null) {
      return "Unknown";
    }
    Component name = spec.displayName();
    if (name != null && !Component.empty().equals(name)) {
      String plain = PlainTextComponentSerializer.plainText().serialize(name);
      if (plain != null && !plain.isBlank()) {
        return plain;
      }
    }
    return spec.id();
  }

  private ItemStack defaultTokenItem() {
    if (shopRegistry == null) {
      return null;
    }
    ItemStack token = shopRegistry.resolveTokenItem("token");
    return token == null ? null : token;
  }

  private void applyManaDrops(MobSpec spec, LivingEntity entity, Player killer) {
    MobManaDropSpec drop = spec.manaDrop();
    if (drop == null || drop.isEmpty()) {
      return;
    }
    ManaProvider provider = engine.manaProvider();
    if (provider == null) {
      return;
    }
    if (killer == null) {
      return;
    }
    String resourceId = drop.resourceId();
    if (drop.killer() != null && !drop.killer().isEmpty()) {
      double amount = drop.killer().roll(rng) * drop.rollTierMultiplier(rng);
      amount *= streakMultiplier(killer, drop);
      addMana(provider, killer, resourceId, amount, drop.capPerKill());
    }
    if (drop.nearby() != null && !drop.nearby().isEmpty() && drop.nearbyRadius() > 0.0) {
      double radius = drop.nearbyRadius();
      double radiusSq = radius * radius;
      var loc = entity.getLocation();
      for (Player player : entity.getWorld().getPlayers()) {
        if (player.getLocation().distanceSquared(loc) <= radiusSq) {
          double amount = drop.nearby().roll(rng) * drop.rollTierMultiplier(rng);
          addMana(provider, player, resourceId, amount, drop.capPerKill());
        }
      }
    }
  }

  private double streakMultiplier(Player player, MobManaDropSpec drop) {
    if (player == null || drop == null || drop.streak() == null) {
      return 1.0;
    }
    MobManaDropSpec.MobManaStreak streak = drop.streak();
    if (streak.maxStacks() <= 0 || streak.multiplier() <= 0.0) {
      return 1.0;
    }
    long now = engine.tickNow();
    ManaKillStreak prev = manaKillStreaks.get(player.getUniqueId());
    int next = 1;
    if (prev != null) {
      if (streak.windowTicks() <= 0L || now - prev.lastKillTick() <= streak.windowTicks()) {
        next = Math.min(streak.maxStacks(), prev.streak() + 1);
      }
    }
    manaKillStreaks.put(player.getUniqueId(), new ManaKillStreak(now, next));
    if (next <= 1) {
      return 1.0;
    }
    return 1.0 + (next - 1) * streak.multiplier();
  }

  private void addMana(ManaProvider provider, Player player, String resourceId, double amount, double cap) {
    if (provider == null || player == null || resourceId == null || resourceId.isBlank()) {
      return;
    }
    if (!Double.isFinite(amount) || amount <= 0.0) {
      return;
    }
    if (cap > 0.0) {
      amount = Math.min(cap, amount);
    }
    double gainCap = engine.manaGainMaxPerTick();
    if (gainCap > 0.0) {
      amount = Math.min(amount, gainCap);
    }
    double max = provider.getMax(player, resourceId);
    if (max <= 0.0) {
      return;
    }
    double current = provider.get(player, resourceId);
    provider.set(player, resourceId, Math.min(max, current + amount));
  }

  public Map<String, Integer> countById() {
    Map<String, Integer> out = new LinkedHashMap<>();
    for (MobInstance inst : active.values()) {
      out.put(inst.specId(), out.getOrDefault(inst.specId(), 0) + 1);
    }
    return out;
  }

  public java.util.List<MobSnapshot> snapshots() {
    java.util.List<MobSnapshot> out = new ArrayList<>();
    for (Map.Entry<UUID, MobInstance> entry : active.entrySet()) {
      UUID entityId = entry.getKey();
      MobInstance inst = entry.getValue();
      Entity entity = Bukkit.getEntity(entityId);
      if (!(entity instanceof LivingEntity living)) {
        continue;
      }
      MobState state = states.get(entityId);
      String variantId = state == null ? null : state.variantId;
      String traitId = state == null ? null : state.traitId;
      Location loc = living.getLocation();
      double maxHealth = maxHealth(living);
      out.add(new MobSnapshot(
          entityId,
          inst.specId(),
          variantId,
          traitId,
          inst.ownerId(),
          loc.getWorld() == null ? "unknown" : loc.getWorld().getName(),
          loc.getX(),
          loc.getY(),
          loc.getZ(),
          living.getHealth(),
          maxHealth));
    }
    return out;
  }

  public MobSnapshot snapshot(UUID entityId) {
    Entity entity = Bukkit.getEntity(entityId);
    if (!(entity instanceof LivingEntity living)) {
      return null;
    }
    MobInstance inst = active.get(entityId);
    if (inst == null) {
      return null;
    }
    MobState state = states.get(entityId);
    String variantId = state == null ? null : state.variantId;
    String traitId = state == null ? null : state.traitId;
    Location loc = living.getLocation();
    double maxHealth = maxHealth(living);
    return new MobSnapshot(
        entityId,
        inst.specId(),
        variantId,
        traitId,
        inst.ownerId(),
        loc.getWorld() == null ? "unknown" : loc.getWorld().getName(),
        loc.getX(),
        loc.getY(),
        loc.getZ(),
        living.getHealth(),
        maxHealth);
  }

  public int restoreSnapshots(List<MobSnapshot> snapshots) {
    if (snapshots == null || snapshots.isEmpty()) {
      return 0;
    }
    int restored = 0;
    for (MobSnapshot snapshot : snapshots) {
      if (snapshot == null || snapshot.mobId() == null || snapshot.world() == null) {
        continue;
      }
      World world = Bukkit.getWorld(snapshot.world());
      if (world == null || !worldAllowed.test(world)) {
        continue;
      }
      MobSpec spec = get(snapshot.mobId());
      if (spec == null) {
        continue;
      }
      Location location = new Location(world, snapshot.x(), snapshot.y(), snapshot.z());
      LivingEntity entity = spawnRestored(spec, snapshot, location);
      if (entity == null) {
        continue;
      }
      double maxHealth = maxHealth(entity);
      double desired = snapshot.health();
      if (Double.isFinite(desired) && desired > 0.0) {
        entity.setHealth(Math.min(maxHealth, desired));
      }
      restored++;
    }
    return restored;
  }

  private LivingEntity spawnRestored(MobSpec spec, MobSnapshot snapshot, Location location) {
    Entity entity = location.getWorld().spawnEntity(location, spec.entityType());
    if (!(entity instanceof LivingEntity living)) {
      entity.remove();
      return null;
    }
    MobVariantSpec variant = resolveVariant(spec, snapshot.variantId());
    MobTraitSpec trait = resolveTrait(spec, snapshot.traitId());
    applySpec(spec, living, snapshot.ownerId(), variant, trait);
    active.put(living.getUniqueId(), new MobInstance(spec.id(), snapshot.ownerId()));
    MobState state = new MobState();
    state.home = living.getLocation().clone();
    state.lastPosition = state.home.clone();
    state.lastMoveTick = engine.tickNow();
    state.variantId = variant == null ? null : variant.id();
    state.traitId = trait == null ? null : trait.id();
    state.baseScale = readScale(living);
    states.put(living.getUniqueId(), state);
    MobContext ctx = new MobContext(spec, living, snapshot.ownerId());
    spec.onSpawn().accept(ctx);
    spawnComposite(spec, living, snapshot.ownerId());
    return living;
  }

  private MobVariantSpec resolveVariant(MobSpec spec, String variantId) {
    if (variantId == null || variantId.isBlank()) {
      return null;
    }
    String normalized = Ids.normalize(variantId);
    for (MobVariantSpec variant : spec.variants()) {
      if (normalized.equals(variant.id())) {
        return variant;
      }
    }
    return null;
  }

  private MobTraitSpec resolveTrait(MobSpec spec, String traitId) {
    if (traitId == null || traitId.isBlank()) {
      return null;
    }
    String normalized = Ids.normalize(traitId);
    for (MobTraitSpec trait : spec.traits()) {
      if (normalized.equals(trait.id())) {
        return trait;
      }
    }
    return null;
  }

  private LivingEntity resolveTarget(UUID targetId) {
    if (targetId == null) {
      return null;
    }
    Entity entity = org.bukkit.Bukkit.getEntity(targetId);
    if (entity instanceof LivingEntity living && living.isValid() && !living.isDead()) {
      return living;
    }
    return null;
  }

  private boolean isMinion(LivingEntity entity) {
    return MobMarkers.getMinionId(entity) != null;
  }

  private LivingEntity resolveOwner(LivingEntity entity) {
    UUID ownerId = MobMarkers.getOwner(entity);
    if (ownerId == null) {
      return null;
    }
    Entity owner = Bukkit.getEntity(ownerId);
    if (owner instanceof LivingEntity living && living.isValid() && !living.isDead()) {
      return living;
    }
    return null;
  }

  private LivingEntity resolveOwnerTarget(LivingEntity owner) {
    if (owner == null) {
      return null;
    }
    if (owner instanceof Mob mob) {
      LivingEntity target = mob.getTarget();
      if (target != null && target.isValid() && !target.isDead()) {
        return target;
      }
    }
    if (owner instanceof Player player) {
      Entity target = player.getTargetEntity(32);
      if (target instanceof LivingEntity living && living.isValid() && !living.isDead()) {
        return living;
      }
    }
    return null;
  }

  private MinionMode minionMode(LivingEntity minion, LivingEntity owner) {
    if (minionManager == null) {
      return MinionMode.AGGRESSIVE;
    }
    UUID ownerId = owner == null ? MobMarkers.getOwner(minion) : owner.getUniqueId();
    return minionManager.mode(minion.getUniqueId(), ownerId);
  }

  private LivingEntity resolveOwnerLastAttacker(LivingEntity owner) {
    if (owner == null || minionManager == null) {
      return null;
    }
    UUID attackerId = minionManager.ownerLastAttacker(owner.getUniqueId());
    return resolveTarget(attackerId);
  }

  private boolean isFriendlyTarget(LivingEntity mob, LivingEntity target) {
    if (mob == null || target == null) {
      return false;
    }
    UUID ownerId = MobMarkers.getOwner(mob);
    if (ownerId == null) {
      return false;
    }
    if (isMinion(mob) && target instanceof Player player && !player.getUniqueId().equals(ownerId)) {
      if (minionManager == null) {
        return true;
      }
      MinionTargetRules rules = minionManager.targetRules(mob.getUniqueId());
      if (!rules.allowPvp()) {
        return true;
      }
      if (!rules.allowPartyTargets() && partyService != null) {
        dev.patric.dungeonsreborn.party.Party party = partyService.partyOf(player);
        dev.patric.dungeonsreborn.party.Party ownerParty = partyService.partyOf(Bukkit.getPlayer(ownerId));
        if (party != null && ownerParty != null && party.id().equals(ownerParty.id())) {
          return true;
        }
      }
    }
    if (target.getUniqueId().equals(ownerId)) {
      return true;
    }
    UUID targetOwner = MobMarkers.getOwner(target);
    return ownerId.equals(targetOwner);
  }

  private boolean isValidTarget(LivingEntity mob, LivingEntity target, double radius) {
    if (target == null || !target.isValid() || target.isDead()) {
      return false;
    }
    if (mob.getWorld() != target.getWorld()) {
      return false;
    }
    if (!isAllowedSpawnTarget(mob, target)) {
      return false;
    }
    if (radius <= 0.0) {
      return true;
    }
    if (mob.getLocation().distanceSquared(target.getLocation()) > radius * radius) {
      return false;
    }
    if (isMinion(mob) && minionManager != null) {
      MinionTargetRules rules = minionManager.targetRules(mob.getUniqueId());
      double ownerRadius = rules.maxDistanceFromOwner();
      if (ownerRadius > 0.0) {
        LivingEntity owner = resolveOwner(mob);
        if (owner != null && owner.getLocation().distanceSquared(target.getLocation()) > ownerRadius * ownerRadius) {
          return false;
        }
      }
    }
    return true;
  }

  private LivingEntity selectAggroTarget(LivingEntity mob, MobState state, MobAiSpec ai) {
    double radius = ai.aggroRadius();
    if (isMinion(mob)) {
      LivingEntity owner = resolveOwner(mob);
      MinionMode mode = minionMode(mob, owner);
      if (mode == MinionMode.PASSIVE || mode == MinionMode.HOLD || mode == MinionMode.AVOID) {
        return null;
      }
      MinionTargetRules rules = minionManager == null
          ? MinionTargetRules.DEFAULT
          : minionManager.targetRules(mob.getUniqueId());
      boolean shareOwnerAggro = rules.shareOwnerAggro();
      LivingEntity ownerTarget = shareOwnerAggro ? resolveOwnerTarget(owner) : null;
      if (mode == MinionMode.FOLLOW) {
        if (ownerTarget != null && isValidTarget(mob, ownerTarget, radius) && !isFriendlyTarget(mob, ownerTarget)) {
          return ownerTarget;
        }
        return null;
      }
      if (mode == MinionMode.ASSIST) {
        if (ownerTarget != null && isValidTarget(mob, ownerTarget, radius) && !isFriendlyTarget(mob, ownerTarget)) {
          return ownerTarget;
        }
        LivingEntity last = shareOwnerAggro ? resolveOwnerLastAttacker(owner) : null;
        if (last != null && isValidTarget(mob, last, radius) && !isFriendlyTarget(mob, last)) {
          return last;
        }
        return null;
      }
      if (mode == MinionMode.DEFENSIVE || mode == MinionMode.GUARD) {
        LivingEntity last = shareOwnerAggro ? resolveOwnerLastAttacker(owner) : null;
        if (last != null && isValidTarget(mob, last, radius) && !isFriendlyTarget(mob, last)) {
          return last;
        }
        return null;
      }
      if (ownerTarget != null && isValidTarget(mob, ownerTarget, radius) && !isFriendlyTarget(mob, ownerTarget)) {
        return ownerTarget;
      }
      LivingEntity hostile = MobTargeting.nearestHostile(mob, radius);
      return isFriendlyTarget(mob, hostile) ? null : hostile;
    }
    if (ai.preferLastAttacker()) {
      LivingEntity last = resolveTarget(state.lastAttacker);
      if (last != null && isValidTarget(mob, last, radius) && !isFriendlyTarget(mob, last)) {
        return last;
      }
    }
    LivingEntity candidate = switch (ai.aggroTargetMode()) {
      case NEAREST_HOSTILE -> MobTargeting.nearestHostile(mob, radius);
      case NEAREST_PLAYER -> MobTargeting.nearestPlayer(mob, radius);
      case LAST_ATTACKER -> resolveTarget(state.lastAttacker);
      case WEIGHT_DISTANCE -> MobTargeting.weightedByDistance(mob, radius);
      case WEIGHT_THREAT -> MobTargeting.weightedByThreat(mob, radius, state.threat);
      case PARTY_LEADER -> partyService == null ? MobTargeting.nearestPlayer(mob, radius)
          : MobTargeting.nearestPartyLeader(mob, partyService, radius);
    };
    LivingEntity normalized = normalizePartyTarget(candidate, ai);
    return isFriendlyTarget(mob, normalized) ? null : normalized;
  }

  private LivingEntity normalizePartyTarget(LivingEntity candidate, MobAiSpec ai) {
    if (!(candidate instanceof Player player)) {
      return candidate;
    }
    if (partyService == null || ai == null) {
      return candidate;
    }
    dev.patric.dungeonsreborn.party.Party party = partyService.partyOf(player);
    if (party == null) {
      return candidate;
    }
    if (ai.partyRule() == MobPartyRule.AVOID_PARTY && !party.leader().equals(player.getUniqueId())) {
      return null;
    }
    if (ai.partyRule() == MobPartyRule.FOCUS_LEADER) {
      Player leader = Bukkit.getPlayer(party.leader());
      if (leader != null && leader.isValid() && !leader.isDead()) {
        return leader;
      }
    }
    return candidate;
  }

  private void setTarget(LivingEntity mob, MobState state, LivingEntity target, long now) {
    setTargetInternal(mob, state, target, now, true);
  }

  private void setTargetTracked(LivingEntity mob, MobState state, LivingEntity target, long now) {
    setTargetInternal(mob, state, target, now, false);
  }

  private void setTargetInternal(LivingEntity mob, MobState state, LivingEntity target, long now, boolean syncVanillaTarget) {
    if (state == null || target == null) {
      return;
    }
    state.currentTarget = target.getUniqueId();
    state.lastTargetSwitchTick = now;
    if (syncVanillaTarget && mob instanceof Mob bukkitMob) {
      bukkitMob.setTarget(target);
    }
    ensureAerialAggroState(mob, target);
    MobSpec spec = resolveSpecFromEntity(mob);
    if (spec != null && spec.events() != null) {
      triggerEventAbility(spec, mob, state, MobMarkers.getOwner(mob), spec.events().onTarget(), target);
    }
  }

  private void clearTarget(LivingEntity mob, MobState state) {
    clearTargetInternal(mob, state, true);
  }

  private void clearTargetTracked(LivingEntity mob, MobState state) {
    clearTargetInternal(mob, state, false);
  }

  private void clearTargetInternal(LivingEntity mob, MobState state, boolean syncVanillaTarget) {
    if (state == null) {
      return;
    }
    state.currentTarget = null;
    if (syncVanillaTarget && mob instanceof Mob bukkitMob) {
      bukkitMob.setTarget(null);
    }
  }

  private boolean hasRangedAttack(MobSpec spec, MobPhaseSpec phase) {
    MobAttackSpec main = phase != null && phase.mainAttack() != null ? phase.mainAttack() : spec.mainAttack();
    if (main != null && main.trigger() == MobAttackTrigger.RANGED) {
      return true;
    }
    MobAttackSpec secondary = phase != null && phase.secondaryAttack() != null ? phase.secondaryAttack() : spec.secondaryAttack();
    return secondary != null && secondary.trigger() == MobAttackTrigger.RANGED;
  }

  private boolean isAllowedSpawnTarget(LivingEntity mob, LivingEntity target) {
    if (mob == null || target == null || spawnManager == null) {
      return true;
    }
    MobSpawnSpec spec = spawnManager.spawnSpecForEntity(mob);
    if (spec == null) {
      return true;
    }
    if (spec.attackIgnorePlayers() && target instanceof Player) {
      return false;
    }
    if (!spec.attackIgnoreOutsideRadius()) {
      return true;
    }
    double radius = spec.attackRadius();
    if (radius <= 0.0) {
      return true;
    }
    Location center = spec.location();
    World world = Bukkit.getWorld(spec.worldName());
    if (center == null || world == null) {
      return true;
    }
    if (!target.getWorld().equals(world)) {
      return false;
    }
    Location spawnLoc = center.clone();
    spawnLoc.setWorld(world);
    return target.getLocation().distanceSquared(spawnLoc) <= radius * radius;
  }

  private MobPhaseSpec resolvePhase(MobSpec spec, LivingEntity entity, MobState state, long now) {
    List<MobPhaseSpec> phases = spec.phases();
    if (phases.isEmpty()) {
      if (state.phaseId != null) {
        state.phaseId = null;
        state.nextPassiveTick.clear();
        state.nextMainTick = now;
        state.nextSecondaryTick = now;
      }
      return null;
    }
    double max = maxHealth(entity);
    double ratio = max <= 0.0 ? 0.0 : Math.max(0.0, entity.getHealth() / max);
    MobPhaseSpec selected = null;
    double bestThreshold = Double.POSITIVE_INFINITY;
    for (MobPhaseSpec phase : phases) {
      double threshold = phase.healthBelow();
      if (ratio <= threshold && threshold < bestThreshold) {
        selected = phase;
        bestThreshold = threshold;
      }
    }
    String nextId = selected == null ? null : selected.id();
    if (!Objects.equals(state.phaseId, nextId)) {
      state.phaseId = nextId;
      state.nextPassiveTick.clear();
      state.nextMainTick = now;
      state.nextSecondaryTick = now;
      applyPhaseOverrides(spec, entity, state, selected);
      if (spec.events() != null) {
        triggerEventAbility(spec, entity, state, MobMarkers.getOwner(entity), spec.events().onPhaseChange(), null);
      }
    }
    return selected;
  }

  private boolean consumeAiStepBudget() {
    aiStepsThisTick++;
    aiTotalSteps++;
    if (aiMaxStepsPerTick > 0 && aiStepsThisTick > aiMaxStepsPerTick) {
      aiGuardrailTrips++;
      return false;
    }
    return true;
  }

  private boolean consumePathMutationBudget() {
    aiPathMutationsThisTick++;
    aiTotalPathMutations++;
    if (aiMaxPathMutationsPerTick > 0 && aiPathMutationsThisTick > aiMaxPathMutationsPerTick) {
      aiGuardrailTrips++;
      return false;
    }
    return true;
  }

  private MobAiNavigationDriver navigationDriver(LivingEntity entity, MobAiSpec ai) {
    if (usesAirNavigation(entity, ai)) {
      return velocityNavigationDriver;
    }
    // In FULL_OVERRIDE with hard vanilla AI disable, Bukkit pathfinder moves can become no-op.
    // Force velocity driver so override intents (WANDER/FLEE/CHASE) stay effective.
    if (ai != null
        && ai.isFullOverride()
        && aiFullOverrideHardDisableVanilla
        && ai.runtimeModel() != MobAiRuntimeModel.NATURAL_V1) {
      return velocityNavigationDriver;
    }
    if (aiPathfinderEnabled && aiUseMobGoalsApi && mobGoalsNavigationDriver.supports(entity)) {
      return mobGoalsNavigationDriver;
    }
    return velocityNavigationDriver;
  }

  private MobAiNavigationDriver navigationDriver(LivingEntity entity) {
    return navigationDriver(entity, null);
  }

  private boolean navigateToward(LivingEntity entity, org.bukkit.Location target, double speed, MobAiSpec ai) {
    if (entity == null || target == null) {
      return false;
    }
    if (!consumePathMutationBudget()) {
      aiFallbackTicks++;
      return false;
    }
    org.bukkit.Location next = target.clone();
    if (ai != null && ai.preferGround() && !usesAirNavigation(entity, ai)) {
      next.setY(entity.getLocation().getY());
    }
    MobAiNavigationDriver driver = navigationDriver(entity, ai);
    if (driver.moveToward(entity, next, Math.max(0.0, speed))) {
      return true;
    }
    return velocityNavigationDriver.moveToward(entity, next, Math.max(0.0, speed));
  }

  private boolean navigateTowardNatural(
      MobSpec spec,
      LivingEntity entity,
      org.bukkit.Location target,
      double speed,
      MobAiSpec ai,
      MobState state) {
    if (entity == null || target == null || ai == null) {
      return false;
    }
    if (!consumePathMutationBudget()) {
      aiFallbackTicks++;
      return false;
    }
    MobAiMovementPolicy policy = resolveMovementPolicy(spec, ai, MobAiRuntimeModel.NATURAL_V1);
    org.bukkit.Location next = target.clone();
    if (ai.movementGroundClamp() && !usesAirNavigation(entity, ai)) {
      next.setY(entity.getLocation().getY());
    }
    if (policy != MobAiMovementPolicy.VELOCITY_ONLY
        && aiPathfinderEnabled
        && aiUseMobGoalsApi
        && mobGoalsNavigationDriver.supports(entity)
        && mobGoalsNavigationDriver.moveToward(entity, next, Math.max(0.0, speed))) {
      state.lastNavigationDriver = "pathfinder";
      return true;
    }
    boolean moved = velocityMoveTowardGroundAware(entity, next, Math.max(0.0, speed), ai);
    state.lastNavigationDriver = moved ? "velocity-fallback" : "none";
    return moved;
  }

  private boolean navigateAwayFrom(LivingEntity entity, LivingEntity target, double speed, MobAiSpec ai) {
    if (entity == null || target == null) {
      return false;
    }
    if (!consumePathMutationBudget()) {
      aiFallbackTicks++;
      return false;
    }
    MobAiNavigationDriver driver = navigationDriver(entity, ai);
    if (driver.moveAway(entity, target, Math.max(0.0, speed))) {
      return true;
    }
    return velocityNavigationDriver.moveAway(entity, target, Math.max(0.0, speed));
  }

  private boolean navigateAwayFromNatural(
      MobSpec spec,
      LivingEntity entity,
      LivingEntity target,
      double speed,
      MobAiSpec ai,
      MobState state) {
    if (entity == null || target == null || ai == null) {
      return false;
    }
    if (!consumePathMutationBudget()) {
      aiFallbackTicks++;
      return false;
    }
    MobAiMovementPolicy policy = resolveMovementPolicy(spec, ai, MobAiRuntimeModel.NATURAL_V1);
    if (policy != MobAiMovementPolicy.VELOCITY_ONLY
        && aiPathfinderEnabled
        && aiUseMobGoalsApi
        && mobGoalsNavigationDriver.supports(entity)
        && mobGoalsNavigationDriver.moveAway(entity, target, Math.max(0.0, speed))) {
      state.lastNavigationDriver = "pathfinder";
      return true;
    }
    boolean moved = velocityMoveAwayGroundAware(entity, target, Math.max(0.0, speed), ai);
    state.lastNavigationDriver = moved ? "velocity-fallback" : "none";
    return moved;
  }

  private void stopNavigation(LivingEntity entity) {
    navigationDriver(entity, null).stop(entity);
  }

  private boolean usesAirNavigation(LivingEntity entity, MobAiSpec ai) {
    return entity instanceof Bat || (ai != null && ai.locomotionMode() == MobLocomotionMode.FLY);
  }

  private boolean velocityMoveTowardGroundAware(LivingEntity entity, Location target, double speed, MobAiSpec ai) {
    if (entity == null || target == null || !entity.isValid()) {
      return false;
    }
    Vector dir = target.toVector().subtract(entity.getLocation().toVector());
    if (dir.lengthSquared() <= 1e-9) {
      return false;
    }
    Vector current = entity.getVelocity();
    if (!usesAirNavigation(entity, ai)) {
      dir.setY(0.0);
      if (dir.lengthSquared() <= 1e-9) {
        return false;
      }
      entity.setVelocity(dir.normalize().multiply(speed).setY(current.getY()));
      return true;
    }
    entity.setVelocity(dir.normalize().multiply(speed));
    return true;
  }

  private boolean velocityMoveAwayGroundAware(LivingEntity entity, LivingEntity target, double speed, MobAiSpec ai) {
    if (entity == null || target == null || !entity.isValid() || !target.isValid()) {
      return false;
    }
    Vector dir = entity.getLocation().toVector().subtract(target.getLocation().toVector());
    if (dir.lengthSquared() <= 1e-9) {
      return false;
    }
    Vector current = entity.getVelocity();
    if (!usesAirNavigation(entity, ai)) {
      dir.setY(0.0);
      if (dir.lengthSquared() <= 1e-9) {
        return false;
      }
      entity.setVelocity(dir.normalize().multiply(speed).setY(current.getY()));
      return true;
    }
    entity.setVelocity(dir.normalize().multiply(speed));
    return true;
  }

  private void ensureAerialAggroState(LivingEntity entity, LivingEntity target) {
    if (!(entity instanceof Bat bat) || target == null || !target.isValid() || target.isDead()) {
      return;
    }
    if (!bat.isAwake()) {
      bat.setAwake(true);
    }
  }

  private boolean applyTerrainAvoidanceV2(LivingEntity entity, MobState state, MobAiSpec ai) {
    if (state.home == null || entity == null || ai == null) {
      return false;
    }
    Block standing = entity.getLocation().getBlock();
    Material type = standing.getType();
    if (ai.avoidLava() && type.name().contains("LAVA")) {
      navigateToward(entity, state.home, ai.chaseSpeed(), ai);
      return true;
    }
    if (ai.avoidWater() && type.name().contains("WATER")) {
      navigateToward(entity, state.home, ai.chaseSpeed(), ai);
      return true;
    }
    if (ai.avoidPowderSnow() && type == Material.POWDER_SNOW) {
      navigateToward(entity, state.home, ai.chaseSpeed(), ai);
      return true;
    }
    if (ai.avoidCactus() && type == Material.CACTUS) {
      navigateToward(entity, state.home, ai.chaseSpeed(), ai);
      return true;
    }
    if (ai.avoidSunlight()) {
      long time = entity.getWorld().getTime();
      boolean day = time >= 0 && time <= 12300;
      if (day && entity.getLocation().getBlock().getLightFromSky() > 10) {
        navigateToward(entity, state.home, ai.chaseSpeed(), ai);
        return true;
      }
    }
    return false;
  }

  private void tryOpenDoorAhead(LivingEntity entity) {
    if (!(entity instanceof Mob)) {
      return;
    }
    org.bukkit.Location eye = entity.getEyeLocation();
    Vector dir = eye.getDirection();
    if (dir.lengthSquared() <= 1e-9) {
      return;
    }
    org.bukkit.Location front = eye.clone().add(dir.normalize().multiply(1.2));
    Block block = front.getBlock();
    if (!(block.getBlockData() instanceof Openable openable)) {
      return;
    }
    if (openable.isOpen()) {
      return;
    }
    openable.setOpen(true);
    block.setBlockData(openable, true);
  }

  private void moveToward(LivingEntity mob, org.bukkit.Location target, double speed) {
    if (target == null) {
      return;
    }
    org.bukkit.Location from = mob.getLocation();
    Vector dir = target.toVector().subtract(from.toVector());
    MobSpec spec = resolveSpecFromEntity(mob);
    MobAiSpec ai = spec == null ? null : spec.aiSpec();
    if (ai != null && ai.preferGround()) {
      dir.setY(0);
    }
    if (dir.lengthSquared() == 0) {
      return;
    }
    mob.setVelocity(dir.normalize().multiply(speed));
  }

  private void moveAwayFrom(LivingEntity mob, LivingEntity target, double speed) {
    if (target == null) {
      return;
    }
    org.bukkit.Location from = mob.getLocation();
    Vector dir = from.toVector().subtract(target.getLocation().toVector());
    if (dir.lengthSquared() == 0) {
      return;
    }
    mob.setVelocity(dir.normalize().multiply(speed));
  }

  private org.bukkit.Location randomHomeOffset(org.bukkit.Location home, double radius) {
    double angle = rng.nextDouble() * Math.PI * 2.0;
    double r = rng.nextDouble() * radius;
    double dx = Math.cos(angle) * r;
    double dz = Math.sin(angle) * r;
    return home.clone().add(dx, 0, dz);
  }

  private final class ContextImpl implements MobAiContext {
    private final MobSpec spec;
    private final LivingEntity entity;
    private final MobState state;
    private final UUID ownerId;
    private final long tick;

    private ContextImpl(MobSpec spec, LivingEntity entity, MobState state, UUID ownerId, long tick) {
      this.spec = spec;
      this.entity = entity;
      this.state = state;
      this.ownerId = ownerId;
      this.tick = tick;
    }

    @Override
    public MobSpec spec() {
      return spec;
    }

    @Override
    public LivingEntity entity() {
      return entity;
    }

    @Override
    public UUID ownerId() {
      return ownerId;
    }

    @Override
    public long tick() {
      return tick;
    }

    @Override
    public org.bukkit.Location home() {
      return state.home;
    }

    @Override
    public LivingEntity currentTarget() {
      return resolveTarget(state.currentTarget);
    }

    @Override
    public void setCurrentTarget(LivingEntity target) {
      if (target == null) {
        MobRegistry.this.clearTarget(entity, state);
        return;
      }
      setTarget(entity, state, target, tick);
    }

    @Override
    public void clearTarget() {
      MobRegistry.this.clearTarget(entity, state);
    }

    @Override
    public MobBehaviorState behaviorState() {
      return state.behaviorState;
    }

    @Override
    public void setBehaviorState(MobBehaviorState behaviorState) {
      if (behaviorState == null) {
        return;
      }
      state.behaviorState = behaviorState;
      state.lastStateChangeTick = tick;
    }

    @Override
    public void moveToward(org.bukkit.Location target, double speed) {
      MobRegistry.this.moveToward(entity, target, speed);
    }

    @Override
    public void moveAwayFrom(LivingEntity target, double speed) {
      MobRegistry.this.moveAwayFrom(entity, target, speed);
    }

    @Override
    public void teleportHome() {
      if (state.home != null) {
        entity.teleport(state.home);
      }
    }
  }

  private void playSpawnFx(MobSpec spec, LivingEntity entity) {
    if (spec.spawnParticles() != null) {
      spec.spawnParticles().spawn(entity.getLocation());
    }
    if (spec.spawnSound() != null) {
      spec.spawnSound().play(entity.getLocation());
    }
  }

  private void playDeathFx(MobSpec spec, LivingEntity entity) {
    if (spec.deathParticles() != null) {
      spec.deathParticles().spawn(entity.getLocation());
    }
    if (spec.deathSound() != null) {
      spec.deathSound().play(entity.getLocation());
    }
  }

  public void playMinionDespawnFx(LivingEntity entity) {
    if (entity == null) {
      return;
    }
    String id = MobMarkers.getMobId(entity);
    if (id == null) {
      return;
    }
    MobSpec spec = specs.get(id);
    if (spec == null) {
      return;
    }
    playDeathFx(spec, entity);
  }

  private void updateBossBar(MobSpec spec, MobPhaseSpec phase, LivingEntity entity, MobState state, UUID ownerId,
      long now) {
    MobBossBarSpec barSpec = resolveBossBar(spec, phase);
    if (barSpec == null) {
      return;
    }
    if (entity.getType() == EntityType.WITHER || entity.getType() == EntityType.ENDER_DRAGON) {
      removeBossBar(state);
      return;
    }
    if (state.bossBar == null) {
      state.bossBar = BossBar.bossBar(barSpec.title(), 1.0f, barSpec.color(), barSpec.overlay());
    } else {
      state.bossBar.name(barSpec.title());
      state.bossBar.color(barSpec.color());
      state.bossBar.overlay(barSpec.overlay());
    }

    double max = maxHealth(entity);
    double current = entity.getHealth();
    float progress = max <= 0 ? 0.0f : (float) Math.max(0.0, Math.min(1.0, current / max));
    state.bossBar.progress(progress);

    if (now >= state.nextBossBarAudienceTick) {
      state.nextBossBarAudienceTick = now + 20L;
      refreshBossBarAudience(state.bossBar, barSpec, entity, ownerId);
    }
  }

  private void refreshBossBarAudience(BossBar bar, MobBossBarSpec spec, LivingEntity entity, UUID ownerId) {
    switch (spec.audience()) {
      case OWNER_ONLY -> {
        for (var viewer : bar.viewers()) {
          if (viewer instanceof org.bukkit.entity.Player player) {
            if (ownerId == null || !player.getUniqueId().equals(ownerId)) {
              bar.removeViewer(player);
            }
          }
        }
        if (ownerId != null) {
          org.bukkit.entity.Player owner = org.bukkit.Bukkit.getPlayer(ownerId);
          if (owner != null) {
            bar.addViewer(owner);
          }
        }
      }
      case ALL_PLAYERS -> {
        if (entity.getWorld() == null) {
          return;
        }
        for (org.bukkit.entity.Player player : entity.getWorld().getPlayers()) {
          bar.addViewer(player);
        }
      }
    }
  }

  private void removeBossBar(MobState state) {
    if (state.bossBar == null) {
      return;
    }
    for (var viewer : state.bossBar.viewers()) {
      if (viewer instanceof org.bukkit.entity.Player player) {
        state.bossBar.removeViewer(player);
      }
    }
    state.bossBar = null;
  }

  private double maxHealth(LivingEntity entity) {
    var attr = entity.getAttribute(Attribute.MAX_HEALTH);
    return attr == null ? entity.getHealth() : attr.getValue();
  }
}
