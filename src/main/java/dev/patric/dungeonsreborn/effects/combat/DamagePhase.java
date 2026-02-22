package dev.patric.dungeonsreborn.effects.combat;

import dev.patric.dungeonsreborn.effects.CastContext;
import dev.patric.dungeonsreborn.effects.EffectsEngine;

public interface DamagePhase {
  String id();

  void apply(EffectsEngine engine, CastContext ctx, DamagePacket packet);
}

