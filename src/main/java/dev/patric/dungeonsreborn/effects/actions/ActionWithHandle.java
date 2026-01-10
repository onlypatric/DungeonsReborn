package dev.patric.dungeonsreborn.effects.actions;

import dev.patric.dungeonsreborn.effects.CastContext;

/**
 * Convenience base for actions that produce a running handle.
 */
public abstract class ActionWithHandle implements Action {
  @Override
  public final void execute(CastContext ctx) {
    executeWithHandle(ctx);
  }

  @Override
  public abstract ActionHandle executeWithHandle(CastContext ctx);
}
