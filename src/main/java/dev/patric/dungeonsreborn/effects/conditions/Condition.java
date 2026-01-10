package dev.patric.dungeonsreborn.effects.conditions;

import dev.patric.dungeonsreborn.effects.CastContext;

@FunctionalInterface
public interface Condition {
  boolean test(CastContext ctx);
}

