package dev.patric.dungeonsreborn.util;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.configuration.Configuration;

public final class WorldAllowlist {
  private final Set<String> worlds;
  private final boolean allowAll;

  private WorldAllowlist(Set<String> worlds) {
    this.worlds = Set.copyOf(worlds);
    this.allowAll = this.worlds.isEmpty();
  }

  public static WorldAllowlist fromConfig(Configuration config) {
    Objects.requireNonNull(config, "config");
    List<String> raw = config.getStringList("worlds.allowlist");
    Set<String> worlds = new HashSet<>();
    for (String entry : raw) {
      if (entry == null) {
        continue;
      }
      String trimmed = entry.trim();
      if (!trimmed.isEmpty()) {
        worlds.add(trimmed);
      }
    }
    return new WorldAllowlist(worlds);
  }

  public boolean allowAll() {
    return allowAll;
  }

  public Set<String> worlds() {
    return worlds;
  }

  public boolean isAllowed(World world) {
    if (world == null) {
      return false;
    }
    if (allowAll) {
      return true;
    }
    String name = world.getName();
    String key = world.getKey().toString();
    return worlds.contains(name) || worlds.contains(key);
  }

  public boolean isAllowed(String worldName) {
    if (worldName == null || worldName.isBlank()) {
      return false;
    }
    return allowAll || worlds.contains(worldName);
  }
}
