package dev.patric.dungeonsreborn.effects.targeting;

import java.util.List;

import dev.patric.dungeonsreborn.effects.CastContext;

@FunctionalInterface
public interface Targeter<T> {
  List<T> select(CastContext ctx);
}

