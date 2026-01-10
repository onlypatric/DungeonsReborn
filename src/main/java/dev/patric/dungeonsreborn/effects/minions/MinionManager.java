package dev.patric.dungeonsreborn.effects.minions;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.damage.DamageType;
import dev.patric.dungeonsreborn.effects.mana.ManaProvider;
import dev.patric.dungeonsreborn.mobs.MobMarkers;
import dev.patric.dungeonsreborn.mobs.MobRegistry;

public final class MinionManager implements Listener {
  private record MinionInstance(String id, String mobId, UUID ownerId, long expiresTick, boolean despawnOnLogout) {
  }

  private final EffectsEngine engine;
  private final MobRegistry mobs;
  private final Random rng = new Random();
  private final Map<UUID, MinionInstance> byEntity = new ConcurrentHashMap<>();
  private final Map<UUID, java.util.Set<UUID>> byOwner = new ConcurrentHashMap<>();
  private final Map<UUID, MinionMode> modeByOwner = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> lastAttackerByOwner = new ConcurrentHashMap<>();
  private final AtomicLong spawnedCount = new AtomicLong();
  private final AtomicLong despawnedCount = new AtomicLong();

  public MinionManager(EffectsEngine engine, MobRegistry mobs) {
    this.engine = Objects.requireNonNull(engine, "engine");
    this.mobs = Objects.requireNonNull(mobs, "mobs");
  }

  public List<LivingEntity> summon(MinionSpec spec, Location origin) {
    Objects.requireNonNull(spec, "spec");
    Objects.requireNonNull(origin, "origin");
    if (spec.ownerId() != null && spec.id() != null && !spec.id().isBlank()) {
      dismissById(spec.ownerId(), spec.id());
    }
    List<LivingEntity> spawned = new ArrayList<>();
    for (int i = 0; i < spec.count(); i++) {
      Location spawn = offset(origin, spec.spawnRadius());
      LivingEntity entity = mobs.spawn(spec.mobId(), spawn, spec.ownerId());
      MobMarkers.setMinionId(entity, spec.id());
      long expires = engine.tickNow() + spec.durationTicks();
      register(entity.getUniqueId(), new MinionInstance(spec.id(), spec.mobId(), spec.ownerId(), expires, spec.despawnOnOwnerLogout()));
      if (spec.mode() != null && spec.ownerId() != null) {
        modeByOwner.put(spec.ownerId(), spec.mode());
      }
      applyScaling(entity, spec);
      applyResistances(entity, spec);
      schedulePassives(entity, spec, expires);
      scheduleSpecialAttacks(entity, spec, expires);
      engine.runLater(spec.durationTicks(), () -> despawn(entity.getUniqueId()));
      spawned.add(entity);
    }
    sendStatus(spec.ownerId());
    return spawned;
  }

  public int recall(UUID ownerId, Location origin) {
    Objects.requireNonNull(ownerId, "ownerId");
    Objects.requireNonNull(origin, "origin");
    java.util.Set<UUID> set = byOwner.get(ownerId);
    if (set == null || set.isEmpty()) {
      return 0;
    }
    int moved = 0;
    for (UUID entityId : List.copyOf(set)) {
      Entity entity = Bukkit.getEntity(entityId);
      if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
        continue;
      }
      living.teleport(offset(origin, 1.5));
      moved++;
    }
    sendStatus(ownerId);
    return moved;
  }

  public int dismiss(UUID ownerId) {
    Objects.requireNonNull(ownerId, "ownerId");
    java.util.Set<UUID> set = byOwner.get(ownerId);
    if (set == null || set.isEmpty()) {
      return 0;
    }
    int removed = 0;
    for (UUID entityId : List.copyOf(set)) {
      if (despawn(entityId)) {
        removed++;
      }
    }
    sendStatus(ownerId);
    return removed;
  }

  public int dismissById(UUID ownerId, String minionId) {
    Objects.requireNonNull(ownerId, "ownerId");
    Objects.requireNonNull(minionId, "minionId");
    java.util.Set<UUID> set = byOwner.get(ownerId);
    if (set == null || set.isEmpty()) {
      return 0;
    }
    int removed = 0;
    for (UUID entityId : List.copyOf(set)) {
      MinionInstance inst = byEntity.get(entityId);
      if (inst != null && minionId.equals(inst.id())) {
        if (despawn(entityId)) {
          removed++;
        }
      }
    }
    sendStatus(ownerId);
    return removed;
  }

  public MinionMode mode(UUID ownerId) {
    if (ownerId == null) {
      return MinionMode.AGGRESSIVE;
    }
    return modeByOwner.getOrDefault(ownerId, MinionMode.AGGRESSIVE);
  }

  public void setMode(UUID ownerId, MinionMode mode) {
    if (ownerId == null) {
      return;
    }
    if (mode == null) {
      modeByOwner.remove(ownerId);
    } else {
      modeByOwner.put(ownerId, mode);
    }
  }

  public UUID ownerLastAttacker(UUID ownerId) {
    if (ownerId == null) {
      return null;
    }
    return lastAttackerByOwner.get(ownerId);
  }

  public List<UUID> minionsFor(UUID ownerId) {
    java.util.Set<UUID> set = byOwner.get(ownerId);
    if (set == null) {
      return List.of();
    }
    return List.copyOf(set);
  }

  public int activeCount() {
    return byEntity.size();
  }

  public long spawnedCount() {
    return spawnedCount.get();
  }

  public long despawnedCount() {
    return despawnedCount.get();
  }

  public Map<UUID, List<UUID>> ownersSnapshot() {
    Map<UUID, List<UUID>> out = new java.util.LinkedHashMap<>();
    for (Map.Entry<UUID, java.util.Set<UUID>> entry : byOwner.entrySet()) {
      out.put(entry.getKey(), List.copyOf(entry.getValue()));
    }
    return out;
  }

  public boolean hasMob(String id) {
    return id != null && mobs.has(id);
  }

  public boolean despawn(UUID entityId) {
    MinionInstance inst = byEntity.remove(entityId);
    if (inst == null) {
      return false;
    }
    if (inst.ownerId() != null) {
      java.util.Set<UUID> set = byOwner.get(inst.ownerId());
      if (set != null) {
        set.remove(entityId);
        if (set.isEmpty()) {
          byOwner.remove(inst.ownerId());
        }
      }
    }
    Entity entity = Bukkit.getEntity(entityId);
    if (entity != null) {
      entity.remove();
    }
    despawnedCount.incrementAndGet();
    sendStatus(inst.ownerId());
    return true;
  }

  private void register(UUID entityId, MinionInstance inst) {
    byEntity.put(entityId, inst);
    if (inst.ownerId() != null) {
      byOwner.computeIfAbsent(inst.ownerId(), k -> java.util.Collections.newSetFromMap(new ConcurrentHashMap<>())).add(entityId);
    }
    spawnedCount.incrementAndGet();
  }

  private Location offset(Location origin, double radius) {
    if (radius <= 0.0) {
      return origin.clone();
    }
    double angle = rng.nextDouble() * Math.PI * 2.0;
    double r = rng.nextDouble() * radius;
    double dx = Math.cos(angle) * r;
    double dz = Math.sin(angle) * r;
    return origin.clone().add(dx, 0.0, dz);
  }

  @EventHandler
  public void onDeath(EntityDeathEvent event) {
    LivingEntity entity = event.getEntity();
    if (MobMarkers.getMinionId(entity) == null) {
      return;
    }
    despawn(entity.getUniqueId());
  }

  @EventHandler
  public void onRemove(EntityRemoveFromWorldEvent event) {
    Entity entity = event.getEntity();
    if (MobMarkers.getMinionId(entity) == null) {
      return;
    }
    despawn(entity.getUniqueId());
  }

  @EventHandler
  public void onOwnerQuit(PlayerQuitEvent event) {
    UUID ownerId = event.getPlayer().getUniqueId();
    java.util.Set<UUID> set = byOwner.get(ownerId);
    if (set == null || set.isEmpty()) {
      return;
    }
    for (UUID entityId : List.copyOf(set)) {
      MinionInstance inst = byEntity.get(entityId);
      if (inst != null && inst.despawnOnLogout()) {
        despawn(entityId);
      }
    }
    modeByOwner.remove(ownerId);
    lastAttackerByOwner.remove(ownerId);
  }

  @EventHandler
  public void onDamage(EntityDamageByEntityEvent event) {
    LivingEntity damager = resolveDamager(event.getDamager());
    if (damager == null) {
      return;
    }
    if (MobMarkers.getMinionId(damager) == null) {
      return;
    }
    UUID ownerId = MobMarkers.getOwner(damager);
    if (ownerId == null) {
      return;
    }
    Entity target = event.getEntity();
    if (target.getUniqueId().equals(ownerId)) {
      event.setCancelled(true);
      return;
    }
    if (target instanceof Player) {
      event.setCancelled(true);
      return;
    }
    if (target instanceof LivingEntity living) {
      UUID targetOwner = MobMarkers.getOwner(living);
      if (targetOwner != null && targetOwner.equals(ownerId)) {
        event.setCancelled(true);
      }
    }
  }

  @EventHandler
  public void onOwnerDamaged(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Player player)) {
      return;
    }
    LivingEntity attacker = resolveDamager(event.getDamager());
    if (attacker == null) {
      return;
    }
    lastAttackerByOwner.put(player.getUniqueId(), attacker.getUniqueId());
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

  private void applyScaling(LivingEntity entity, MinionSpec spec) {
    MinionScaling scaling = spec.scaling();
    if (scaling == null || !scaling.isEnabled()) {
      return;
    }
    LivingEntity owner = resolveOwner(spec.ownerId());
    if (owner == null) {
      return;
    }
    int level = owner instanceof Player player ? player.getLevel() : 0;
    double ownerMaxHealth = 0.0;
    AttributeInstance ownerHealth = owner.getAttribute(Attribute.MAX_HEALTH);
    if (ownerHealth != null) {
      ownerMaxHealth = ownerHealth.getValue();
    }
    double ownerMaxMana = 0.0;
    if (owner instanceof Player player) {
      ManaProvider mana = engine.manaProvider();
      if (mana != null) {
        ownerMaxMana = Math.max(0.0, mana.getMax(player));
      }
    }

    double bonusHealth = scaling.healthPerLevel() * level
        + scaling.healthPerMaxHealth() * ownerMaxHealth
        + scaling.healthPerManaMax() * ownerMaxMana;
    double bonusDamage = scaling.damagePerLevel() * level
        + scaling.damagePerMaxHealth() * ownerMaxHealth
        + scaling.damagePerManaMax() * ownerMaxMana;

    if (Math.abs(bonusHealth) > 1e-9) {
      AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
      if (maxHealth != null) {
        double next = Math.max(1.0, maxHealth.getBaseValue() + bonusHealth);
        maxHealth.setBaseValue(next);
        entity.setHealth(Math.min(entity.getHealth(), next));
      }
    }
    if (Math.abs(bonusDamage) > 1e-9) {
      AttributeInstance attack = entity.getAttribute(Attribute.ATTACK_DAMAGE);
      if (attack != null) {
        attack.setBaseValue(Math.max(0.0, attack.getBaseValue() + bonusDamage));
      }
    }
  }

  private void applyResistances(LivingEntity entity, MinionSpec spec) {
    if (spec.resistances().isEmpty() && spec.immunities().isEmpty()) {
      return;
    }
    UUID entityId = entity.getUniqueId();
    for (Map.Entry<DamageType, Double> entry : spec.resistances().entrySet()) {
      engine.setResistance(entityId, entry.getKey(), entry.getValue());
    }
    if (!spec.immunities().isEmpty()) {
      EnumSet<DamageType> immune = EnumSet.copyOf(spec.immunities());
      for (DamageType type : immune) {
        engine.setResistance(entityId, type, 0.0);
      }
    }
  }

  private LivingEntity resolveOwner(UUID ownerId) {
    if (ownerId == null) {
      return null;
    }
    Entity entity = Bukkit.getEntity(ownerId);
    if (entity instanceof LivingEntity living && living.isValid() && !living.isDead()) {
      return living;
    }
    return null;
  }

  private void schedulePassives(LivingEntity entity, MinionSpec spec, long expiresTick) {
    if (spec.passives().isEmpty()) {
      return;
    }
    for (MinionPassiveSpec passive : spec.passives()) {
      engine.runLater(passive.periodTicks(), () -> tickPassive(entity.getUniqueId(), passive, expiresTick));
    }
  }

  private void tickPassive(UUID entityId, MinionPassiveSpec passive, long expiresTick) {
    MinionInstance inst = byEntity.get(entityId);
    if (inst == null || engine.tickNow() >= expiresTick) {
      return;
    }
    Entity entity = Bukkit.getEntity(entityId);
    if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
      return;
    }
    castAbility(living, inst.ownerId(), null, passive.abilityId());
    engine.runLater(passive.periodTicks(), () -> tickPassive(entityId, passive, expiresTick));
  }

  private void scheduleSpecialAttacks(LivingEntity entity, MinionSpec spec, long expiresTick) {
    if (spec.specialAttacks().isEmpty()) {
      return;
    }
    for (MinionSpecialAttackSpec attack : spec.specialAttacks()) {
      engine.runLater(attack.cooldownTicks(), () -> tickSpecialAttack(entity.getUniqueId(), attack, expiresTick));
    }
  }

  private void tickSpecialAttack(UUID entityId, MinionSpecialAttackSpec attack, long expiresTick) {
    MinionInstance inst = byEntity.get(entityId);
    if (inst == null || engine.tickNow() >= expiresTick) {
      return;
    }
    Entity entity = Bukkit.getEntity(entityId);
    if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
      return;
    }
    LivingEntity target = null;
    if (living instanceof Mob mob) {
      target = mob.getTarget();
    }
    if (attack.requireTarget() && target == null) {
      engine.runLater(attack.cooldownTicks(), () -> tickSpecialAttack(entityId, attack, expiresTick));
      return;
    }
    if (attack.chance() < 1.0 && rng.nextDouble() > attack.chance()) {
      engine.runLater(attack.cooldownTicks(), () -> tickSpecialAttack(entityId, attack, expiresTick));
      return;
    }
    castAbility(living, inst.ownerId(), target, attack.abilityId());
    engine.runLater(attack.cooldownTicks(), () -> tickSpecialAttack(entityId, attack, expiresTick));
  }

  private void castAbility(LivingEntity caster, UUID ownerId, LivingEntity target, String abilityId) {
    Location origin = caster.getEyeLocation();
    Vector direction;
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
    engine.castWithContext(abilityId, caster, origin, direction, null, ctx -> {
      if (ownerId != null) {
        ctx.state().put(dev.patric.dungeonsreborn.effects.Vars.MOB_OWNER, ownerId);
      }
      if (target != null) {
        ctx.state().put(dev.patric.dungeonsreborn.effects.Vars.MOB_TARGET, target);
      }
      ctx.state().put(dev.patric.dungeonsreborn.effects.Vars.MOB_ID, MobMarkers.getMobId(caster));
      String minionId = MobMarkers.getMinionId(caster);
      if (minionId != null) {
        ctx.state().put(dev.patric.dungeonsreborn.effects.Vars.MINION_ID, minionId);
      }
    });
  }

  private void sendStatus(UUID ownerId) {
    if (ownerId == null) {
      return;
    }
    Player player = Bukkit.getPlayer(ownerId);
    if (player == null || !player.isOnline()) {
      return;
    }
    int count = minionsFor(ownerId).size();
    player.sendActionBar(Component.text("§aMinions: §f" + count));
  }
}
