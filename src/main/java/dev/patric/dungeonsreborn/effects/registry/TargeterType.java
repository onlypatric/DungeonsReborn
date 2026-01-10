package dev.patric.dungeonsreborn.effects.registry;

import dev.patric.dungeonsreborn.effects.targeting.Targeter;

public interface TargeterType<T> extends NodeType {
  Class<T> targetType();

  Targeter<T> build(Params params);
}

