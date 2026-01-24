package dev.patric.dungeonsreborn.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.party.Party;
import dev.patric.dungeonsreborn.party.PartyService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

public final class PartyCommand {
  private PartyCommand() {
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createCommand(PartyService parties) {
    return Commands.literal("party")
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
        .then(Commands.literal("promote")
            .then(Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestOnlinePlayers(builder))
                .executes(ctx -> promote(ctx, parties, StringArgumentType.getString(ctx, "player")))))
        .then(Commands.literal("demote")
            .then(Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestOnlinePlayers(builder))
                .executes(ctx -> demote(ctx, parties, StringArgumentType.getString(ctx, "player")))))
        .then(Commands.literal("public")
            .then(Commands.literal("on").executes(ctx -> setPublic(ctx, parties, true)))
            .then(Commands.literal("off").executes(ctx -> setPublic(ctx, parties, false))))
        .then(Commands.literal("request")
            .then(Commands.argument("leader", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestOnlinePlayers(builder))
                .executes(ctx -> requestJoin(ctx, parties, StringArgumentType.getString(ctx, "leader"))))
            .then(Commands.literal("accept")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestOnlinePlayers(builder))
                    .executes(ctx -> acceptRequest(ctx, parties, StringArgumentType.getString(ctx, "player")))))
            .then(Commands.literal("deny")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestOnlinePlayers(builder))
                    .executes(ctx -> denyRequest(ctx, parties, StringArgumentType.getString(ctx, "player"))))))
        .then(Commands.literal("admin")
            .then(Commands.literal("list").executes(ctx -> adminList(ctx, parties)))
            .then(Commands.literal("info")
                .then(Commands.argument("party", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestPartyTargets(parties, builder))
                    .executes(ctx -> adminInfo(ctx, parties, StringArgumentType.getString(ctx, "party")))))
            .then(Commands.literal("disband")
                .then(Commands.argument("party", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestPartyTargets(parties, builder))
                    .executes(ctx -> adminDisband(ctx, parties, StringArgumentType.getString(ctx, "party")))))
            .then(Commands.literal("cleanup").executes(ctx -> adminCleanup(ctx, parties))))
        .then(Commands.literal("chat")
            .then(Commands.literal("on").executes(ctx -> chatToggle(ctx, parties, true)))
            .then(Commands.literal("off").executes(ctx -> chatToggle(ctx, parties, false))));
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

  private static int promote(CommandContext<CommandSourceStack> ctx, PartyService parties, String targetName) {
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
    PartyService.Result result = parties.promote(player, target);
    CommandMessages.sendResult(sender, result.success(), result.message());
    return Command.SINGLE_SUCCESS;
  }

  private static int demote(CommandContext<CommandSourceStack> ctx, PartyService parties, String targetName) {
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
    PartyService.Result result = parties.demote(player, target);
    CommandMessages.sendResult(sender, result.success(), result.message());
    return Command.SINGLE_SUCCESS;
  }

  private static int setPublic(CommandContext<CommandSourceStack> ctx, PartyService parties, boolean open) {
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
    PartyService.Result result = parties.setPublic(player, open);
    CommandMessages.sendResult(sender, result.success(), result.message());
    return Command.SINGLE_SUCCESS;
  }

  private static int requestJoin(CommandContext<CommandSourceStack> ctx, PartyService parties, String leaderName) {
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
    Player leader = Bukkit.getPlayerExact(leaderName);
    if (leader == null) {
      CommandMessages.send(sender, "messages.command.targetNotFound");
      return Command.SINGLE_SUCCESS;
    }
    Party party = parties.partyOf(leader);
    if (party == null) {
      CommandMessages.send(sender, "messages.party.error.partyMissing");
      return Command.SINGLE_SUCCESS;
    }
    PartyService.Result result = parties.requestJoin(player, party.id());
    CommandMessages.sendResult(sender, result.success(), result.message());
    return Command.SINGLE_SUCCESS;
  }

  private static int acceptRequest(CommandContext<CommandSourceStack> ctx, PartyService parties, String targetName) {
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
    PartyService.Result result = parties.acceptRequest(player, targetName);
    CommandMessages.sendResult(sender, result.success(), result.message());
    return Command.SINGLE_SUCCESS;
  }

  private static int denyRequest(CommandContext<CommandSourceStack> ctx, PartyService parties, String targetName) {
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
    PartyService.Result result = parties.denyRequest(player, targetName);
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

  private static int adminList(CommandContext<CommandSourceStack> ctx, PartyService parties) {
    var sender = ctx.getSource().getSender();
    if (parties == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.party")));
      return Command.SINGLE_SUCCESS;
    }
    if (!requireAdminPermission(sender)) {
      return Command.SINGLE_SUCCESS;
    }
    List<Party> list = parties.allParties();
    CommandMessages.send(sender, "messages.command.party.admin.list.header",
        Locales.placeholders("count", list.size()));
    for (Party party : list) {
      if (party == null) {
        continue;
      }
      CommandMessages.send(sender, "messages.command.party.admin.list.entry",
          Locales.placeholders(
              "id", party.id(),
              "leader", parties.leaderName(party),
              "size", party.size(),
              "public", boolText(sender, party.isPublic()),
              "world", party.worldName()));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int adminInfo(CommandContext<CommandSourceStack> ctx, PartyService parties, String target) {
    var sender = ctx.getSource().getSender();
    if (parties == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.party")));
      return Command.SINGLE_SUCCESS;
    }
    if (!requireAdminPermission(sender)) {
      return Command.SINGLE_SUCCESS;
    }
    Party party = resolveParty(parties, target);
    if (party == null) {
      CommandMessages.send(sender, "messages.party.error.partyMissing");
      return Command.SINGLE_SUCCESS;
    }
    CommandMessages.send(sender, "messages.command.party.admin.info.header",
        Locales.placeholders("id", party.id()));
    CommandMessages.send(sender, "messages.command.party.admin.info.leader",
        Locales.placeholders("leader", parties.leaderName(party)));
    CommandMessages.send(sender, "messages.command.party.admin.info.world",
        Locales.placeholders("world", party.worldName()));
    CommandMessages.send(sender, "messages.command.party.admin.info.public",
        Locales.placeholders("public", boolText(sender, party.isPublic())));
    CommandMessages.send(sender, "messages.command.party.admin.info.invites",
        Locales.placeholders("count", parties.inviteCount(party.id())));
    CommandMessages.send(sender, "messages.command.party.admin.info.requests",
        Locales.placeholders("count", parties.requestCount(party.id())));
    CommandMessages.send(sender, "messages.command.party.admin.info.members",
        Locales.placeholders("count", party.size()));
    for (UUID memberId : party.members()) {
      Player member = Bukkit.getPlayer(memberId);
      String name = member != null ? member.getName() : memberId.toString();
      String role = party.role(memberId) == null ? "member" : party.role(memberId).name().toLowerCase(java.util.Locale.ROOT);
      String online = boolText(sender, member != null);
      CommandMessages.send(sender, "messages.command.party.admin.info.member",
          Locales.placeholders("player", name, "role", role, "online", online));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int adminDisband(CommandContext<CommandSourceStack> ctx, PartyService parties, String target) {
    var sender = ctx.getSource().getSender();
    if (parties == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.party")));
      return Command.SINGLE_SUCCESS;
    }
    if (!requireAdminPermission(sender)) {
      return Command.SINGLE_SUCCESS;
    }
    Party party = resolveParty(parties, target);
    if (party == null) {
      CommandMessages.send(sender, "messages.party.error.partyMissing");
      return Command.SINGLE_SUCCESS;
    }
    UUID actorId = sender instanceof Player player ? player.getUniqueId() : null;
    String actorName = sender instanceof Player player ? player.getName() : "console";
    boolean ok = parties.forceDisband(party.id(), actorId, actorName, "admin_command");
    CommandMessages.sendResult(sender, ok, CommandMessages.text(sender, "messages.command.party.admin.disbanded"));
    return Command.SINGLE_SUCCESS;
  }

  private static int adminCleanup(CommandContext<CommandSourceStack> ctx, PartyService parties) {
    var sender = ctx.getSource().getSender();
    if (parties == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.party")));
      return Command.SINGLE_SUCCESS;
    }
    if (!requireAdminPermission(sender)) {
      return Command.SINGLE_SUCCESS;
    }
    PartyService.CleanupResult result = parties.cleanupStale();
    CommandMessages.send(sender, "messages.command.party.admin.cleanup",
        Locales.placeholders(
            "invites", result.invitesRemoved(),
            "requests", result.requestsRemoved(),
            "parties", result.partiesDisbanded()));
    return Command.SINGLE_SUCCESS;
  }

  private static boolean requireAdminPermission(org.bukkit.command.CommandSender sender) {
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.party.admin")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.party.admin"));
      return false;
    }
    return true;
  }

  private static String boolText(org.bukkit.command.CommandSender sender, boolean value) {
    return CommandMessages.text(sender, value ? "messages.common.true" : "messages.common.false");
  }

  private static Party resolveParty(PartyService parties, String target) {
    if (parties == null || target == null || target.isBlank()) {
      return null;
    }
    UUID partyId = parsePartyId(target);
    if (partyId != null) {
      return parties.partyById(partyId);
    }
    Player leader = Bukkit.getPlayerExact(target);
    if (leader != null) {
      return parties.partyOf(leader);
    }
    return parties.findPartyByLeaderName(target);
  }

  private static UUID parsePartyId(String value) {
    if (value == null) {
      return null;
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static CompletableFuture<Suggestions> suggestPartyTargets(PartyService parties, SuggestionsBuilder builder) {
    if (parties == null) {
      return builder.buildFuture();
    }
    for (Party party : parties.allParties()) {
      if (party == null) {
        continue;
      }
      builder.suggest(party.id().toString());
      String leaderName = parties.leaderName(party);
      if (leaderName != null && !leaderName.isBlank()) {
        builder.suggest(leaderName);
      }
    }
    return builder.buildFuture();
  }

  private static CompletableFuture<Suggestions> suggestOnlinePlayers(SuggestionsBuilder builder) {
    for (Player player : Bukkit.getOnlinePlayers()) {
      builder.suggest(player.getName());
    }
    return builder.buildFuture();
  }
}
