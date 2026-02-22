package dev.patric.dungeonsreborn.mobs.model;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import dev.patric.dungeonsreborn.logging.ServiceLogger;
import dev.patric.dungeonsreborn.mobs.MobModelSpec;

public final class ModelEngineMobModelBridge implements MobModelBridge {
  private record Handle(Object modeledEntity, Object activeModel, String modelId, ModelRuntimeSpec spec) {
  }

  private record ActiveModelResolution(String resolvedModelId, Object activeModel, Throwable failure) {
  }

  private record ResolvedApi(
      Method getOrCreateModeledEntity,
      Method createModeledEntity,
      Method createActiveModel,
      Method getBlueprint,
      Method modeledAddModel,
      Method modeledGetModel,
      Method modeledRemoveModel,
      Method modeledDestroy,
      Method modeledSetBaseEntityVisible,
      Method modeledGetModels,
      Method activeModelGetAnimationHandler,
      Method animationPlay,
      Method animationForceStop) {
  }

  private final ServiceLogger logger;
  private final boolean debug;
  private final ResolvedApi api;
  private final Map<UUID, Handle> handles = new ConcurrentHashMap<>();
  private final String unavailableReason;

  public ModelEngineMobModelBridge(ServiceLogger logger, boolean debug) {
    this.logger = logger;
    this.debug = debug;
    ResolvedApi resolved = null;
    String reason = null;
    Plugin plugin = Bukkit.getPluginManager().getPlugin("ModelEngine");
    if (plugin == null) {
      plugin = Bukkit.getPluginManager().getPlugin("ModelEngineR4");
    }
    if (plugin == null || !plugin.isEnabled()) {
      reason = "ModelEngine plugin is not installed or not enabled";
    } else {
      try {
        resolved = resolveApi();
      } catch (RuntimeException ex) {
        reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
      }
    }
    this.api = resolved;
    this.unavailableReason = reason;
  }

  @Override
  public boolean available() {
    return api != null;
  }

  public String unavailableReason() {
    return unavailableReason;
  }

  @Override
  public String providerKey() {
    return "model_engine";
  }

  @Override
  public int activeCount() {
    return handles.size();
  }

  @Override
  public boolean attach(LivingEntity entity, ModelRuntimeSpec spec) {
    if (entity == null || spec == null || !available()) {
      return false;
    }
    if (spec.provider() != MobModelSpec.Provider.MODEL_ENGINE) {
      return false;
    }
    if (spec.modelId() == null || spec.modelId().isBlank()) {
      return false;
    }
    detach(entity);
    try {
      Object modeledEntity = invokeStaticNullable(resolveModeledEntityMethod(), entity);
      if (modeledEntity == null) {
        logWarn("attach failed: modeled entity is null for entity=" + entity.getUniqueId());
        return false;
      }
      ActiveModelResolution resolution = resolveActiveModel(spec.modelId());
      if (resolution.activeModel() == null) {
        if (resolution.failure() != null) {
          logWarn("attach failed: unable to create active model for id=" + spec.modelId()
              + " (" + describeThrowable(resolution.failure()) + ")", resolution.failure());
        } else {
          logWarn("attach failed: active model is null for id=" + spec.modelId()
              + " (check ModelEngine model id)");
        }
        return false;
      }
      int modelsBefore = modelCount(modeledEntity);
      Object addResult = invokeNullable(api.modeledAddModel(), modeledEntity, resolution.activeModel(), Boolean.TRUE);
      int modelsAfter = modelCount(modeledEntity);
      boolean attached = modelsAfter > modelsBefore || hasModel(modeledEntity, resolution.resolvedModelId());
      if (!attached) {
        logWarn("attach failed: model not attached for id=" + resolution.resolvedModelId()
            + " (addModel result=" + describeAddModelResult(addResult) + ")");
        return false;
      }
      if (addResult instanceof Optional<?> optional && optional.isEmpty()) {
        logDebug("addModel returned empty Optional for id=" + resolution.resolvedModelId()
            + " (expected on first attach in some ModelEngine builds)");
      }
      setBaseEntityVisible(modeledEntity, !spec.hideBaseEntity());
      entity.setInvisible(spec.hideBaseEntity());
      Handle handle = new Handle(modeledEntity, resolution.activeModel(), resolution.resolvedModelId(), spec);
      handles.put(entity.getUniqueId(), handle);
      playOnHandle(handle, "idle");
      if (!Objects.equals(spec.modelId(), resolution.resolvedModelId())) {
        logDebug("resolved model id " + spec.modelId() + " -> " + resolution.resolvedModelId());
      }
      logDebug("attached model id=" + resolution.resolvedModelId() + " entity=" + entity.getUniqueId());
      return true;
    } catch (Exception ex) {
      logWarn("attach failed for model " + spec.modelId() + ": " + describeThrowable(ex), ex);
      return false;
    }
  }

  @Override
  public void update(LivingEntity entity, ModelRuntimeSpec spec) {
    if (entity == null || !available()) {
      return;
    }
    if (spec != null && spec.provider() != MobModelSpec.Provider.MODEL_ENGINE) {
      detach(entity);
      return;
    }
    if (spec == null || spec.modelId() == null || spec.modelId().isBlank()) {
      detach(entity);
      return;
    }
    Handle current = handles.get(entity.getUniqueId());
    if (current == null || !Objects.equals(current.modelId(), spec.modelId())) {
      attach(entity, spec);
      return;
    }
    try {
      setBaseEntityVisible(current.modeledEntity(), !spec.hideBaseEntity());
      entity.setInvisible(spec.hideBaseEntity());
      Handle updated = new Handle(current.modeledEntity(), current.activeModel(), current.modelId(), spec);
      handles.put(entity.getUniqueId(), updated);
      String currentAnimation = current.spec() == null ? null : current.spec().resolveAnimation("idle");
      String nextAnimation = spec.resolveAnimation("idle");
      if (!Objects.equals(currentAnimation, nextAnimation)) {
        playOnHandle(updated, "idle");
      }
      logDebug("updated model id=" + spec.modelId() + " entity=" + entity.getUniqueId());
    } catch (Exception ex) {
      logWarn("update failed for model " + spec.modelId() + ": " + describeThrowable(ex), ex);
      detach(entity);
    }
  }

  @Override
  public void play(LivingEntity entity, String animationKey) {
    if (entity == null || !available()) {
      return;
    }
    Handle handle = handles.get(entity.getUniqueId());
    if (handle == null) {
      return;
    }
    playOnHandle(handle, animationKey);
  }

  @Override
  public void detach(LivingEntity entity) {
    if (entity == null || !available()) {
      return;
    }
    Handle handle = handles.remove(entity.getUniqueId());
    if (handle == null) {
      return;
    }
    try {
      if (api.modeledRemoveModel() != null) {
        invokeNullable(api.modeledRemoveModel(), handle.modeledEntity(), handle.modelId());
      }
      if (api.modeledGetModels() != null && api.modeledDestroy() != null) {
        Object models = invokeNullable(api.modeledGetModels(), handle.modeledEntity());
        if (models instanceof Map<?, ?> modelMap && modelMap.isEmpty()) {
          invokeNullable(api.modeledDestroy(), handle.modeledEntity());
        }
      }
      setBaseEntityVisible(handle.modeledEntity(), true);
    } catch (Exception ex) {
      logWarn("detach failed for model " + handle.modelId() + ": " + describeThrowable(ex), ex);
    } finally {
      entity.setInvisible(false);
    }
  }

  private void playOnHandle(Handle handle, String animationKey) {
    if (handle == null || handle.activeModel() == null || handle.spec() == null || api.animationPlay() == null) {
      return;
    }
    String animation = handle.spec().resolveAnimation(animationKey);
    if (animation == null || animation.isBlank()) {
      return;
    }
    try {
      Object animationHandler = invokeNullable(api.activeModelGetAnimationHandler(), handle.activeModel());
      if (animationHandler == null) {
        return;
      }
      if (api.animationForceStop() != null) {
        invokeNullable(api.animationForceStop(), animationHandler);
      }
      Object[] args = buildAnimationArgs(api.animationPlay().getParameterTypes(), animation, handle.spec().animationSpeed());
      invokeNullable(api.animationPlay(), animationHandler, args);
      logDebug("play animation=" + animation + " modelId=" + handle.modelId());
    } catch (Exception ex) {
      logWarn("play animation failed (" + animation + "): " + describeThrowable(ex), ex);
    }
  }

  private ActiveModelResolution resolveActiveModel(String requestedModelId) {
    String requested = requestedModelId == null ? "" : requestedModelId.trim();
    if (requested.isBlank()) {
      return new ActiveModelResolution(requested, null, null);
    }
    Throwable lastFailure = null;
    for (String candidate : modelIdCandidates(requested)) {
      try {
        if (api.getBlueprint() != null) {
          Object blueprint = invokeStaticNullable(api.getBlueprint(), candidate);
          if (blueprint == null) {
            logDebug("blueprint missing for candidate=" + candidate);
            continue;
          }
        }
        Object model = invokeStaticNullable(api.createActiveModel(), candidate);
        if (model != null) {
          return new ActiveModelResolution(candidate, model, null);
        }
      } catch (Exception ex) {
        lastFailure = ex;
        logDebug("createActiveModel failed for candidate=" + candidate + " reason=" + describeThrowable(ex));
      }
    }
    return new ActiveModelResolution(requested, null, lastFailure);
  }

  private java.util.List<String> modelIdCandidates(String requested) {
    java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
    String trimmed = requested.trim();
    if (!trimmed.isBlank()) {
      out.add(trimmed);
      String lower = trimmed.toLowerCase(Locale.ROOT);
      out.add(lower);
      if (!trimmed.contains(":")) {
        out.add(trimmed + ":body");
        out.add(lower + ":body");
      }
    }
    return java.util.List.copyOf(out);
  }

  private String describeThrowable(Throwable throwable) {
    if (throwable == null) {
      return "unknown";
    }
    Throwable root = throwable;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    String message = root.getMessage();
    if (message == null || message.isBlank()) {
      return root.getClass().getSimpleName();
    }
    return root.getClass().getSimpleName() + ": " + message;
  }

  private String describeAddModelResult(Object addResult) {
    if (addResult == null) {
      return "null";
    }
    if (addResult instanceof Optional<?> optional) {
      return optional.isPresent() ? "optional[present]" : "optional[empty]";
    }
    return addResult.getClass().getSimpleName();
  }

  private int modelCount(Object modeledEntity) {
    if (modeledEntity == null || api.modeledGetModels() == null) {
      return -1;
    }
    try {
      Object models = invokeNullable(api.modeledGetModels(), modeledEntity);
      if (models instanceof Map<?, ?> modelMap) {
        return modelMap.size();
      }
    } catch (Exception ex) {
      logDebug("modelCount failed: " + describeThrowable(ex));
    }
    return -1;
  }

  private boolean hasModel(Object modeledEntity, String modelId) {
    if (modeledEntity == null || modelId == null || modelId.isBlank()) {
      return false;
    }
    try {
      if (api.modeledGetModel() != null) {
        Object resolved = invokeNullable(api.modeledGetModel(), modeledEntity, modelId);
        if (resolved instanceof Optional<?> optional && optional.isPresent()) {
          return true;
        }
      }
      if (api.modeledGetModels() != null) {
        Object models = invokeNullable(api.modeledGetModels(), modeledEntity);
        if (models instanceof Map<?, ?> modelMap) {
          if (modelMap.containsKey(modelId)) {
            return true;
          }
          String lower = modelId.toLowerCase(Locale.ROOT);
          if (!lower.equals(modelId) && modelMap.containsKey(lower)) {
            return true;
          }
        }
      }
    } catch (Exception ex) {
      logDebug("hasModel failed: " + describeThrowable(ex));
    }
    return false;
  }

  private Object[] buildAnimationArgs(Class<?>[] parameterTypes, String animation, double speed) {
    Object[] args = new Object[parameterTypes.length];
    int numericIndex = 0;
    for (int i = 0; i < parameterTypes.length; i++) {
      Class<?> type = parameterTypes[i];
      if (type == String.class) {
        args[i] = animation;
      } else if (type == boolean.class || type == Boolean.class) {
        args[i] = Boolean.TRUE;
      } else if (type == double.class || type == Double.class) {
        args[i] = numericIndex >= 2 ? speed : 0.1D;
        numericIndex++;
      } else if (type == float.class || type == Float.class) {
        float value = numericIndex >= 2 ? (float) speed : 0.1F;
        args[i] = value;
        numericIndex++;
      } else if (type == int.class || type == Integer.class) {
        args[i] = 0;
      } else if (type == long.class || type == Long.class) {
        args[i] = 0L;
      } else {
        args[i] = null;
      }
    }
    return args;
  }

  private Method resolveModeledEntityMethod() {
    return api.getOrCreateModeledEntity() != null ? api.getOrCreateModeledEntity() : api.createModeledEntity();
  }

  private void setBaseEntityVisible(Object modeledEntity, boolean visible) {
    if (modeledEntity == null || api.modeledSetBaseEntityVisible() == null) {
      return;
    }
    try {
      invokeNullable(api.modeledSetBaseEntityVisible(), modeledEntity, visible);
    } catch (Exception ex) {
      logDebug("setBaseEntityVisible failed: " + ex.getMessage());
    }
  }

  private Object invokeNullable(Method method, Object target, Object... args)
      throws IllegalAccessException, InvocationTargetException {
    if (method == null) {
      return null;
    }
    return method.invoke(target, args);
  }

  private Object invokeStaticNullable(Method method, Object... args)
      throws IllegalAccessException, InvocationTargetException {
    if (method == null) {
      return null;
    }
    return method.invoke(null, args);
  }

  private static ResolvedApi resolveApi() {
    try {
      Class<?> apiClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
      Method getOrCreateModeled = findStatic(apiClass, "getOrCreateModeledEntity", Entity.class);
      Method createModeled = findStatic(apiClass, "createModeledEntity", Entity.class);
      if (getOrCreateModeled == null && createModeled == null) {
        throw new IllegalStateException("ModelEngineAPI missing getOrCreateModeledEntity/createModeledEntity");
      }
      Method createActiveModel = findStatic(apiClass, "createActiveModel", String.class);
      if (createActiveModel == null) {
        throw new IllegalStateException("ModelEngineAPI missing createActiveModel(String)");
      }
      Method getBlueprint = findStatic(apiClass, "getBlueprint", String.class);

      Class<?> modeledClass = getOrCreateModeled != null ? getOrCreateModeled.getReturnType() : createModeled.getReturnType();
      Class<?> activeModelClass = createActiveModel.getReturnType();
      Method addModel = findAddModel(modeledClass, activeModelClass);
      if (addModel == null) {
        throw new IllegalStateException("ModeledEntity.addModel(activeModel, boolean) not found");
      }
      Method getModel = findMethod(modeledClass, "getModel", String.class);
      Method removeModel = findMethod(modeledClass, "removeModel", String.class);
      Method destroy = findNoArg(modeledClass, "destroy");
      Method setBaseEntityVisible = findMethod(modeledClass, "setBaseEntityVisible", boolean.class);
      if (setBaseEntityVisible == null) {
        setBaseEntityVisible = findMethod(modeledClass, "setBaseEntityVisible", Boolean.class);
      }
      Method getModels = findNoArg(modeledClass, "getModels");
      Method getAnimationHandler = findNoArg(activeModelClass, "getAnimationHandler");
      if (getAnimationHandler == null) {
        throw new IllegalStateException("ActiveModel.getAnimationHandler() not found");
      }
      Class<?> animationHandlerClass = getAnimationHandler.getReturnType();
      Method playAnimation = findPlayMethod(animationHandlerClass);
      if (playAnimation == null) {
        throw new IllegalStateException("AnimationHandler.playAnimation(...) not found");
      }
      Method forceStop = findNoArg(animationHandlerClass, "forceStopAllAnimations");
      return new ResolvedApi(
          getOrCreateModeled,
          createModeled,
          createActiveModel,
          getBlueprint,
          addModel,
          getModel,
          removeModel,
          destroy,
          setBaseEntityVisible,
          getModels,
          getAnimationHandler,
          playAnimation,
          forceStop);
    } catch (ClassNotFoundException ex) {
      throw new IllegalStateException("ModelEngine API not found: " + ex.getMessage(), ex);
    }
  }

  private static Method findAddModel(Class<?> modeledClass, Class<?> activeModelClass) {
    Method direct = findMethod(modeledClass, "addModel", activeModelClass, boolean.class);
    if (direct != null) {
      return direct;
    }
    direct = findMethod(modeledClass, "addModel", activeModelClass, Boolean.class);
    if (direct != null) {
      return direct;
    }
    for (Method method : modeledClass.getMethods()) {
      if (!method.getName().equals("addModel") || method.getParameterCount() != 2) {
        continue;
      }
      Class<?>[] params = method.getParameterTypes();
      if (!params[0].isAssignableFrom(activeModelClass) && !activeModelClass.isAssignableFrom(params[0])) {
        continue;
      }
      if (params[1] != boolean.class && params[1] != Boolean.class) {
        continue;
      }
      method.setAccessible(true);
      return method;
    }
    return null;
  }

  private static Method findPlayMethod(Class<?> animationHandlerClass) {
    Method best = null;
    for (Method method : animationHandlerClass.getMethods()) {
      if (!method.getName().equals("playAnimation")) {
        continue;
      }
      Class<?>[] params = method.getParameterTypes();
      if (params.length == 0 || params[0] != String.class) {
        continue;
      }
      if (best == null || method.getParameterCount() > best.getParameterCount()) {
        best = method;
      }
    }
    if (best != null) {
      best.setAccessible(true);
    }
    return best;
  }

  private static Method findStatic(Class<?> type, String name, Class<?>... params) {
    Method method = findMethod(type, name, params);
    if (method == null) {
      return null;
    }
    return Modifier.isStatic(method.getModifiers()) ? method : null;
  }

  private static Method findMethod(Class<?> type, String name, Class<?>... params) {
    try {
      Method method = type.getMethod(name, params);
      method.setAccessible(true);
      return method;
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  private static Method findNoArg(Class<?> type, String name) {
    return findMethod(type, name);
  }

  private void logWarn(String message) {
    if (logger != null) {
      logger.warn("[Mobs][ModelBridge] " + message);
    } else {
      Bukkit.getLogger().warning("[DungeonsReborn] [Mobs][ModelBridge] " + message);
    }
  }

  private void logWarn(String message, Throwable throwable) {
    if (logger != null) {
      logger.warn("[Mobs][ModelBridge] " + message, throwable);
      return;
    }
    Bukkit.getLogger().log(java.util.logging.Level.WARNING, "[DungeonsReborn] [Mobs][ModelBridge] " + message,
        throwable);
  }

  private void logDebug(String message) {
    if (!debug) {
      return;
    }
    if (logger != null) {
      logger.debug("[Mobs][ModelBridge] " + message);
    } else {
      Bukkit.getLogger().info("[DungeonsReborn] [Mobs][ModelBridge] " + message);
    }
  }
}
