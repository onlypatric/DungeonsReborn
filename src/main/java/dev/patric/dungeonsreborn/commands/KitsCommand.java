package dev.patric.dungeonsreborn.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.kits.KitService;
import dev.patric.dungeonsreborn.kits.KitSpec;
import dev.patric.dungeonsreborn.locale.Locales;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

public final class KitsCommand {
  private KitsCommand() {
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createCommand(KitService kits) {
    return Commands.literal("kits")
        .executes(ctx -> claimDefault(ctx, kits))
        .then(Commands.literal("list").executes(ctx -> list(ctx, kits)))
        .then(Commands.literal("reload").executes(ctx -> reload(ctx, kits)))
        .then(Commands.argument("id", StringArgumentType.word())
            .suggests((ctx, builder) -> suggestKits(kits, builder))
            .executes(ctx -> claim(ctx, kits, StringArgumentType.getString(ctx, "id"))));
  }

  private static int claimDefault(CommandContext<CommandSourceStack> ctx, KitService kits) {
    var sender = ctx.getSource().getSender();
    if (kits == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.kits")));
      return Command.SINGLE_SUCCESS;
    }
    if (!(ctx.getSource().getExecutor() instanceof Player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    KitSpec starter = kits.registry().kit("starter");
    if (starter != null) {
      return claim(ctx, kits, starter.id());
    }
    if (kits.registry().kits().size() == 1) {
      KitSpec only = kits.registry().kits().values().iterator().next();
      return claim(ctx, kits, only.id());
    }
    return list(ctx, kits);
  }

  private static int claim(CommandContext<CommandSourceStack> ctx, KitService kits, String id) {
    var sender = ctx.getSource().getSender();
    if (kits == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.kits")));
      return Command.SINGLE_SUCCESS;
    }
    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    KitService.ClaimResult result = kits.claim(player, id);
    CommandMessages.sendResult(sender, result.success(), result.message());
    return Command.SINGLE_SUCCESS;
  }

  private static int list(CommandContext<CommandSourceStack> ctx, KitService kits) {
    var sender = ctx.getSource().getSender();
    if (kits == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.kits")));
      return Command.SINGLE_SUCCESS;
    }
    CommandMessages.send(sender, "messages.command.kits.list.header");
    for (KitSpec kit : kits.registry().kits().values()) {
      CommandMessages.send(sender, "messages.command.kits.list.entry",
          Locales.placeholders("id", kit.id(), "title", kit.title()));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int reload(CommandContext<CommandSourceStack> ctx, KitService kits) {
    var sender = ctx.getSource().getSender();
    if (kits == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.kits")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.kits.reload")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.kits.reload"));
      return Command.SINGLE_SUCCESS;
    }
    var result = kits.registry().reload();
    CommandMessages.send(sender, "messages.command.reload.file",
        Locales.placeholders("path", kits.registry().file().getPath()));
    CommandMessages.send(sender, "messages.command.reload.kitsSummary",
        Locales.placeholders("loaded", result.loaded(), "errors", result.errors().size()));
    return Command.SINGLE_SUCCESS;
  }

  private static CompletableFuture<Suggestions> suggestKits(KitService kits, SuggestionsBuilder builder) {
    if (kits == null) {
      return builder.buildFuture();
    }
    for (String id : kits.registry().kits().keySet()) {
      builder.suggest(id);
    }
    return builder.buildFuture();
  }
}
