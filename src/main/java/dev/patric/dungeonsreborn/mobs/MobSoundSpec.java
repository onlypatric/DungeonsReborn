package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;

public record MobSoundSpec(Sound sound, float volume, float pitch) {
  public MobSoundSpec {
    Objects.requireNonNull(sound, "sound");
    if (volume <= 0.0f) {
      throw new IllegalArgumentException("volume must be > 0");
    }
    if (pitch <= 0.0f) {
      throw new IllegalArgumentException("pitch must be > 0");
    }
  }

  public void play(Location location) {
    if (location == null || location.getWorld() == null) {
      return;
    }
    World world = location.getWorld();
    world.playSound(location, sound, volume, pitch);
  }
}
