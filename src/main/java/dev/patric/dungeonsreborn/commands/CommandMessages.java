package dev.patric.dungeonsreborn.commands;

import java.util.Map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

final class CommandMessages {
  private CommandMessages() {
  }

  static void sendClosestMatch(CommandSender sender, String input, java.util.Collection<String> options) {
    String suggestion = closestMatch(input, options);
    if (suggestion == null) {
      return;
    }
    send(sender, "messages.command.closestMatch", Locales.placeholders("suggestion", suggestion));
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
    if (message == null) {
      sendResult(sender, success, "");
      return;
    }
    String rendered = MiniMessage.miniMessage().serialize(message);
    sendResult(sender, success, rendered);
  }

  private static String closestMatch(String input, java.util.Collection<String> options) {
    if (input == null || input.isBlank() || options == null || options.isEmpty()) {
      return null;
    }
    String needle = input.toLowerCase(java.util.Locale.ROOT);
    int bestScore = Integer.MAX_VALUE;
    String best = null;
    for (String option : options) {
      if (option == null || option.isBlank()) {
        continue;
      }
      int score = levenshtein(needle, option.toLowerCase(java.util.Locale.ROOT));
      if (score < bestScore) {
        bestScore = score;
        best = option;
      }
    }
    if (best == null) {
      return null;
    }
    int threshold = Math.max(2, needle.length() / 2);
    return bestScore <= threshold ? best : null;
  }

  private static int levenshtein(String a, String b) {
    int alen = a.length();
    int blen = b.length();
    int[] prev = new int[blen + 1];
    int[] curr = new int[blen + 1];
    for (int j = 0; j <= blen; j++) {
      prev[j] = j;
    }
    for (int i = 1; i <= alen; i++) {
      curr[0] = i;
      char ca = a.charAt(i - 1);
      for (int j = 1; j <= blen; j++) {
        int cost = ca == b.charAt(j - 1) ? 0 : 1;
        curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
      }
      int[] swap = prev;
      prev = curr;
      curr = swap;
    }
    return prev[blen];
  }
}
