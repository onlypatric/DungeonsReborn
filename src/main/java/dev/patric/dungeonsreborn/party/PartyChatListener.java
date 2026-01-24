package dev.patric.dungeonsreborn.party;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.plugin.Plugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class PartyChatListener implements Listener {
  private final Plugin plugin;
  private final PartyService parties;

  public PartyChatListener(Plugin plugin, PartyService parties) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.parties = Objects.requireNonNull(parties, "parties");
  }

  @EventHandler
  public void onChat(AsyncChatEvent event) {
    Player player = event.getPlayer();
    if (!parties.isChatEnabled(player)) {
      return;
    }
    String message = PlainTextComponentSerializer.plainText().serialize(event.message());
    if (message.startsWith("!")) {
      String trimmed = message.substring(1).trim();
      if (trimmed.isEmpty()) {
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
          PartyService.Result result = parties.sendChat(player, "");
          if (!result.success()) {
            player.sendMessage(result.message());
          }
        });
        return;
      }
      event.message(Component.text(trimmed));
      return;
    }
    event.setCancelled(true);
    Bukkit.getScheduler().runTask(plugin, () -> {
      PartyService.Result result = parties.sendChat(player, message);
      if (!result.success()) {
        player.sendMessage(result.message());
      }
    });
  }
}
