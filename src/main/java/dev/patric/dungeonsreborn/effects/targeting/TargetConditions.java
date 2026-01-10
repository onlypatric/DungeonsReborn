package dev.patric.dungeonsreborn.effects.targeting;

import java.util.Objects;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.effects.relations.Relation;

public final class TargetConditions {
  private TargetConditions() {
  }

  public static TargetCondition<LivingEntity> lineOfSight() {
    return (ctx, target) -> ctx.caster() instanceof Player player && player.hasLineOfSight(target);
  }

  public static TargetCondition<LivingEntity> healthAbove(double value) {
    if (value < 0) {
      throw new IllegalArgumentException("value must be >= 0");
    }
    return (ctx, target) -> target.getHealth() > value;
  }

  public static TargetCondition<LivingEntity> healthBelow(double value) {
    if (value < 0) {
      throw new IllegalArgumentException("value must be >= 0");
    }
    return (ctx, target) -> target.getHealth() < value;
  }

  public static TargetCondition<LivingEntity> playersOnly() {
    return (ctx, target) -> target instanceof Player;
  }

  public static TargetCondition<LivingEntity> mobsOnly() {
    return (ctx, target) -> !(target instanceof Player);
  }

  public static TargetCondition<LivingEntity> isAlly() {
    return (ctx, target) -> ctx.engine().relation(ctx.caster(), target) == Relation.ALLY;
  }

  public static TargetCondition<LivingEntity> isEnemy() {
    return (ctx, target) -> ctx.engine().relation(ctx.caster(), target) == Relation.ENEMY;
  }

  public static TargetCondition<LivingEntity> isNeutral() {
    return (ctx, target) -> ctx.engine().relation(ctx.caster(), target) == Relation.NEUTRAL;
  }

  public static TargetCondition<LivingEntity> hasTag(String tag) {
    Objects.requireNonNull(tag, "tag");
    if (tag.isBlank()) {
      throw new IllegalArgumentException("tag is blank");
    }
    return (ctx, target) -> target.getScoreboardTags().contains(tag);
  }

  public static TargetCondition<LivingEntity> lacksTag(String tag) {
    return not(hasTag(tag));
  }

  public static <T> TargetCondition<T> not(TargetCondition<T> condition) {
    Objects.requireNonNull(condition, "condition");
    return (ctx, target) -> !condition.test(ctx, target);
  }

  public static <T> TargetCondition<T> and(TargetCondition<T> a, TargetCondition<T> b) {
    Objects.requireNonNull(a, "a");
    Objects.requireNonNull(b, "b");
    return (ctx, target) -> a.test(ctx, target) && b.test(ctx, target);
  }
}
