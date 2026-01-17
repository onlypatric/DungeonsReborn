package dev.patric.dungeonsreborn.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.classes.ClassAbilityBindings;
import dev.patric.dungeonsreborn.classes.ClassService;
import dev.patric.dungeonsreborn.classes.ClassSpec;
import dev.patric.dungeonsreborn.classes.ClassYamlRegistry;
import dev.patric.dungeonsreborn.classes.skills.ClassSkillService;
import dev.patric.dungeonsreborn.classes.skills.SkillNodeSpec;
import dev.patric.dungeonsreborn.classes.editor.menu.ClassEditorListMenu;
import dev.patric.dungeonsreborn.classes.menu.ClassSelectMenu;
import dev.patric.dungeonsreborn.classes.menu.ClassSkillTreeMenu;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;

public final class ClassesCommand {
  private ClassesCommand() {
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createUserCommand(ClassYamlRegistry yaml, ClassService service,
      ClassSkillService skills, ClassAbilityBindings abilityBindings) {
    return createCommand(yaml, service, skills, abilityBindings, false);
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createAdminCommand(ClassYamlRegistry yaml, ClassService service,
      ClassSkillService skills, ClassAbilityBindings abilityBindings) {
    return createCommand(yaml, service, skills, abilityBindings, true);
  }

  private static LiteralArgumentBuilder<CommandSourceStack> createCommand(ClassYamlRegistry yaml, ClassService service,
      ClassSkillService skills, ClassAbilityBindings abilityBindings, boolean includeAdmin) {
    LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("classes")
        .executes(ctx -> open(ctx, yaml, service, skills))
        .then(Commands.literal("skills").executes(ctx -> skills(ctx, yaml, service, skills)));
    if (includeAdmin) {
      builder.then(Commands.literal("reload").executes(ctx -> reload(ctx, yaml, abilityBindings)));
      LiteralArgumentBuilder<CommandSourceStack> nodesBuilder = Commands.literal("nodes")
          .executes(ctx -> nodes(ctx, yaml, service));
      nodesBuilder.then(Commands.argument("classId", StringArgumentType.word())
          .suggests((ctx, builder1) -> suggestClassIds(yaml, builder1))
          .executes(ctx -> nodes(ctx, yaml, service, StringArgumentType.getString(ctx, "classId"))));
      builder.then(nodesBuilder);

      LiteralArgumentBuilder<CommandSourceStack> stateBuilder = Commands.literal("state")
          .executes(ctx -> state(ctx, yaml, service, skills));
      RequiredArgumentBuilder<CommandSourceStack, String> statePlayer = Commands.argument("player", StringArgumentType.word())
          .suggests((ctx, builder1) -> suggestPlayers(builder1))
          .executes(ctx -> state(ctx, yaml, service, skills,
              StringArgumentType.getString(ctx, "player"), null));
      statePlayer.then(Commands.argument("classId", StringArgumentType.word())
          .suggests((ctx, builder1) -> suggestClassIds(yaml, builder1))
          .executes(ctx -> state(ctx, yaml, service, skills,
              StringArgumentType.getString(ctx, "player"),
              StringArgumentType.getString(ctx, "classId"))));
      stateBuilder.then(statePlayer);
      builder.then(stateBuilder);
      builder.then(Commands.literal("editor").executes(ctx -> editor(ctx, yaml)));
    }
    return builder;
  }

  private static int open(CommandContext<CommandSourceStack> ctx, ClassYamlRegistry yaml, ClassService service,
      ClassSkillService skills) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (yaml == null || service == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.classes")));
      return Command.SINGLE_SUCCESS;
    }
    new ClassSelectMenu(yaml, service, skills).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int reload(CommandContext<CommandSourceStack> ctx, ClassYamlRegistry yaml,
      ClassAbilityBindings abilityBindings) {
    var sender = ctx.getSource().getSender();
    if (yaml == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.classesRegistry")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.classes.reload")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.classes.reload"));
      return Command.SINGLE_SUCCESS;
    }
    var result = yaml.reload();
    if (abilityBindings != null) {
      abilityBindings.reload();
    }
    CommandMessages.send(sender, "messages.command.reload.file",
        Locales.placeholders("path", yaml.file().getPath()));
    CommandMessages.send(sender, "messages.command.reload.classesSummary",
        Locales.placeholders("loaded", result.loaded(), "errors", result.errors().size()));
    return Command.SINGLE_SUCCESS;
  }

  private static int editor(CommandContext<CommandSourceStack> ctx, ClassYamlRegistry yaml) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (yaml == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.classesRegistry")));
      return Command.SINGLE_SUCCESS;
    }
    if (!player.hasPermission("dungeonsreborn.classes.editor")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.classes.editor"));
      return Command.SINGLE_SUCCESS;
    }
    new ClassEditorListMenu(yaml).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int skills(CommandContext<CommandSourceStack> ctx, ClassYamlRegistry yaml, ClassService service,
      ClassSkillService skills) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (yaml == null || service == null || skills == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.classSkills")));
      return Command.SINGLE_SUCCESS;
    }
    String classId = service.currentClassId(player.getUniqueId());
    if (classId == null) {
      CommandMessages.send(sender, "messages.command.classes.selectFirst");
      return Command.SINGLE_SUCCESS;
    }
    ClassSpec spec = yaml.classSpec(classId);
    if (spec == null) {
      CommandMessages.send(sender, "messages.command.classes.missingSpec");
      return Command.SINGLE_SUCCESS;
    }
    new ClassSkillTreeMenu(spec, skills).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int nodes(CommandContext<CommandSourceStack> ctx, ClassYamlRegistry yaml, ClassService service) {
    return nodes(ctx, yaml, service, null);
  }

  private static int nodes(CommandContext<CommandSourceStack> ctx, ClassYamlRegistry yaml, ClassService service,
      String classId) {
    var sender = ctx.getSource().getSender();
    if (yaml == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.classesRegistry")));
      return Command.SINGLE_SUCCESS;
    }
    if (classId == null || classId.isBlank()) {
      if (sender instanceof Player player && service != null) {
        String current = service.currentClassId(player.getUniqueId());
        if (current != null) {
          return nodes(ctx, yaml, service, current);
        }
      }
      CommandMessages.send(sender, "messages.command.classes.nodes.headerAll");
      for (ClassSpec spec : yaml.classes().values()) {
        int count = spec.skillTreeOrEmpty().nodes().size();
        sender.sendMessage(Locales.component(sender instanceof Player p ? p : null,
            "messages.command.classes.nodes.entry",
            Locales.placeholders("id", spec.id(), "count", String.valueOf(count))));
      }
      return Command.SINGLE_SUCCESS;
    }
    ClassSpec spec = yaml.classSpec(classId);
    if (spec == null) {
      CommandMessages.send(sender, "messages.command.classes.nodes.unknown",
          Locales.placeholders("id", classId));
      CommandMessages.sendClosestMatch(sender, classId, yaml.classes().keySet());
      return Command.SINGLE_SUCCESS;
    }
    CommandMessages.send(sender, "messages.command.classes.nodes.header",
        Locales.placeholders("id", spec.id(), "count", String.valueOf(spec.skillTreeOrEmpty().nodes().size())));
    for (SkillNodeSpec node : spec.skillTreeOrEmpty().nodes()) {
      if (node == null) {
        continue;
      }
      sender.sendMessage(Component.text("- " + node.id()));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int state(CommandContext<CommandSourceStack> ctx, ClassYamlRegistry yaml, ClassService service,
      ClassSkillService skills) {
    var sender = ctx.getSource().getSender();
    if (!(sender instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    return state(ctx, yaml, service, skills, player.getName(), null);
  }

  private static int state(CommandContext<CommandSourceStack> ctx, ClassYamlRegistry yaml, ClassService service,
      ClassSkillService skills, String playerName, String classId) {
    var sender = ctx.getSource().getSender();
    if (yaml == null || service == null || skills == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.classSkills")));
      return Command.SINGLE_SUCCESS;
    }
    Player target = org.bukkit.Bukkit.getPlayerExact(playerName);
    if (target == null) {
      CommandMessages.send(sender, "messages.command.classes.state.playerMissing",
          Locales.placeholders("player", playerName));
      return Command.SINGLE_SUCCESS;
    }
    String resolvedClass = classId;
    if (resolvedClass == null || resolvedClass.isBlank()) {
      resolvedClass = service.currentClassId(target.getUniqueId());
    }
    if (resolvedClass == null) {
      CommandMessages.send(sender, "messages.command.classes.state.noClass",
          Locales.placeholders("player", target.getName()));
      return Command.SINGLE_SUCCESS;
    }
    ClassSpec spec = yaml.classSpec(resolvedClass);
    if (spec == null) {
      CommandMessages.send(sender, "messages.command.classes.state.unknownClass",
          Locales.placeholders("id", resolvedClass));
      return Command.SINGLE_SUCCESS;
    }
    int points = skills.skillPoints(target);
    int spent = skills.spentSkillPoints(target);
    int total = skills.totalSkillPoints(target);
    int unlocked = skills.unlockedNodes(target.getUniqueId(), spec.id()).size();
    int totalNodes = spec.skillTreeOrEmpty().nodes().size();
    CommandMessages.send(sender, "messages.command.classes.state.header",
        Locales.placeholders("player", target.getName(), "id", spec.id()));
    CommandMessages.send(sender, "messages.command.classes.state.points",
        Locales.placeholders("unspent", String.valueOf(points), "spent", String.valueOf(spent),
            "total", String.valueOf(total)));
    CommandMessages.send(sender, "messages.command.classes.state.unlocked",
        Locales.placeholders("count", String.valueOf(unlocked), "total", String.valueOf(totalNodes)));
    for (String nodeId : skills.unlockedNodes(target.getUniqueId(), spec.id())) {
      sender.sendMessage(Component.text("- " + nodeId));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static CompletableFuture<Suggestions> suggestClassIds(ClassYamlRegistry yaml, SuggestionsBuilder builder) {
    if (yaml == null) {
      return builder.buildFuture();
    }
    String lower = builder.getRemainingLowerCase();
    for (String id : yaml.classes().keySet()) {
      if (id.startsWith(lower)) {
        builder.suggest(id);
      }
    }
    return builder.buildFuture();
  }

  private static CompletableFuture<Suggestions> suggestPlayers(SuggestionsBuilder builder) {
    String lower = builder.getRemainingLowerCase();
    for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
      String name = player.getName();
      if (name != null && name.toLowerCase(java.util.Locale.ROOT).startsWith(lower)) {
        builder.suggest(name);
      }
    }
    return builder.buildFuture();
  }
}
