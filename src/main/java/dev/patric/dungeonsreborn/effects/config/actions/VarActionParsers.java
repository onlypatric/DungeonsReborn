package dev.patric.dungeonsreborn.effects.config.actions;

import java.util.Map;

import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities.VarScope;
import dev.patric.dungeonsreborn.effects.actions.Action;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities.NumValue;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities.ValueSupplier;

final class VarActionParsers {
  private VarActionParsers() {
  }

  static void register(Map<String, ActionParser> registry) {
    registry.put("set_var", VarActionParsers::parseSetVar);
    registry.put("inc_var", VarActionParsers::parseIncVar);
    registry.put("with_var", VarActionParsers::parseWithVar);
    registry.put("debug_var", VarActionParsers::parseDebugVar);
  }

  private static Action parseSetVar(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    String key = ctx.requireString(node, "key", path + ".key");
    VarScope scope = ctx.parseVarScope(ctx.string(node, "scope", null), path + ".scope", VarScope.CAST);
    ValueSupplier value = ctx.varValue(node.get("value"), path + ".value");
    NumValue ttl = node.containsKey("ttlTicks") ? ctx.numValue(node, "ttlTicks", 0.0, path) : null;
    return castCtx -> {
      Object v = value.eval(castCtx);
      long ttlTicks = ctx.evalTtlTicks(ttl, castCtx);
      ctx.setVar(castCtx, scope, key, v, ttlTicks > 0 ? ttlTicks : null);
    };
  }

  private static Action parseIncVar(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    String key = ctx.requireString(node, "key", path + ".key");
    VarScope scope = ctx.parseVarScope(ctx.string(node, "scope", null), path + ".scope", VarScope.CAST);
    NumValue amount = ctx.numValue(node, "amount", 1.0, path);
    NumValue def = ctx.numValue(node, "default", 0.0, path);
    NumValue ttl = node.containsKey("ttlTicks") ? ctx.numValue(node, "ttlTicks", 0.0, path) : null;
    return castCtx -> {
      Object cur = ctx.vars(castCtx, scope).get(key);
      double next = ctx.numericVar(cur, ctx.evalDouble(def, castCtx)) + ctx.evalDouble(amount, castCtx);
      long ttlTicks = ctx.evalTtlTicks(ttl, castCtx);
      ctx.setVar(castCtx, scope, key, next, ttlTicks > 0 ? ttlTicks : null);
    };
  }

  private static Action parseWithVar(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    String key = ctx.requireString(node, "key", path + ".key");
    VarScope scope = ctx.parseVarScope(ctx.string(node, "scope", null), path + ".scope", VarScope.CAST);
    ValueSupplier value = ctx.varValue(node.get("value"), path + ".value");
    Map<String, Object> then = ctx.castMap(ctx.require(node, "then", path + ".then"), path + ".then");
    Action thenAction = ctx.compileAction(then, path + ".then", includeStack);
    return castCtx -> {
      Map<String, Object> vars = ctx.vars(castCtx, scope);
      Map<String, Long> expirations = ctx.varExpirations(castCtx, scope);
      boolean had = vars.containsKey(key);
      Object prev = vars.get(key);
      boolean hadExp = expirations.containsKey(key);
      Long prevExp = expirations.get(key);
      Object v = value.eval(castCtx);
      ctx.setVar(castCtx, scope, key, v);
      try {
        thenAction.execute(castCtx);
      } finally {
        if (!had) {
          vars.remove(key);
          expirations.remove(key);
        } else {
          vars.put(key, prev);
          if (hadExp) {
            expirations.put(key, prevExp);
          } else {
            expirations.remove(key);
          }
        }
      }
    };
  }

  private static Action parseDebugVar(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    String key = ctx.requireString(node, "key", path + ".key");
    VarScope scope = ctx.parseVarScope(ctx.string(node, "scope", null), path + ".scope", VarScope.CAST);
    String label = ctx.string(node, "label", key);
    return castCtx -> {
      if (!castCtx.engine().isDebugEnabled()) {
        return;
      }
      Object v = ctx.vars(castCtx, scope).get(key);
      castCtx.engine().debug("var(" + scope.name().toLowerCase(java.util.Locale.ROOT) + "): " + label + "=" + v);
    };
  }
}
