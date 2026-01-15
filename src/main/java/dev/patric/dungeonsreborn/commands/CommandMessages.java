package dev.patric.dungeonsreborn.commands;

import java.util.Map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

final class CommandMessages {
  private CommandMessages() {
  }

  static void send(CommandSender sender, String key) {
    send(sender, key, Map.of());
  }

  static void send(CommandSender sender, String key, Map<String, String> placeholders) {
    Player player = sender instanceof Player typed ? typed : null;
    sender.sendMessage(Locales.component(player, key, placeholders));
  }

  static String text(CommandSender sender, String key) {
    Player player = sender instanceof Player typed ? typed : null;
    return Locales.text(player, key);
  }

  static void sendResult(CommandSender sender, boolean success, String message) {
    String key = success ? "messages.command.result.success" : "messages.command.result.error";
    send(sender, key, Locales.placeholders("message", message));
  }

  static void sendResult(CommandSender sender, boolean success, Component message) {
    String rendered = PlainTextComponentSerializer.plainText().serialize(message);
    sendResult(sender, success, rendered);
  }
}
