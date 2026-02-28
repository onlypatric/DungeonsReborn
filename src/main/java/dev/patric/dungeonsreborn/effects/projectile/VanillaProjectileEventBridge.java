package dev.patric.dungeonsreborn.effects.projectile;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.combat.CombatEventContext;
import dev.patric.dungeonsreborn.effects.combat.CombatEventSource;
import dev.patric.dungeonsreborn.effects.combat.CombatEventType;
import dev.patric.dungeonsreborn.effects.combat.ProjectileFamily;
import dev.patric.dungeonsreborn.effects.damage.DamageCause;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;

public final class VanillaProjectileEventBridge implements Listener {
  private final EffectsEngine engine;
  private final ProjectileTelemetryStore telemetryStore;
  private final ProjectileTravelTracker travelTracker;
  private final Set<UUID> launchHandled = ConcurrentHashMap.newKeySet();

  private volatile boolean enabled = true;
  private volatile boolean vanillaEnabled = true;

  public VanillaProjectileEventBridge(EffectsEngine engine, ProjectileTelemetryStore telemetryStore, ProjectileTravelTracker travelTracker) {
    this.engine = Objects.requireNonNull(engine, "engine");
    this.telemetryStore = Objects.requireNonNull(telemetryStore, "telemetryStore");
    this.travelTracker = Objects.requireNonNull(travelTracker, "travelTracker");
  }

  public void configure(boolean enabled, boolean vanillaEnabled, boolean useProjectileCollideEvent) {
    this.enabled = enabled;
    this.vanillaEnabled = vanillaEnabled;
    // Kept for config compatibility; Paper's ProjectileCollideEvent is deprecated and not used.
  }

  public void tick() {
    if (!enabled || !vanillaEnabled) {
      return;
    }
    travelTracker.run();
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void onShootBow(EntityShootBowEvent event) {
    if (!enabled || !vanillaEnabled) {
      return;
    }
    if (!(event.getEntity() instanceof LivingEntity shooter) || !(event.getProjectile() instanceof Projectile projectile)) {
      return;
    }
    ProjectileTelemetry telemetry = buildTelemetry(
        projectile,
        shooter,
        null,
        null,
        null,
        event.getForce(),
        event.getBow());

    if (dispatchPreCancelled(CombatEventType.ON_PROJECTILE_LAUNCH_PRE, telemetry, projectile, null)) {
      event.setCancelled(true);
      dispatchCancelled(telemetry, projectile, null);
      return;
    }

    launchHandled.add(projectile.getUniqueId());
    telemetryStore.upsert(telemetry, engine.tickNow());
    dispatchPost(CombatEventType.ON_PROJECTILE_LAUNCH, telemetry, projectile, null);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void onProjectileLaunch(ProjectileLaunchEvent event) {
    if (!enabled || !vanillaEnabled) {
      return;
    }
    Projectile projectile = event.getEntity();
    if (projectile == null) {
      return;
    }
    if (launchHandled.remove(projectile.getUniqueId())) {
      return;
    }
    if (!(projectile.getShooter() instanceof LivingEntity shooter)) {
      return;
    }
    ProjectileTelemetry telemetry = buildTelemetry(projectile, shooter, null, null, null, 0.0, null);
    if (dispatchPreCancelled(CombatEventType.ON_PROJECTILE_LAUNCH_PRE, telemetry, projectile, null)) {
      event.setCancelled(true);
      dispatchCancelled(telemetry, projectile, null);
      return;
    }
    telemetryStore.upsert(telemetry, engine.tickNow());
    dispatchPost(CombatEventType.ON_PROJECTILE_LAUNCH, telemetry, projectile, null);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void onProjectileHit(ProjectileHitEvent event) {
    if (!enabled || !vanillaEnabled) {
      return;
    }
    if (!(event.getEntity() instanceof Projectile projectile)) {
      return;
    }
    LivingEntity shooter = resolveShooter(projectile);
    LivingEntity victim = event.getHitEntity() instanceof LivingEntity living ? living : null;
    Block block = event.getHitBlock();
    BlockFace blockFace = event.getHitBlockFace();
    ProjectileTelemetry telemetry = buildTelemetry(projectile, shooter, victim, block, blockFace, 0.0, null);

    if (victim != null) {
      if (dispatchPreCancelled(CombatEventType.ON_PROJECTILE_COLLIDE_ENTITY_PRE, telemetry, projectile, victim)) {
        event.setCancelled(true);
        dispatchCancelled(telemetry, projectile, victim);
        return;
      }
      dispatchPost(CombatEventType.ON_PROJECTILE_HIT_ENTITY, telemetry, projectile, victim);
      if (projectile instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0) {
        dispatchPost(CombatEventType.ON_PROJECTILE_PIERCE, telemetry, projectile, victim);
      }
      telemetryStore.upsert(telemetry, engine.tickNow());
      return;
    }

    if (block != null) {
      if (dispatchPreCancelled(CombatEventType.ON_PROJECTILE_COLLIDE_BLOCK_PRE, telemetry, projectile, null)) {
        event.setCancelled(true);
        dispatchCancelled(telemetry, projectile, null);
        return;
      }
      dispatchPost(CombatEventType.ON_PROJECTILE_HIT_BLOCK, telemetry, projectile, null);
      if (projectile instanceof AbstractArrow arrow && arrow.isInBlock()) {
        dispatchPost(CombatEventType.ON_PROJECTILE_STUCK, telemetry, projectile, null);
      } else {
        dispatchPost(CombatEventType.ON_PROJECTILE_DEFLECT, telemetry, projectile, null);
      }
      telemetryStore.upsert(telemetry, engine.tickNow());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void onProjectileBlockedByShield(EntityDamageByEntityEvent event) {
    if (!enabled || !vanillaEnabled) {
      return;
    }
    if (!(event.getDamager() instanceof Projectile projectile) || !(event.getEntity() instanceof Player victim)) {
      return;
    }
    if (!victim.isBlocking()) {
      return;
    }
    if (!event.isCancelled() && event.getFinalDamage() > 0.0) {
      return;
    }
    ProjectileTelemetry telemetry = buildTelemetry(projectile, resolveShooter(projectile), victim, null, null, 0.0, null);
    dispatchPost(CombatEventType.ON_PROJECTILE_BLOCKED_SHIELD, telemetry, projectile, victim);
  }

  private boolean dispatchPreCancelled(CombatEventType type, ProjectileTelemetry telemetry, Projectile projectile, LivingEntity victim) {
    return engine.combatDispatcher().dispatchPre(toContext(type, telemetry, projectile, victim));
  }

  private void dispatchPost(CombatEventType type, ProjectileTelemetry telemetry, Projectile projectile, LivingEntity victim) {
    engine.combatDispatcher().dispatch(toContext(type, telemetry, projectile, victim));
  }

  private void dispatchCancelled(ProjectileTelemetry telemetry, Projectile projectile, LivingEntity victim) {
    dispatchPost(CombatEventType.ON_PROJECTILE_CANCELLED, telemetry, projectile, victim);
  }

  private CombatEventContext toContext(CombatEventType type, ProjectileTelemetry telemetry, Projectile projectile, LivingEntity victim) {
    return new CombatEventContext(
        engine.tickNow(),
        type,
        telemetry.shooter(),
        victim == null ? telemetry.victim() : victim,
        victim == null ? telemetry.victim() : victim,
        projectile,
        CombatEventSource.PROJECTILE,
        0.0,
        telemetry.critical(),
        false,
        false,
        null,
        DamageCause.PROJECTILE,
        null,
        null,
        telemetry.projectileId(),
        telemetry.family(),
        telemetry.projectileType(),
        telemetry.projectileKind(),
        telemetry.distance(),
        telemetry.speed(),
        telemetry.drawForce(),
        telemetry.pierceLevel(),
        telemetry.inGroundTicks(),
        telemetry.critical(),
        telemetry.charged(),
        telemetry.piercing(),
        telemetry.shotFromCrossbow(),
        telemetry.shooterIsPlayer(),
        telemetry.hitBlockMaterial() == null ? null : telemetry.hitBlockMaterial().name(),
        materialTag(telemetry.hitBlockMaterial()),
        telemetry.hitBlockFace() == null ? null : telemetry.hitBlockFace().name(),
        telemetry.impactLocation(),
        telemetry.impactDirection(),
        telemetry);
  }

  private ProjectileTelemetry buildTelemetry(
      Projectile projectile,
      LivingEntity shooter,
      LivingEntity victim,
      Block block,
      BlockFace face,
      double drawForce,
      ItemStack bow) {
    ProjectileTelemetry previous = telemetryStore.get(projectile.getUniqueId());
    Location impact = victim != null ? victim.getLocation() : (block != null ? block.getLocation().add(0.5, 0.5, 0.5) : projectile.getLocation());
    Vector direction = projectile.getVelocity();
    int inGround = projectile instanceof AbstractArrow arrow && arrow.isInBlock()
        ? Math.max(1, previous == null ? 1 : previous.inGroundTicks() + 1)
        : 0;
    int pierceLevel = projectile instanceof AbstractArrow arrow ? arrow.getPierceLevel() : 0;
    boolean critical = projectile instanceof AbstractArrow arrow && arrow.isCritical();
    boolean shotFromCrossbow = projectile instanceof AbstractArrow arrow && arrow.isShotFromCrossbow();
    boolean charged = (bow != null && bow.getType() == Material.CROSSBOW) || shotFromCrossbow;
    double distance = previous == null ? 0.0 : previous.distance() + direction.length();
    String projectileKind = previous == null ? "" : previous.projectileKind();
    if ((projectileKind == null || projectileKind.isBlank()) && bow != null) {
      String itemId = ItemMarkers.getItemId(bow);
      if (itemId != null && !itemId.isBlank()) {
        projectileKind = itemId;
      }
    }

    return ProjectileTelemetry.builder(projectile.getUniqueId(), ProjectileFamily.VANILLA, projectile.getType().name())
        .kind(projectileKind == null ? "" : projectileKind)
        .shooter(shooter)
        .victim(victim)
        .impact(impact, direction)
        .movement(distance, direction.length())
        .drawForce(drawForce > 0.0 ? drawForce : (previous == null ? 0.0 : previous.drawForce()))
        .pierce(pierceLevel, pierceLevel > 0)
        .inGroundTicks(inGround)
        .critical(critical)
        .charged(charged)
        .shotFromCrossbow(shotFromCrossbow)
        .hitBlock(block, face)
        .build();
  }

  private static LivingEntity resolveShooter(Projectile projectile) {
    return projectile == null ? null : (projectile.getShooter() instanceof LivingEntity living ? living : null);
  }

  private static String materialTag(Material material) {
    if (material == null) {
      return null;
    }
    return material.getKey().getKey().toLowerCase();
  }
}
