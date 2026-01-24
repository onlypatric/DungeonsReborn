package dev.patric.dungeonsreborn.effects.config.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.patric.dungeonsreborn.effects.CastContext;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.actions.Action;
import dev.patric.dungeonsreborn.effects.actions.ActionHandle;
import dev.patric.dungeonsreborn.effects.actions.ActionWithHandle;
import dev.patric.dungeonsreborn.effects.actions.Actions;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities.NumValue;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities.TimelineEntrySpec;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities.EasingId;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities.VarScope;
import org.bukkit.entity.LivingEntity;

final class CoreActionParsers {
  private CoreActionParsers() {
  }

  static void register(Map<String, ActionParser> registry) {
    registry.put("include", CoreActionParsers::parseInclude);
    registry.put("sequence", CoreActionParsers::parseSequence);
    registry.put("timeline", CoreActionParsers::parseTimeline);
    registry.put("global_timeline", CoreActionParsers::parseGlobalTimeline);
    registry.put("timeline_global", CoreActionParsers::parseGlobalTimeline);
    registry.put("preset_timeline_pulse", CoreActionParsers::parsePresetTimelinePulse);
    registry.put("preset_timeline_stagger", CoreActionParsers::parsePresetTimelineStagger);
    registry.put("delay", CoreActionParsers::parseDelay);
    registry.put("repeat_ticks", CoreActionParsers::parseRepeatTicks);
  }

  private static Action parseInclude(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    String macro = ctx.requireString(node, "macro", path + ".macro");
    Map<String, Object> def = ctx.macro(macro);
    if (def == null) {
      throw new IllegalArgumentException(path + ".macro: unknown macro: " + macro);
    }
    if (includeStack.contains(macro)) {
      throw new IllegalArgumentException(path + ": include cycle: " + String.join(" -> ", includeStack) + " -> " + macro);
    }
    includeStack.addLast(macro);
    try {
      return ctx.compileAction(def, "macros." + macro, includeStack);
    } finally {
      includeStack.removeLast();
    }
  }

  private static Action parseSequence(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    List<?> list = ctx.mapList(node, "actions", path + ".actions");
    var actions = new ArrayList<Action>(list.size());
    for (int i = 0; i < list.size(); i++) {
      actions.add(ctx.compileAction(ctx.castMap(list.get(i), path + ".actions[" + i + "]"),
          path + ".actions[" + i + "]", includeStack));
    }
    return Actions.sequence(actions.toArray(Action[]::new));
  }

  private static Action parseTimeline(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    List<?> list = ctx.mapList(node, "entries", path + ".entries");
    var entries = new java.util.ArrayList<TimelineEntrySpec>(list.size());
    for (int i = 0; i < list.size(); i++) {
      Map<String, Object> entry = ctx.castMap(list.get(i), path + ".entries[" + i + "]");
      NumValue at = entry.containsKey("at")
          ? ctx.numValue(entry, "at", 0.0, path + ".entries[" + i + "]")
          : ctx.numValue(entry, "delay", 0.0, path + ".entries[" + i + "]");
      Map<String, Object> actionNode = ctx.castMap(ctx.require(entry, "action", path + ".entries[" + i + "].action"),
          path + ".entries[" + i + "].action");
      Action entryAction = ctx.compileAction(actionNode, path + ".entries[" + i + "].action", includeStack);
      entries.add(new TimelineEntrySpec(at, entryAction));
    }
    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext castCtx) {
        var resolved = new java.util.ArrayList<Actions.TimelineEntry>(entries.size());
        for (TimelineEntrySpec spec : entries) {
          long delayTicks = Math.max(0L, ctx.evalLong(spec.delayTicks(), castCtx));
          resolved.add(new Actions.TimelineEntry(delayTicks, spec.action()));
        }
        return Actions.timeline(resolved).executeWithHandle(castCtx);
      }
    };
  }

  private static Action parseGlobalTimeline(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    String id = ctx.requireString(node, "id", path + ".id");
    NumValue durationTicks = ctx.numValue(node, "durationTicks", 200.0, path);
    NumValue periodTicks = ctx.numValue(node, "periodTicks", 1.0, path);
    boolean start = ctx.bool(node, "start", true);
    Map<String, Object> onTickNode = ctx.castMap(ctx.require(node, "onTick", path + ".onTick"), path + ".onTick");
    Action onTickAction = ctx.compileAction(onTickNode, path + ".onTick", includeStack);
    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext castCtx) {
        long duration = Math.max(1L, ctx.evalLong(durationTicks, castCtx));
        long period = Math.max(1L, ctx.evalLong(periodTicks, castCtx));
        EffectsEngine.TimelineHandle timeline = castCtx.engine().timeline(id);
        if (timeline == null && start) {
          timeline = castCtx.engine().startTimeline(id, duration, period);
        }
        if (timeline == null) {
          return ActionHandle.completed();
        }
        AtomicBoolean cancelled = new AtomicBoolean(false);
        castCtx.state().onCancel(() -> cancelled.set(true));
        EffectsEngine.TimelineHandle handleRef = timeline;
        long timelineDuration = Math.max(1L, handleRef.durationTicks());
        timeline.subscribe(tick -> {
          if (cancelled.get()) {
            return;
          }
          double t = Math.min(1.0, Math.max(0.0, tick / (double) timelineDuration));
          Map<String, Object> values = new java.util.HashMap<>();
          values.put("timeline_id", handleRef.id());
          values.put("timeline_tick", tick);
          values.put("timeline_t", t);
          ctx.withTempVars(castCtx, VarScope.CAST, values, () -> onTickAction.execute(castCtx));
        });
        return new ActionHandle() {
          @Override
          public boolean cancel() {
            return handleRef.cancel();
          }

          @Override
          public boolean isDone() {
            return handleRef.isCancelled() || handleRef.currentTick() >= handleRef.durationTicks();
          }
        };
      }
    };
  }

  private static Action parsePresetTimelinePulse(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    NumValue durationTicks = ctx.numValue(node, "durationTicks", 100.0, path);
    NumValue periodTicks = ctx.numValue(node, "periodTicks", 5.0, path);
    EasingId easingId = ctx.easingId(node, path);
    Map<String, Object> onTickNode = ctx.castMap(ctx.require(node, "onTick", path + ".onTick"), path + ".onTick");
    Action onTickAction = ctx.compileAction(onTickNode, path + ".onTick", includeStack);
    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext castCtx) {
        long duration = Math.max(1L, ctx.evalLong(durationTicks, castCtx));
        long period = Math.max(1L, ctx.evalLong(periodTicks, castCtx));
        return Actions.timelinePresetPulse(duration, period, ctx.easingFromId(easingId),
            (exec, t) -> ctx.withTempVar(exec, VarScope.CAST, "t", t, () -> onTickAction.execute(exec)))
            .executeWithHandle(castCtx);
      }
    };
  }

  private static Action parsePresetTimelineStagger(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    List<?> list = ctx.mapList(node, "actions", path + ".actions");
    NumValue startDelay = ctx.numValue(node, "startDelayTicks", 0.0, path);
    NumValue stepTicks = ctx.numValue(node, "stepTicks", 5.0, path);
    var actions = new ArrayList<Action>(list.size());
    for (int i = 0; i < list.size(); i++) {
      actions.add(ctx.compileAction(ctx.castMap(list.get(i), path + ".actions[" + i + "]"),
          path + ".actions[" + i + "]", includeStack));
    }
    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext castCtx) {
        long delay = Math.max(0L, ctx.evalLong(startDelay, castCtx));
        long step = Math.max(0L, ctx.evalLong(stepTicks, castCtx));
        return Actions.timelinePresetStagger(delay, step, actions).executeWithHandle(castCtx);
      }
    };
  }

  private static Action parseDelay(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    NumValue ticksValue = node.containsKey("delayTicks")
        ? ctx.numValue(node, "delayTicks", 0.0, path)
        : ctx.numValue(node, "ticks", 0.0, path);
    Object thenRaw = node.containsKey("then") ? node.get("then") : node.get("action");
    if (thenRaw == null) {
      throw new IllegalArgumentException(path + ".then: missing action");
    }
    Map<String, Object> then = ctx.castMap(thenRaw, path + ".then");
    Action thenAction = ctx.compileAction(then, path + ".then", includeStack);
    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext castCtx) {
        long ticks = Math.max(0L, ctx.evalLong(ticksValue, castCtx));
        final LivingEntity captured = ctx.lastEntity(castCtx);
        final Object prev = castCtx.state().get(ctx.yamlLastEntityKey());
        if (ticks <= 0L) {
          if (captured != null) {
            castCtx.state().put(ctx.yamlLastEntityKey(), captured);
          }
          try {
            return thenAction.executeWithHandle(castCtx);
          } finally {
            castCtx.state().put(ctx.yamlLastEntityKey(), prev);
          }
        }
        AtomicBoolean done = new AtomicBoolean(false);
        var handle = castCtx.engine().runLater(ticks, () -> {
          if (captured != null) {
            castCtx.state().put(ctx.yamlLastEntityKey(), captured);
          }
          try {
            thenAction.executeWithHandle(castCtx);
          } finally {
            castCtx.state().put(ctx.yamlLastEntityKey(), prev);
            done.set(true);
          }
        });
        castCtx.state().track(handle);
        return ctx.scheduledHandle(handle, done);
      }
    };
  }

  private static Action parseRepeatTicks(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    NumValue delayTicksValue = ctx.numValue(node, "delayTicks", 0.0, path);
    NumValue periodTicksValue = ctx.numValue(node, "periodTicks", 1.0, path);
    NumValue timesValue = ctx.numValue(node, "times", 1.0, path);
    Map<String, Object> actionNode = ctx.castMap(ctx.require(node, "action", path + ".action"), path + ".action");
    Action body = ctx.compileAction(actionNode, path + ".action", includeStack);
    return new ActionWithHandle() {
      @Override
      public ActionHandle executeWithHandle(CastContext castCtx) {
        long delayTicks = Math.max(0L, ctx.evalLong(delayTicksValue, castCtx));
        long periodTicks = ctx.evalLong(periodTicksValue, castCtx);
        int times = ctx.evalInt(timesValue, castCtx);
        if (periodTicks <= 0 || times <= 0) {
          return ActionHandle.completed();
        }
        final LivingEntity captured = ctx.lastEntity(castCtx);
        final Object prev = castCtx.state().get(ctx.yamlLastEntityKey());
        final int[] remaining = new int[] { times };
        AtomicBoolean done = new AtomicBoolean(false);
        final EffectsEngine.ScheduledHandle[] handle = new EffectsEngine.ScheduledHandle[1];
        handle[0] = castCtx.engine().runRepeating(delayTicks, periodTicks, () -> {
          if (handle[0] == null || handle[0].isCancelled()) {
            done.set(true);
            return;
          }
          if (remaining[0]-- <= 0) {
            handle[0].cancel();
            done.set(true);
            return;
          }
          if (captured != null) {
            castCtx.state().put(ctx.yamlLastEntityKey(), captured);
          }
          try {
            body.executeWithHandle(castCtx);
          } finally {
            castCtx.state().put(ctx.yamlLastEntityKey(), prev);
          }
        });
        castCtx.state().track(handle[0]);
        return ctx.scheduledHandle(handle[0], done);
      }
    };
  }
}
