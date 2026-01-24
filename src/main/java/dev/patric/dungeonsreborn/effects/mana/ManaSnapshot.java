package dev.patric.dungeonsreborn.effects.mana;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;

public record ManaSnapshot(Map<String, ResourceStateSnapshot> resources) {
  public ManaSnapshot {
    resources = Map.copyOf(resources);
  }

  public static ManaSnapshot from(SessionManaProvider provider, Player player) {
    Objects.requireNonNull(provider, "provider");
    Objects.requireNonNull(player, "player");
    Map<String, ResourceStateSnapshot> map = new LinkedHashMap<>();
    for (String resourceId : provider.resourceIds()) {
      map.put(resourceId, new ResourceStateSnapshot(
          provider.get(player, resourceId),
          provider.baseMax(player, resourceId),
          provider.maxBonus(player, resourceId),
          provider.regenBonus(player, resourceId),
          provider.classMaxBonus(player, resourceId),
          provider.classRegenBonus(player, resourceId)));
    }
    return new ManaSnapshot(map);
  }

  public void apply(SessionManaProvider provider, Player player) {
    Objects.requireNonNull(provider, "provider");
    Objects.requireNonNull(player, "player");
    for (Map.Entry<String, ResourceStateSnapshot> entry : resources.entrySet()) {
      String resourceId = entry.getKey();
      ResourceStateSnapshot snapshot = entry.getValue();
      if (snapshot.baseMax() > 0.0) {
        provider.setMax(player, resourceId, snapshot.baseMax());
      }
      provider.setMaxBonus(player, resourceId, snapshot.maxBonus());
      provider.setRegenBonus(player, resourceId, snapshot.regenBonus());
      provider.setClassMaxBonus(player, resourceId, snapshot.classMaxBonus());
      provider.setClassRegenBonus(player, resourceId, snapshot.classRegenBonus());
      provider.set(player, resourceId, snapshot.current());
    }
  }

  public record ResourceStateSnapshot(
      double current,
      double baseMax,
      double maxBonus,
      double regenBonus,
      double classMaxBonus,
      double classRegenBonus
  ) {
    public ResourceStateSnapshot {
      if (!Double.isFinite(current) || !Double.isFinite(baseMax) || !Double.isFinite(maxBonus)
          || !Double.isFinite(regenBonus) || !Double.isFinite(classMaxBonus)
          || !Double.isFinite(classRegenBonus)) {
        throw new IllegalArgumentException("All resource snapshot values must be finite.");
      }
    }
  }
}
