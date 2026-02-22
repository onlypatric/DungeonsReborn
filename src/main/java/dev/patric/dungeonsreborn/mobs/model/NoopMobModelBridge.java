package dev.patric.dungeonsreborn.mobs.model;

import org.bukkit.entity.LivingEntity;

public final class NoopMobModelBridge implements MobModelBridge {
  @Override
  public boolean available() {
    return false;
  }

  @Override
  public boolean attach(LivingEntity entity, ModelRuntimeSpec spec) {
    return false;
  }

  @Override
  public void update(LivingEntity entity, ModelRuntimeSpec spec) {
  }

  @Override
  public void play(LivingEntity entity, String animationKey) {
  }

  @Override
  public void detach(LivingEntity entity) {
  }
}
