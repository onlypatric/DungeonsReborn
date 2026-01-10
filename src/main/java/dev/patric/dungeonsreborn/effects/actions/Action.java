package dev.patric.dungeonsreborn.effects.actions;

import dev.patric.dungeonsreborn.effects.CastContext;

@FunctionalInterface
public interface Action {
  void execute(CastContext ctx);

  default ActionHandle executeWithHandle(CastContext ctx) {
    execute(ctx);
    return ActionHandle.completed();
  }
}
