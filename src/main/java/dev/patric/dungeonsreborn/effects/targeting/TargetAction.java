package dev.patric.dungeonsreborn.effects.targeting;

import dev.patric.dungeonsreborn.effects.CastContext;

@FunctionalInterface
public interface TargetAction<T> {
  void execute(CastContext ctx, T target);
}

