package dev.patric.dungeonsreborn.effects.targeting;

import dev.patric.dungeonsreborn.effects.CastContext;

@FunctionalInterface
public interface TargetCondition<T> {
  boolean test(CastContext ctx, T target);
}

