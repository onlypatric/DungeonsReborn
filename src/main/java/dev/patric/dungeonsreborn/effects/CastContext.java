package dev.patric.dungeonsreborn.effects;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.bukkit.plugin.java.JavaPlugin;

public record CastContext(
    EffectsEngine engine,
    JavaPlugin plugin,
    UUID castId,
    String abilityId,
    long tick,
    CastState state,
    LivingEntity caster,
    Location origin,
    Vector direction,
    ItemStack itemInHand) {

  public CastContext {
    Objects.requireNonNull(engine, "engine");
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(castId, "castId");
    Objects.requireNonNull(abilityId, "abilityId");
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(caster, "caster");
    Objects.requireNonNull(origin, "origin");
    Objects.requireNonNull(direction, "direction");
    itemInHand = itemInHand == null ? null : itemInHand.clone();
  }

  public World world() {
    return origin.getWorld();
  }

  @Override
  public ItemStack itemInHand() {
    return itemInHand == null ? null : itemInHand.clone();
  }

  public java.util.Map<String, Object> variables() {
    return state.variables();
  }

  public java.util.Random rng() {
    return state.rng();
  }

  public EffectsEngine.TimelineHandle startTimeline(String id, long durationTicks, long periodTicks) {
    return engine.startTimeline(id, durationTicks, periodTicks);
  }

  public EffectsEngine.TimelineHandle timeline(String id) {
    return engine.timeline(id);
  }

  public EffectsEngine.TimelineHandle subscribeTimeline(String id, java.util.function.BiConsumer<CastContext, Long> listener) {
    EffectsEngine.TimelineHandle handle = engine.timeline(id);
    if (handle == null) {
      return null;
    }
    handle.subscribe(tick -> listener.accept(this, tick));
    return handle;
  }
}
