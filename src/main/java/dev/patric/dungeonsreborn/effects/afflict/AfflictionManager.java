package dev.patric.dungeonsreborn.effects.afflict;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import dev.patric.dungeonsreborn.effects.CastContext;
import dev.patric.dungeonsreborn.effects.CastState;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.Vars;
import dev.patric.dungeonsreborn.effects.actions.Action;

public final class AfflictionManager {
  private static final String STATE_LAST_ENTITY = "yaml_last_entity";

  public record ApplySpec(
      String id,
      int stacks,
      int maxStacks,
      long durationTicks,
      AfflictionRefreshPolicy refreshPolicy,
      AfflictionAudience audience,
      long tickEveryTicks,
      Action onTick,
      Action onApply,
      Action onStack,
      Action onExpire) {
    public ApplySpec {
      id = normalizeId(id);
      stacks = Math.max(1, stacks);
      maxStacks = Math.max(1, maxStacks);
      durationTicks = Math.max(1L, durationTicks);
      refreshPolicy = refreshPolicy == null ? AfflictionRefreshPolicy.RESET_DURATION : refreshPolicy;
      audience = audience == null ? AfflictionAudience.PVE_ONLY : audience;
      tickEveryTicks = Math.max(0L, tickEveryTicks);
    }

    private static String normalizeId(String raw) {
      if (raw == null) {
        throw new IllegalArgumentException("affliction id cannot be null");
      }
      String token = raw.trim().toLowerCase();
      if (token.isEmpty()) {
        throw new IllegalArgumentException("affliction id cannot be empty");
      }
      return token;
    }
  }

  private final EffectsEngine engine;
  private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, AfflictionInstance>> byEntity = new ConcurrentHashMap<>();
  private volatile boolean enabled = true;
  private volatile int maxTrackedPerEntity = 24;
  private volatile long cleanupIntervalTicks = 100L;
  private long lastCleanupTick;

  public AfflictionManager(EffectsEngine engine) {
    this.engine = Objects.requireNonNull(engine, "engine");
  }

  public void configure(boolean enabled, int maxTrackedPerEntity, long cleanupIntervalTicks) {
    this.enabled = enabled;
    this.maxTrackedPerEntity = Math.max(1, maxTrackedPerEntity);
    this.cleanupIntervalTicks = Math.max(1L, cleanupIntervalTicks);
  }

  public boolean enabled() {
    return enabled;
  }

  public void clearAll() {
    byEntity.clear();
  }

  public boolean apply(CastContext source, LivingEntity target, ApplySpec spec) {
    Objects.requireNonNull(spec, "spec");
    if (!enabled || target == null || !target.isValid() || target.isDead()) {
      return false;
    }
    LivingEntity caster = source == null ? target : source.caster();
    if (!spec.audience().allows(caster, target)) {
      return false;
    }
    long now = engine.tickNow();
    UUID targetId = target.getUniqueId();
    ConcurrentHashMap<String, AfflictionInstance> map = byEntity.computeIfAbsent(targetId, k -> new ConcurrentHashMap<>());

    AfflictionInstance existing = map.get(spec.id());
    if (existing == null && map.size() >= maxTrackedPerEntity) {
      evictOldest(map);
    }
    if (existing == null) {
      AfflictionInstance created = new AfflictionInstance(
          spec.id(),
          Math.min(spec.maxStacks(), spec.stacks()),
          spec.maxStacks(),
          now + spec.durationTicks(),
          spec.refreshPolicy(),
          spec.audience(),
          spec.tickEveryTicks(),
          spec.onTick(),
          spec.onApply(),
          spec.onStack(),
          spec.onExpire(),
          source == null ? null : source.caster().getUniqueId(),
          source == null ? null : source.abilityId());
      if (created.tickEveryTicks() > 0L) {
        created.setNextTickAt(now + created.tickEveryTicks());
      } else {
        created.setNextTickAt(0L);
      }
      map.put(spec.id(), created);
      runLifecycle(source, target, created, created.onApply(), now);
      return true;
    }

    int beforeStacks = existing.stacks();
    existing.setMaxStacks(Math.max(existing.maxStacks(), spec.maxStacks()));
    existing.setStacks(Math.min(existing.maxStacks(), beforeStacks + spec.stacks()));
    existing.setRefreshPolicy(spec.refreshPolicy());
    existing.setAudience(spec.audience());
    if (spec.onTick() != null) {
      existing.setOnTick(spec.onTick());
    }
    if (spec.onApply() != null) {
      existing.setOnApply(spec.onApply());
    }
    if (spec.onStack() != null) {
      existing.setOnStack(spec.onStack());
    }
    if (spec.onExpire() != null) {
      existing.setOnExpire(spec.onExpire());
    }
    if (source != null) {
      existing.setSourceCasterId(source.caster().getUniqueId());
      existing.setSourceAbilityId(source.abilityId());
    }
    if (spec.tickEveryTicks() > 0L) {
      existing.setTickEveryTicks(spec.tickEveryTicks());
      existing.setNextTickAt(now + spec.tickEveryTicks());
    }
    switch (spec.refreshPolicy()) {
      case RESET_DURATION -> existing.setExpiresAtTick(now + spec.durationTicks());
      case EXTEND_DURATION -> existing.setExpiresAtTick(existing.expiresAtTick() + spec.durationTicks());
      case MAX_DURATION -> existing.setExpiresAtTick(Math.max(existing.expiresAtTick(), now + spec.durationTicks()));
      case KEEP_DURATION -> {
      }
    }
    runLifecycle(source, target, existing, existing.onStack(), now);
    return existing.stacks() != beforeStacks;
  }

  public boolean consume(
      CastContext source,
      LivingEntity target,
      String afflictionId,
      int stacks,
      int requireAtLeast) {
    if (!enabled || target == null || afflictionId == null) {
      return false;
    }
    String id = afflictionId.trim().toLowerCase();
    if (id.isEmpty()) {
      return false;
    }
    ConcurrentHashMap<String, AfflictionInstance> map = byEntity.get(target.getUniqueId());
    if (map == null) {
      return false;
    }
    AfflictionInstance instance = map.get(id);
    if (instance == null) {
      return false;
    }
    if (instance.stacks() < Math.max(1, requireAtLeast)) {
      return false;
    }
    int consume = Math.max(1, stacks);
    int next = instance.stacks() - consume;
    instance.setStacks(Math.max(0, next));
    if (instance.stacks() <= 0) {
      map.remove(id);
      if (map.isEmpty()) {
        byEntity.remove(target.getUniqueId());
      }
    }
    long now = engine.tickNow();
    CastContext ctx = buildContext(source, target, instance, now);
    ctx.state().put(Vars.AFFLICT_ID, id);
    ctx.state().put(Vars.AFFLICT_STACKS, instance.stacks());
    ctx.state().put(Vars.AFFLICT_REMAINING_TICKS, remainingTicks(target, id));
    return true;
  }

  public void clear(CastContext source, LivingEntity target, String afflictionId) {
    if (target == null) {
      return;
    }
    UUID targetId = target.getUniqueId();
    ConcurrentHashMap<String, AfflictionInstance> map = byEntity.get(targetId);
    if (map == null) {
      return;
    }
    long now = engine.tickNow();
    if (afflictionId == null || afflictionId.isBlank()) {
      for (AfflictionInstance instance : map.values()) {
        runLifecycle(source, target, instance, instance.onExpire(), now);
      }
      byEntity.remove(targetId);
      return;
    }
    String id = afflictionId.trim().toLowerCase();
    AfflictionInstance removed = map.remove(id);
    if (removed != null) {
      runLifecycle(source, target, removed, removed.onExpire(), now);
    }
    if (map.isEmpty()) {
      byEntity.remove(targetId);
    }
  }

  public boolean present(LivingEntity target, String afflictionId) {
    return stacks(target, afflictionId) > 0;
  }

  public int stacks(LivingEntity target, String afflictionId) {
    AfflictionInstance instance = getLive(target, afflictionId);
    return instance == null ? 0 : Math.max(0, instance.stacks());
  }

  public long remainingTicks(LivingEntity target, String afflictionId) {
    AfflictionInstance instance = getLive(target, afflictionId);
    if (instance == null) {
      return 0L;
    }
    long remaining = instance.expiresAtTick() - engine.tickNow();
    return Math.max(0L, remaining);
  }

  public void tick(long now) {
    if (!enabled) {
      clearAll();
      return;
    }
    Iterator<Map.Entry<UUID, ConcurrentHashMap<String, AfflictionInstance>>> entityIt = byEntity.entrySet().iterator();
    while (entityIt.hasNext()) {
      Map.Entry<UUID, ConcurrentHashMap<String, AfflictionInstance>> entry = entityIt.next();
      LivingEntity target = resolveLiving(entry.getKey());
      if (target == null) {
        entityIt.remove();
        continue;
      }
      ConcurrentHashMap<String, AfflictionInstance> map = entry.getValue();
      Iterator<Map.Entry<String, AfflictionInstance>> affIt = map.entrySet().iterator();
      while (affIt.hasNext()) {
        Map.Entry<String, AfflictionInstance> affEntry = affIt.next();
        AfflictionInstance instance = affEntry.getValue();
        if (instance == null) {
          affIt.remove();
          continue;
        }
        if (now >= instance.expiresAtTick()) {
          runLifecycle(null, target, instance, instance.onExpire(), now);
          affIt.remove();
          continue;
        }
        if (instance.tickEveryTicks() > 0L && now >= instance.nextTickAt()) {
          runLifecycle(null, target, instance, instance.onTick(), now);
          long next = instance.nextTickAt();
          long step = Math.max(1L, instance.tickEveryTicks());
          while (next <= now) {
            next += step;
          }
          instance.setNextTickAt(next);
        }
      }
      if (map.isEmpty()) {
        entityIt.remove();
      }
    }
    if (cleanupIntervalTicks > 0L && (now - lastCleanupTick) >= cleanupIntervalTicks) {
      cleanup();
      lastCleanupTick = now;
    }
  }

  private void cleanup() {
    Iterator<Map.Entry<UUID, ConcurrentHashMap<String, AfflictionInstance>>> it = byEntity.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<UUID, ConcurrentHashMap<String, AfflictionInstance>> entry = it.next();
      if (resolveLiving(entry.getKey()) == null || entry.getValue().isEmpty()) {
        it.remove();
      }
    }
  }

  private void runLifecycle(
      CastContext source,
      LivingEntity target,
      AfflictionInstance instance,
      Action action,
      long now) {
    if (action == null || target == null) {
      return;
    }
    try {
      CastContext ctx = buildContext(source, target, instance, now);
      action.execute(ctx);
    } catch (Exception ex) {
      engine.warn("affliction action failed id=" + instance.id() + " target=" + target.getUniqueId(), ex);
    }
  }

  private CastContext buildContext(
      CastContext source,
      LivingEntity target,
      AfflictionInstance instance,
      long now) {
    LivingEntity caster = source == null ? null : source.caster();
    if (caster == null || !caster.isValid() || caster.isDead()) {
      UUID sourceCasterId = instance.sourceCasterId();
      if (sourceCasterId != null) {
        caster = resolveLiving(sourceCasterId);
      }
    }
    if (caster == null || !caster.isValid() || caster.isDead()) {
      caster = target;
    }
    UUID castId = UUID.randomUUID();
    CastState state = new CastState(castId, engine.deterministicSeed());
    state.put(STATE_LAST_ENTITY, target);
    state.put(Vars.AFFLICT_ID, instance.id());
    state.put(Vars.AFFLICT_STACKS, instance.stacks());
    state.put(Vars.AFFLICT_REMAINING_TICKS, Math.max(0L, instance.expiresAtTick() - now));
    if (instance.sourceCasterId() != null) {
      state.put(Vars.AFFLICT_SOURCE, instance.sourceCasterId().toString());
    }
    Location origin = target.getLocation().clone();
    Vector direction = origin.getDirection();
    String abilityId = source != null
        ? source.abilityId()
        : (instance.sourceAbilityId() == null ? "afflict_" + instance.id() : instance.sourceAbilityId());
    return new CastContext(
        engine,
        engine.plugin(),
        castId,
        abilityId,
        now,
        state,
        caster,
        origin,
        direction,
        null);
  }

  private AfflictionInstance getLive(LivingEntity target, String afflictionId) {
    if (target == null || afflictionId == null) {
      return null;
    }
    String id = afflictionId.trim().toLowerCase();
    if (id.isEmpty()) {
      return null;
    }
    ConcurrentHashMap<String, AfflictionInstance> map = byEntity.get(target.getUniqueId());
    if (map == null) {
      return null;
    }
    AfflictionInstance instance = map.get(id);
    if (instance == null) {
      return null;
    }
    if (engine.tickNow() >= instance.expiresAtTick()) {
      map.remove(id);
      if (map.isEmpty()) {
        byEntity.remove(target.getUniqueId());
      }
      return null;
    }
    return instance;
  }

  private static LivingEntity resolveLiving(UUID uuid) {
    if (uuid == null) {
      return null;
    }
    Entity entity = Bukkit.getEntity(uuid);
    if (entity instanceof LivingEntity living && living.isValid() && !living.isDead()) {
      return living;
    }
    return null;
  }

  private static void evictOldest(ConcurrentHashMap<String, AfflictionInstance> map) {
    String victim = null;
    long oldestExpiry = Long.MAX_VALUE;
    for (Map.Entry<String, AfflictionInstance> entry : map.entrySet()) {
      AfflictionInstance instance = entry.getValue();
      if (instance == null) {
        continue;
      }
      if (instance.expiresAtTick() < oldestExpiry) {
        oldestExpiry = instance.expiresAtTick();
        victim = entry.getKey();
      }
    }
    if (victim != null) {
      map.remove(victim);
    }
  }
}
