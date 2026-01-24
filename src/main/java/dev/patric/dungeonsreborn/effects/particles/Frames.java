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

  public record FrameSpec(Frame frame, double forward, double right, double up) {
    public FrameSpec {
      Objects.requireNonNull(frame, "frame");
    }

    public Location location(CastContext ctx) {
      Location base = frame.location(ctx);
      if (base == null) {
        return null;
      }
      Vector dir = frame.direction(ctx);
      if (dir == null) {
        dir = ctx.direction();
      }
      if (dir.lengthSquared() < 1e-9) {
        dir = new Vector(0, 0, 1);
      }
      dir = dir.clone().normalize();
      Vector upVec = new Vector(0, 1, 0);
      Vector rightVec = dir.clone().crossProduct(upVec);
      if (rightVec.lengthSquared() < 1e-9) {
        rightVec = new Vector(1, 0, 0);
      } else {
        rightVec.normalize();
      }
      Location out = base.clone();
      out.add(dir.multiply(forward));
      out.add(rightVec.multiply(right));
      out.add(0, up, 0);
      return out;
    }
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

  public static Frame entityEyes(LivingEntity entity) {
    Objects.requireNonNull(entity, "entity");
    return new Frame() {
      @Override
      public Location location(CastContext ctx) {
        if (entity instanceof Player player) {
          return player.getEyeLocation();
        }
        Location base = entity.getLocation();
        return base.add(0.0, entity.getHeight() * 0.9, 0.0);
      }

      @Override
      public Vector direction(CastContext ctx) {
        if (entity instanceof Player player) {
          return player.getEyeLocation().getDirection();
        }
        return entity.getLocation().getDirection();
      }
    };
  }

  public static Frame entityHead(LivingEntity entity) {
    return entityEyes(entity);
  }

  public static Frame entityMainHand(LivingEntity entity) {
    return entityHand(entity, false);
  }

  public static Frame entityOffHand(LivingEntity entity) {
    return entityHand(entity, true);
  }

  private static Frame entityHand(LivingEntity entity, boolean offHand) {
    Objects.requireNonNull(entity, "entity");
    return new Frame() {
      @Override
      public Location location(CastContext ctx) {
        Location base = entity.getLocation();
        Vector dir = base.getDirection();
        if (dir.lengthSquared() < 1e-9) {
          dir = new Vector(0, 0, 1);
        }
        dir.normalize();
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1e-9) {
          right = new Vector(1, 0, 0);
        } else {
          right.normalize();
        }
        double side = offHand ? 0.35 : -0.35;
        Location out = base.clone().add(0.0, entity.getHeight() * 0.75, 0.0);
        out.add(right.multiply(side));
        out.add(dir.multiply(0.2));
        return out;
      }

      @Override
      public Vector direction(CastContext ctx) {
        Vector dir = entity.getLocation().getDirection();
        if (dir.lengthSquared() < 1e-9) {
          dir = new Vector(0, 0, 1);
        }
        return dir.normalize();
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

  public static FrameSpec withOffsets(Frame frame, double forward, double right, double up) {
    return new FrameSpec(frame, forward, right, up);
  }
}
