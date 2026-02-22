package dev.patric.dungeonsreborn.effects.combat;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
  private volatile long tickBudgetDispatches;
  private volatile long tickBudgetDamagePackets;
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

  public void tickStart() {
    tickBudgetDispatches = 0L;
    tickBudgetDamagePackets = 0L;
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
        + " degradePolicy=" + degradePolicy;
  }

  public void dispatch(CombatEventContext context) {
    if (!enabled || context == null) {
      return;
    }
    CopyOnWriteArrayList<CombatEventBinding> list = byType.get(context.eventType());
    if (list == null || list.isEmpty()) {
      return;
    }
    metrics.incDispatchCalls();
    if (maxEventDispatchPerTick > 0 && ++tickBudgetDispatches > maxEventDispatchPerTick) {
      metrics.incDroppedByGuardrail();
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

  private void executeDispatch(CombatEventContext context, List<CombatEventBinding> list) {
    int fired = 0;
    for (CombatEventBinding binding : list) {
      if (!matches(binding, context)) {
        continue;
      }
      if (!rollChance(binding)) {
        continue;
      }
      if (!checkCooldown(binding, context)) {
        continue;
      }
      LivingEntity caster = context.defaultCaster();
      if (caster == null) {
        continue;
      }
      LivingEntity target = context.targetFor(binding.targetBind());
      cast(boundId(binding), caster, target, context);
      fired++;
    }
    metrics.addDispatchedBindings(fired);
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

  private void cast(String abilityId, LivingEntity caster, LivingEntity target, CombatEventContext context) {
    if (!engine.hasAbility(abilityId)) {
      if (debug && warnedLegacy.add("missing:" + abilityId)) {
        engine.warn("[Combat] event ability not registered: " + abilityId);
      }
      return;
    }
    Location origin = caster.getEyeLocation();
    Vector direction = origin.getDirection();
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
}

