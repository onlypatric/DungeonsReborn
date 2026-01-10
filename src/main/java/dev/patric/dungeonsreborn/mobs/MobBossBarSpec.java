package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

public record MobBossBarSpec(Component title, BossBar.Color color, BossBar.Overlay overlay,
    MobBossBarAudience audience) {
  public MobBossBarSpec {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(color, "color");
    Objects.requireNonNull(overlay, "overlay");
    Objects.requireNonNull(audience, "audience");
  }

  public static MobBossBarSpec of(Component title) {
    return new MobBossBarSpec(title, BossBar.Color.RED, BossBar.Overlay.PROGRESS, MobBossBarAudience.ALL_PLAYERS);
  }
}
