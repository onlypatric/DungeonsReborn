package dev.patric.dungeonsreborn.effects.config.actions;

import java.util.List;
import java.util.Map;

import dev.patric.dungeonsreborn.effects.CastContext;
import dev.patric.dungeonsreborn.effects.actions.Action;
import dev.patric.dungeonsreborn.effects.actions.ActionHandle;
import dev.patric.dungeonsreborn.effects.actions.Actions;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities.EasingId;
import dev.patric.dungeonsreborn.effects.conditions.Condition;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities.NumValue;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities.ValueSupplier;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities.VarScope;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public interface ActionParserContext {
  Map<String, Object> macro(String id);
  Action compileAction(Map<String, Object> node, String path, java.util.ArrayDeque<String> includeStack);
  Condition compileCondition(Object raw, String path);
  Action findYamlActionGraph(String abilityId);

  Object require(Map<String, Object> node, String key, String path);
  Map<String, Object> castMap(Object raw, String path);
  List<?> mapList(Map<String, Object> node, String key, String path);
  String requireString(Map<String, Object> node, String key, String path);
  String string(Map<String, Object> node, String key, String def);
  boolean bool(Map<String, Object> node, String key, boolean def);
  int intValue(Map<String, Object> node, String key, int def);

  NumValue numValue(Map<String, Object> node, String key, double def, String path);
  NumValue requireNumValue(Map<String, Object> node, String key, String path);
  EasingId easingId(Map<String, Object> node, String path);
  java.util.function.DoubleUnaryOperator easingFromId(EasingId id);

  VarScope parseVarScope(String raw, String path, VarScope def);
  ValueSupplier varValue(Object raw, String path);
  Map<String, Object> vars(CastContext ctx, VarScope scope);
  Map<String, Long> varExpirations(CastContext ctx, VarScope scope);
  void setVar(CastContext ctx, VarScope scope, String key, Object value);
  void setVar(CastContext ctx, VarScope scope, String key, Object value, Long ttlTicks);
  long evalTtlTicks(NumValue ttl, CastContext ctx);

  long evalLong(NumValue value, CastContext ctx);
  int evalInt(NumValue value, CastContext ctx);
  double evalDouble(NumValue value, CastContext ctx);
  double numericVar(Object raw, double def);

  ActionHandle scheduledHandle(dev.patric.dungeonsreborn.effects.EffectsEngine.ScheduledHandle handle,
      java.util.concurrent.atomic.AtomicBoolean done);

  LivingEntity lastEntity(CastContext ctx);
  Player targetPlayer(CastContext ctx);
  CastContext followCasterContext(CastContext ctx);
  Location resolveAtWithOffsets(CastContext ctx, Object atMode, NumValue forward, NumValue right, NumValue up);
  Location resolveAtWithEntity(CastContext ctx, Object atMode);
  Object parseAt(String raw, String path);
  String yamlLastEntityKey();
  String yamlInvokeStackKey();

  void withTempVar(CastContext ctx, VarScope scope, String key, Object value, Runnable task);
  void withTempVars(CastContext ctx, VarScope scope, Map<String, Object> values, Runnable task);
  net.kyori.adventure.text.Component renderText(String raw, CastContext ctx);

  Actions.MotionMode parseMotionMode(String raw, String path);

  org.bukkit.Sound soundValue(Map<String, Object> node, String key, String path);
}
