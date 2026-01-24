package dev.patric.dungeonsreborn.classes;

import java.util.List;
import java.util.Locale;

import org.bukkit.Location;

import dev.patric.dungeonsreborn.quests.QuestRegion;

public record ClassConditionalBonusSpec(List<String> worlds, List<QuestRegion> regions, ClassBonusSpec bonuses) {
  public boolean matches(Location location) {
    if (location == null || location.getWorld() == null) {
      return false;
    }
    if (worlds != null && !worlds.isEmpty()) {
      String name = location.getWorld().getName().toLowerCase(Locale.ROOT);
      String key = location.getWorld().getKey().toString().toLowerCase(Locale.ROOT);
      for (String world : worlds) {
        if (world == null || world.isBlank()) {
          continue;
        }
        String normalized = world.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals(name) || normalized.equals(key)) {
          return true;
        }
      }
    }
    if (regions != null && !regions.isEmpty()) {
      for (QuestRegion region : regions) {
        if (region != null && region.contains(location)) {
          return true;
        }
      }
    }
    return false;
  }

  public ClassBonusSpec bonusesOrEmpty() {
    return bonuses == null ? ClassBonusSpec.empty() : bonuses;
  }
}
