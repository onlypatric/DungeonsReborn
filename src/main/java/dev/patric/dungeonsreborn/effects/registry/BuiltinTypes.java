package dev.patric.dungeonsreborn.effects.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.DungeonsRebornPlugin;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.actions.Actions;
import dev.patric.dungeonsreborn.effects.conditions.Conditions;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.effects.targeting.Targeters;
import dev.patric.dungeonsreborn.effects.Vars;
import dev.patric.dungeonsreborn.effects.minions.MinionManager;

public final class BuiltinTypes {
  private BuiltinTypes() {
  }

  public static void registerAll(EffectsEngine engine) {
    Objects.requireNonNull(engine, "engine");

    engine.actionTypes().register(new ActionType() {
      @Override
      public String id() {
        return "particles_point";
      }

      @Override
      public dev.patric.dungeonsreborn.effects.actions.Action build(Params params) {
        Particle particle = params.enumValue("particle", Particle.class, Particle.END_ROD);
        int count = params.integer("count", 1);
        double offset = params.dbl("offset", 0.0);
        double extra = params.dbl("extra", 0.0);
        return Actions.particlesPoint(particle, count, offset, extra);
      }
    });

    engine.targeterTypes().register(new TargeterType<org.bukkit.entity.LivingEntity>() {
      @Override
      public String id() {
        return "self";
      }

      @Override
      public Class<org.bukkit.entity.LivingEntity> targetType() {
        return org.bukkit.entity.LivingEntity.class;
      }

      @Override
      public dev.patric.dungeonsreborn.effects.targeting.Targeter<org.bukkit.entity.LivingEntity> build(Params params) {
        return Targeters.self();
      }
    });

    engine.targeterTypes().register(new TargeterType<org.bukkit.entity.LivingEntity>() {
      @Override
      public String id() {
        return "context_target";
      }

      @Override
      public Class<org.bukkit.entity.LivingEntity> targetType() {
        return org.bukkit.entity.LivingEntity.class;
      }

      @Override
      public dev.patric.dungeonsreborn.effects.targeting.Targeter<org.bukkit.entity.LivingEntity> build(Params params) {
        String key = params.string("key", Vars.MOB_TARGET);
        return Targeters.contextTarget(key);
      }
    });

    MinionManager minions = null;
    if (engine.plugin() instanceof DungeonsRebornPlugin plugin) {
      minions = plugin.minionManager();
    }
    MinionManager finalMinions = minions;

    engine.targeterTypes().register(new TargeterType<org.bukkit.entity.LivingEntity>() {
      @Override
      public String id() {
        return "owner_target";
      }

      @Override
      public Class<org.bukkit.entity.LivingEntity> targetType() {
        return org.bukkit.entity.LivingEntity.class;
      }

      @Override
      public dev.patric.dungeonsreborn.effects.targeting.Targeter<org.bukkit.entity.LivingEntity> build(Params params) {
        double maxDistance = params.dbl("maxDistance", 32.0);
        return ctx -> {
          LivingEntity owner = resolveOwner(ctx, Vars.MOB_OWNER);
          if (owner == null) {
            owner = ctx.caster();
          }
          LivingEntity target = resolveOwnerTarget(owner, maxDistance);
          return target == null ? List.of() : List.of(target);
        };
      }
    });

    engine.targeterTypes().register(new TargeterType<org.bukkit.entity.LivingEntity>() {
      @Override
      public String id() {
        return "owner_minions";
      }

      @Override
      public Class<org.bukkit.entity.LivingEntity> targetType() {
        return org.bukkit.entity.LivingEntity.class;
      }

      @Override
      public dev.patric.dungeonsreborn.effects.targeting.Targeter<org.bukkit.entity.LivingEntity> build(Params params) {
        return ctx -> {
          if (finalMinions == null) {
            return List.of();
          }
          LivingEntity owner = resolveOwner(ctx, Vars.MOB_OWNER);
          if (owner == null) {
            owner = ctx.caster();
          }
          List<LivingEntity> results = new ArrayList<>();
          for (java.util.UUID id : finalMinions.minionsFor(owner.getUniqueId())) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof LivingEntity living && living.isValid() && !living.isDead()) {
              results.add(living);
            }
          }
          return results;
        };
      }
    });

    engine.targeterTypes().register(new TargeterType<org.bukkit.entity.LivingEntity>() {
      @Override
      public String id() {
        return "look_ray";
      }

      @Override
      public Class<org.bukkit.entity.LivingEntity> targetType() {
        return org.bukkit.entity.LivingEntity.class;
      }

      @Override
      public dev.patric.dungeonsreborn.effects.targeting.Targeter<org.bukkit.entity.LivingEntity> build(Params params) {
        double maxDistance = params.dbl("maxDistance", 20.0);
        double raySize = params.dbl("raySize", 0.35);
        boolean stopOnBlock = params.bool("stopOnBlock", true);
        boolean ignoreCaster = params.bool("ignoreCaster", true);
        return Targeters.lookRay(maxDistance, raySize, stopOnBlock, ignoreCaster, e -> true);
      }
    });

    engine.targeterTypes().register(new TargeterType<org.bukkit.entity.LivingEntity>() {
      @Override
      public String id() {
        return "area_cylinder";
      }

      @Override
      public Class<org.bukkit.entity.LivingEntity> targetType() {
        return org.bukkit.entity.LivingEntity.class;
      }

      @Override
      public dev.patric.dungeonsreborn.effects.targeting.Targeter<org.bukkit.entity.LivingEntity> build(Params params) {
        double radius = params.dbl("radius", 6.0);
        double halfHeight = params.dbl("halfHeight", 2.5);
        boolean ignoreCaster = params.bool("ignoreCaster", true);
        return Targeters.cylinder(radius, halfHeight, ignoreCaster, e -> true);
      }
    });

    engine.targeterTypes().register(new TargeterType<org.bukkit.entity.LivingEntity>() {
      @Override
      public String id() {
        return "capsule_ray";
      }

      @Override
      public Class<org.bukkit.entity.LivingEntity> targetType() {
        return org.bukkit.entity.LivingEntity.class;
      }

      @Override
      public dev.patric.dungeonsreborn.effects.targeting.Targeter<org.bukkit.entity.LivingEntity> build(Params params) {
        double maxDistance = params.dbl("maxDistance", 20.0);
        double radius = params.dbl("radius", 0.65);
        boolean stopOnBlock = params.bool("stopOnBlock", true);
        boolean ignoreCaster = params.bool("ignoreCaster", true);
        return Targeters.capsuleRay(maxDistance, radius, stopOnBlock, ignoreCaster, e -> true);
      }
    });

    engine.conditionTypes().register(new ConditionType() {
      @Override
      public String id() {
        return "sneaking";
      }

      @Override
      public dev.patric.dungeonsreborn.effects.conditions.Condition build(Params params) {
        return Conditions.sneaking();
      }
    });

    engine.conditionTypes().register(new ConditionType() {
      @Override
      public String id() {
        return "permission";
      }

      @Override
      public dev.patric.dungeonsreborn.effects.conditions.Condition build(Params params) {
        return Conditions.permission(params.requireString("permission"));
      }
    });

    engine.conditionTypes().register(new ConditionType() {
      @Override
      public String id() {
        return "has_item_tag";
      }

      @Override
      public dev.patric.dungeonsreborn.effects.conditions.Condition build(Params params) {
        String keyStr = params.string("key", ItemMarkers.DEBUG_MARKER.asString());
        NamespacedKey key = NamespacedKey.fromString(keyStr);
        if (key == null) {
          throw new IllegalArgumentException("Invalid namespaced key: " + keyStr);
        }
        return Conditions.hasItemTag(key);
      }
    });
  }

  private static LivingEntity resolveOwner(dev.patric.dungeonsreborn.effects.CastContext ctx, String key) {
    Object raw = ctx.state().get(key);
    if (raw instanceof LivingEntity living && living.isValid() && !living.isDead()) {
      return living;
    }
    if (raw instanceof java.util.UUID uuid) {
      Entity entity = Bukkit.getEntity(uuid);
      if (entity instanceof LivingEntity living && living.isValid() && !living.isDead()) {
        return living;
      }
    }
    return null;
  }

  private static LivingEntity resolveOwnerTarget(LivingEntity owner, double maxDistance) {
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
      Entity target = player.getTargetEntity((int) Math.max(1, Math.round(maxDistance)));
      if (target instanceof LivingEntity living && living.isValid() && !living.isDead()) {
        return living;
      }
    }
    return null;
  }
}
