package dev.patric.dungeonsreborn.effects.combat;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.Vars;

public final class CombatEventDispatcher {
  private static final String YAML_LAST_ENTITY = "yaml_last_entity";

  private final EffectsEngine engine;
  private final CombatPlannerService planner;
  private final Map<CombatEventType, CopyOnWriteArrayList<CombatEventBinding>> byType =
      new EnumMap<>(CombatEventType.class);
  private final CombatMetrics metrics = new CombatMetrics();
  private final Set<String> warnedLegacy = ConcurrentHashMap.newKeySet();

  private volatile boolean enabled = true;
  private volatile boolean debug;
  private volatile boolean asyncPlannerEnabled = true;
  private volatile long planTtlTicks = 1L;
  private volatile int maxEventDispatchPerTick = 2000;
  private volatile int maxDamagePacketsPerTick = 4000;
  private volatile int maxProjectileEventsPerTick = 6000;
  private volatile int maxTravelStepDispatchPerTick = 1200;
  private volatile long tickBudgetDispatches;
  private volatile long tickBudgetDamagePackets;
  private volatile long tickBudgetProjectileEvents;
  private volatile long tickBudgetTravelStepEvents;
  private volatile String degradePolicy = "DROP_LOW_PRIORITY";

  public CombatEventDispatcher(EffectsEngine engine, int workerThreads, int queueCapacity) {
    this.engine = Objects.requireNonNull(engine, "engine");
    this.planner = new CombatPlannerService(workerThreads, queueCapacity);
    for (CombatEventType type : CombatEventType.values()) {
      byType.put(type, new CopyOnWriteArrayList<>());
    }
  }

  public void configure(boolean enabled, boolean debug, boolean asyncPlannerEnabled, int queueCapacity,
      long planTtlTicks, int maxEventDispatchPerTick, int maxDamagePacketsPerTick, String degradePolicy) {
    this.enabled = enabled;
    this.debug = debug;
    this.asyncPlannerEnabled = asyncPlannerEnabled;
    this.planTtlTicks = Math.max(0L, planTtlTicks);
    this.maxEventDispatchPerTick = Math.max(0, maxEventDispatchPerTick);
    this.maxDamagePacketsPerTick = Math.max(0, maxDamagePacketsPerTick);
    this.degradePolicy = degradePolicy == null ? "DROP_LOW_PRIORITY" : degradePolicy;
    planner.configure(asyncPlannerEnabled, queueCapacity);
  }

  public void configureProjectiles(int maxProjectileEventsPerTick, int maxTravelStepDispatchPerTick) {
    this.maxProjectileEventsPerTick = Math.max(0, maxProjectileEventsPerTick);
    this.maxTravelStepDispatchPerTick = Math.max(0, maxTravelStepDispatchPerTick);
  }

  public void tickStart() {
    tickBudgetDispatches = 0L;
    tickBudgetDamagePackets = 0L;
    tickBudgetProjectileEvents = 0L;
    tickBudgetTravelStepEvents = 0L;
  }

  public void register(CombatEventBinding binding) {
    Objects.requireNonNull(binding, "binding");
    byType.computeIfAbsent(binding.eventType(), k -> new CopyOnWriteArrayList<>()).add(binding);
  }

  public boolean unregister(String id) {
    if (id == null) {
      return false;
    }
    boolean removed = false;
    for (CopyOnWriteArrayList<CombatEventBinding> list : byType.values()) {
      removed |= list.removeIf(b -> b.id().equals(id));
    }
    return removed;
  }

  public List<CombatEventBinding> bindings(CombatEventType type) {
    var list = byType.get(type);
    return list == null ? List.of() : List.copyOf(list);
  }

  public void clear() {
    for (CopyOnWriteArrayList<CombatEventBinding> list : byType.values()) {
      list.clear();
    }
  }

  public CombatMetrics metrics() {
    return metrics;
  }

  public String status() {
    int totalBindings = byType.values().stream().mapToInt(List::size).sum();
    return "enabled=" + enabled
        + " async=" + asyncPlannerEnabled
        + " plannerQueued=" + planner.queued()
        + " bindings=" + totalBindings
        + " ttl=" + planTtlTicks
        + " guardrailDispatch=" + maxEventDispatchPerTick
        + " guardrailDamagePackets=" + maxDamagePacketsPerTick
        + " guardrailProjectile=" + maxProjectileEventsPerTick
        + " guardrailTravelStep=" + maxTravelStepDispatchPerTick
        + " degradePolicy=" + degradePolicy;
  }

  public boolean hasBindings(CombatEventType type) {
    var list = byType.get(type);
    return list != null && !list.isEmpty();
  }

  public boolean dispatchPre(CombatEventContext context) {
    if (!enabled || context == null) {
      return false;
    }
    CopyOnWriteArrayList<CombatEventBinding> list = byType.get(context.eventType());
    if (list == null || list.isEmpty()) {
      return false;
    }
    metrics.incDispatchCalls();
    metrics.incEventType(context.eventType());
    if (!consumeDispatchBudget(context)) {
      return false;
    }
    DispatchResult result = executeDispatch(context, list);
    if (result.cancelled()) {
      metrics.incCancelledPreEvents();
    }
    return result.cancelled();
  }

  public void dispatch(CombatEventContext context) {
    if (!enabled || context == null) {
      return;
    }
    if (context.eventType().isPreEvent()) {
      dispatchPre(context);
      return;
    }
    CopyOnWriteArrayList<CombatEventBinding> list = byType.get(context.eventType());
    if (list == null || list.isEmpty()) {
      return;
    }
    metrics.incDispatchCalls();
    metrics.incEventType(context.eventType());
    if (!consumeDispatchBudget(context)) {
      return;
    }
    if (!asyncPlannerEnabled || !planner.enabled()) {
      executeDispatch(context, list);
      return;
    }
    metrics.incAsyncSubmitted();
    boolean accepted = planner.submit(context, List.copyOf(list), planned -> Bukkit.getScheduler().runTask(engine.plugin(), () -> {
      if (planTtlTicks > 0 && engine.tickNow() - planned.tick() > planTtlTicks) {
        metrics.incStalePlans();
        return;
      }
      metrics.incAsyncCompleted();
      executeDispatch(context, planned.bindings());
    }));
    if (!accepted) {
      metrics.incDroppedByQueue();
      executeDispatch(context, list);
    }
  }

  private DispatchResult executeDispatch(CombatEventContext context, List<CombatEventBinding> list) {
    int fired = 0;
    boolean cancelled = false;
    boolean preEvent = context.eventType().isPreEvent();
    for (CombatEventBinding binding : list) {
      if (preEvent && binding.phase() != CombatEventPhase.PRE) {
        continue;
      }
      if (!preEvent && binding.phase() == CombatEventPhase.PRE) {
        continue;
      }
      if (!matches(binding, context)) {
        continue;
      }
      if (!rollChance(binding)) {
        continue;
      }
      if (!checkCooldown(binding, context)) {
        continue;
      }
      if (binding.cancelEvent()) {
        cancelled = true;
      }
      String abilityId = boundId(binding);
      if (abilityId == null || abilityId.isBlank()) {
        continue;
      }
      LivingEntity caster = context.defaultCaster();
      if (caster == null) {
        continue;
      }
      LivingEntity target = context.targetFor(binding.targetBind());
      cast(binding, abilityId, caster, target, context);
      fired++;
    }
    metrics.addDispatchedBindings(fired);
    return new DispatchResult(fired, cancelled);
  }

  private String boundId(CombatEventBinding binding) {
    return binding.abilityId();
  }

  private boolean matches(CombatEventBinding binding, CombatEventContext context) {
    LivingEntity attacker = context.attacker();
    if (binding.requireSneaking()) {
      if (!(attacker instanceof Player player) || !player.isSneaking()) {
        return false;
      }
    }
    if (binding.requiredPermission() != null && !binding.requiredPermission().isBlank()) {
      if (!(attacker instanceof Player player) || !player.hasPermission(binding.requiredPermission())) {
        return false;
      }
    }
    return binding.filters().matches(context, engine);
  }

  private boolean rollChance(CombatEventBinding binding) {
    if (binding.chance() >= 1.0) {
      return true;
    }
    if (binding.chance() <= 0.0) {
      return false;
    }
    return Math.random() <= binding.chance();
  }

  private boolean checkCooldown(CombatEventBinding binding, CombatEventContext context) {
    if (binding.cooldownTicks() <= 0L) {
      return true;
    }
    LivingEntity attacker = context.attacker();
    LivingEntity victim = context.victim();
    if (!(attacker instanceof Player player)) {
      return true;
    }
    String key = switch (binding.cooldownScope()) {
      case PER_PLAYER -> "combat_event:" + binding.eventType().name().toLowerCase(Locale.ROOT) + ":" + binding.abilityId();
      case PER_TARGET -> "combat_event_target:" + (victim == null ? "none" : victim.getUniqueId()) + ":" + binding.abilityId();
      case PER_ABILITY -> "combat_event_ability:" + binding.abilityId();
    };
    return engine.tryStartCooldown(player.getUniqueId(), key, binding.cooldownTicks());
  }

  private void cast(CombatEventBinding binding, String abilityId, LivingEntity caster, LivingEntity target, CombatEventContext context) {
    if (!engine.hasAbility(abilityId)) {
      if (debug && warnedLegacy.add("missing:" + abilityId)) {
        engine.warn("[Combat] event ability not registered: " + abilityId);
      }
      return;
    }
    Location origin = resolveOrigin(binding.originBind(), context, caster);
    if (origin == null || origin.getWorld() == null) {
      origin = caster.getEyeLocation();
    }
    Vector direction = resolveDirection(binding.originBind(), context, caster, origin);
    ItemStack item = null;
    if (caster instanceof Player player) {
      ItemStack hand = player.getInventory().getItemInMainHand();
      if (hand != null && !hand.getType().isAir()) {
        item = hand;
      }
    }
    engine.castWithContext(abilityId, caster, origin, direction, item, ctx -> {
      if (target != null) {
        ctx.state().put(YAML_LAST_ENTITY, target);
      }
      if (context.projectileTelemetry() != null) {
        ctx.state().put(Vars.PROJECTILE_LAST_HIT, context.projectileTelemetry());
      }
      ctx.variables().put(Vars.COMBAT_EVENT_TYPE, context.eventType().name());
      ctx.variables().put(Vars.COMBAT_EVENT_SOURCE, context.source().name());
      ctx.variables().put(Vars.COMBAT_EVENT_DAMAGE, context.damage());
      if (context.attacker() != null) {
        ctx.variables().put(Vars.COMBAT_EVENT_ATTACKER, context.attacker().getUniqueId().toString());
      }
      if (context.victim() != null) {
        ctx.variables().put(Vars.COMBAT_EVENT_VICTIM, context.victim().getUniqueId().toString());
      }
      if (context.ccType() != null) {
        ctx.variables().put(Vars.COMBAT_EVENT_CC_TYPE, context.ccType());
      }
      if (context.dotTag() != null) {
        ctx.variables().put(Vars.COMBAT_EVENT_DOT_TAG, context.dotTag());
      }
      if (context.projectileId() != null) {
        ctx.variables().put(Vars.COMBAT_EVENT_PROJECTILE_ID, context.projectileId().toString());
      }
      if (context.projectileType() != null) {
        ctx.variables().put(Vars.COMBAT_EVENT_PROJECTILE_TYPE, context.projectileType());
      }
      if (context.projectileFamily() != null) {
        ctx.variables().put(Vars.COMBAT_EVENT_PROJECTILE_FAMILY, context.projectileFamily().name());
      }
      if (context.projectileKind() != null && !context.projectileKind().isBlank()) {
        ctx.variables().put(Vars.COMBAT_EVENT_PROJECTILE_KIND, context.projectileKind());
      }
      ctx.variables().put(Vars.COMBAT_EVENT_PROJECTILE_DISTANCE, context.projectileDistance());
      ctx.variables().put(Vars.COMBAT_EVENT_PROJECTILE_SPEED, context.projectileSpeed());
      ctx.variables().put(Vars.COMBAT_EVENT_PROJECTILE_DRAW_FORCE, context.projectileDrawForce());
      ctx.variables().put(Vars.COMBAT_EVENT_PROJECTILE_PIERCE_LEVEL, context.projectilePierceLevel());
      ctx.variables().put(Vars.COMBAT_EVENT_PROJECTILE_IN_GROUND_TICKS, context.projectileInGroundTicks());
      ctx.variables().put(Vars.COMBAT_EVENT_PROJECTILE_CRITICAL, context.projectileCritical());
      ctx.variables().put(Vars.COMBAT_EVENT_PROJECTILE_CHARGED, context.projectileCharged());
      ctx.variables().put(Vars.COMBAT_EVENT_PROJECTILE_PIERCING, context.projectilePiercing());
      ctx.variables().put(Vars.COMBAT_EVENT_PROJECTILE_SHOT_FROM_CROSSBOW, context.projectileShotFromCrossbow());
      ctx.variables().put(Vars.COMBAT_EVENT_PROJECTILE_SHOOTER_IS_PLAYER, context.shooterIsPlayer());
      if (context.hitBlockMaterial() != null) {
        ctx.variables().put(Vars.COMBAT_EVENT_PROJECTILE_HIT_BLOCK_MATERIAL, context.hitBlockMaterial());
      }
      if (context.hitBlockFace() != null) {
        ctx.variables().put(Vars.COMBAT_EVENT_PROJECTILE_HIT_BLOCK_FACE, context.hitBlockFace());
      }
      if (context.hitBlockTag() != null) {
        ctx.variables().put(Vars.COMBAT_EVENT_PROJECTILE_HIT_BLOCK_TAG, context.hitBlockTag());
      }
      if (context.impactLocation() != null) {
        ctx.variables().put(Vars.COMBAT_EVENT_PROJECTILE_IMPACT_WORLD, context.impactLocation().getWorld() == null
            ? ""
            : context.impactLocation().getWorld().getName());
        ctx.variables().put(Vars.COMBAT_EVENT_PROJECTILE_IMPACT_X, context.impactLocation().getX());
        ctx.variables().put(Vars.COMBAT_EVENT_PROJECTILE_IMPACT_Y, context.impactLocation().getY());
        ctx.variables().put(Vars.COMBAT_EVENT_PROJECTILE_IMPACT_Z, context.impactLocation().getZ());
      }
    });
    if (debug) {
      engine.debug("[Combat] dispatch ability=" + abilityId
          + " event=" + context.eventType()
          + " caster=" + caster.getUniqueId()
          + " target=" + (target == null ? "none" : target.getUniqueId()));
    }
  }

  public boolean onDamagePacketBudget() {
    if (maxDamagePacketsPerTick <= 0) {
      return true;
    }
    if (++tickBudgetDamagePackets > maxDamagePacketsPerTick) {
      metrics.incDroppedByGuardrail();
      return false;
    }
    return true;
  }

  public void shutdown() {
    planner.shutdown();
    clear();
  }

  private Location resolveOrigin(CombatEventOriginBind bind, CombatEventContext context, LivingEntity caster) {
    if (bind == null) {
      bind = CombatEventOriginBind.IMPACT;
    }
    return switch (bind) {
      case IMPACT -> context.impactLocation() == null ? caster.getEyeLocation() : context.impactLocation().clone();
      case ATTACKER -> context.attacker() == null ? caster.getEyeLocation() : context.attacker().getEyeLocation();
      case VICTIM -> context.victim() == null ? caster.getEyeLocation() : context.victim().getLocation();
      case EVENT_PRIMARY -> context.primaryTarget() == null ? caster.getEyeLocation() : context.primaryTarget().getLocation();
      case LEGACY_ORIGIN -> caster.getEyeLocation();
    };
  }

  private Vector resolveDirection(CombatEventOriginBind bind, CombatEventContext context, LivingEntity caster, Location origin) {
    Vector dir = switch (bind == null ? CombatEventOriginBind.IMPACT : bind) {
      case IMPACT -> context.impactDirection();
      case ATTACKER -> context.attacker() == null ? null : context.attacker().getEyeLocation().getDirection();
      case VICTIM -> context.victim() == null ? null : context.victim().getLocation().getDirection();
      case EVENT_PRIMARY -> context.primaryTarget() == null ? null : context.primaryTarget().getLocation().getDirection();
      case LEGACY_ORIGIN -> caster.getEyeLocation().getDirection();
    };
    if (dir == null || dir.lengthSquared() < 1.0e-9) {
      if (origin != null && origin.getDirection() != null && origin.getDirection().lengthSquared() > 1.0e-9) {
        dir = origin.getDirection();
      } else {
        dir = caster.getEyeLocation().getDirection();
      }
    }
    return dir.clone().normalize();
  }

  private boolean consumeDispatchBudget(CombatEventContext context) {
    if (maxEventDispatchPerTick > 0 && ++tickBudgetDispatches > maxEventDispatchPerTick) {
      metrics.incDroppedByGuardrail();
      return false;
    }
    if (!context.eventType().isProjectileEvent()) {
      return true;
    }
    if (maxProjectileEventsPerTick > 0 && ++tickBudgetProjectileEvents > maxProjectileEventsPerTick) {
      metrics.incDroppedProjectileGuardrail();
      return false;
    }
    if (context.eventType() == CombatEventType.ON_PROJECTILE_TRAVEL_STEP
        && maxTravelStepDispatchPerTick > 0
        && ++tickBudgetTravelStepEvents > maxTravelStepDispatchPerTick) {
      metrics.incTravelStepDropped();
      return false;
    }
    return true;
  }

  private record DispatchResult(int fired, boolean cancelled) {
  }
}
