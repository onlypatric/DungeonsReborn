package dev.patric.dungeonsreborn.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.quests.QuestGiverYamlRegistry;
import dev.patric.dungeonsreborn.quests.QuestService;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.party.PartyService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class QuestsCommand {
  private QuestsCommand() {
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createUserCommand(QuestService quests,
      QuestGiverYamlRegistry givers, PartyService parties) {
    return createCommand(quests, givers, parties, false);
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createAdminCommand(QuestService quests,
      QuestGiverYamlRegistry givers, PartyService parties) {
    return createCommand(quests, givers, parties, true);
  }

  private static LiteralArgumentBuilder<CommandSourceStack> createCommand(QuestService quests,
      QuestGiverYamlRegistry givers, PartyService parties, boolean includeAdmin) {
    var builder = Commands.literal("quests")
        .then(Commands.literal("accept")
            .then(Commands.argument("id", StringArgumentType.word())
                .suggests((ctx, builderSuggestion) -> suggestQuests(quests, builderSuggestion))
                .executes(ctx -> accept(ctx, quests, parties, StringArgumentType.getString(ctx, "id")))))
        .then(Commands.argument("id", StringArgumentType.word())
            .suggests((ctx, builderSuggestion) -> suggestQuests(quests, builderSuggestion))
            .executes(ctx -> accept(ctx, quests, parties, StringArgumentType.getString(ctx, "id"))));
    if (includeAdmin) {
      builder.then(Commands.literal("reload").executes(ctx -> reload(ctx, quests, givers)));
    }
    return builder;
  }

  private static int accept(CommandContext<CommandSourceStack> ctx, QuestService quests, PartyService parties, String questId) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (quests == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.quests")));
      return Command.SINGLE_SUCCESS;
    }
    if (parties != null && parties.partyOf(player) != null) {
      CommandMessages.send(sender, "messages.command.quests.partyAcceptGiver");
      return Command.SINGLE_SUCCESS;
    }
    if (quests.registry().quest(questId) == null) {
      CommandMessages.send(sender, "messages.quests.accept.unknown",
          Locales.placeholders("id", questId));
      CommandMessages.sendClosestMatch(sender, questId, quests.registry().quests().keySet());
      return Command.SINGLE_SUCCESS;
    }
    QuestService.QuestAcceptResult result = quests.accept(player, questId);
    String message = PlainTextComponentSerializer.plainText().serialize(result.message());
    CommandMessages.sendResult(sender, result.success(), message);
    return Command.SINGLE_SUCCESS;
  }

  private static int reload(CommandContext<CommandSourceStack> ctx, QuestService quests, QuestGiverYamlRegistry givers) {
    var sender = ctx.getSource().getSender();
    if (quests == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.quests")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.quests.reload")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.quests.reload"));
      return Command.SINGLE_SUCCESS;
    }
    var result = quests.registry().reload();
    CommandMessages.send(sender, "messages.command.reload.file",
        Locales.placeholders("path", quests.registry().file().getPath()));
    int giverCount = 0;
    int giverErrors = 0;
    if (givers != null) {
      var giverResult = givers.reload();
      CommandMessages.send(sender, "messages.command.reload.questsGiversFile",
          Locales.placeholders("path", givers.file().getPath()));
      giverCount = giverResult.loaded();
      giverErrors = giverResult.errors().size();
    }
    CommandMessages.send(sender, "messages.command.reload.questsSummary",
        Locales.placeholders("loaded", result.loaded(),
            "errors", result.errors().size(),
            "givers", giverCount,
            "giverErrors", giverErrors));
    return Command.SINGLE_SUCCESS;
  }

  private static CompletableFuture<Suggestions> suggestQuests(QuestService quests, SuggestionsBuilder builder) {
    if (quests == null) {
      return builder.buildFuture();
    }
    for (String id : quests.registry().quests().keySet()) {
      builder.suggest(id);
    }
    return builder.buildFuture();
  }

}
