package dev.patric.dungeonsreborn.mobs.model;

import org.bukkit.entity.LivingEntity;

public interface MobModelBridge {
  boolean available();

  boolean attach(LivingEntity entity, ModelRuntimeSpec spec);

  void update(LivingEntity entity, ModelRuntimeSpec spec);

  void play(LivingEntity entity, String animationKey);

  void detach(LivingEntity entity);

  default int activeCount() {
    return 0;
  }

  default String providerKey() {
    return "none";
  }
}
