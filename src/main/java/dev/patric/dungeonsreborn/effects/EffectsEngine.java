package dev.patric.dungeonsreborn.effects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.particles.ParticleEngine;
import dev.patric.dungeonsreborn.effects.actions.EntityActions;
import dev.patric.dungeonsreborn.effects.damage.DamageType;
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
import dev.patric.dungeonsreborn.effects.mana.SessionManaProvider;
import dev.patric.dungeonsreborn.logging.ServiceLogger;

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

  public record EngineStats(
      long tick,
      int scheduledTickTasks,
      int scheduledRealTimeTasks,
      int trackedCastRecords,
      int cooldownPlayers,
      int immunityEntities,
      long lastTickNanos) {
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
  private final List<ScheduledTask> tasks = new ArrayList<>();
  private final List<RealTimeScheduledTask> realTimeTasks = new ArrayList<>();
  private final Map<UUID, Map<String, Long>> cooldownUntilTickByPlayer = new ConcurrentHashMap<>();
  private final Map<UUID, Map<String, Long>> immunityUntilTickByEntity = new ConcurrentHashMap<>();
  private final Map<UUID, DamageAttribution> lastDamageAttributionByVictim = new ConcurrentHashMap<>();
  private final Map<UUID, java.util.EnumMap<DamageType, ResistanceEntry>> resistancesByEntity = new ConcurrentHashMap<>();
  private final Map<UUID, ReflectEntry> reflectByEntity = new ConcurrentHashMap<>();
  private final Map<String, GlobalTimeline> timelines = new ConcurrentHashMap<>();
  private final ParticleEngine particles = new ParticleEngine();
  private final CinematicSettings cinematicSettings = new CinematicSettings();
  private final TypeRegistry<ActionType> actionTypes = new TypeRegistry<>("action");
  private final TypeRegistry<TargeterType<?>> targeterTypes = new TypeRegistry<>("targeter");
  private final TypeRegistry<ConditionType> conditionTypes = new TypeRegistry<>("condition");
  private volatile RelationProvider relationProvider = RelationProviders.scoreboardTeams();
  private volatile ManaProvider manaProvider;
  private volatile long manaRegenPeriodTicks = 20L;
  private volatile double manaRegenAmount = 5.0;
  private volatile boolean manaRegenEnabled;
  private long tick;
  private final long startedNanos = System.nanoTime();
  private BukkitTask ticker;
  private volatile boolean debugEnabled;
  private volatile long lastTickNanos;
  private long lastParticleWarnTick;

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

  private EffectsEngine(JavaPlugin plugin, ServiceLogger logger) {
    this.plugin = plugin;
    this.logger = logger;
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
    timelines.values().forEach(GlobalTimeline::cancel);
    timelines.clear();
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

  public ServiceLogger logger() {
    return logger;
  }

  public long tickNow() {
    return tick;
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

  public void disableManaRegen() {
    manaRegenEnabled = false;
  }

  public boolean isManaRegenEnabled() {
    return manaRegenEnabled;
  }

  public long manaRegenPeriodTicks() {
    return manaRegenPeriodTicks;
  }

  public double manaRegenAmount() {
    return manaRegenAmount;
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
    }
    return removed != null;
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
    CastState state = new CastState(castId);
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

    CastState state = new CastState(castId);
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

    CastState state = new CastState(castId);
    castRecords.put(castId, new CastRecord(castId, caster.getUniqueId(), normalized, tickNow(), state));
    lastCastIdByCaster.put(caster.getUniqueId(), castId);
    CastContext ctx = new CastContext(this, plugin, castId, normalized, tickNow(), state, caster, origin.clone(), direction.clone(), itemInHand);
    debug("cast: id=" + castId + " ability=" + normalized + " caster=" + caster.getType().name());
    try {
      return action.executeWithHandle(ctx);
    } catch (Exception ex) {
      state.cancel();
      throw ex;
    }
  }

  private void tick() {
    final long start = System.nanoTime();
    tick++;
    if (tasks.isEmpty() && realTimeTasks.isEmpty()) {
      tickManaRegen();
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

    tickManaRegen();
    cleanupOldCastRecords(20L * 60L * 5L);
    particles.flush();
    warnParticleBudget();
    lastTickNanos = Math.max(0L, System.nanoTime() - start);
  }

  private void warnParticleBudget() {
    ParticleEngine.Stats stats = particles.stats();
    long droppedBudget = stats.lastFlushParticlesDroppedByBudget();
    long droppedQueue = stats.lastDroppedRequestsByQueueCap();
    if (droppedBudget <= 0 && droppedQueue <= 0) {
      return;
    }
    long nowTick = tickNow();
    if (nowTick - lastParticleWarnTick < 20L * 10L) {
      return;
    }
    lastParticleWarnTick = nowTick;
    if (droppedBudget > 0) {
      warn("[Effects] Particle budget dropped " + droppedBudget + " particles in last flush.");
    }
    if (droppedQueue > 0) {
      warn("[Effects] Particle queue cap dropped " + droppedQueue + " requests in last flush.");
    }
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
    for (Player player : Bukkit.getOnlinePlayers()) {
      double maxBonus = 0.0;
      double regenBonus = 0.0;
      double classRegenBonus = 0.0;
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
      }
      if (session != null) {
        session.setMaxBonus(player, maxBonus);
        session.setRegenBonus(player, regenBonus);
        classRegenBonus = session.classRegenBonus(player);
      }
      double max = provider.getMax(player);
      double current = provider.get(player);
      if (current >= max - 1e-9) {
        continue;
      }
      double total = amount + Math.max(0.0, regenBonus + classRegenBonus) * (period / 20.0);
      if (total <= 0.0) {
        continue;
      }
      provider.set(player, Math.min(max, current + total));
    }
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

  private static String normalizeId(String id) {
    return Ids.normalize(id);
  }
}
