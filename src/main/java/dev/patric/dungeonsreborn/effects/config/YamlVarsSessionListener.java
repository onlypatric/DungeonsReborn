package dev.patric.dungeonsreborn.effects.config;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class YamlVarsSessionListener implements Listener {
  private final EffectsYamlAbilities yaml;

  public YamlVarsSessionListener(EffectsYamlAbilities yaml) {
    this.yaml = Objects.requireNonNull(yaml, "yaml");
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    yaml.clearPlayerVars(event.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onKick(PlayerKickEvent event) {
    yaml.clearPlayerVars(event.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onDeath(EntityDeathEvent event) {
    yaml.clearEntityVars(event.getEntity().getUniqueId());
  }
}
