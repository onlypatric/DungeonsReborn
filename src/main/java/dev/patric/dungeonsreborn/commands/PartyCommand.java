package dev.patric.dungeonsreborn.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.menus.PartyMenu;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.party.PartyService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

public final class PartyCommand {
  private PartyCommand() {
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createCommand(PartyService parties) {
    return Commands.literal("party")
        .executes(ctx -> open(ctx, parties))
        .then(Commands.literal("gui").executes(ctx -> open(ctx, parties)))
        .then(Commands.literal("create").executes(ctx -> create(ctx, parties)))
        .then(Commands.literal("invite")
            .then(Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestOnlinePlayers(builder))
                .executes(ctx -> invite(ctx, parties, StringArgumentType.getString(ctx, "player")))))
        .then(Commands.literal("accept")
            .executes(ctx -> accept(ctx, parties, null))
            .then(Commands.argument("leader", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestOnlinePlayers(builder))
                .executes(ctx -> accept(ctx, parties, StringArgumentType.getString(ctx, "leader")))))
        .then(Commands.literal("leave").executes(ctx -> leave(ctx, parties)))
        .then(Commands.literal("kick")
            .then(Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestOnlinePlayers(builder))
                .executes(ctx -> kick(ctx, parties, StringArgumentType.getString(ctx, "player")))))
        .then(Commands.literal("leader")
            .then(Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestOnlinePlayers(builder))
                .executes(ctx -> leader(ctx, parties, StringArgumentType.getString(ctx, "player")))))
        .then(Commands.literal("chat")
            .then(Commands.literal("on").executes(ctx -> chatToggle(ctx, parties, true)))
            .then(Commands.literal("off").executes(ctx -> chatToggle(ctx, parties, false))));
  }

  private static int open(CommandContext<CommandSourceStack> ctx, PartyService parties) {
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
    new PartyMenu(parties).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int create(CommandContext<CommandSourceStack> ctx, PartyService parties) {
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
    PartyService.Result result = parties.createParty(player);
    CommandMessages.sendResult(sender, result.success(), result.message());
    return Command.SINGLE_SUCCESS;
  }

  private static int invite(CommandContext<CommandSourceStack> ctx, PartyService parties, String targetName) {
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
    Player target = Bukkit.getPlayerExact(targetName);
    if (target == null) {
      CommandMessages.send(sender, "messages.command.targetNotFound");
      return Command.SINGLE_SUCCESS;
    }
    if (target.getUniqueId().equals(player.getUniqueId())) {
      CommandMessages.send(sender, "messages.command.party.inviteSelf");
      return Command.SINGLE_SUCCESS;
    }
    PartyService.InviteResult result = parties.invite(player, target);
    CommandMessages.sendResult(sender, result.success(), result.message());
    if (result.success() && result.invite() != null) {
      CommandMessages.send(target, "messages.command.party.inviteReceived",
          Locales.placeholders("leader", result.invite().leaderName()));
      CommandMessages.send(target, "messages.command.party.inviteHint",
          Locales.placeholders("leader", result.invite().leaderName()));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int accept(CommandContext<CommandSourceStack> ctx, PartyService parties, String leaderName) {
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
    PartyService.Result result = parties.acceptInvite(player, leaderName);
    CommandMessages.sendResult(sender, result.success(), result.message());
    return Command.SINGLE_SUCCESS;
  }

  private static int leave(CommandContext<CommandSourceStack> ctx, PartyService parties) {
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
    PartyService.Result result = parties.leave(player);
    CommandMessages.sendResult(sender, result.success(), result.message());
    return Command.SINGLE_SUCCESS;
  }

  private static int kick(CommandContext<CommandSourceStack> ctx, PartyService parties, String targetName) {
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
    Player target = Bukkit.getPlayerExact(targetName);
    if (target == null) {
      CommandMessages.send(sender, "messages.command.targetNotFound");
      return Command.SINGLE_SUCCESS;
    }
    PartyService.Result result = parties.kick(player, target);
    CommandMessages.sendResult(sender, result.success(), result.message());
    return Command.SINGLE_SUCCESS;
  }

  private static int leader(CommandContext<CommandSourceStack> ctx, PartyService parties, String targetName) {
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
    Player target = Bukkit.getPlayerExact(targetName);
    if (target == null) {
      CommandMessages.send(sender, "messages.command.targetNotFound");
      return Command.SINGLE_SUCCESS;
    }
    PartyService.Result result = parties.transferLeader(player, target);
    CommandMessages.sendResult(sender, result.success(), result.message());
    return Command.SINGLE_SUCCESS;
  }

  private static int chatToggle(CommandContext<CommandSourceStack> ctx, PartyService parties, boolean enabled) {
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
    PartyService.Result result = parties.toggleChat(player, enabled);
    CommandMessages.sendResult(sender, result.success(), result.message());
    return Command.SINGLE_SUCCESS;
  }

  private static CompletableFuture<Suggestions> suggestOnlinePlayers(SuggestionsBuilder builder) {
    for (Player player : Bukkit.getOnlinePlayers()) {
      builder.suggest(player.getName());
    }
    return builder.buildFuture();
  }
}
