package dev.patric.dungeonsreborn.effects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import dev.patric.dungeonsreborn.effects.particles.ParticleEngine;
import dev.patric.dungeonsreborn.effects.actions.EntityActions;
import dev.patric.dungeonsreborn.effects.combat.CombatEventContext;
import dev.patric.dungeonsreborn.effects.combat.CombatEventDispatcher;
import dev.patric.dungeonsreborn.effects.combat.CombatEventSource;
import dev.patric.dungeonsreborn.effects.combat.CombatEventType;
import dev.patric.dungeonsreborn.effects.combat.DamagePacket;
import dev.patric.dungeonsreborn.effects.combat.DamagePipeline;
import dev.patric.dungeonsreborn.effects.damage.DamageType;
import dev.patric.dungeonsreborn.effects.damage.DamageCause;
import dev.patric.dungeonsreborn.effects.damage.DamageAmountMode;
import dev.patric.dungeonsreborn.effects.damage.DamageSpec;
import dev.patric.dungeonsreborn.effects.heal.HealAmountMode;
import dev.patric.dungeonsreborn.effects.heal.HealSpec;
import dev.patric.dungeonsreborn.effects.afflict.AfflictionManager;
import dev.patric.dungeonsreborn.effects.actions.ActionHandle;
import dev.patric.dungeonsreborn.effects.registry.ActionType;
import dev.patric.dungeonsreborn.effects.registry.ConditionType;
import dev.patric.dungeonsreborn.effects.registry.Params;
import dev.patric.dungeonsreborn.effects.registry.TargeterType;
import dev.patric.dungeonsreborn.effects.registry.TypeRegistry;
import dev.patric.dungeonsreborn.effects.relations.Relation;
import dev.patric.dungeonsreborn.effects.relations.RelationProvider;
import dev.patric.dungeonsreborn.effects.relations.RelationProviders;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.effects.mana.ManaProvider;
import dev.patric.dungeonsreborn.effects.mana.ManaUiConfig;
import dev.patric.dungeonsreborn.effects.mana.ManaUiSettings;
import dev.patric.dungeonsreborn.effects.mana.ResourceRules;
import dev.patric.dungeonsreborn.effects.mana.SessionManaProvider;
import dev.patric.dungeonsreborn.logging.ServiceLogger;
import dev.patric.dungeonsreborn.quests.QuestRegion;

/**
 * Minimal core runtime for spell/effect casting.
 * <p>
 * This is intentionally code-first (no YAML loading): developers register abilities at startup.
 */
public final class EffectsEngine {
  public record CastResult(UUID castId, String abilityId, long tickStarted) {
  }

  public record CastRecord(UUID castId, UUID casterId, String abilityId, long tickStarted, CastState state) {
  }

  public record DamageAttribution(UUID castId, String abilityId, UUID casterId, long tick) {
  }

  public enum CastFailureType {
    REQUIREMENT,
    COST,
    COOLDOWN
  }

  public record CastFailure(UUID castId, String abilityId, CastFailureType type, String reason, long tick) {
  }

  public record EngineStats(
      long tick,
      int scheduledTickTasks,
      int scheduledRealTimeTasks,
      int trackedCastRecords,
      int cooldownPlayers,
      int immunityEntities,
      long lastTickNanos) {
  }

  public record CombatProfile(
      double damageMultiplier,
      double healMultiplier,
      double damageCap,
      double healCap,
      double maxDamagePercent,
      double maxHealPercent,
      boolean allowDamage,
      boolean allowHeal) {
    public CombatProfile {
      if (!Double.isFinite(damageMultiplier)) {
        throw new IllegalArgumentException("damageMultiplier must be finite");
      }
      if (!Double.isFinite(healMultiplier)) {
        throw new IllegalArgumentException("healMultiplier must be finite");
      }
      if (!Double.isFinite(damageCap) || damageCap < 0.0) {
        throw new IllegalArgumentException("damageCap must be finite and >= 0");
      }
      if (!Double.isFinite(healCap) || healCap < 0.0) {
        throw new IllegalArgumentException("healCap must be finite and >= 0");
      }
      if (!Double.isFinite(maxDamagePercent) || maxDamagePercent < 0.0) {
        throw new IllegalArgumentException("maxDamagePercent must be finite and >= 0");
      }
      if (!Double.isFinite(maxHealPercent) || maxHealPercent < 0.0) {
        throw new IllegalArgumentException("maxHealPercent must be finite and >= 0");
      }
    }

    public static CombatProfile defaults() {
      return new CombatProfile(1.0, 1.0, 0.0, 0.0, 0.0, 0.0, true, true);
    }
  }

  public record CombatProfilePair(CombatProfile pvp, CombatProfile pve) {
    public CombatProfilePair {
      Objects.requireNonNull(pvp, "pvp");
      Objects.requireNonNull(pve, "pve");
    }

    public CombatProfile forContext(boolean pvpMode) {
      return pvpMode ? pvp : pve;
    }
  }

  public record RegionProfile(QuestRegion region, CombatProfilePair profiles) {
    public RegionProfile {
      Objects.requireNonNull(region, "region");
      Objects.requireNonNull(profiles, "profiles");
    }
  }

  public record AbilityCombatProfile(
      CombatProfilePair baseProfiles,
      Map<String, CombatProfilePair> worldOverrides,
      List<RegionProfile> regionOverrides) {
    public AbilityCombatProfile {
      Objects.requireNonNull(baseProfiles, "baseProfiles");
      worldOverrides = worldOverrides == null ? Map.of() : Map.copyOf(worldOverrides);
      regionOverrides = regionOverrides == null ? List.of() : List.copyOf(regionOverrides);
    }
  }

  public interface ScheduledHandle {
    boolean cancel();

    boolean isCancelled();
  }

  public interface TimelineHandle extends ScheduledHandle {
    String id();

    long startTick();

    long durationTicks();

    long periodTicks();

    long currentTick();

    void subscribe(Consumer<Long> listener);
  }

  public interface DamageHook {
    double onDamage(CastContext ctx, LivingEntity target, DamageSpec spec, double amount);
  }

  public interface HealHook {
    double onHeal(CastContext ctx, LivingEntity target, HealSpec spec, double amount);
  }

  private static final class ScheduledTask implements ScheduledHandle {
    private final Runnable runnable;
    private long nextTick;
    private final long period;
    private boolean cancelled;

    ScheduledTask(Runnable runnable, long nextTick, long period) {
      this.runnable = runnable;
      this.nextTick = nextTick;
      this.period = period;
    }

    @Override
    public boolean cancel() {
      boolean was = cancelled;
      cancelled = true;
      return !was;
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }
  }

  private static final class RealTimeScheduledTask implements ScheduledHandle {
    private final Runnable runnable;
    private long nextNanos;
    private final long periodNanos;
    private boolean cancelled;

    RealTimeScheduledTask(Runnable runnable, long nextNanos, long periodNanos) {
      this.runnable = runnable;
      this.nextNanos = nextNanos;
      this.periodNanos = periodNanos;
    }

    @Override
    public boolean cancel() {
      boolean was = cancelled;
      cancelled = true;
      return !was;
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }
  }

  private static EffectsEngine instance;

  public static EffectsEngine init(JavaPlugin plugin, ServiceLogger logger) {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(logger, "logger");
    if (instance != null) {
      return instance;
    }
    instance = new EffectsEngine(plugin, logger);
    instance.start();
    return instance;
  }

  public static EffectsEngine get() {
    if (instance == null) {
      throw new IllegalStateException("EffectsEngine not initialized");
    }
    return instance;
  }

  private final JavaPlugin plugin;
  private final ServiceLogger logger;
  private final Map<String, Ability> abilities = new LinkedHashMap<>();
  private final Map<String, AbilitySpec> abilitySpecs = new LinkedHashMap<>();
  private final Map<UUID, CastRecord> castRecords = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> lastCastIdByCaster = new ConcurrentHashMap<>();
  private final Map<UUID, CastFailure> lastFailureByCaster = new ConcurrentHashMap<>();
  private final List<ScheduledTask> tasks = new ArrayList<>();
  private final List<RealTimeScheduledTask> realTimeTasks = new ArrayList<>();
  private final Map<UUID, Map<String, Long>> cooldownUntilTickByPlayer = new ConcurrentHashMap<>();
  private final Map<UUID, Map<String, Long>> immunityUntilTickByEntity = new ConcurrentHashMap<>();
  private final Map<UUID, DamageAttribution> lastDamageAttributionByVictim = new ConcurrentHashMap<>();
  private final Map<UUID, java.util.EnumMap<DamageType, ResistanceEntry>> resistancesByEntity = new ConcurrentHashMap<>();
  private final Map<UUID, ReflectEntry> reflectByEntity = new ConcurrentHashMap<>();
  private final AfflictionManager afflictions = new AfflictionManager(this);
  private final List<DamageHook> damageHooks = new CopyOnWriteArrayList<>();
  private final List<HealHook> healHooks = new CopyOnWriteArrayList<>();
  private final Map<UUID, ShieldEntry> shieldsByEntity = new ConcurrentHashMap<>();
  private final Map<String, GlobalTimeline> timelines = new ConcurrentHashMap<>();
  private final Map<String, AbilityCombatProfile> abilityCombatProfiles = new ConcurrentHashMap<>();
  private final ParticleEngine particles = new ParticleEngine();
  private final CinematicSettings cinematicSettings = new CinematicSettings();
  private final ManaUiSettings manaUiSettings = new ManaUiSettings();
  private final TypeRegistry<ActionType> actionTypes = new TypeRegistry<>("action");
  private final TypeRegistry<TargeterType<?>> targeterTypes = new TypeRegistry<>("targeter");
  private final TypeRegistry<ConditionType> conditionTypes = new TypeRegistry<>("condition");
  private final CombatEventDispatcher combatDispatcher;
  private final DamagePipeline damagePipeline = DamagePipeline.defaults();
  private volatile RelationProvider relationProvider = RelationProviders.scoreboardTeams();
  private volatile ManaProvider manaProvider;
  private volatile ManaUiConfig manaUiConfig = ManaUiConfig.defaults();
  private volatile long manaRegenPeriodTicks = 20L;
  private volatile double manaRegenAmount = 5.0;
  private volatile double manaMaxRegenPerTick;
  private volatile double manaMaxGainPerTick;
  private volatile boolean manaRegenEnabled;
  private volatile long manaRegenDelayTicks;
  private volatile long manaCombatDelayTicks;
  private volatile boolean manaTimedGrantEnabled;
  private volatile long manaTimedGrantPeriodTicks;
  private volatile double manaTimedGrantAmount;
  private volatile String manaTimedGrantResource = ManaProvider.DEFAULT_RESOURCE;
  private final ConcurrentHashMap<UUID, Long> manaSpendTicks = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, Long> combatTicks = new ConcurrentHashMap<>();
  private long tick;
  private final long startedNanos = System.nanoTime();
  private BukkitTask ticker;
  private volatile boolean debugEnabled;
  private final java.util.Map<String, Long> vfxCountsThisTick = new java.util.HashMap<>();
  private final java.util.Map<String, Long> vfxParticlesThisTick = new java.util.HashMap<>();
  private long vfxEventsThisTick;
  private volatile long lastTickNanos;
  private long lastParticleWarnTick;
  private Long deterministicSeed;

  public record ResistanceSnapshot(double previous, long token) {
  }

  public record ReflectSpec(double ratio, double flat, DamageType type, boolean ignoreResistance, EntityActions.DamagePolicy policy) {
  }

  private static final class ResistanceEntry {
    private double multiplier;
    private long token;

    private ResistanceEntry(double multiplier, long token) {
      this.multiplier = multiplier;
      this.token = token;
    }
  }

  private static final class ReflectEntry {
    private ReflectSpec spec;
    private long token;

    private ReflectEntry(ReflectSpec spec, long token) {
      this.spec = spec;
      this.token = token;
    }
  }

  private static final class ShieldEntry {
    private double amount;
    private long decayAtTick;

    private ShieldEntry(double amount, long decayAtTick) {
      this.amount = amount;
      this.decayAtTick = decayAtTick;
    }
  }

  private EffectsEngine(JavaPlugin plugin, ServiceLogger logger) {
    this.plugin = plugin;
    this.logger = logger;
    int workers = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    this.combatDispatcher = new CombatEventDispatcher(this, workers, 12_000);
  }

  public void start() {
    if (ticker != null) {
      return;
    }
    ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    logger.info("[Effects] Initialized");
  }

  public EngineStats stats() {
    return new EngineStats(
        tick,
        tasks.size(),
        realTimeTasks.size(),
        castRecords.size(),
        cooldownUntilTickByPlayer.size(),
        immunityUntilTickByEntity.size(),
        lastTickNanos);
  }

  public CinematicSettings cinematicSettings() {
    return cinematicSettings;
  }

  public ManaUiSettings manaUiSettings() {
    return manaUiSettings;
  }

  public ManaUiConfig manaUiConfig() {
    return manaUiConfig;
  }

  public void setManaUiConfig(ManaUiConfig config) {
    if (config != null) {
      this.manaUiConfig = config;
    }
  }

  public void shutdown() {
    if (ticker != null) {
      ticker.cancel();
      ticker = null;
    }
    tasks.clear();
    abilities.clear();
    cooldownUntilTickByPlayer.clear();
    resistancesByEntity.clear();
    reflectByEntity.clear();
    afflictions.clearAll();
    timelines.values().forEach(GlobalTimeline::cancel);
    timelines.clear();
    combatDispatcher.shutdown();
  }

  private final class GlobalTimeline implements TimelineHandle {
    private final String id;
    private final long startTick;
    private final long durationTicks;
    private final long periodTicks;
    private final List<Consumer<Long>> listeners = new CopyOnWriteArrayList<>();
    private ScheduledHandle handle;
    private volatile boolean cancelled;

    private GlobalTimeline(String id, long startTick, long durationTicks, long periodTicks) {
      this.id = id;
      this.startTick = startTick;
      this.durationTicks = durationTicks;
      this.periodTicks = periodTicks;
    }

    private void start() {
      handle = runRepeating(0L, periodTicks, () -> {
        if (cancelled) {
          return;
        }
        long now = tickNow();
        long elapsed = Math.max(0L, now - startTick);
        if (elapsed >= durationTicks) {
          cancel();
          timelines.remove(id);
          return;
        }
        for (Consumer<Long> listener : listeners) {
          listener.accept(elapsed);
        }
      });
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public long startTick() {
      return startTick;
    }

    @Override
    public long durationTicks() {
      return durationTicks;
    }

    @Override
    public long periodTicks() {
      return periodTicks;
    }

    @Override
    public long currentTick() {
      return Math.max(0L, tickNow() - startTick);
    }

    @Override
    public void subscribe(Consumer<Long> listener) {
      if (listener != null) {
        listeners.add(listener);
      }
    }

    @Override
    public boolean cancel() {
      boolean was = cancelled;
      cancelled = true;
      if (handle != null) {
        handle.cancel();
      }
      return !was;
    }

    @Override
    public boolean isCancelled() {
      return cancelled || (handle != null && handle.isCancelled());
    }
  }

  public TimelineHandle startTimeline(String id, long durationTicks, long periodTicks) {
    Objects.requireNonNull(id, "id");
    if (durationTicks <= 0) {
      throw new IllegalArgumentException("durationTicks must be > 0");
    }
    if (periodTicks <= 0) {
      throw new IllegalArgumentException("periodTicks must be > 0");
    }
    String normalized = normalizeId(id);
    GlobalTimeline existing = timelines.remove(normalized);
    if (existing != null) {
      existing.cancel();
    }
    GlobalTimeline timeline = new GlobalTimeline(normalized, tickNow(), durationTicks, periodTicks);
    timelines.put(normalized, timeline);
    timeline.start();
    return timeline;
  }

  public TimelineHandle timeline(String id) {
    Objects.requireNonNull(id, "id");
    return timelines.get(normalizeId(id));
  }

  public JavaPlugin plugin() {
    return plugin;
  }

  public ParticleEngine particles() {
    return particles;
  }

  public Long deterministicSeed() {
    return deterministicSeed;
  }

  public void setDeterministicSeed(Long seed) {
    deterministicSeed = seed;
  }

  public void clearDeterministicSeed() {
    deterministicSeed = null;
  }

  public TypeRegistry<ActionType> actionTypes() {
    return actionTypes;
  }

  public void registerActionType(ActionType type) {
    actionTypes.register(type);
  }

  public void registerAction(String id, java.util.function.Function<Params, dev.patric.dungeonsreborn.effects.actions.Action> factory) {
    Objects.requireNonNull(factory, "factory");
    String normalized = normalizeId(id);
    registerActionType(new ActionType() {
      @Override
      public String id() {
        return normalized;
      }

      @Override
      public dev.patric.dungeonsreborn.effects.actions.Action build(Params params) {
        return factory.apply(params == null ? Params.empty() : params);
      }
    });
  }

  public TypeRegistry<TargeterType<?>> targeterTypes() {
    return targeterTypes;
  }

  public void registerTargeterType(TargeterType<?> type) {
    targeterTypes.register(type);
  }

  public void registerCondition(String id, java.util.function.Function<Params, dev.patric.dungeonsreborn.effects.conditions.Condition> factory) {
    Objects.requireNonNull(factory, "factory");
    String normalized = normalizeId(id);
    registerConditionType(new ConditionType() {
      @Override
      public String id() {
        return normalized;
      }

      @Override
      public dev.patric.dungeonsreborn.effects.conditions.Condition build(Params params) {
        return factory.apply(params == null ? Params.empty() : params);
      }
    });
  }

  public TypeRegistry<ConditionType> conditionTypes() {
    return conditionTypes;
  }

  public void registerConditionType(ConditionType type) {
    conditionTypes.register(type);
  }

  public void setDebug(boolean enabled) {
    debugEnabled = enabled;
    logger.info("[Effects] Debug " + (enabled ? "enabled" : "disabled"));
  }

  public boolean isDebugEnabled() {
    return debugEnabled;
  }

  public void debug(String message) {
    if (!debugEnabled) {
      return;
    }
    logger.debug("[Effects] " + message);
  }

  public void warn(String message) {
    logger.warn("[Effects] " + message);
  }

  public void warn(String message, Throwable throwable) {
    logger.warn("[Effects] " + message, throwable);
  }

  public void logDamageEvent(CastContext ctx, LivingEntity target, double amount, DamageSpec spec) {
    if (!debugEnabled) {
      return;
    }
    String type = spec.type() == null ? "NONE" : spec.type().name();
    String cause = spec.cause() == null ? "NONE" : spec.cause().name();
    String mode = spec.mode() == null ? "NONE" : spec.mode().name();
    boolean pvp = target instanceof Player && ctx.caster() instanceof Player;
    logger.debug("[Effects] damage ability=" + normalizeId(ctx.abilityId())
        + " cast=" + ctx.castId()
        + " caster=" + ctx.caster().getUniqueId()
        + " target=" + target.getUniqueId()
        + " amount=" + formatAmount(amount)
        + " mode=" + mode
        + " type=" + type
        + " cause=" + cause
        + " pvp=" + pvp);
  }

  public void logHealEvent(CastContext ctx, LivingEntity target, double amount, double overheal, HealSpec spec) {
    if (!debugEnabled) {
      return;
    }
    String mode = spec.mode() == null ? "NONE" : spec.mode().name();
    logger.debug("[Effects] heal ability=" + normalizeId(ctx.abilityId())
        + " cast=" + ctx.castId()
        + " caster=" + ctx.caster().getUniqueId()
        + " target=" + target.getUniqueId()
        + " amount=" + formatAmount(amount)
        + " overheal=" + formatAmount(overheal)
        + " mode=" + mode
        + " shield=" + spec.overhealToShield());
  }

  public void logVfxEvent(CastContext ctx, String action, Particle particle, int count) {
    String abilityId = normalizeId(ctx.abilityId());
    vfxEventsThisTick++;
    vfxCountsThisTick.merge(abilityId, 1L, Long::sum);
    if (count > 0) {
      vfxParticlesThisTick.merge(abilityId, (long) count, Long::sum);
    }
    if (!debugEnabled) {
      return;
    }
    String particleName = particle == null ? "NONE" : particle.name();
    logger.debug("[Effects] vfx ability=" + normalizeId(ctx.abilityId())
        + " cast=" + ctx.castId()
        + " caster=" + ctx.caster().getUniqueId()
        + " action=" + action
        + " particle=" + particleName
        + " count=" + count);
  }

  public ServiceLogger logger() {
    return logger;
  }

  public AfflictionManager afflictions() {
    return afflictions;
  }

  public void configureAfflictions(boolean enabled, int maxTrackedPerEntity, long cleanupIntervalTicks) {
    afflictions.configure(enabled, maxTrackedPerEntity, cleanupIntervalTicks);
  }

  public long tickNow() {
    return tick;
  }

  public CombatEventDispatcher combatDispatcher() {
    return combatDispatcher;
  }

  public void configureCombat(boolean enabled, boolean debug, boolean asyncPlannerEnabled, int queueCapacity,
      long planTtlTicks, int maxEventDispatchPerTick, int maxDamagePacketsPerTick, String degradePolicy) {
    combatDispatcher.configure(enabled, debug, asyncPlannerEnabled, queueCapacity, planTtlTicks,
        maxEventDispatchPerTick, maxDamagePacketsPerTick, degradePolicy);
  }

  public void configureProjectileCombat(int maxProjectileEventsPerTick, int maxTravelStepDispatchPerTick) {
    combatDispatcher.configureProjectiles(maxProjectileEventsPerTick, maxTravelStepDispatchPerTick);
  }

  public long nanoTime() {
    return System.nanoTime();
  }

  public Duration uptime() {
    return Duration.ofNanos(Math.max(0L, nanoTime() - startedNanos));
  }

  public void setRelationProvider(RelationProvider provider) {
    this.relationProvider = Objects.requireNonNull(provider, "provider");
  }

  public RelationProvider relationProvider() {
    return relationProvider;
  }

  public Relation relation(LivingEntity caster, LivingEntity target) {
    Objects.requireNonNull(caster, "caster");
    Objects.requireNonNull(target, "target");
    RelationProvider rp = relationProvider;
    if (rp == null) {
      return caster.getUniqueId().equals(target.getUniqueId()) ? Relation.SELF : Relation.NEUTRAL;
    }
    try {
      return rp.relation(caster, target);
    } catch (Exception ex) {
      warn("relationProvider threw: " + ex.getMessage(), ex);
      return caster.getUniqueId().equals(target.getUniqueId()) ? Relation.SELF : Relation.NEUTRAL;
    }
  }

  public void setManaProvider(ManaProvider provider) {
    this.manaProvider = provider;
  }

  public ManaProvider manaProvider() {
    return manaProvider;
  }

  public long manaRegenPeriodTicks() {
    return manaRegenPeriodTicks;
  }

  public double manaRegenAmount() {
    return manaRegenAmount;
  }

  public long manaRegenDelayTicks() {
    return manaRegenDelayTicks;
  }

  public long manaCombatDelayTicks() {
    return manaCombatDelayTicks;
  }

  public boolean manaTimedGrantEnabled() {
    return manaTimedGrantEnabled;
  }

  public long manaTimedGrantPeriodTicks() {
    return manaTimedGrantPeriodTicks;
  }

  public double manaTimedGrantAmount() {
    return manaTimedGrantAmount;
  }

  public String manaTimedGrantResource() {
    return manaTimedGrantResource;
  }

  public double manaRegenMaxPerTick() {
    return manaMaxRegenPerTick;
  }

  public void enableManaRegen(long periodTicks, double amountPerTick) {
    if (periodTicks <= 0) {
      throw new IllegalArgumentException("periodTicks must be > 0");
    }
    if (amountPerTick <= 0) {
      throw new IllegalArgumentException("amountPerTick must be > 0");
    }
    manaRegenPeriodTicks = periodTicks;
    manaRegenAmount = amountPerTick;
    manaRegenEnabled = true;
  }

  public void setManaRegenDelays(long afterCastTicks, long combatDelayTicks) {
    manaRegenDelayTicks = Math.max(0L, afterCastTicks);
    manaCombatDelayTicks = Math.max(0L, combatDelayTicks);
  }

  public void setManaTimedGrant(boolean enabled, long periodTicks, double amount, String resourceId) {
    manaTimedGrantEnabled = enabled;
    manaTimedGrantPeriodTicks = Math.max(0L, periodTicks);
    manaTimedGrantAmount = amount;
    manaTimedGrantResource = resourceId == null || resourceId.isBlank()
        ? ManaProvider.DEFAULT_RESOURCE
        : resourceId.trim();
  }

  public void setManaAntiExploit(double maxRegenPerTick, double maxGainPerTick) {
    this.manaMaxRegenPerTick = Math.max(0.0, maxRegenPerTick);
    this.manaMaxGainPerTick = Math.max(0.0, maxGainPerTick);
  }

  public double manaGainMaxPerTick() {
    return manaMaxGainPerTick;
  }

  public void disableManaRegen() {
    manaRegenEnabled = false;
  }

  public boolean isManaRegenEnabled() {
    return manaRegenEnabled;
  }

  public void markManaSpend(UUID playerId) {
    if (playerId == null) {
      return;
    }
    manaSpendTicks.put(playerId, tickNow());
  }

  public void markCombat(UUID playerId) {
    if (playerId == null) {
      return;
    }
    combatTicks.put(playerId, tickNow());
  }

  public Set<String> abilityIds() {
    return Collections.unmodifiableSet(abilities.keySet());
  }

  public CastRecord castRecord(UUID castId) {
    Objects.requireNonNull(castId, "castId");
    return castRecords.get(castId);
  }

  public CastRecord lastCastRecord(UUID casterId) {
    Objects.requireNonNull(casterId, "casterId");
    UUID castId = lastCastIdByCaster.get(casterId);
    if (castId == null) {
      return null;
    }
    return castRecords.get(castId);
  }

  public CastFailure lastCastFailure(UUID casterId) {
    Objects.requireNonNull(casterId, "casterId");
    return lastFailureByCaster.get(casterId);
  }

  public void recordCastFailure(CastContext ctx, CastFailureType type, String reason) {
    Objects.requireNonNull(ctx, "ctx");
    Objects.requireNonNull(type, "type");
    String detail = reason == null || reason.isBlank() ? type.name() : reason;
    lastFailureByCaster.put(ctx.caster().getUniqueId(), new CastFailure(
        ctx.castId(),
        normalizeId(ctx.abilityId()),
        type,
        detail,
        tickNow()));
    if (debugEnabled) {
      logger.debug("[Effects] cast_failure ability=" + normalizeId(ctx.abilityId())
          + " cast=" + ctx.castId()
          + " caster=" + ctx.caster().getUniqueId()
          + " type=" + type.name()
          + " reason=\"" + detail + "\"");
    }
  }

  public void clearCastFailure(UUID casterId) {
    Objects.requireNonNull(casterId, "casterId");
    lastFailureByCaster.remove(casterId);
  }

  public AbilitySpec abilitySpec(String id) {
    Objects.requireNonNull(id, "id");
    return abilitySpecs.get(normalizeId(id));
  }

  public Map<String, AbilitySpec> abilitySpecs() {
    return Collections.unmodifiableMap(abilitySpecs);
  }

  public int cancelCasts(Predicate<CastRecord> predicate, boolean removeRecord) {
    Objects.requireNonNull(predicate, "predicate");
    int cancelled = 0;
    for (var it = castRecords.entrySet().iterator(); it.hasNext();) {
      var e = it.next();
      CastRecord r = e.getValue();
      if (r == null) {
        it.remove();
        continue;
      }
      if (!predicate.test(r)) {
        continue;
      }
      try {
        r.state().cancel();
      } catch (Exception ignored) {
      }
      cancelled++;
      if (removeRecord) {
        it.remove();
      }
    }
    return cancelled;
  }

  public void registerAbility(AbilitySpec spec) {
    Objects.requireNonNull(spec, "spec");
    String normalized = normalizeId(spec.id());
    Ability compiled = spec.compile();
    Ability existing = abilities.putIfAbsent(normalized, compiled);
    if (existing != null) {
      throw new IllegalArgumentException("Ability already registered: " + normalized);
    }
    abilitySpecs.put(normalized, spec);
  }

  public void registerAbility(String id, Ability ability) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(ability, "ability");
    String normalized = normalizeId(id);
    Ability existing = abilities.putIfAbsent(normalized, ability);
    if (existing != null) {
      throw new IllegalArgumentException("Ability already registered: " + normalized);
    }
    abilitySpecs.putIfAbsent(normalized, AbilitySpec.simple(normalized, ability));
  }

  public boolean unregisterAbility(String id) {
    Objects.requireNonNull(id, "id");
    String normalized = normalizeId(id);
    Ability removed = abilities.remove(normalized);
    if (removed != null) {
      abilitySpecs.remove(normalized);
      abilityCombatProfiles.remove(normalized);
    }
    return removed != null;
  }

  public void registerAbilityProfile(String abilityId, AbilityCombatProfile profile) {
    Objects.requireNonNull(abilityId, "abilityId");
    Objects.requireNonNull(profile, "profile");
    abilityCombatProfiles.put(normalizeId(abilityId), profile);
  }

  public AbilityCombatProfile abilityProfile(String abilityId) {
    Objects.requireNonNull(abilityId, "abilityId");
    return abilityCombatProfiles.get(normalizeId(abilityId));
  }

  public void clearAbilityProfile(String abilityId) {
    Objects.requireNonNull(abilityId, "abilityId");
    abilityCombatProfiles.remove(normalizeId(abilityId));
  }

  public boolean hasAbility(String id) {
    Objects.requireNonNull(id, "id");
    return abilities.containsKey(normalizeId(id));
  }

  public long cooldownRemainingTicks(UUID playerId, String key) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(key, "key");
    String normalized = normalizeId(key);
    Map<String, Long> map = cooldownUntilTickByPlayer.get(playerId);
    if (map == null) {
      return 0L;
    }
    Long until = map.get(normalized);
    if (until == null) {
      return 0L;
    }
    long remaining = until - tickNow();
    return Math.max(0L, remaining);
  }

  public long immunityRemainingTicks(UUID entityId, String key) {
    Objects.requireNonNull(entityId, "entityId");
    Objects.requireNonNull(key, "key");
    String normalized = normalizeId(key);
    Map<String, Long> map = immunityUntilTickByEntity.get(entityId);
    if (map == null) {
      return 0L;
    }
    Long until = map.get(normalized);
    if (until == null) {
      return 0L;
    }
    long remaining = until - tickNow();
    if (remaining <= 0L) {
      map.remove(normalized);
      if (map.isEmpty()) {
        immunityUntilTickByEntity.remove(entityId);
      }
      return 0L;
    }
    return remaining;
  }

  /**
   * @return true if immunity was started; false if it was already active.
   */
  public boolean tryStartImmunity(UUID entityId, String key, long durationTicks) {
    Objects.requireNonNull(entityId, "entityId");
    Objects.requireNonNull(key, "key");
    if (durationTicks <= 0) {
      throw new IllegalArgumentException("durationTicks must be > 0");
    }
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("EffectsEngine.tryStartImmunity must be called on the primary thread");
    }
    String normalized = normalizeId(key);
    long now = tickNow();
    long until = now + durationTicks;
    Map<String, Long> map = immunityUntilTickByEntity.computeIfAbsent(entityId, k -> new ConcurrentHashMap<>());
    Long existing = map.get(normalized);
    if (existing != null && existing > now) {
      return false;
    }
    map.put(normalized, until);
    return true;
  }

  public void recordDamageAttribution(UUID victimId, UUID castId, String abilityId, UUID casterId) {
    Objects.requireNonNull(victimId, "victimId");
    Objects.requireNonNull(castId, "castId");
    Objects.requireNonNull(abilityId, "abilityId");
    Objects.requireNonNull(casterId, "casterId");
    lastDamageAttributionByVictim.put(victimId, new DamageAttribution(castId, normalizeId(abilityId), casterId, tickNow()));
  }

  public DamageAttribution lastDamageAttribution(UUID victimId, long maxAgeTicks) {
    Objects.requireNonNull(victimId, "victimId");
    if (maxAgeTicks < 0) {
      throw new IllegalArgumentException("maxAgeTicks must be >= 0");
    }
    DamageAttribution attr = lastDamageAttributionByVictim.get(victimId);
    if (attr == null) {
      return null;
    }
    long age = tickNow() - attr.tick();
    if (age > maxAgeTicks) {
      lastDamageAttributionByVictim.remove(victimId);
      return null;
    }
    return attr;
  }

  public void registerDamageHook(DamageHook hook) {
    Objects.requireNonNull(hook, "hook");
    damageHooks.add(hook);
  }

  public void unregisterDamageHook(DamageHook hook) {
    Objects.requireNonNull(hook, "hook");
    damageHooks.remove(hook);
  }

  public void registerHealHook(HealHook hook) {
    Objects.requireNonNull(hook, "hook");
    healHooks.add(hook);
  }

  public void unregisterHealHook(HealHook hook) {
    Objects.requireNonNull(hook, "hook");
    healHooks.remove(hook);
  }

  public double applyDamage(CastContext ctx, LivingEntity target, DamageSpec spec) {
    Objects.requireNonNull(ctx, "ctx");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(spec, "spec");
    if (!combatDispatcher.onDamagePacketBudget()) {
      return 0.0;
    }
    if (!EntityActions.canAffect(ctx, target, spec.policy())) {
      return 0.0;
    }
    CombatProfile profile = resolveCombatProfile(ctx, target);
    if (!profile.allowDamage()) {
      return 0.0;
    }

    DamagePacket packet = new DamagePacket(ctx.caster(), target, spec, spec.amount());
    packet = damagePipeline.process(this, ctx, packet);
    double amount = packet.amount();
    if (!(amount > 0.0)) {
      return 0.0;
    }

    if (packet.critical()) {
      combatDispatcher.dispatch(new CombatEventContext(
          tickNow(),
          CombatEventType.ON_ATTACK_CRIT,
          ctx.caster(),
          target,
          target,
          null,
          CombatEventSource.MELEE,
          packet.amount(),
          true,
          false,
          false,
          spec.type(),
          spec.cause(),
          spec.tags().stream().findFirst().orElse(null),
          null));
    }
    amount *= profile.damageMultiplier();
    if (!(amount > 0.0)) {
      return 0.0;
    }

    if (!damageHooks.isEmpty()) {
      double next = amount;
      for (DamageHook hook : damageHooks) {
        try {
          next = hook.onDamage(ctx, target, spec, next);
        } catch (Exception ignored) {
        }
        if (!(next > 0.0)) {
          return 0.0;
        }
      }
      amount = next;
    }

    amount = applyDamageCaps(target, amount, spec, profile);
    if (spec.minDamageFloor() > 0.0) {
      amount = Math.max(spec.minDamageFloor(), amount);
    }
    if (!(amount > 0.0)) {
      return 0.0;
    }

    amount = applyShieldReduction(target.getUniqueId(), amount);
    if (!(amount > 0.0)) {
      return 0.0;
    }

    recordDamageAttribution(target.getUniqueId(), ctx.castId(), ctx.abilityId(), ctx.caster().getUniqueId());
    if (spec.mode() == DamageAmountMode.TRUE || spec.cause() == DamageCause.TRUE) {
      double next = Math.max(0.0, target.getHealth() - amount);
      target.setHealth(next);
    } else {
      target.damage(amount, ctx.caster());
    }
    if (spec.applyStatusEffects()) {
      EntityActions.applyUpgradeStatusEffects(ctx, target);
    }
    logDamageEvent(ctx, target, amount, spec);
    CombatEventType type = spec.cause() == DamageCause.DOT ? CombatEventType.ON_DOT_TICK : CombatEventType.ON_HIT_TAKEN;
    CombatEventSource source = switch (spec.cause()) {
      case PROJECTILE -> CombatEventSource.PROJECTILE;
      case DOT -> CombatEventSource.DOT;
      case ENVIRONMENT -> CombatEventSource.ENVIRONMENT;
      default -> CombatEventSource.MELEE;
    };
    combatDispatcher.dispatch(new CombatEventContext(
        tickNow(),
        type,
        ctx.caster(),
        target,
        target,
        null,
        source,
        amount,
        false,
        false,
        false,
        spec.type(),
        spec.cause(),
        spec.tags().stream().findFirst().orElse(null),
        null));
    return amount;
  }

  public double applyHeal(CastContext ctx, LivingEntity target, HealSpec spec) {
    Objects.requireNonNull(ctx, "ctx");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(spec, "spec");
    if (!EntityActions.canAffect(ctx, target, spec.policy())) {
      return 0.0;
    }
    CombatProfile profile = resolveCombatProfile(ctx, target);
    if (!profile.allowHeal()) {
      return 0.0;
    }
    double amount = spec.amount();
    if (spec.mode() == HealAmountMode.PERCENT_MAX_HEALTH) {
      double pct = amount > 1.0 ? amount / 100.0 : amount;
      if (pct <= 0.0) {
        return 0.0;
      }
      double max = EntityActions.resolveMaxHealth(target);
      amount = max * pct;
    }
    if (!(amount > 0.0)) {
      return 0.0;
    }
    amount *= profile.healMultiplier();
    if (!(amount > 0.0)) {
      return 0.0;
    }
    if (spec.cap() > 0.0) {
      amount = Math.min(amount, spec.cap());
      if (!(amount > 0.0)) {
        return 0.0;
      }
    }

    if (!healHooks.isEmpty()) {
      double next = amount;
      for (HealHook hook : healHooks) {
        try {
          next = hook.onHeal(ctx, target, spec, next);
        } catch (Exception ignored) {
        }
        if (!(next > 0.0)) {
          return 0.0;
        }
      }
      amount = next;
    }

    amount = applyHealCaps(target, amount, profile);
    if (!(amount > 0.0)) {
      return 0.0;
    }

    double max = EntityActions.resolveMaxHealth(target);
    double health = target.getHealth();
    double applied = Math.min(amount, Math.max(0.0, max - health));
    if (applied > 0.0) {
      target.setHealth(Math.min(max, health + applied));
    }
    double overheal = amount - applied;
    if (spec.overhealToShield() && overheal > 0.0) {
      addShield(target.getUniqueId(), overheal, spec.shieldCap(), spec.shieldDecayTicks());
    }
    logHealEvent(ctx, target, applied, overheal, spec);
    return applied;
  }

  private CombatProfile resolveCombatProfile(CastContext ctx, LivingEntity target) {
    AbilityCombatProfile profile = abilityCombatProfiles.get(normalizeId(ctx.abilityId()));
    if (profile == null) {
      return CombatProfile.defaults();
    }
    boolean pvpMode = target instanceof Player && ctx.caster() instanceof Player;
    Location location = target.getLocation();
    if (location != null) {
      for (RegionProfile region : profile.regionOverrides()) {
        if (region.region().contains(location)) {
          return region.profiles().forContext(pvpMode);
        }
      }
      World world = location.getWorld();
      if (world != null && !profile.worldOverrides().isEmpty()) {
        String worldName = world.getName().toLowerCase(Locale.ROOT);
        String worldKey = world.getKey().toString().toLowerCase(Locale.ROOT);
        CombatProfilePair pair = profile.worldOverrides().get(worldName);
        if (pair == null) {
          pair = profile.worldOverrides().get(worldKey);
        }
        if (pair != null) {
          return pair.forContext(pvpMode);
        }
      }
    }
    return profile.baseProfiles().forContext(pvpMode);
  }

  private double applyDamageCaps(LivingEntity target, double amount, DamageSpec spec, CombatProfile profile) {
    amount = applyCap(amount, spec.cap());
    amount = applyPercentCap(target, amount, spec.maxPercent());
    amount = applyCap(amount, profile.damageCap());
    amount = applyPercentCap(target, amount, profile.maxDamagePercent());
    return amount;
  }

  private double applyHealCaps(LivingEntity target, double amount, CombatProfile profile) {
    amount = applyCap(amount, profile.healCap());
    amount = applyPercentCap(target, amount, profile.maxHealPercent());
    return amount;
  }

  private double applyCap(double amount, double cap) {
    if (cap > 0.0) {
      return Math.min(amount, cap);
    }
    return amount;
  }

  private double applyPercentCap(LivingEntity target, double amount, double percent) {
    if (!(percent > 0.0)) {
      return amount;
    }
    double pct = percent > 1.0 ? percent / 100.0 : percent;
    if (!(pct > 0.0)) {
      return amount;
    }
    double max = EntityActions.resolveMaxHealth(target);
    double cap = max * pct;
    if (!(cap > 0.0)) {
      return 0.0;
    }
    return Math.min(amount, cap);
  }

  public double shieldAmount(UUID entityId) {
    ShieldEntry entry = shieldEntry(entityId);
    return entry == null ? 0.0 : entry.amount;
  }

  public void clearShield(UUID entityId) {
    shieldsByEntity.remove(entityId);
  }

  public double addShield(UUID entityId, double amount, double cap, long decayTicks) {
    if (!(amount > 0.0)) {
      return shieldAmount(entityId);
    }
    ShieldEntry entry = shieldEntry(entityId);
    double current = entry == null ? 0.0 : entry.amount;
    double next = current + amount;
    if (cap > 0.0) {
      next = Math.min(next, cap);
    }
    long decayAt = decayTicks > 0 ? tickNow() + decayTicks : 0L;
    if (entry == null) {
      shieldsByEntity.put(entityId, new ShieldEntry(next, decayAt));
    } else {
      entry.amount = next;
      if (decayAt > 0) {
        entry.decayAtTick = decayAt;
      }
    }
    return next;
  }

  private ShieldEntry shieldEntry(UUID entityId) {
    ShieldEntry entry = shieldsByEntity.get(entityId);
    if (entry == null) {
      return null;
    }
    if (entry.decayAtTick > 0 && tickNow() >= entry.decayAtTick) {
      shieldsByEntity.remove(entityId);
      return null;
    }
    return entry;
  }

  private double applyShieldReduction(UUID entityId, double amount) {
    ShieldEntry entry = shieldEntry(entityId);
    if (entry == null || !(amount > 0.0)) {
      return amount;
    }
    double absorbed = Math.min(entry.amount, amount);
    entry.amount -= absorbed;
    if (entry.amount <= 0.0) {
      shieldsByEntity.remove(entityId);
    }
    return amount - absorbed;
  }

  public double resistanceMultiplier(UUID entityId, DamageType type) {
    Objects.requireNonNull(entityId, "entityId");
    Objects.requireNonNull(type, "type");
    java.util.EnumMap<DamageType, ResistanceEntry> map = resistancesByEntity.get(entityId);
    if (map == null) {
      return 1.0;
    }
    ResistanceEntry entry = map.get(type);
    return entry == null ? 1.0 : entry.multiplier;
  }

  public ResistanceSnapshot setResistance(UUID entityId, DamageType type, double multiplier) {
    Objects.requireNonNull(entityId, "entityId");
    Objects.requireNonNull(type, "type");
    if (!Double.isFinite(multiplier) || multiplier < 0.0) {
      throw new IllegalArgumentException("multiplier must be finite and >= 0");
    }
    java.util.EnumMap<DamageType, ResistanceEntry> map = resistancesByEntity
        .computeIfAbsent(entityId, k -> new java.util.EnumMap<>(DamageType.class));
    ResistanceEntry entry = map.get(type);
    double previous = entry == null ? 1.0 : entry.multiplier;
    long token = entry == null ? 1L : entry.token + 1L;
    map.put(type, new ResistanceEntry(multiplier, token));
    return new ResistanceSnapshot(previous, token);
  }

  public boolean restoreResistance(UUID entityId, DamageType type, long expectedToken, double previous) {
    Objects.requireNonNull(entityId, "entityId");
    Objects.requireNonNull(type, "type");
    java.util.EnumMap<DamageType, ResistanceEntry> map = resistancesByEntity.get(entityId);
    if (map == null) {
      return false;
    }
    ResistanceEntry entry = map.get(type);
    if (entry == null || entry.token != expectedToken) {
      return false;
    }
    if (!Double.isFinite(previous) || previous < 0.0) {
      previous = 1.0;
    }
    if (Math.abs(previous - 1.0) < 1e-9) {
      map.remove(type);
      if (map.isEmpty()) {
        resistancesByEntity.remove(entityId);
      }
    } else {
      entry.multiplier = previous;
      entry.token = expectedToken + 1L;
    }
    return true;
  }

  public void clearResistance(UUID entityId, DamageType type) {
    Objects.requireNonNull(entityId, "entityId");
    Objects.requireNonNull(type, "type");
    java.util.EnumMap<DamageType, ResistanceEntry> map = resistancesByEntity.get(entityId);
    if (map == null) {
      return;
    }
    map.remove(type);
    if (map.isEmpty()) {
      resistancesByEntity.remove(entityId);
    }
  }

  public void clearResistances(UUID entityId) {
    Objects.requireNonNull(entityId, "entityId");
    resistancesByEntity.remove(entityId);
  }

  public ReflectSpec reflectSpec(UUID entityId) {
    Objects.requireNonNull(entityId, "entityId");
    ReflectEntry entry = reflectByEntity.get(entityId);
    return entry == null ? null : entry.spec;
  }

  public long setReflect(UUID entityId, ReflectSpec spec) {
    Objects.requireNonNull(entityId, "entityId");
    Objects.requireNonNull(spec, "spec");
    ReflectEntry entry = reflectByEntity.get(entityId);
    long token = entry == null ? 1L : entry.token + 1L;
    reflectByEntity.put(entityId, new ReflectEntry(spec, token));
    return token;
  }

  public boolean clearReflect(UUID entityId, long expectedToken) {
    Objects.requireNonNull(entityId, "entityId");
    ReflectEntry entry = reflectByEntity.get(entityId);
    if (entry == null || entry.token != expectedToken) {
      return false;
    }
    reflectByEntity.remove(entityId);
    return true;
  }

  public void clearReflect(UUID entityId) {
    Objects.requireNonNull(entityId, "entityId");
    reflectByEntity.remove(entityId);
  }

  /**
   * @return true if the cooldown was started; false if it was already active.
   */
  public boolean tryStartCooldown(UUID playerId, String key, long durationTicks) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(key, "key");
    if (durationTicks <= 0) {
      throw new IllegalArgumentException("durationTicks must be > 0");
    }
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("EffectsEngine.tryStartCooldown must be called on the primary thread");
    }
    String normalized = normalizeId(key);
    long now = tickNow();
    long until = now + durationTicks;
    Map<String, Long> map = cooldownUntilTickByPlayer.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
    Long existing = map.get(normalized);
    if (existing != null && existing > now) {
      return false;
    }
    map.put(normalized, until);
    return true;
  }

  public CastResult cast(String abilityId, LivingEntity caster) {
    Objects.requireNonNull(abilityId, "abilityId");
    Objects.requireNonNull(caster, "caster");
    String normalized = normalizeId(abilityId);
    Ability ability = abilities.get(normalized);
    if (ability == null) {
      throw new IllegalArgumentException("Unknown ability: " + normalized);
    }

    UUID castId = UUID.randomUUID();
    if (!Bukkit.isPrimaryThread()) {
      Bukkit.getScheduler().runTask(plugin, () -> castInternal(normalized, ability, caster, castId));
      return new CastResult(castId, normalized, tickNow());
    }

    return castInternal(normalized, ability, caster, castId);
  }

  public ScheduledHandle runLater(long delayTicks, Runnable runnable) {
    Objects.requireNonNull(runnable, "runnable");
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("EffectsEngine.runLater must be called on the primary thread");
    }
    if (delayTicks < 0) {
      throw new IllegalArgumentException("delayTicks must be >= 0");
    }
    ScheduledTask task = new ScheduledTask(runnable, tickNow() + delayTicks, 0L);
    tasks.add(task);
    return task;
  }

  public ScheduledHandle runRepeating(long delayTicks, long periodTicks, Runnable runnable) {
    Objects.requireNonNull(runnable, "runnable");
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("EffectsEngine.runRepeating must be called on the primary thread");
    }
    if (delayTicks < 0) {
      throw new IllegalArgumentException("delayTicks must be >= 0");
    }
    if (periodTicks <= 0) {
      throw new IllegalArgumentException("periodTicks must be > 0");
    }
    ScheduledTask task = new ScheduledTask(runnable, tickNow() + delayTicks, periodTicks);
    tasks.add(task);
    return task;
  }

  public ScheduledHandle runLater(Duration delay, Runnable runnable) {
    Objects.requireNonNull(delay, "delay");
    Objects.requireNonNull(runnable, "runnable");
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("EffectsEngine.runLater(Duration) must be called on the primary thread");
    }
    long delayNanos = Math.max(0L, delay.toNanos());
    RealTimeScheduledTask task = new RealTimeScheduledTask(runnable, nanoTime() + delayNanos, 0L);
    realTimeTasks.add(task);
    return task;
  }

  public ScheduledHandle runRepeating(Duration delay, Duration period, Runnable runnable) {
    Objects.requireNonNull(delay, "delay");
    Objects.requireNonNull(period, "period");
    Objects.requireNonNull(runnable, "runnable");
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("EffectsEngine.runRepeating(Duration) must be called on the primary thread");
    }
    long delayNanos = Math.max(0L, delay.toNanos());
    long periodNanos = period.toNanos();
    if (periodNanos <= 0L) {
      throw new IllegalArgumentException("period must be > 0");
    }
    RealTimeScheduledTask task = new RealTimeScheduledTask(runnable, nanoTime() + delayNanos, periodNanos);
    realTimeTasks.add(task);
    return task;
  }

  private CastResult castInternal(String normalized, Ability ability, LivingEntity caster, UUID castId) {
    Location origin;
    Vector direction;
    ItemStack itemInHand = null;
    if (caster instanceof Player player) {
      origin = player.getEyeLocation();
      direction = origin.getDirection();
      ItemStack current = player.getInventory().getItemInMainHand();
      if (current != null && !current.getType().isAir()) {
        itemInHand = current;
      }
    } else {
      origin = caster.getLocation();
      direction = origin.getDirection();
    }
    return castInternalWithContext(normalized, ability, caster, castId, origin, direction, itemInHand, null);
  }

  private CastResult castInternalWithContext(String normalized, Ability ability, LivingEntity caster, UUID castId,
      Location origin, Vector direction, ItemStack itemInHand, Consumer<CastContext> mutator) {
    Objects.requireNonNull(origin, "origin");
    Objects.requireNonNull(direction, "direction");
    CastState state = new CastState(castId, deterministicSeed);
    castRecords.put(castId, new CastRecord(castId, caster.getUniqueId(), normalized, tickNow(), state));
    lastCastIdByCaster.put(caster.getUniqueId(), castId);
    CastContext ctx = new CastContext(this, plugin, castId, normalized, tickNow(), state, caster, origin.clone(), direction.clone(), itemInHand);
    if (mutator != null) {
      mutator.accept(ctx);
    }
    debug("cast: id=" + castId + " ability=" + normalized + " caster=" + caster.getType().name());
    try {
      ability.cast(ctx);
    } catch (Exception ex) {
      state.cancel();
      throw ex;
    }
    showDebugOverlay(ctx);
    return new CastResult(castId, normalized, ctx.tick());
  }

  public CastResult castWithContext(String abilityId, LivingEntity caster, Location origin, Vector direction,
      ItemStack itemInHand, Consumer<CastContext> mutator) {
    Objects.requireNonNull(abilityId, "abilityId");
    Objects.requireNonNull(caster, "caster");
    Objects.requireNonNull(origin, "origin");
    Objects.requireNonNull(direction, "direction");
    String normalized = normalizeId(abilityId);
    Ability ability = abilities.get(normalized);
    if (ability == null) {
      throw new IllegalArgumentException("Unknown ability: " + normalized);
    }
    UUID castId = UUID.randomUUID();
    if (!Bukkit.isPrimaryThread()) {
      Bukkit.getScheduler().runTask(plugin,
          () -> castInternalWithContext(normalized, ability, caster, castId, origin, direction, itemInHand, mutator));
      return new CastResult(castId, normalized, tickNow());
    }
    return castInternalWithContext(normalized, ability, caster, castId, origin, direction, itemInHand, mutator);
  }

  public CastResult castAction(String abilityId, LivingEntity caster, dev.patric.dungeonsreborn.effects.actions.Action action) {
    Objects.requireNonNull(abilityId, "abilityId");
    Objects.requireNonNull(caster, "caster");
    Objects.requireNonNull(action, "action");
    String normalized;
    try {
      normalized = dev.patric.dungeonsreborn.effects.Ids.normalize(abilityId);
    } catch (Exception ex) {
      normalized = "dsl_playground";
    }
    UUID castId = UUID.randomUUID();
    Location origin;
    Vector direction;
    ItemStack itemInHand = null;
    if (caster instanceof Player player) {
      origin = player.getEyeLocation();
      direction = origin.getDirection();
      ItemStack current = player.getInventory().getItemInMainHand();
      if (current != null && !current.getType().isAir()) {
        itemInHand = current;
      }
    } else {
      origin = caster.getLocation();
      direction = origin.getDirection();
    }

    CastState state = new CastState(castId, deterministicSeed);
    castRecords.put(castId, new CastRecord(castId, caster.getUniqueId(), normalized, tickNow(), state));
    lastCastIdByCaster.put(caster.getUniqueId(), castId);
    CastContext ctx = new CastContext(this, plugin, castId, normalized, tickNow(), state, caster, origin.clone(), direction.clone(), itemInHand);
    debug("cast: id=" + castId + " ability=" + normalized + " caster=" + caster.getType().name());
    try {
      action.executeWithHandle(ctx);
    } catch (Exception ex) {
      state.cancel();
      throw ex;
    }
    showDebugOverlay(ctx);
    return new CastResult(castId, normalized, ctx.tick());
  }

  public ActionHandle castActionHandle(String abilityId, LivingEntity caster, dev.patric.dungeonsreborn.effects.actions.Action action) {
    Objects.requireNonNull(abilityId, "abilityId");
    Objects.requireNonNull(caster, "caster");
    Objects.requireNonNull(action, "action");
    String normalized;
    try {
      normalized = dev.patric.dungeonsreborn.effects.Ids.normalize(abilityId);
    } catch (Exception ex) {
      normalized = "dsl_playground";
    }
    UUID castId = UUID.randomUUID();
    Location origin;
    Vector direction;
    ItemStack itemInHand = null;
    if (caster instanceof Player player) {
      origin = player.getEyeLocation();
      direction = origin.getDirection();
      ItemStack current = player.getInventory().getItemInMainHand();
      if (current != null && !current.getType().isAir()) {
        itemInHand = current;
      }
    } else {
      origin = caster.getLocation();
      direction = origin.getDirection();
    }

    CastState state = new CastState(castId, deterministicSeed);
    castRecords.put(castId, new CastRecord(castId, caster.getUniqueId(), normalized, tickNow(), state));
    lastCastIdByCaster.put(caster.getUniqueId(), castId);
    CastContext ctx = new CastContext(this, plugin, castId, normalized, tickNow(), state, caster, origin.clone(), direction.clone(), itemInHand);
    debug("cast: id=" + castId + " ability=" + normalized + " caster=" + caster.getType().name());
    try {
      ActionHandle handle = action.executeWithHandle(ctx);
      showDebugOverlay(ctx);
      return handle;
    } catch (Exception ex) {
      state.cancel();
      throw ex;
    }
  }

  private void tick() {
    final long start = System.nanoTime();
    tick++;
    combatDispatcher.tickStart();
    if (tasks.isEmpty() && realTimeTasks.isEmpty()) {
      afflictions.tick(tickNow());
      tickManaRegen();
      tickManaTimedGrant();
      cleanupOldCastRecords(20L * 60L * 5L);
      particles.flush();
      warnParticleBudget();
      lastTickNanos = Math.max(0L, System.nanoTime() - start);
      return;
    }
    for (int i = 0; i < tasks.size(); i++) {
      ScheduledTask task = tasks.get(i);
      if (task.cancelled) {
        tasks.remove(i--);
        continue;
      }
      if (tick < task.nextTick) {
        continue;
      }
      try {
        task.runnable.run();
      } catch (Exception ex) {
        warn("scheduled task threw: " + ex.getMessage(), ex);
        ex.printStackTrace();
      }
      if (task.cancelled) {
        tasks.remove(i--);
        continue;
      }
      if (task.period > 0L) {
        task.nextTick = tick + task.period;
      } else {
        tasks.remove(i--);
      }
    }

    if (!realTimeTasks.isEmpty()) {
      final long now = nanoTime();
      final int maxCatchUpRuns = 10;

      for (int i = 0; i < realTimeTasks.size(); i++) {
        RealTimeScheduledTask task = realTimeTasks.get(i);
        if (task.cancelled) {
          realTimeTasks.remove(i--);
          continue;
        }
        if (now < task.nextNanos) {
          continue;
        }

        int runs = 0;
        while (!task.cancelled && now >= task.nextNanos && runs++ < maxCatchUpRuns) {
          try {
            task.runnable.run();
          } catch (Exception ex) {
            warn("real-time scheduled task threw: " + ex.getMessage(), ex);
            ex.printStackTrace();
          }
          if (task.periodNanos > 0L) {
            task.nextNanos += task.periodNanos;
          } else {
            task.cancelled = true;
          }
        }

        if (!task.cancelled && task.periodNanos > 0L && runs >= maxCatchUpRuns) {
          // If we fell behind a lot, resync to avoid spending too long catching up.
          task.nextNanos = now + task.periodNanos;
        }

        if (task.cancelled) {
          realTimeTasks.remove(i--);
        }
      }
    }

    afflictions.tick(tickNow());
    tickManaRegen();
    tickManaTimedGrant();
    cleanupOldCastRecords(20L * 60L * 5L);
    particles.flush();
    warnParticleBudget();
    particles.autoAdjustQuality(tickNow());
    lastTickNanos = Math.max(0L, System.nanoTime() - start);
  }

  private void warnParticleBudget() {
    ParticleEngine.Stats stats = particles.stats();
    long droppedBudget = stats.lastFlushParticlesDroppedByBudget();
    long droppedQueue = stats.lastDroppedRequestsByQueueCap();
    if (droppedBudget <= 0 && droppedQueue <= 0) {
      vfxCountsThisTick.clear();
      vfxParticlesThisTick.clear();
      vfxEventsThisTick = 0L;
      return;
    }
    long nowTick = tickNow();
    if (nowTick - lastParticleWarnTick < 20L * 10L) {
      vfxCountsThisTick.clear();
      vfxParticlesThisTick.clear();
      vfxEventsThisTick = 0L;
      return;
    }
    lastParticleWarnTick = nowTick;
    if (droppedBudget > 0) {
      warn("[Effects] Particle budget dropped " + droppedBudget + " particles in last flush.");
    }
    if (droppedQueue > 0) {
      warn("[Effects] Particle queue cap dropped " + droppedQueue + " requests in last flush.");
    }
    if (!vfxParticlesThisTick.isEmpty()) {
      warn("[Effects] Particle sources (top): " + topVfxSources(vfxParticlesThisTick, 5)
          + " totalEvents=" + vfxEventsThisTick);
    }
    vfxCountsThisTick.clear();
    vfxParticlesThisTick.clear();
    vfxEventsThisTick = 0L;
  }

  private static String topVfxSources(java.util.Map<String, Long> counts, int limit) {
    if (counts.isEmpty() || limit <= 0) {
      return "none";
    }
    return counts.entrySet().stream()
        .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
        .limit(limit)
        .map(e -> e.getKey() + "=" + e.getValue())
        .reduce((a, b) -> a + ", " + b)
        .orElse("none");
  }

  private void tickManaRegen() {
    if (!manaRegenEnabled) {
      return;
    }
    ManaProvider provider = manaProvider;
    if (provider == null) {
      return;
    }
    long period = manaRegenPeriodTicks;
    if (period <= 0) {
      return;
    }
    if ((tickNow() % period) != 0L) {
      return;
    }
    double amount = manaRegenAmount;
    SessionManaProvider session = provider instanceof SessionManaProvider sp ? sp : null;
    var resourceIds = provider.resourceIds();
    for (Player player : Bukkit.getOnlinePlayers()) {
      long now = tickNow();
      Long lastSpend = manaSpendTicks.get(player.getUniqueId());
      if (lastSpend != null && (now - lastSpend) < manaRegenDelayTicks) {
        continue;
      }
      Long lastCombat = combatTicks.get(player.getUniqueId());
      if (lastCombat != null && (now - lastCombat) < manaCombatDelayTicks) {
        continue;
      }
      double maxBonus = 0.0;
      double regenBonus = 0.0;
      double regenMultiplierBonus = 0.0;
      double regenPercentBonus = 0.0;
      ResourceRules.RegenMode regenModeOverride = null;
      var inv = player.getInventory();
      ItemStack[] items = {
          inv.getItemInMainHand(),
          inv.getItemInOffHand(),
          inv.getHelmet(),
          inv.getChestplate(),
          inv.getLeggings(),
          inv.getBoots()
      };
      for (ItemStack item : items) {
        if (item == null || item.getType().isAir()) {
          continue;
        }
        maxBonus += ItemMarkers.getManaMaxBonus(item);
        regenBonus += ItemMarkers.getManaRegenBonus(item);
        regenMultiplierBonus += ItemMarkers.getManaRegenMultiplier(item);
        regenPercentBonus += ItemMarkers.getManaRegenPercent(item);
        if (regenModeOverride == null) {
          String mode = ItemMarkers.getManaRegenMode(item);
          if (mode != null) {
            regenModeOverride = parseRegenMode(mode);
          }
        }
      }
      if (session != null) {
        session.setMaxBonus(player, ManaProvider.DEFAULT_RESOURCE, maxBonus);
        session.setRegenBonus(player, ManaProvider.DEFAULT_RESOURCE, regenBonus);
      }
      for (String resourceId : resourceIds) {
        double max = provider.getMax(player, resourceId);
        double current = provider.get(player, resourceId);
        if (current >= max - 1e-9) {
          continue;
        }
        double sessionRegen = session != null ? session.regenBonus(player, resourceId) : 0.0;
        double classRegen = session != null ? session.classRegenBonus(player, resourceId) : 0.0;
        ResourceRules rules = provider.rules(player, resourceId);
        ResourceRules.RegenMode regenMode = rules.regenMode();
        if (ManaProvider.DEFAULT_RESOURCE.equals(resourceId) && regenModeOverride != null) {
          regenMode = regenModeOverride;
        }
        double multiplier = rules.regenMultiplier();
        double percentBonus = 0.0;
        if (ManaProvider.DEFAULT_RESOURCE.equals(resourceId)) {
          multiplier *= (1.0 + regenMultiplierBonus);
          percentBonus = regenPercentBonus;
        }
        double total = resolveRegenBase(amount, max, period, rules, regenMode) * multiplier
            + max * percentBonus * (period / 20.0)
            + Math.max(0.0, sessionRegen + classRegen) * (period / 20.0);
        if (total <= 0.0) {
          continue;
        }
        if (manaMaxRegenPerTick > 0.0) {
          double cap = manaMaxRegenPerTick * (period / 20.0);
          total = Math.min(total, cap);
        }
        double next = Math.min(max, current + total);
        provider.set(player, resourceId, next);
        if (debugEnabled) {
          logger.debug("[Mana] regen player=" + player.getUniqueId()
              + " resource=" + resourceId
              + " amount=" + formatAmount(total)
              + " current=" + formatAmount(next)
              + " max=" + formatAmount(max));
        }
      }
    }
  }

  private void tickManaTimedGrant() {
    if (!manaTimedGrantEnabled) {
      return;
    }
    ManaProvider provider = manaProvider;
    if (provider == null) {
      return;
    }
    long period = manaTimedGrantPeriodTicks;
    if (period <= 0L || manaTimedGrantAmount <= 0.0) {
      return;
    }
    if ((tickNow() % period) != 0L) {
      return;
    }
    for (Player player : Bukkit.getOnlinePlayers()) {
      addResource(provider, player, manaTimedGrantResource, manaTimedGrantAmount);
    }
  }

  private ResourceRules.RegenMode parseRegenMode(String raw) {
    if (raw == null) {
      return null;
    }
    return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
      case "percent", "percentage" -> ResourceRules.RegenMode.PERCENT;
      case "hybrid" -> ResourceRules.RegenMode.HYBRID;
      case "flat" -> ResourceRules.RegenMode.FLAT;
      default -> null;
    };
  }

  private double resolveRegenBase(double globalFlat, double max, long periodTicks, ResourceRules rules,
      ResourceRules.RegenMode mode) {
    double flat = rules.regenFlat() > 0.0 ? rules.regenFlat() : globalFlat;
    double percentPerSecond = rules.regenPercent() > 0.0 ? rules.regenPercent() : 0.0;
    double percentAmount = max * percentPerSecond * (periodTicks / 20.0);
    ResourceRules.RegenMode resolved = mode == null ? rules.regenMode() : mode;
    return switch (resolved) {
      case PERCENT -> percentAmount;
      case HYBRID -> flat + percentAmount;
      case FLAT -> flat;
    };
  }

  private static void addResource(ManaProvider provider, Player player, String resourceId, double amount) {
    if (provider == null || player == null || resourceId == null || resourceId.isBlank()) {
      return;
    }
    if (!Double.isFinite(amount) || amount <= 0.0) {
      return;
    }
    EffectsEngine engine = EffectsEngine.get();
    if (engine.manaMaxGainPerTick > 0.0) {
      amount = Math.min(amount, engine.manaMaxGainPerTick);
    }
    double max = provider.getMax(player, resourceId);
    if (max <= 0.0) {
      return;
    }
    double current = provider.get(player, resourceId);
    double next = Math.min(max, current + amount);
    provider.set(player, resourceId, next);
    if (engine.debugEnabled) {
      engine.logger.debug("[Mana] gain player=" + player.getUniqueId()
          + " resource=" + resourceId
          + " amount=" + formatAmount(amount)
          + " current=" + formatAmount(next)
          + " max=" + formatAmount(max));
    }
  }

  public boolean grantResource(Player player, String resourceId, double amount) {
    ManaProvider provider = manaProvider;
    if (provider == null || player == null) {
      return false;
    }
    addResource(provider, player, resourceId, amount);
    return true;
  }

  private void cleanupOldCastRecords(long maxAgeTicks) {
    if (maxAgeTicks <= 0) {
      return;
    }
    long now = tickNow();
    for (var it = castRecords.entrySet().iterator(); it.hasNext();) {
      var e = it.next();
      CastRecord r = e.getValue();
      if (r == null) {
        it.remove();
        continue;
      }
      if (now - r.tickStarted() > maxAgeTicks) {
        it.remove();
      }
    }
  }

  private void showDebugOverlay(CastContext ctx) {
    if (!debugEnabled) {
      return;
    }
    if (!(ctx.caster() instanceof Player player)) {
      return;
    }
    if (!cinematicSettings.enabled(player, CinematicSettings.Flag.DEBUG_OVERLAY)) {
      return;
    }
    CastFailure failure = lastFailureByCaster.get(player.getUniqueId());
    if (failure != null && failure.castId().equals(ctx.castId())) {
      player.sendActionBar(Component.text("FAIL " + failure.type().name() + ": " + failure.reason()));
      return;
    }
    player.sendActionBar(Component.text("CAST " + normalizeId(ctx.abilityId())));
  }

  private static String formatAmount(double amount) {
    return String.format(Locale.ROOT, "%.3f", amount);
  }

  private static String normalizeId(String id) {
    return Ids.normalize(id);
  }
}
