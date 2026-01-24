package dev.patric.dungeonsreborn.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.dungeons.DungeonQueueService;
import dev.patric.dungeonsreborn.dungeons.DungeonSessionManager;
import dev.patric.dungeonsreborn.dungeons.DungeonYamlRegistry;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.party.Party;
import dev.patric.dungeonsreborn.party.PartyService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

public final class DungeonCommand {
  private DungeonCommand() {
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createUserCommand(DungeonYamlRegistry registry,
      DungeonQueueService queue, DungeonSessionManager sessions, PartyService parties) {
    return createCommand(registry, queue, sessions, parties, false);
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createAdminCommand(DungeonYamlRegistry registry,
      DungeonQueueService queue, DungeonSessionManager sessions, PartyService parties) {
    return createCommand(registry, queue, sessions, parties, true);
  }

  private static LiteralArgumentBuilder<CommandSourceStack> createCommand(DungeonYamlRegistry registry,
      DungeonQueueService queue, DungeonSessionManager sessions, PartyService parties, boolean includeAdmin) {
    var builder = Commands.literal("dungeon")
        .then(Commands.literal("queue")
            .then(Commands.literal("join")
                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                    .suggests((ctx, suggestionsBuilder) -> suggestLevels(registry, suggestionsBuilder))
                    .executes(ctx -> join(ctx, queue, parties, IntegerArgumentType.getInteger(ctx, "level")))))
            .then(Commands.literal("leave").executes(ctx -> leave(ctx, queue)))
            .then(Commands.literal("status").executes(ctx -> status(ctx, registry, queue))));
    if (includeAdmin) {
      builder.then(Commands.literal("reload").executes(ctx -> reload(ctx, registry, queue, sessions)));
      builder.then(Commands.literal("validate").executes(ctx -> validate(ctx, registry)));
      builder.then(Commands.literal("debug")
          .then(Commands.literal("start")
              .then(Commands.argument("level", IntegerArgumentType.integer(1))
                  .suggests((ctx, suggestionsBuilder) -> suggestLevels(registry, suggestionsBuilder))
                  .executes(ctx -> debugStart(ctx, queue, IntegerArgumentType.getInteger(ctx, "level")))))
          .then(Commands.literal("skip").executes(ctx -> debugSkip(ctx, sessions)))
          .then(Commands.literal("end")
              .then(Commands.literal("win").executes(ctx -> debugEnd(ctx, sessions, true)))
              .then(Commands.literal("fail").executes(ctx -> debugEnd(ctx, sessions, false)))
              .executes(ctx -> debugEnd(ctx, sessions, false))));
    }
    return builder;
  }

  private static int join(CommandContext<CommandSourceStack> ctx, DungeonQueueService queue, PartyService parties,
      int level) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (queue == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.dungeons")));
      return Command.SINGLE_SUCCESS;
    }
    Party party = parties == null ? null : parties.partyOf(player);
    if (party != null && party.leader() != null && party.leader().equals(player.getUniqueId())) {
      java.util.List<Player> members = new java.util.ArrayList<>();
      for (java.util.UUID memberId : party.members()) {
        Player member = org.bukkit.Bukkit.getPlayer(memberId);
        if (member != null) {
          members.add(member);
        }
      }
      var result = queue.joinParty(player, members, level);
      if (result.success()) {
        for (Player member : members) {
          member.sendMessage(Locales.component(member, "messages.dungeons.queue.result.queued",
              Locales.placeholders("level", level)));
        }
      } else {
        player.sendMessage(result.message());
      }
      return Command.SINGLE_SUCCESS;
    }
    var result = queue.join(player, level);
    player.sendMessage(result.message());
    return Command.SINGLE_SUCCESS;
  }

  private static int leave(CommandContext<CommandSourceStack> ctx, DungeonQueueService queue) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (queue == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.dungeons")));
      return Command.SINGLE_SUCCESS;
    }
    var result = queue.leave(player);
    player.sendMessage(result.message());
    return Command.SINGLE_SUCCESS;
  }

  private static int status(CommandContext<CommandSourceStack> ctx, DungeonYamlRegistry registry,
      DungeonQueueService queue) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (queue == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.dungeons")));
      return Command.SINGLE_SUCCESS;
    }
    if (!isConfigured(sender, registry)) {
      return Command.SINGLE_SUCCESS;
    }
    var status = queue.status(player.getUniqueId());
    if (status.active()) {
      CommandMessages.send(player, "messages.command.dungeons.active",
          Locales.placeholders("level", status.level()));
      return Command.SINGLE_SUCCESS;
    }
    if (!status.queued()) {
      CommandMessages.send(player, "messages.command.dungeons.notQueued");
      return Command.SINGLE_SUCCESS;
    }
    CommandMessages.send(player, "messages.command.dungeons.queued",
        Locales.placeholders("level", status.level(),
            "position", status.position(),
            "total", status.totalInLevel()));
    return Command.SINGLE_SUCCESS;
  }

  private static int reload(CommandContext<CommandSourceStack> ctx, DungeonYamlRegistry registry,
      DungeonQueueService queue, DungeonSessionManager sessions) {
    var sender = ctx.getSource().getSender();
    if (registry == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.dungeons")));
      return Command.SINGLE_SUCCESS;
    }
    var result = registry.reload();
    if (sessions != null) {
      sessions.abortActive(Locales.component(sender instanceof Player player ? player : null,
          "messages.command.dungeons.abortedReload"));
    }
    if (queue != null) {
      queue.clearQueues();
    }
    CommandMessages.send(sender, "messages.command.reload.file",
        Locales.placeholders("path", registry.file().getPath()));
    CommandMessages.send(sender, "messages.command.reload.dungeonsSummary",
        Locales.placeholders("loaded", result.loaded() ? "configured" : "none",
            "errors", result.errors().size()));
    return Command.SINGLE_SUCCESS;
  }

  private static int validate(CommandContext<CommandSourceStack> ctx, DungeonYamlRegistry registry) {
    var sender = ctx.getSource().getSender();
    if (registry == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.dungeons")));
      return Command.SINGLE_SUCCESS;
    }
    var errors = registry.lastErrors();
    if (errors == null || errors.isEmpty()) {
      CommandMessages.send(sender, "messages.command.dungeons.validate.ok");
      return Command.SINGLE_SUCCESS;
    }
    CommandMessages.send(sender, "messages.command.dungeons.validate.errors",
        Locales.placeholders("count", errors.size()));
    for (String error : errors) {
      CommandMessages.send(sender, "messages.command.dungeons.validate.entry",
          Locales.placeholders("error", error));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int debugStart(CommandContext<CommandSourceStack> ctx, DungeonQueueService queue, int level) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (queue == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.dungeons")));
      return Command.SINGLE_SUCCESS;
    }
    if (!queue.debugStart(player, level)) {
      CommandMessages.send(sender, "messages.command.dungeons.debugStartFailed");
      return Command.SINGLE_SUCCESS;
    }
    CommandMessages.send(sender, "messages.command.dungeons.debugStart",
        Locales.placeholders("level", level));
    return Command.SINGLE_SUCCESS;
  }

  private static int debugSkip(CommandContext<CommandSourceStack> ctx, DungeonSessionManager sessions) {
    var sender = ctx.getSource().getSender();
    if (sessions == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.dungeons")));
      return Command.SINGLE_SUCCESS;
    }
    if (!sessions.debugSkipWave()) {
      CommandMessages.send(sender, "messages.command.dungeons.noActiveSession");
      return Command.SINGLE_SUCCESS;
    }
    CommandMessages.send(sender, "messages.command.dungeons.debugSkip");
    return Command.SINGLE_SUCCESS;
  }

  private static int debugEnd(CommandContext<CommandSourceStack> ctx, DungeonSessionManager sessions, boolean success) {
    var sender = ctx.getSource().getSender();
    if (sessions == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.dungeons")));
      return Command.SINGLE_SUCCESS;
    }
    if (!sessions.debugEnd(success)) {
      CommandMessages.send(sender, "messages.command.dungeons.noActiveSession");
      return Command.SINGLE_SUCCESS;
    }
    CommandMessages.send(sender, "messages.command.dungeons.debugEnd",
        Locales.placeholders("result", success ? "win" : "fail"));
    return Command.SINGLE_SUCCESS;
  }

  private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestLevels(
      DungeonYamlRegistry registry,
      com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
    if (registry == null) {
      return builder.buildFuture();
    }
    for (Integer level : registry.levelIds()) {
      builder.suggest(String.valueOf(level));
    }
    return builder.buildFuture();
  }

  private static boolean isConfigured(org.bukkit.command.CommandSender sender, DungeonYamlRegistry registry) {
    if (registry == null || registry.dungeon() == null) {
      CommandMessages.send(sender, "messages.dungeons.unavailable");
      return false;
    }
    if (registry.levelIds().isEmpty()) {
      CommandMessages.send(sender, "messages.dungeons.unavailable");
      return false;
    }
    return true;
  }
}
