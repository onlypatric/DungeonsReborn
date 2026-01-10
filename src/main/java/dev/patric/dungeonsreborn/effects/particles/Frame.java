package dev.patric.dungeonsreborn.effects.particles;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import dev.patric.dungeonsreborn.effects.CastContext;

public interface Frame {
  Location location(CastContext ctx);

  Vector direction(CastContext ctx);
}

