package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

import net.kyori.adventure.text.Component;

public record MobStyleSpec(Component nameplate, Boolean showName, MobBossBarSpec bossBar) {
  public MobStyleSpec {
    if (nameplate != null) {
      Objects.requireNonNull(nameplate, "nameplate");
    }
  }
}
