package dev.patric.dungeonsreborn.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.dungeons.DungeonQueueService;
import dev.patric.dungeonsreborn.dungeons.DungeonSessionManager;
import dev.patric.dungeonsreborn.dungeons.DungeonYamlRegistry;
import dev.patric.dungeonsreborn.dungeons.menu.DungeonQueueMenu;
import dev.patric.dungeonsreborn.dungeons.menu.DungeonStatusMenu;
import dev.patric.dungeonsreborn.locale.Locales;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

public final class DungeonCommand {
  private DungeonCommand() {
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createCommand(DungeonYamlRegistry registry,
      DungeonQueueService queue, DungeonSessionManager sessions) {
    return Commands.literal("dungeon")
        .executes(ctx -> open(ctx, registry, queue))
        .then(Commands.literal("status")
            .executes(ctx -> openStatus(ctx, registry, queue, sessions)))
        .then(Commands.literal("queue")
            .then(Commands.literal("join")
                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                    .executes(ctx -> join(ctx, queue, IntegerArgumentType.getInteger(ctx, "level")))))
            .then(Commands.literal("leave").executes(ctx -> leave(ctx, queue)))
            .then(Commands.literal("status").executes(ctx -> status(ctx, queue))))
        .then(Commands.literal("reload").executes(ctx -> reload(ctx, registry, queue, sessions)))
        .then(Commands.literal("validate").executes(ctx -> validate(ctx, registry)))
        .then(Commands.literal("debug")
            .then(Commands.literal("start")
                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                    .executes(ctx -> debugStart(ctx, queue, IntegerArgumentType.getInteger(ctx, "level")))))
            .then(Commands.literal("skip").executes(ctx -> debugSkip(ctx, sessions)))
            .then(Commands.literal("end")
                .then(Commands.literal("win").executes(ctx -> debugEnd(ctx, sessions, true)))
                .then(Commands.literal("fail").executes(ctx -> debugEnd(ctx, sessions, false)))
                .executes(ctx -> debugEnd(ctx, sessions, false))))
        .then(Commands.literal("gui").executes(ctx -> open(ctx, registry, queue)));
  }

  private static int open(CommandContext<CommandSourceStack> ctx, DungeonYamlRegistry registry, DungeonQueueService queue) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (registry == null || queue == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.dungeons")));
      return Command.SINGLE_SUCCESS;
    }
    new DungeonQueueMenu(registry, queue).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int openStatus(CommandContext<CommandSourceStack> ctx, DungeonYamlRegistry registry,
      DungeonQueueService queue, DungeonSessionManager sessions) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (registry == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.dungeons")));
      return Command.SINGLE_SUCCESS;
    }
    new DungeonStatusMenu(registry, queue, sessions).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int join(CommandContext<CommandSourceStack> ctx, DungeonQueueService queue, int level) {
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

  private static int status(CommandContext<CommandSourceStack> ctx, DungeonQueueService queue) {
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
}
