package dev.patric.dungeonsreborn.effects;

import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import dev.patric.dungeonsreborn.effects.actions.Actions;
import dev.patric.dungeonsreborn.effects.actions.EntityActions;
import dev.patric.dungeonsreborn.effects.anim.Easings;
import dev.patric.dungeonsreborn.effects.conditions.Conditions;
import dev.patric.dungeonsreborn.effects.costs.Costs;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.effects.targeting.TargetConditions;
import dev.patric.dungeonsreborn.effects.targeting.Targeters;
import net.kyori.adventure.text.Component;
import dev.patric.dungeonsreborn.effects.math.Noise;

import java.time.Duration;

public final class DebugAbilities {
  private DebugAbilities() {
  }

  public static void registerAll(EffectsEngine engine) {
    engine.registerAbility("debug_spark", DebugAbilities::spark);
    engine.registerAbility("debug_beam", DebugAbilities::beam);
    engine.registerAbility("debug_hit", DebugAbilities::hit);
    engine.registerAbility("debug_projectile", DebugAbilities::projectile);
    engine.registerAbility("debug_projectile_pass_through", DebugAbilities::projectilePassThrough);
    engine.registerAbility("debug_projectile_bounce", DebugAbilities::projectileBounce);
    engine.registerAbility("debug_damage", DebugAbilities::damage);
    engine.registerAbility("debug_heal", DebugAbilities::heal);
    engine.registerAbility("debug_potion", DebugAbilities::potion);
    engine.registerAbility("debug_knockback", DebugAbilities::knockback);
    engine.registerAbility("debug_pull_aoe", DebugAbilities::pullAoe);
    engine.registerAbility("debug_cooldown", DebugAbilities::cooldown);
    engine.registerAbility("debug_requires_tag", DebugAbilities::requiresTag);
    engine.registerAbility("debug_los_pull_cone", DebugAbilities::losPullCone);
    engine.registerAbility("debug_cost_consume", DebugAbilities::costConsume);
    engine.registerAbility("debug_cost_durability", DebugAbilities::costDurability);
    engine.registerAbility("debug_health_gate", DebugAbilities::healthGate);
    engine.registerAbility("debug_particles_spam", DebugAbilities::particlesSpam);
    engine.registerAbility("debug_particles_arc", DebugAbilities::particlesArc);
    engine.registerAbility("debug_particles_disk", DebugAbilities::particlesDisk);
    engine.registerAbility("debug_particles_sphere", DebugAbilities::particlesSphere);
    engine.registerAbility("debug_particles_helix", DebugAbilities::particlesHelix);
    engine.registerAbility("debug_particles_trail", DebugAbilities::particlesTrail);
    engine.registerAbility("debug_particles_bezier", DebugAbilities::particlesBezier);
    engine.registerAbility("debug_particles_spline", DebugAbilities::particlesSpline);
    engine.registerAbility("debug_preset_shockwave", DebugAbilities::presetShockwave);
    engine.registerAbility("debug_preset_orbit", DebugAbilities::presetOrbit);
    engine.registerAbility("debug_preset_swirl", DebugAbilities::presetSwirl);
    engine.registerAbility("debug_visualize_raycast", DebugAbilities::visualizeRaycast);
    engine.registerAbility("debug_visualize_capsule", DebugAbilities::visualizeCapsule);
    engine.registerAbility("debug_rotating_arc", DebugAbilities::rotatingArc);
    engine.registerAbility("debug_projectile_attached_trail", DebugAbilities::projectileAttachedTrail);
    engine.registerAbility("debug_projectile_hit_targeter", DebugAbilities::projectileHitTargeter);
    engine.registerAbility("debug_animate_ease", DebugAbilities::animateEase);
    engine.registerAbility("debug_realtime_animate", DebugAbilities::animateRealTime);
    engine.registerAbility("debug_relation_probe", DebugAbilities::relationProbe);
    engine.registerAbility("debug_damage_iframes", DebugAbilities::damageIFrames);
    engine.registerAbility("debug_knockback_capped", DebugAbilities::knockbackCapped);
    engine.registerAbility("debug_potion_immunity", DebugAbilities::potionImmunity);
    engine.registerAbility("debug_mana_cost", DebugAbilities::manaCost);
    engine.registerAbility("debug_tag_apply", DebugAbilities::tagApply);
    engine.registerAbility("debug_tag_gate", DebugAbilities::tagGate);
    engine.registerAbility(AbilitySpec.builder("debug_spec_example")
        .name("AbilitySpec Example")
        .description("Demonstrates AbilitySpec metadata + requirements + costs + cooldown.")
        .require(Conditions.sneaking(), Component.text("§cSneak to cast this ability."))
        .cost(Costs.mana(10.0))
        .cooldownTicks(40L)
        .action(Actions.sequence(
            Actions.sound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.6f),
            Actions.particlesRing(org.bukkit.Particle.END_ROD, 1.4, 28, 1, 0.0, 0.0),
            Actions.delayTicks(10L, Actions.particlesRing(org.bukkit.Particle.WAX_ON, 2.2, 36, 1, 0.0, 0.0))))
        .build());
    engine.registerAbility("debug_timings_demo", DebugAbilities::timingsDemo);
    engine.registerAbility("debug_async_compute", DebugAbilities::asyncCompute);
    engine.registerAbility("debug_wither_skull", DebugAbilities::witherSkull);
    engine.registerAbility("debug_fireball", DebugAbilities::fireball);
    engine.registerAbility("debug_dragon_fireball", DebugAbilities::dragonFireball);
    engine.registerAbility("debug_arrow_volley", DebugAbilities::arrowVolley);
    engine.registerAbility("debug_trident", DebugAbilities::trident);
    engine.registerAbility("debug_lightning", DebugAbilities::lightning);
    engine.registerAbility("debug_fangs", DebugAbilities::fangs);
    engine.registerAbility("debug_explode_safe", DebugAbilities::explodeSafe);
    engine.registerAbility("debug_splash_harming", DebugAbilities::splashHarming);
    engine.registerAbility("debug_cloud_poison", DebugAbilities::cloudPoison);
    engine.registerAbility("debug_message", DebugAbilities::message);
    engine.registerAbility("debug_action_bar", DebugAbilities::actionBar);
    engine.registerAbility("debug_title", DebugAbilities::title);
    engine.registerAbility("debug_teleport_look", DebugAbilities::teleportLook);
    engine.registerAbility("debug_dash", DebugAbilities::dash);
    engine.registerAbility("debug_particles_cone", DebugAbilities::particlesCone);
    engine.registerAbility("debug_particles_cylinder", DebugAbilities::particlesCylinder);
    engine.registerAbility("debug_particles_box", DebugAbilities::particlesBox);
    engine.registerAbility("debug_particles_polygon", DebugAbilities::particlesPolygon);
    engine.registerAbility("debug_random_choice", DebugAbilities::randomChoice);
    engine.registerAbility("debug_chance", DebugAbilities::chance);
    engine.registerAbility("debug_debug_log", DebugAbilities::debugLog);
    engine.registerAbility("debug_vars_counter", DebugAbilities::varsCounter);
  }

  private static void spark(CastContext ctx) {
    LivingEntity caster = ctx.caster();
    Actions.sequence(
        Actions.particlesRing(org.bukkit.Particle.CRIT, 0.9, 24, 2, 0.02, 0.0),
        Actions.sound(Sound.ENTITY_BLAZE_SHOOT, 0.7f, 1.6f))
        .execute(ctx);

    if (caster instanceof Player player) {
      player.sendMessage("§7Cast §f" + "debug_spark" + "§7 (castId=" + ctx.castId() + ")");
    }
  }

  private static void beam(CastContext ctx) {
    Actions.sequence(
        Actions.particlesLine(org.bukkit.Particle.END_ROD, 20.0, 0.4, 1, 0.0, 0.0),
        Actions.sound(Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.9f))
        .execute(ctx);
  }

  private static void hit(CastContext ctx) {
    Actions.sequence(
        Actions.particlesLine(org.bukkit.Particle.ELECTRIC_SPARK, 20.0, 0.3, 1, 0.0, 0.0),
        Actions.sound(Sound.ENTITY_BEE_STING, 0.6f, 1.4f),
        Actions.raycastHitEntity(20.0, 0.35, (cast, target) -> {
          if (target.getWorld() == null) {
            return;
          }
          target.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getLocation().add(0, 1.0, 0), 18, 0.25, 0.35, 0.25, 0.02);
          target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.2f);
          if (cast.caster() instanceof Player player) {
            player.sendMessage("§aHit: §f" + target.getType().name() + " §7(" + target.getName() + ")");
          }
        }))
        .execute(ctx);
  }

  private static void projectile(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_SNOWBALL_THROW, 0.6f, 1.2f),
        Actions.projectile(p -> p
            .speedPerTick(1.35)
            .maxDistance(30.0)
            .hitRadius(0.35)
            .stopOnBlock(true)
            .trail(org.bukkit.Particle.END_ROD, 1, 0.0, 0.0)
            .onHit(hit -> {
              if (hit.location().getWorld() == null) {
                return;
              }
              if (hit.isEntityHit()) {
                LivingEntity e = hit.hitEntity();
                e.getWorld().spawnParticle(org.bukkit.Particle.CRIT, e.getLocation().add(0, 1.0, 0), 22, 0.25, 0.35, 0.25, 0.02);
                e.getWorld().playSound(e.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1.25f);
                if (hit.cast().caster() instanceof Player player) {
                  player.sendMessage("§aProjectile hit: §f" + e.getType().name() + " §7(" + e.getName() + ")");
                }
              } else if (hit.isBlockHit()) {
                hit.location().getWorld().spawnParticle(org.bukkit.Particle.SMOKE, hit.location(), 10, 0.15, 0.15, 0.15, 0.01);
                hit.location().getWorld().playSound(hit.location(), Sound.BLOCK_STONE_HIT, 0.6f, 1.1f);
              }
            })))
        .execute(ctx);
  }

  private static void projectilePassThrough(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_SNOWBALL_THROW, 0.6f, 1.2f),
        Actions.projectile(p -> p
            .speedPerTick(1.35)
            .maxDistance(35.0)
            .hitRadius(0.35)
            .stopOnBlock(false)
            .trail(org.bukkit.Particle.END_ROD, 1, 0.0, 0.0)
            .onHit(hit -> {
              if (hit.location().getWorld() == null) {
                return;
              }
              if (hit.isEntityHit()) {
                LivingEntity e = hit.hitEntity();
                e.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, e.getLocation().add(0, 1.0, 0), 14, 0.25, 0.35, 0.25, 0.02);
                e.getWorld().playSound(e.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1.25f);
                if (hit.cast().caster() instanceof Player player) {
                  player.sendMessage("§aPass-through hit: §f" + e.getType().name() + " §7(" + e.getName() + ")");
                }
              } else if (hit.isBlockHit()) {
                // Should not happen in pass-through mode, but keep a marker for safety.
                hit.location().getWorld().spawnParticle(org.bukkit.Particle.WAX_ON, hit.location(), 10, 0.15, 0.15, 0.15, 0.01);
              }
            })))
        .execute(ctx);
  }

  private static void projectileBounce(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_SNOWBALL_THROW, 0.6f, 1.2f),
        Actions.projectile(p -> p
            .speedPerTick(1.35)
            .maxDistance(45.0)
            .hitRadius(0.35)
            .bounces(6, 0.88)
            .trail(org.bukkit.Particle.SMOKE, 1, 0.02, 0.0)
            .onHit(hit -> {
              if (hit.location().getWorld() == null) {
                return;
              }
              if (hit.isEntityHit()) {
                LivingEntity e = hit.hitEntity();
                e.getWorld().spawnParticle(org.bukkit.Particle.CRIT, e.getLocation().add(0, 1.0, 0), 18, 0.25, 0.35, 0.25, 0.02);
                e.getWorld().playSound(e.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.2f);
                if (hit.cast().caster() instanceof Player player) {
                  player.sendMessage("§aBounce hit entity: §f" + e.getName());
                }
              } else if (hit.isBlockHit()) {
                hit.location().getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, hit.location(), 8, 0.05, 0.05, 0.05, 0.01);
                hit.location().getWorld().playSound(hit.location(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.45f, 1.9f);
              }
            })))
        .execute(ctx);
  }

  private static void damage(CastContext ctx) {
    Actions.sequence(
        Actions.particlesLine(org.bukkit.Particle.ELECTRIC_SPARK, 20.0, 0.25, 1, 0.0, 0.0),
        Actions.sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 1.4f),
        Actions.forEach(
            Targeters.lookRay(20.0, 0.35),
            (cast, target) -> {
              EntityActions.damage(4.0, EntityActions.DamagePolicy.hostileDefault()).execute(cast, target);
              target.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1.0, 0), 10, 0.2, 0.3, 0.2, 0.02);
              if (cast.caster() instanceof Player player) {
                player.sendMessage("§cDamaged: §f" + target.getType().name() + " §7(" + target.getName() + ")");
              }
            }))
        .execute(ctx);
  }

  private static void damageIFrames(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 1.6f),
        Actions.repeatTicks(0L, 2L, 30,
            Actions.forEach(
                Targeters.lookRay(20.0, 0.35),
                (cast, target) -> {
                  boolean applied = cast.engine().tryStartImmunity(target.getUniqueId(), "iframes:" + cast.abilityId(), 10L);
                  if (!applied) {
                    target.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, target.getLocation().add(0, 1.0, 0), 2, 0.1, 0.15, 0.1, 0.0);
                    return;
                  }
                  EntityActions.damage(2.0, EntityActions.DamagePolicy.hostileDefault()).execute(cast, target);
                  target.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1.0, 0), 6, 0.2, 0.25, 0.2, 0.01);
                  if (cast.caster() instanceof Player player) {
                    player.sendMessage("§cI-frame damage applied to §f" + target.getName());
                  }
                })))
        .execute(ctx);
  }

  private static void heal(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.6f),
        Actions.forEach(
            Targeters.lookRay(20.0, 0.35),
            (cast, target) -> {
              EntityActions.heal(6.0).execute(cast, target);
              target.getWorld().spawnParticle(org.bukkit.Particle.HEART, target.getLocation().add(0, 1.0, 0), 4, 0.25, 0.25, 0.25, 0.0);
              if (cast.caster() instanceof Player player) {
                player.sendMessage("§aHealed: §f" + target.getType().name() + " §7(" + target.getName() + ")");
              }
            }))
        .execute(ctx);
  }

  private static void potion(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_WITCH_DRINK, 0.7f, 1.2f),
        Actions.forEach(
            Targeters.lookRay(20.0, 0.35),
            (cast, target) -> {
              EntityActions.potion(PotionEffectType.GLOWING, java.time.Duration.ofSeconds(6), 0).execute(cast, target);
              target.getWorld().spawnParticle(org.bukkit.Particle.WAX_ON, target.getLocation().add(0, 1.0, 0), 14, 0.3, 0.4, 0.3, 0.01);
              if (cast.caster() instanceof Player player) {
                player.sendMessage("§ePotion: §fGLOWING §7-> " + target.getName());
              }
            }))
        .execute(ctx);
  }

  private static void potionImmunity(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_WITCH_DRINK, 0.6f, 1.4f),
        Actions.repeatTicks(0L, 10L, 10,
            Actions.forEach(
                Targeters.lookRay(20.0, 0.35),
                (cast, target) -> {
                  EntityActions.potionWithImmunity(PotionEffectType.GLOWING, java.time.Duration.ofSeconds(6), 0, "glowing", 40L)
                      .execute(cast, target);
                  target.getWorld().spawnParticle(org.bukkit.Particle.WAX_ON, target.getLocation().add(0, 1.0, 0), 4, 0.25, 0.35, 0.25, 0.01);
                })))
        .execute(ctx);
  }

  private static void manaCost(CastContext ctx) {
    Actions.withCost(
        Costs.mana(15.0),
        Actions.sequence(
            Actions.sound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.2f),
            Actions.particlesRing(org.bukkit.Particle.WAX_ON, 1.1, 20, 1, 0.0, 0.0)))
        .execute(ctx);
  }

  private static void tagApply(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.6f),
        Actions.forEach(
            Targeters.lookRay(20.0, 0.35),
            (cast, target) -> {
              EntityActions.tagForDuration("dr_debug_tagged", 100L).execute(cast, target);
              target.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, target.getLocation().add(0, 1.0, 0), 8, 0.25, 0.35, 0.25, 0.01);
              if (cast.caster() instanceof Player player) {
                player.sendMessage("§aTagged §f" + target.getName() + "§a for 5s");
              }
            }))
        .execute(ctx);
  }

  private static void tagGate(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.9f),
        Actions.forEachWhere(
            Targeters.lookRay(20.0, 0.35),
            TargetConditions.hasTag("dr_debug_tagged"),
            (cast, target) -> {
              target.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getLocation().add(0, 1.0, 0), 18, 0.25, 0.35, 0.25, 0.02);
              if (cast.caster() instanceof Player player) {
                player.sendMessage("§eGate passed (has tag): §f" + target.getName());
              }
            }))
        .execute(ctx);
  }

  private static void timingsDemo(CastContext ctx) {
    Actions.sequence(
        Actions.timed("particles.line", Actions.particlesLine(org.bukkit.Particle.ELECTRIC_SPARK, 18.0, 0.22, 1, 0.0, 0.0)),
        Actions.timed("particles.ring", Actions.particlesRing(org.bukkit.Particle.END_ROD, 1.6, 40, 1, 0.0, 0.0)),
        Actions.timed("raycast.hitEntity", Actions.raycastHitEntity(20.0, 0.35, (cast, target) -> {
          target.getWorld().spawnParticle(org.bukkit.Particle.WAX_ON, target.getLocation().add(0, 1.0, 0), 6, 0.25, 0.35, 0.25, 0.01);
        })),
        Actions.delayTicks(5L, Actions.timed("particles.delayedDisk",
            Actions.particlesDisk(org.bukkit.Particle.HAPPY_VILLAGER, 2.2, 6, 42, 1, 0.0, 0.0))))
        .execute(ctx);
  }

  private static void asyncCompute(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 1.6f),
        Actions.asyncCompute("noiseRing", () -> {
          long seed = ctx.castId().getMostSignificantBits() ^ ctx.castId().getLeastSignificantBits();
          int base = (int) (seed ^ (seed >>> 32));
          int points = 48;
          double[] radii = new double[points];
          for (int i = 0; i < points; i++) {
            double n = Noise.value2D(base, i * 0.15, 0.0); // [0..1)
            radii[i] = 1.5 + (n * 1.2);
          }
          return radii;
        }, (cast, radii) -> {
          if (cast.world() == null) {
            return;
          }
          var pe = cast.engine().particles();
          var center = cast.origin().clone();
          int points = radii.length;
          for (int i = 0; i < points; i++) {
            double ang = (Math.PI * 2.0) * (i / (double) points);
            double r = radii[i];
            var loc = center.clone().add(Math.cos(ang) * r, 0.15, Math.sin(ang) * r);
            pe.emit(cast.world(), loc, org.bukkit.Particle.END_ROD, 1, 0.0, 0.0, 0.0, 0.0);
          }
        }))
        .execute(ctx);
  }

  private static void witherSkull(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_WITHER_SHOOT, 0.7f, 1.2f),
        Actions.launchWitherSkull(30.0, 0.45, true, false, 1.25, false, 0.0f, false))
        .execute(ctx);
  }

  private static void message(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.7f),
        Actions.message(Component.text("§aHello from §fdebug_message§a (castId=" + ctx.castId() + ")")))
        .execute(ctx);
  }

  private static void actionBar(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.8f),
        Actions.actionBar(Component.text("§eActionBar: §fdebug_action_bar")))
        .execute(ctx);
  }

  private static void title(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.6f),
        Actions.title(
            Component.text("§6Debug Title"),
            Component.text("§7subtitle: §fdebug_title"),
            Duration.ofMillis(200),
            Duration.ofMillis(900),
            Duration.ofMillis(300)))
        .execute(ctx);
  }

  private static void teleportLook(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.4f),
        Actions.particlesLine(org.bukkit.Particle.PORTAL, 18.0, 0.5, 1, 0.0, 0.0),
        Actions.teleportCasterToLook(18.0, true, 0.8),
        Actions.delayTicks(1L, c -> {
          if (c.world() == null) {
            return;
          }
          var loc = c.caster().getLocation().add(0, 0.2, 0);
          c.engine().particles().emit(c.world(), loc, org.bukkit.Particle.END_ROD, 22, 0.15, 0.25, 0.15, 0.01);
        }))
        .execute(ctx);
  }

  private static void dash(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ITEM_TRIDENT_RIPTIDE_1, 0.7f, 1.4f),
        Actions.dashCaster(4.0, true, 1.8, 0.35, 1.35, 0.85, true),
        Actions.delayTicks(1L, Actions.particlesRing(org.bukkit.Particle.CLOUD, 1.2, 18, 1, 0.0, 0.02)))
        .execute(ctx);
  }

  private static void particlesCone(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_BLAZE_SHOOT, 0.6f, 1.6f),
        Actions.particlesCone(org.bukkit.Particle.FLAME, 8.0, 70.0, 10, 18, 1, 0.0, 0.0))
        .execute(ctx);
  }

  private static void particlesCylinder(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 1.2f),
        Actions.particlesCylinder(org.bukkit.Particle.END_ROD, 2.4, 3.2, 10, 24, 1, 0.0, 0.0))
        .execute(ctx);
  }

  private static void particlesBox(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.4f),
        Actions.particlesBox(org.bukkit.Particle.WAX_ON, 2.2, 1.6, 2.2, 0.35, 1, 0.0, 0.0))
        .execute(ctx);
  }

  private static void particlesPolygon(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.7f),
        Actions.particlesPolygon(org.bukkit.Particle.ELECTRIC_SPARK, new Vector(0, 1, 0), 2.5, 6, 10, 1, 0.0, 0.0))
        .execute(ctx);
  }

  private static void randomChoice(CastContext ctx) {
    Actions.randomChoice(
        Actions.sequence(
            Actions.sound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.9f),
            Actions.particlesRing(org.bukkit.Particle.HAPPY_VILLAGER, 1.2, 20, 1, 0.0, 0.0),
            Actions.message(Component.text("§aRandom choice: §fA"))),
        Actions.sequence(
            Actions.sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f),
            Actions.particlesRing(org.bukkit.Particle.SMOKE, 1.2, 20, 1, 0.0, 0.01),
            Actions.message(Component.text("§cRandom choice: §fB"))))
        .execute(ctx);
  }

  private static void chance(CastContext ctx) {
    Actions.chanceElse(
        0.25,
        Actions.sequence(
            Actions.sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.8f),
            Actions.message(Component.text("§aChance success (25%)"))),
        Actions.sequence(
            Actions.sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.4f, 0.7f),
            Actions.message(Component.text("§7Chance fail (75%)"))))
        .execute(ctx);
  }

  private static void debugLog(CastContext ctx) {
    Actions.sequence(
        Actions.debugLog("debug_debug_log invoked"),
        Actions.sound(Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f, 1.9f),
        Actions.message(Component.text("§7Wrote a debug log line (enable with §f/effects debug on§7).")))
        .execute(ctx);
  }

  private static void varsCounter(CastContext ctx) {
    Actions.sequence(
        Actions.incrementIntVar("debug_counter", 1, 0),
        Actions.withVar("debug_counter", Integer.class, 0, (cast, count) -> {
          Actions.actionBar(Component.text("§eCounter: §f" + count)).execute(cast);
        }),
        Actions.sound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, 1.6f))
        .execute(ctx);
  }

  private static void fireball(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_GHAST_SHOOT, 0.7f, 1.2f),
        Actions.launchFireball(30.0, 0.55, true, false, 1.05, 0.0f, false))
        .execute(ctx);
  }

  private static void dragonFireball(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_ENDER_DRAGON_SHOOT, 0.6f, 1.4f),
        Actions.launchDragonFireball(30.0, 0.55, true, false, 0.95))
        .execute(ctx);
  }

  private static void arrowVolley(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_ARROW_SHOOT, 0.7f, 1.2f),
        Actions.arrowVolley(30.0, 0.45, true, false, 8, 6.0, 2.2, false))
        .execute(ctx);
  }

  private static void trident(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ITEM_TRIDENT_THROW, 0.7f, 1.2f),
        Actions.throwTrident(30.0, 0.45, true, false, 2.0))
        .execute(ctx);
  }

  private static void lightning(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.6f),
        Actions.strikeLightning(40.0, true, false, true))
        .execute(ctx);
  }

  private static void fangs(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_EVOKER_PREPARE_ATTACK, 0.7f, 1.2f),
        Actions.evokerFangsLine(25.0, true, false, 10, 1.1, 2L))
        .execute(ctx);
  }

  private static void explodeSafe(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.6f),
        Actions.explodeAtLook(30.0, true, false, 2.0f, false, false))
        .execute(ctx);
  }

  private static void splashHarming(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_WITCH_THROW, 0.7f, 1.2f),
        Actions.throwSplashPotion(25.0, 0.6, true, false, org.bukkit.potion.PotionType.STRONG_HARMING, 1.2))
        .execute(ctx);
  }

  private static void cloudPoison(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.6f, 1.6f),
        Actions.areaEffectCloud(
            25.0, 0.6, true, false,
            org.bukkit.potion.PotionEffectType.POISON, java.time.Duration.ofSeconds(4), 0,
            80L, 2.5f, -0.01f, 0, 10))
        .execute(ctx);
  }

  private static void knockback(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.6f, 1.0f),
        Actions.forEach(
            Targeters.lookRay(20.0, 0.35),
            (cast, target) -> {
              EntityActions.knockbackFromOrigin(1.2, 0.25).execute(cast, target);
              target.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, target.getLocation().add(0, 0.2, 0), 10, 0.25, 0.15, 0.25, 0.02);
              if (cast.caster() instanceof Player player) {
                player.sendMessage("§bKnockback: §f" + target.getName());
              }
            }))
        .execute(ctx);
  }

  private static void knockbackCapped(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.6f, 0.9f),
        Actions.forEach(
            Targeters.lookRay(20.0, 0.35),
            (cast, target) -> {
              EntityActions.knockbackFromOriginCapped(1.6, 0.45, 0.75, 0.55, 0.95).execute(cast, target);
              target.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, target.getLocation().add(0, 0.2, 0), 10, 0.25, 0.15, 0.25, 0.02);
              if (cast.caster() instanceof Player player) {
                player.sendMessage("§bKnockback (capped): §f" + target.getName());
              }
            }))
        .execute(ctx);
  }

  private static void pullAoe(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.7f, 1.6f),
        Actions.forEach(
            Targeters.sphere(6.0, true, e -> true),
            (cast, target) -> {
              EntityActions.pullToOriginScaledCapped(0.15, 1.35, 0.06, 6.0, 0.85, 0.55, 0.95).execute(cast, target);
              target.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, target.getLocation().add(0, 0.9, 0), 6, 0.2, 0.25, 0.2, 0.02);
            }))
        .execute(ctx);
  }

  private static void cooldown(CastContext ctx) {
    Actions.withCooldown(
        "debug_cooldown",
        60L,
        Actions.sequence(
            Actions.sound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.8f),
            Actions.particlesRing(org.bukkit.Particle.HAPPY_VILLAGER, 1.1, 20, 1, 0.0, 0.0)))
        .execute(ctx);
  }

  private static void requiresTag(CastContext ctx) {
    Actions.whenElse(
        Conditions.hasItemTag(ItemMarkers.DEBUG_MARKER),
        Actions.sequence(
            Actions.sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.9f),
            Actions.particlesRing(org.bukkit.Particle.HAPPY_VILLAGER, 1.2, 24, 1, 0.0, 0.0)),
        c -> {
          if (c.caster() instanceof Player player) {
            player.sendMessage("§cMissing item tag: " + ItemMarkers.DEBUG_MARKER.asString() + " (use /effects tag on)");
          }
        })
        .execute(ctx);
  }

  private static void losPullCone(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.6f, 1.4f),
        Actions.forEachWhere(
            Targeters.cone(10.0, 70.0, true, e -> true),
            TargetConditions.lineOfSight(),
            (cast, target) -> {
              EntityActions.pullToOriginScaledCapped(0.15, 1.5, 0.05, 10.0, 0.9, 0.55, 1.05).execute(cast, target);
              target.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, target.getLocation().add(0, 0.9, 0), 4, 0.2, 0.25, 0.2, 0.02);
            }))
        .execute(ctx);
  }

  private static void costConsume(CastContext ctx) {
    Actions.withCost(
        Costs.consumeMainHand(1),
        Actions.sequence(
            Actions.sound(Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.7f),
            Actions.particlesRing(org.bukkit.Particle.ITEM_SLIME, 1.0, 18, 1, 0.0, 0.0)))
        .execute(ctx);
  }

  private static void costDurability(CastContext ctx) {
    Actions.withCost(
        Costs.durabilityMainHand(8, false),
        Actions.sequence(
            Actions.sound(Sound.BLOCK_ANVIL_USE, 0.5f, 1.6f),
            Actions.particlesRing(org.bukkit.Particle.CRIT, 1.0, 18, 1, 0.0, 0.0)))
        .execute(ctx);
  }

  private static void healthGate(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.BLOCK_NOTE_BLOCK_HARP, 0.6f, 1.2f),
        Actions.forEachWhere(
            Targeters.lookRay(20.0, 0.35),
            TargetConditions.healthBelow(10.0),
            (cast, target) -> {
              EntityActions.heal(4.0).execute(cast, target);
              target.getWorld().spawnParticle(org.bukkit.Particle.HEART, target.getLocation().add(0, 1.0, 0), 5, 0.25, 0.25, 0.25, 0.0);
              if (cast.caster() instanceof Player player) {
                player.sendMessage("§aHealed low-health target: §f" + target.getName());
              }
            }))
        .execute(ctx);
  }

  private static void particlesSpam(CastContext ctx) {
    if (ctx.world() == null) {
      return;
    }
    Actions.sound(Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.4f).execute(ctx);
    ctx.engine().particles().emit(
        ctx.world(),
        ctx.origin(),
        org.bukkit.Particle.FLAME,
        5000,
        0.8, 0.8, 0.8,
        0.01,
        null,
        ctx.engine().particles().defaultRange(),
        null,
        null);
  }

  private static void particlesArc(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.8f),
        Actions.particlesArc(org.bukkit.Particle.END_ROD, 1.6, 140.0, 32, 1, 0.0, 0.0))
        .execute(ctx);
  }

  private static void particlesDisk(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.6f),
        Actions.particlesDisk(org.bukkit.Particle.HAPPY_VILLAGER, 2.2, 6, 42, 1, 0.0, 0.0))
        .execute(ctx);
  }

  private static void particlesSphere(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.4f),
        Actions.particlesSphereShell(org.bukkit.Particle.WAX_ON, 2.0, 110, 1, 0.0, 0.0))
        .execute(ctx);
  }

  private static void particlesHelix(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.8f),
        Actions.particlesHelix(org.bukkit.Particle.ELECTRIC_SPARK, 0.65, 6.0, 3, 120, 1, 0.0, 0.0))
        .execute(ctx);
  }

  private static void particlesTrail(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.3f),
        Actions.particlesTrailCasterEyes(60L, org.bukkit.Particle.END_ROD, 1, 0.0, 0.0))
        .execute(ctx);
  }

  private static void visualizeRaycast(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.9f),
        Actions.visualizeLookRay(30.0, 0.35, org.bukkit.Particle.END_ROD, org.bukkit.Particle.ELECTRIC_SPARK))
        .execute(ctx);
  }

  private static void visualizeCapsule(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.6f),
        Actions.visualizeCapsuleRay(24.0, 0.65, 0.9, org.bukkit.Particle.END_ROD, org.bukkit.Particle.ELECTRIC_SPARK))
        .execute(ctx);
  }

  /**
   * Rotating arc around the caster: rotates in 5° steps until 360° over ~10 seconds.
   */
  private static void rotatingArc(CastContext ctx) {
    if (!(ctx.caster() instanceof Player player)) {
      return;
    }
    if (ctx.world() == null) {
      return;
    }

    final long durationTicks = 20L;
    final double stepDegrees = 5.0;
    final double arcSpanDegrees = 90.0;
    final int points = 28;
    final double radius = 2.3;

    Actions.sound(Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.4f).execute(ctx);

    long start = ctx.engine().tickNow();
    final var handle = new dev.patric.dungeonsreborn.effects.EffectsEngine.ScheduledHandle[1];
    handle[0] = ctx.engine().runRepeating(0L, 1L, () -> {
      if (handle[0] == null || handle[0].isCancelled()) {
        return;
      }

      long elapsed = ctx.engine().tickNow() - start;
      if (elapsed >= durationTicks) {
        handle[0].cancel();
        return;
      }

      var world = player.getWorld();
      var center = player.getLocation().add(0, 1.0, 0);
      var pe = ctx.engine().particles();

      // Rotate around the player in discrete 5° steps over the full duration.
      int steps = (int) Math.floor((elapsed / (double) durationTicks) * (360.0 / stepDegrees));
      double baseDeg = (steps * stepDegrees) % 360.0;

      // Keep the arc itself static in its vertical plane, but rotate that plane around the world Y axis.
      // This is a "horizontal-only" rotation (no yaw/pitch binding).
      double startDeg = -(arcSpanDegrees / 2.0);
      double step = arcSpanDegrees / Math.max(1, (points - 1));

      // Vertical arc: a circle segment in the plane spanned by (up) and a rotating horizontal axis (right).
      double planeRad = Math.toRadians(baseDeg);
      Vector right = new Vector(Math.cos(planeRad), 0, Math.sin(planeRad)).normalize();
      Vector up = new Vector(0, 1, 0);

      for (int i = 0; i < points; i++) {
        double deg = startDeg + (i * step);
        double rad = Math.toRadians(deg);
        Vector offset = right.clone().multiply(Math.cos(rad) * radius).add(up.clone().multiply(Math.sin(rad) * radius));
        var loc = center.clone().add(offset);
        pe.emit(world, loc, org.bukkit.Particle.END_ROD, 1, 0.0, 0.0, 0.0, 0.0);
      }

      // Add a small inner ring to make rotation easier to see.
      var marker = center.clone().add(right.clone().multiply(1.2));
      pe.emit(world, marker, org.bukkit.Particle.ELECTRIC_SPARK, 2, 0.02, 0.02, 0.02, 0.0);
    });
    ctx.state().track(handle[0]);
  }

  private static void projectileAttachedTrail(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_SNOWBALL_THROW, 0.6f, 1.2f),
        Actions.projectile(p -> p
            .frameOut(frame -> Actions.particlesTrail(frame, 80L, 1L, org.bukkit.Particle.END_ROD, 1, 0.0, 0.0).execute(ctx))
            .speedPerTick(1.35)
            .maxDistance(30.0)
            .hitRadius(0.35)
            .stopOnBlock(true)
            .trail(org.bukkit.Particle.SMOKE, 1, 0.02, 0.0)
            .onHit(hit -> {
              if (hit.location().getWorld() == null) {
                return;
              }
              hit.location().getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, hit.location(), 1, 0, 0, 0, 0);
              hit.location().getWorld().playSound(hit.location(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
            })))
        .execute(ctx);
  }

  private static void projectileHitTargeter(CastContext ctx) {
    Actions.projectile(p -> p
        .speedPerTick(1.35)
        .maxDistance(30.0)
        .hitRadius(0.35)
        .stopOnBlock(true)
        .trail(org.bukkit.Particle.END_ROD, 1, 0.0, 0.0)
        .onHit(hit -> Actions.forEach(
            Targeters.projectileHit(),
            (cast, target) -> {
              EntityActions.damageIFramed(3.0, 5L).execute(cast, target);
              target.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1.0, 0), 12, 0.25, 0.35, 0.25, 0.02);
              if (cast.caster() instanceof Player player) {
                player.sendMessage("§cHit via targeter: §f" + target.getName());
              }
            }).execute(hit.cast())))
        .execute(ctx);
  }

  private static void animateEase(CastContext ctx) {
    Actions.animate(60L, 1L, Easings::inOutCubic, (cast, t) -> {
      if (cast.world() == null) {
        return;
      }
      double radius = 0.5 + (2.5 * t);
      Actions.particlesRing(org.bukkit.Particle.HAPPY_VILLAGER, radius, 28, 1, 0.0, 0.0).execute(cast);
    }).execute(ctx);
  }

  private static void animateRealTime(CastContext ctx) {
    Actions.animateRealTime(Duration.ofSeconds(3), Duration.ofMillis(50), Easings::inOutCubic, (cast, t) -> {
      if (cast.world() == null) {
        return;
      }
      double radius = 0.5 + (2.5 * t);
      Actions.particlesRing(org.bukkit.Particle.END_ROD, radius, 28, 1, 0.0, 0.0).execute(cast);
    }).execute(ctx);
  }

  private static void relationProbe(CastContext ctx) {
    if (!(ctx.caster() instanceof Player player)) {
      return;
    }
    Actions.raycastHitEntity(20.0, 0.35, (cast, target) -> {
      var rel = cast.engine().relation(cast.caster(), target);
      player.sendMessage("§eRelation: §f" + target.getName() + " §7-> §b" + rel.name());
      if (cast.world() != null) {
        cast.engine().particles().emit(cast.world(), target.getLocation().add(0, 1.0, 0),
            org.bukkit.Particle.WAX_ON, 10, 0.25, 0.35, 0.25, 0.01);
      }
    }).execute(ctx);
  }

  private static void particlesBezier(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.6f),
        Actions.particlesBezier(
            c -> {
              if (!(c.caster() instanceof Player p)) {
                return c.origin();
              }
              return p.getEyeLocation();
            },
            c -> {
              var start = c.origin().clone();
              var right = c.direction().clone().crossProduct(new Vector(0, 1, 0));
              if (right.lengthSquared() < 1e-9) {
                right = new Vector(1, 0, 0);
              }
              right.normalize();
              return start.add(right.multiply(2.0)).add(0, 1.0, 0);
            },
            c -> {
              var start = c.origin().clone();
              var right = c.direction().clone().crossProduct(new Vector(0, 1, 0));
              if (right.lengthSquared() < 1e-9) {
                right = new Vector(1, 0, 0);
              }
              right.normalize();
              return start.add(right.multiply(-2.0)).add(0, 1.2, 0).add(c.direction().clone().multiply(6.0));
            },
            c -> c.origin().clone().add(c.direction().clone().multiply(12.0)).add(0, 0.4, 0),
            6.0, 180,
            org.bukkit.Particle.END_ROD,
            1, 0.0, 0.0))
        .execute(ctx);
  }

  private static void particlesSpline(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.7f),
        Actions.particlesSpline(
            java.util.List.of(
                c -> c.origin().clone(),
                c -> c.origin().clone().add(c.direction().clone().multiply(4.0)).add(0.0, 1.5, 0.0),
                c -> c.origin().clone().add(c.direction().clone().multiply(8.0)).add(2.0, 0.8, 0.0),
                c -> c.origin().clone().add(c.direction().clone().multiply(12.0)).add(-1.5, 1.2, 0.0),
                c -> c.origin().clone().add(c.direction().clone().multiply(16.0)).add(0.0, 0.2, 0.0)),
            10.0,
            320,
            org.bukkit.Particle.END_ROD,
            1,
            0.0,
            0.0))
        .execute(ctx);
  }

  private static void presetShockwave(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.9f),
        Actions.presetShockwave(org.bukkit.Particle.SONIC_BOOM, 0.5, 7.0, 40L, 1L, Easings::outQuad, 56, 1, 0.0, 0.0))
        .execute(ctx);
  }

  private static void presetOrbit(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.6f),
        Actions.presetOrbit(org.bukkit.Particle.ELECTRIC_SPARK, 2.4, 60L, 1L, Easings::linear, 3, 1, 0.02, 0.0))
        .execute(ctx);
  }

  private static void presetSwirl(CastContext ctx) {
    Actions.sequence(
        Actions.sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 1.2f),
        Actions.presetSwirl(org.bukkit.Particle.END_ROD, 1.8, 2.6, 60L, 1L, Easings::inOutCubic, 22, 1, 0.0, 0.0))
        .execute(ctx);
  }
}
