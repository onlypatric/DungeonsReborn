package dev.patric.dungeonsreborn.effects;

@FunctionalInterface
public interface Ability {
  void cast(CastContext ctx);
}

