package dev.patric.dungeonsreborn.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.party.PartyService;
import dev.patric.dungeonsreborn.locale.Locales;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

public final class ChatCommand {
  private ChatCommand() {
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createCommand(PartyService parties) {
    return Commands.literal("chat")
        .then(Commands.argument("message", StringArgumentType.greedyString())
            .executes(ctx -> send(ctx, parties, StringArgumentType.getString(ctx, "message"))));
  }

  private static int send(CommandContext<CommandSourceStack> ctx, PartyService parties, String message) {
    var sender = ctx.getSource().getSender();
    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (parties == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.party")));
      return Command.SINGLE_SUCCESS;
    }
    PartyService.Result result = parties.sendChat(player, message);
    if (!result.success()) {
      CommandMessages.sendResult(sender, false, result.message());
    }
    return Command.SINGLE_SUCCESS;
  }
}
