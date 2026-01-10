package dev.patric.dungeonsreborn.effects.particles;

import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import dev.patric.dungeonsreborn.effects.CastContext;

public final class Frames {
  private Frames() {
  }

  public static Frame castOrigin() {
    return new Frame() {
      @Override
      public Location location(CastContext ctx) {
        return ctx.origin().clone();
      }

      @Override
      public Vector direction(CastContext ctx) {
        return ctx.direction().clone();
      }
    };
  }

  public static Frame casterEyes() {
    return new Frame() {
      @Override
      public Location location(CastContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster instanceof Player player) {
          return player.getEyeLocation();
        }
        return caster.getLocation();
      }

      @Override
      public Vector direction(CastContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster instanceof Player player) {
          return player.getEyeLocation().getDirection();
        }
        return caster.getLocation().getDirection();
      }
    };
  }

  public static Frame entity(LivingEntity entity) {
    Objects.requireNonNull(entity, "entity");
    return new Frame() {
      @Override
      public Location location(CastContext ctx) {
        return entity.getLocation();
      }

      @Override
      public Vector direction(CastContext ctx) {
        return entity.getLocation().getDirection();
      }
    };
  }

  public static Frame dynamic(Supplier<Location> location, Supplier<Vector> direction) {
    Objects.requireNonNull(location, "location");
    Objects.requireNonNull(direction, "direction");
    return new Frame() {
      @Override
      public Location location(CastContext ctx) {
        return location.get();
      }

      @Override
      public Vector direction(CastContext ctx) {
        return direction.get();
      }
    };
  }
}

