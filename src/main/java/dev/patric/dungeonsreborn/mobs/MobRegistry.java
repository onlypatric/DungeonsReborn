package dev.patric.dungeonsreborn.mobs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.Vars;
import dev.patric.dungeonsreborn.effects.damage.DamageType;
import dev.patric.dungeonsreborn.effects.mana.ManaProvider;
import dev.patric.dungeonsreborn.effects.minions.MinionManager;
import dev.patric.dungeonsreborn.effects.minions.MinionMode;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import net.kyori.adventure.bossbar.BossBar;

public final class MobRegistry implements Listener {
  private record MobInstance(String specId, UUID ownerId) {
  }

  public record MobSnapshot(UUID entityId, String mobId, String variantId, UUID ownerId, String world,
      double x, double y, double z, double health, double maxHealth) {
  }

  private static final long TICK_PERIOD = 1L;
  private final Map<String, MobSpec> specs = new LinkedHashMap<>();
  private final Map<UUID, MobInstance> active = new java.util.HashMap<>();
  private final Map<UUID, MobState> states = new java.util.HashMap<>();
  private final Random rng = new Random();
  private final EffectsEngine engine;
  private MinionManager minionManager;

  private static final class MobState {
    private UUID lastAttacker;
    private long nextMainTick;
    private long nextSecondaryTick;
    private final Map<String, Long> nextPassiveTick = new java.util.HashMap<>();
    private BossBar bossBar;
    private long nextBossBarAudienceTick;
    private org.bukkit.Location home;
    private UUID currentTarget;
    private long lastTargetSwitchTick;
    private long nextWanderTick;
    private String variantId;
    private String phaseId;
  }

  public MobRegistry(EffectsEngine engine) {
    this.engine = Objects.requireNonNull(engine, "engine");
    engine.runRepeating(TICK_PERIOD, TICK_PERIOD, this::tick);
  }

  public void setMinionManager(MinionManager minionManager) {
    this.minionManager = minionManager;
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

  public boolean has(String id) {
    Objects.requireNonNull(id, "id");
    return specs.containsKey(id);
  }

  public Set<String> ids() {
    return Collections.unmodifiableSet(specs.keySet());
  }

  public LivingEntity spawn(String id, Location location) {
    return spawn(id, location, null);
  }

  public LivingEntity spawn(String id, Location location, UUID ownerId) {
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
    applySpec(spec, living, ownerId, variant);
    active.put(living.getUniqueId(), new MobInstance(spec.id(), ownerId));
    MobState state = new MobState();
    state.home = living.getLocation().clone();
    state.variantId = variant == null ? null : variant.id();
    states.put(living.getUniqueId(), state);
    MobContext ctx = new MobContext(spec, living, ownerId);
    playSpawnFx(spec, living);
    spec.onSpawn().accept(ctx);
    return living;
  }

  private void applySpec(MobSpec spec, LivingEntity entity, UUID ownerId, MobVariantSpec variant) {
    if (spec.displayName() != null || (variant != null && (variant.name() != null || variant.namePrefix() != null || variant.nameSuffix() != null))) {
      net.kyori.adventure.text.Component base = spec.displayName();
      if (variant != null && variant.name() != null) {
        base = MobText.parse(variant.name());
      } else if (base == null) {
        base = net.kyori.adventure.text.Component.text(entity.getType().name());
      }
      if (variant != null && variant.namePrefix() != null) {
        base = MobText.parse(variant.namePrefix()).append(base);
      }
      if (variant != null && variant.nameSuffix() != null) {
        base = base.append(MobText.parse(variant.nameSuffix()));
      }
      entity.customName(base);
      entity.setCustomNameVisible(spec.showName() || (variant != null && variant.name() != null));
    } else {
      entity.customName(null);
      entity.setCustomNameVisible(false);
    }
    MobMarkers.setMobId(entity, spec.id());
    MobMarkers.setOwner(entity, ownerId);
    if (variant != null && variant.id() != null) {
      MobMarkers.setVariant(entity, variant.id());
    } else {
      MobMarkers.setVariant(entity, null);
    }

    var equipment = entity.getEquipment();
    if (equipment != null) {
      equipment.setItemInMainHand(spec.mainHand());
      equipment.setItemInOffHand(spec.offHand());
      equipment.setHelmet(spec.head());
      equipment.setChestplate(spec.chest());
      equipment.setLeggings(spec.legs());
      equipment.setBoots(spec.feet());
    }

    applyAttributes(entity, spec.attributes());
    if (variant != null) {
      applyVariant(entity, variant);
    }
    applyResistances(entity, spec.resistances());
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
    MobInstance inst = active.remove(entity.getUniqueId());
    states.remove(entity.getUniqueId());
    UUID ownerId = inst == null ? MobMarkers.getOwner(entity) : inst.ownerId();
    MobContext ctx = new MobContext(spec, entity, ownerId);
    MobState state = states.remove(entity.getUniqueId());
    if (state != null) {
      removeBossBar(state);
    }
    playDeathFx(spec, entity);
    applyLoot(spec, entity, event);
    applyManaDrops(spec, entity);
    spec.onDeath().accept(ctx);
    spec.onRemove().accept(ctx, MobRemovalReason.DEATH);
  }

  @EventHandler
  public void onRemove(EntityRemoveFromWorldEvent event) {
    Entity entity = event.getEntity();
    UUID uuid = entity.getUniqueId();
    MobInstance inst = active.remove(uuid);
    states.remove(uuid);
    if (inst == null) {
      return;
    }
    MobSpec spec = specs.get(inst.specId());
    if (spec == null || !(entity instanceof LivingEntity living)) {
      return;
    }
    engine.clearResistances(uuid);
    engine.clearReflect(uuid);
    MobContext ctx = new MobContext(spec, living, inst.ownerId());
    MobState state = states.remove(uuid);
    if (state != null) {
      removeBossBar(state);
    }
    spec.onRemove().accept(ctx, MobRemovalReason.REMOVED);
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
    MobState state = states.get(entity.getUniqueId());
    if (state != null) {
      state.lastAttacker = attacker.getUniqueId();
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

  private void tick() {
    if (active.isEmpty()) {
      return;
    }
    long now = engine.tickNow();
    var iterator = active.entrySet().iterator();
    while (iterator.hasNext()) {
      var entry = iterator.next();
      UUID entityId = entry.getKey();
      MobInstance inst = entry.getValue();
      MobSpec spec = specs.get(inst.specId());
      Entity entity = org.bukkit.Bukkit.getEntity(entityId);
      if (!(entity instanceof LivingEntity living) || !entity.isValid() || living.isDead()) {
        iterator.remove();
        states.remove(entityId);
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
      if (spec.bossBar() != null) {
        updateBossBar(spec, living, state, inst.ownerId(), now);
      }
      tickPassives(spec, phase, living, state, inst.ownerId(), now);
      MobAttackSpec mainAttack = phase != null && phase.mainAttack() != null ? phase.mainAttack() : spec.mainAttack();
      MobAttackSpec secondaryAttack = phase != null && phase.secondaryAttack() != null ? phase.secondaryAttack() : spec.secondaryAttack();
      tickAttack(spec, inst, mainAttack, living, state, now, true);
      tickAttack(spec, inst, secondaryAttack, living, state, now, false);
    }
  }

  private void tickPassives(MobSpec spec, MobPhaseSpec phase, LivingEntity entity, MobState state, UUID ownerId, long now) {
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
    MobAiSpec ai = spec.aiSpec();
    if (ai == null || !ai.enabled()) {
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
        long interval = ai.idleWanderIntervalTicks();
        if (ai.idleWanderRadius() > 0.0 && now >= state.nextWanderTick) {
          state.nextWanderTick = now + interval;
          org.bukkit.Location wander = randomHomeOffset(state.home, ai.idleWanderRadius());
          moveToward(entity, wander, 0.18);
        }
      }
    }

    LivingEntity target = resolveTarget(state.currentTarget);
    if (target == null) {
      return;
    }

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

  private void tickAttack(MobSpec spec, MobInstance inst, MobAttackSpec attack, LivingEntity entity, MobState state, long now, boolean main) {
    if (attack == null) {
      return;
    }
    long next = main ? state.nextMainTick : state.nextSecondaryTick;
    if (now < next) {
      return;
    }
    LivingEntity target = selectTarget(entity, state, attack);
    if (target == null && attack.requireTarget()) {
      return;
    }
    if (target != null && !canTrigger(attack, entity, target)) {
      return;
    }
    if (attack.chance() < 1.0 && rng.nextDouble() > attack.chance()) {
      scheduleNext(state, attack, now, main);
      return;
    }
    if (entity instanceof Mob mob) {
      if (target != null) {
        mob.setTarget(target);
      }
    }
    UUID ownerId = inst == null ? MobMarkers.getOwner(entity) : inst.ownerId();
    MobCastContext ctx = new MobCastContext(spec, attack, entity, target, ownerId);
    attack.beforeCast().accept(ctx);
    tryCast(entity, attack.abilityId(), spec, attack, target, ownerId);
    attack.afterCast().accept(ctx);
    scheduleNext(state, attack, now, main);
  }

  private void scheduleNext(MobState state, MobAttackSpec attack, long now, boolean main) {
    long next = now + Math.max(0L, attack.cooldownTicks());
    if (main) {
      state.nextMainTick = next;
    } else {
      state.nextSecondaryTick = next;
    }
  }

  private LivingEntity selectTarget(LivingEntity entity, MobState state, MobAttackSpec attack) {
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
    };
    return isFriendlyTarget(entity, candidate) ? null : candidate;
  }

  private boolean canTrigger(MobAttackSpec attack, LivingEntity entity, LivingEntity target) {
    double range = attack.range();
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
      });
    } catch (IllegalArgumentException ignored) {
    }
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

  private void applyLoot(MobSpec spec, LivingEntity entity, EntityDeathEvent event) {
    MobLootSpec loot = spec.loot();
    if (loot == null) {
      return;
    }
    if (loot.clearVanilla()) {
      event.getDrops().clear();
    }
    for (MobDropSpec drop : loot.drops()) {
      ItemStack item = drop.roll(rng);
      if (item == null || item.getType().isAir()) {
        continue;
      }
      entity.getWorld().dropItemNaturally(entity.getLocation(), item);
    }
  }

  private void applyManaDrops(MobSpec spec, LivingEntity entity) {
    MobManaDropSpec drop = spec.manaDrop();
    if (drop == null || drop.isEmpty()) {
      return;
    }
    ManaProvider provider = engine.manaProvider();
    if (provider == null) {
      return;
    }
    Player killer = entity.getKiller();
    if (killer == null) {
      return;
    }
    if (drop.killer() != null && !drop.killer().isEmpty()) {
      addMana(provider, killer, drop.killer().roll(rng));
    }
    if (drop.nearby() != null && !drop.nearby().isEmpty() && drop.nearbyRadius() > 0.0) {
      double radius = drop.nearbyRadius();
      double radiusSq = radius * radius;
      var loc = entity.getLocation();
      for (Player player : entity.getWorld().getPlayers()) {
        if (player.getLocation().distanceSquared(loc) <= radiusSq) {
          addMana(provider, player, drop.nearby().roll(rng));
        }
      }
    }
  }

  private static void addMana(ManaProvider provider, Player player, double amount) {
    if (provider == null || player == null || !Double.isFinite(amount) || amount <= 0.0) {
      return;
    }
    double max = provider.getMax(player);
    if (max <= 0.0) {
      return;
    }
    double current = provider.get(player);
    provider.set(player, Math.min(max, current + amount));
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
      Location loc = living.getLocation();
      double maxHealth = maxHealth(living);
      out.add(new MobSnapshot(
          entityId,
          inst.specId(),
          variantId,
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
    Location loc = living.getLocation();
    double maxHealth = maxHealth(living);
    return new MobSnapshot(
        entityId,
        inst.specId(),
        variantId,
        inst.ownerId(),
        loc.getWorld() == null ? "unknown" : loc.getWorld().getName(),
        loc.getX(),
        loc.getY(),
        loc.getZ(),
        living.getHealth(),
        maxHealth);
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
    return minionManager.mode(ownerId);
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
    if (isMinion(mob) && target instanceof Player && !target.getUniqueId().equals(ownerId)) {
      return true;
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
    if (radius <= 0.0) {
      return true;
    }
    return mob.getLocation().distanceSquared(target.getLocation()) <= radius * radius;
  }

  private LivingEntity selectAggroTarget(LivingEntity mob, MobState state, MobAiSpec ai) {
    double radius = ai.aggroRadius();
    if (isMinion(mob)) {
      LivingEntity owner = resolveOwner(mob);
      MinionMode mode = minionMode(mob, owner);
      if (mode == MinionMode.PASSIVE) {
        return null;
      }
      LivingEntity ownerTarget = resolveOwnerTarget(owner);
      if (ownerTarget != null && isValidTarget(mob, ownerTarget, radius) && !isFriendlyTarget(mob, ownerTarget)) {
        return ownerTarget;
      }
      if (mode == MinionMode.DEFENSIVE) {
        LivingEntity last = resolveOwnerLastAttacker(owner);
        if (last != null && isValidTarget(mob, last, radius) && !isFriendlyTarget(mob, last)) {
          return last;
        }
        return null;
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
    };
    return isFriendlyTarget(mob, candidate) ? null : candidate;
  }

  private void setTarget(LivingEntity mob, MobState state, LivingEntity target, long now) {
    state.currentTarget = target.getUniqueId();
    state.lastTargetSwitchTick = now;
    if (mob instanceof Mob bukkitMob) {
      bukkitMob.setTarget(target);
    }
  }

  private void clearTarget(LivingEntity mob, MobState state) {
    state.currentTarget = null;
    if (mob instanceof Mob bukkitMob) {
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
    }
    return selected;
  }

  private void moveToward(LivingEntity mob, org.bukkit.Location target, double speed) {
    if (target == null) {
      return;
    }
    org.bukkit.Location from = mob.getLocation();
    Vector dir = target.toVector().subtract(from.toVector());
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

  private void updateBossBar(MobSpec spec, LivingEntity entity, MobState state, UUID ownerId, long now) {
    MobBossBarSpec barSpec = spec.bossBar();
    if (barSpec == null) {
      return;
    }
    if (entity.getType() == EntityType.WITHER || entity.getType() == EntityType.ENDER_DRAGON) {
      removeBossBar(state);
      return;
    }
    if (state.bossBar == null) {
      state.bossBar = BossBar.bossBar(barSpec.title(), 1.0f, barSpec.color(), barSpec.overlay());
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
