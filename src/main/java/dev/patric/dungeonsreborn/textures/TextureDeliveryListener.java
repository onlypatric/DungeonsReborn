package dev.patric.dungeonsreborn.textures;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class TextureDeliveryListener implements Listener {
  private final TextureService textures;

  public TextureDeliveryListener(TextureService textures) {
    this.textures = Objects.requireNonNull(textures, "textures");
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    textures.sendConfiguredPack(event.getPlayer());
  }
}
