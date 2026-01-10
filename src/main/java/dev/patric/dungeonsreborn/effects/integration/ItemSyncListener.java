package dev.patric.dungeonsreborn.effects.integration;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;

public final class ItemSyncListener implements Listener {
  private final EffectsYamlAbilities yaml;

  public ItemSyncListener(EffectsYamlAbilities yaml) {
    this.yaml = Objects.requireNonNull(yaml, "yaml");
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    yaml.syncPlayerItems(event.getPlayer());
  }
}
