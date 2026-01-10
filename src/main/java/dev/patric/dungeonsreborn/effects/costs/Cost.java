package dev.patric.dungeonsreborn.effects.costs;

import dev.patric.dungeonsreborn.effects.CastContext;
import net.kyori.adventure.text.Component;

@FunctionalInterface
public interface Cost {
  /**
   * @return a failure message, or {@code null} if the cost was successfully paid.
   */
  Component tryApply(CastContext ctx);
}

