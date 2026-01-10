package dev.patric.dungeonsreborn.effects.actions;

import java.util.List;
import java.util.Objects;

/**
 * Handle for a running action/timeline.
 */
public interface ActionHandle {
  boolean cancel();

  boolean isDone();

  static ActionHandle completed() {
    return CompletedHandle.INSTANCE;
  }

  static ActionHandle composite(List<ActionHandle> handles) {
    if (handles == null || handles.isEmpty()) {
      return completed();
    }
    List<ActionHandle> list = handles.stream().filter(Objects::nonNull).toList();
    if (list.isEmpty()) {
      return completed();
    }
    return new ActionHandle() {
      @Override
      public boolean cancel() {
        boolean any = false;
        for (ActionHandle handle : list) {
          try {
            any |= handle.cancel();
          } catch (Exception ignored) {
          }
        }
        return any;
      }

      @Override
      public boolean isDone() {
        for (ActionHandle handle : list) {
          try {
            if (!handle.isDone()) {
              return false;
            }
          } catch (Exception ignored) {
            return false;
          }
        }
        return true;
      }
    };
  }

  final class CompletedHandle implements ActionHandle {
    private static final CompletedHandle INSTANCE = new CompletedHandle();

    private CompletedHandle() {
    }

    @Override
    public boolean cancel() {
      return false;
    }

    @Override
    public boolean isDone() {
      return true;
    }
  }
}
