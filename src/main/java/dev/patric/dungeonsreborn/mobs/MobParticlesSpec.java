package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

public record MobParticlesSpec(Particle particle, int count, double offsetX, double offsetY, double offsetZ, double extra) {
  public MobParticlesSpec {
    Objects.requireNonNull(particle, "particle");
    if (count <= 0) {
      throw new IllegalArgumentException("count must be > 0");
    }
    if (offsetX < 0 || offsetY < 0 || offsetZ < 0) {
      throw new IllegalArgumentException("offsets must be >= 0");
    }
  }

  public void spawn(Location location) {
    if (location == null || location.getWorld() == null) {
      return;
    }
    World world = location.getWorld();
    world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
  }
}
