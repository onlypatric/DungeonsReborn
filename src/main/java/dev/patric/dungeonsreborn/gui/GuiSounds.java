package dev.patric.dungeonsreborn.gui;

import java.util.Objects;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Small sound helpers for GUI feedback.
 */
public final class GuiSounds {
  private GuiSounds() {
  }

  public static void play(Player player, Sound sound, float volume, float pitch) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(sound, "sound");
    player.playSound(player.getLocation(), sound, volume, pitch);
  }

  public static void click(Player player) {
    play(player, Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
  }

  public static void success(Player player) {
    play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
  }

  public static void error(Player player) {
    play(player, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
  }

  public static void open(Player player) {
    play(player, Sound.BLOCK_CHEST_OPEN, 0.6f, 1.0f);
  }

  public static void close(Player player) {
    play(player, Sound.BLOCK_CHEST_CLOSE, 0.6f, 1.0f);
  }
}

