package dev.patric.dungeonsreborn.effects.config.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.patric.dungeonsreborn.effects.actions.Action;
import dev.patric.dungeonsreborn.effects.mana.ManaProvider;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities.NumValue;
import dev.patric.dungeonsreborn.effects.conditions.Condition;
import org.bukkit.entity.Player;

final class ControlActionParsers {
  private ControlActionParsers() {
  }

  static void register(Map<String, ActionParser> registry) {
    registry.put("when", ControlActionParsers::parseWhen);
    registry.put("random_choice_weighted", ControlActionParsers::parseRandomWeighted);
    registry.put("invoke_ability", ControlActionParsers::parseInvokeAbility);
    registry.put("resource_drain", ControlActionParsers::parseResourceDrain);
    registry.put("mana_drain", ControlActionParsers::parseResourceDrain);
  }

  private static Action parseWhen(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    Condition cond = ctx.compileCondition(ctx.require(node, "condition", path + ".condition"), path + ".condition");
    Map<String, Object> then = ctx.castMap(ctx.require(node, "then", path + ".then"), path + ".then");
    Action thenAction = ctx.compileAction(then, path + ".then", includeStack);
    Action otherwise = dev.patric.dungeonsreborn.effects.actions.Actions.noop();
    if (node.containsKey("otherwise")) {
      otherwise = ctx.compileAction(ctx.castMap(node.get("otherwise"), path + ".otherwise"),
          path + ".otherwise", includeStack);
    }
    final Action finalOtherwise = otherwise;
    return castCtx -> {
      if (cond.test(castCtx)) {
        thenAction.execute(castCtx);
      } else {
        finalOtherwise.execute(castCtx);
      }
    };
  }

  private static Action parseRandomWeighted(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    Object v = ctx.require(node, "choices", path + ".choices");
    if (!(v instanceof List<?> choices) || choices.isEmpty()) {
      throw new IllegalArgumentException(path + ".choices: expected non-empty list");
    }
    record Choice(NumValue weight, Action action) {
    }
    var compiled = new ArrayList<Choice>(choices.size());
    for (int i = 0; i < choices.size(); i++) {
      Map<String, Object> c = ctx.castMap(choices.get(i), path + ".choices[" + i + "]");
      NumValue w = ctx.requireNumValue(c, "weight", path + ".choices[" + i + "].weight");
      Map<String, Object> a = ctx.castMap(ctx.require(c, "action", path + ".choices[" + i + "].action"),
          path + ".choices[" + i + "].action");
      compiled.add(new Choice(w, ctx.compileAction(a, path + ".choices[" + i + "].action", includeStack)));
    }
    return castCtx -> {
      double totalWeight = 0.0;
      double[] weights = new double[compiled.size()];
      for (int i = 0; i < compiled.size(); i++) {
        double w = ctx.evalDouble(compiled.get(i).weight(), castCtx);
        if (w > 0.0) {
          weights[i] = w;
          totalWeight += w;
        } else {
          weights[i] = 0.0;
        }
      }
      if (!(totalWeight > 0.0)) {
        if (castCtx.engine().isDebugEnabled()) {
          castCtx.engine().debug("random_choice_weighted: no positive weights");
        }
        return;
      }
      double r = castCtx.rng().nextDouble() * totalWeight;
      double acc = 0.0;
      for (int i = 0; i < compiled.size(); i++) {
        acc += weights[i];
        if (r <= acc) {
          compiled.get(i).action().execute(castCtx);
          return;
        }
      }
      compiled.get(compiled.size() - 1).action().execute(castCtx);
    };
  }

  private static Action parseInvokeAbility(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    String rawAbility = ctx.requireString(node, "ability", path + ".ability");
    String abilityId;
    try {
      abilityId = dev.patric.dungeonsreborn.effects.Ids.normalize(rawAbility);
    } catch (Exception ex) {
      throw new IllegalArgumentException(path + ".ability: invalid id: " + rawAbility);
    }

    String mode = ctx.string(node, "mode", "subgraph").trim().toLowerCase(java.util.Locale.ROOT);
    int maxDepth = ctx.intValue(node, "maxDepth", 8);
    if (maxDepth <= 0) {
      throw new IllegalArgumentException(path + ".maxDepth: must be > 0");
    }

    return castCtx -> {
      if ("cast".equals(mode)) {
        if (castCtx.engine().hasAbility(abilityId)) {
          castCtx.engine().cast(abilityId, castCtx.caster());
        } else if (castCtx.engine().isDebugEnabled()) {
          castCtx.engine().debug("invoke_ability cast: ability not registered: " + abilityId);
        }
        return;
      }

      Action target = ctx.findYamlActionGraph(abilityId);
      if (target == null) {
        if (castCtx.engine().hasAbility(abilityId)) {
          castCtx.engine().cast(abilityId, castCtx.caster());
          return;
        }
        if (castCtx.engine().isDebugEnabled()) {
          castCtx.engine().debug("invoke_ability: unknown ability: " + abilityId);
        }
        return;
      }

      Object existing = castCtx.state().get(ctx.yamlInvokeStackKey());
      @SuppressWarnings("unchecked")
      java.util.ArrayDeque<String> stack = existing instanceof java.util.ArrayDeque<?> d ? (java.util.ArrayDeque<String>) d : null;
      if (stack == null) {
        stack = new java.util.ArrayDeque<>();
        stack.addLast(castCtx.abilityId());
        castCtx.state().put(ctx.yamlInvokeStackKey(), stack);
      }
      if (stack.contains(abilityId)) {
        throw new IllegalArgumentException(path + ": invoke_ability cycle: " + stack + " -> " + abilityId);
      }
      if (stack.size() >= maxDepth) {
        throw new IllegalArgumentException(path + ": invoke_ability depth exceeded: " + stack.size() + " >= " + maxDepth);
      }
      stack.addLast(abilityId);
      try {
        target.execute(castCtx);
      } finally {
        stack.removeLast();
      }
    };
  }

  private static Action parseResourceDrain(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    String resourceId = ctx.string(node, "resource", ManaProvider.DEFAULT_RESOURCE);
    NumValue amount = ctx.requireNumValue(node, "amount", path + ".amount");
    boolean allowPartial = ctx.bool(node, "allowPartial", true);
    Action onFail = dev.patric.dungeonsreborn.effects.actions.Actions.noop();
    if (node.containsKey("onFail")) {
      onFail = ctx.compileAction(ctx.castMap(node.get("onFail"), path + ".onFail"), path + ".onFail", includeStack);
    }
    Action finalOnFail = onFail;
    String normalizedResource = resourceId == null || resourceId.isBlank()
        ? ManaProvider.DEFAULT_RESOURCE
        : resourceId.trim();
    return castCtx -> {
      if (!(castCtx.caster() instanceof Player player)) {
        return;
      }
      ManaProvider provider = castCtx.engine().manaProvider();
      if (provider == null) {
        finalOnFail.execute(castCtx);
        return;
      }
      double value = ctx.evalDouble(amount, castCtx);
      if (value <= 0.0) {
        return;
      }
      if (allowPartial) {
        double current = provider.get(player, normalizedResource);
        if (current <= 0.0) {
          finalOnFail.execute(castCtx);
          return;
        }
        double next = Math.max(0.0, current - value);
        provider.set(player, normalizedResource, next);
        if (next <= 0.0 && current < value) {
          finalOnFail.execute(castCtx);
        }
        return;
      }
      if (provider.tryConsume(player, normalizedResource, value) != null) {
        finalOnFail.execute(castCtx);
      }
    };
  }
}
