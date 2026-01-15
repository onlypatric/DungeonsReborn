package dev.patric.dungeonsreborn.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.classes.ClassService;
import dev.patric.dungeonsreborn.classes.ClassSpec;
import dev.patric.dungeonsreborn.classes.ClassYamlRegistry;
import dev.patric.dungeonsreborn.classes.skills.ClassSkillService;
import dev.patric.dungeonsreborn.classes.editor.menu.ClassEditorListMenu;
import dev.patric.dungeonsreborn.classes.menu.ClassSelectMenu;
import dev.patric.dungeonsreborn.classes.menu.ClassSkillTreeMenu;
import dev.patric.dungeonsreborn.locale.Locales;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

public final class ClassesCommand {
  private ClassesCommand() {
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createCommand(ClassYamlRegistry yaml, ClassService service,
      ClassSkillService skills) {
    return Commands.literal("classes")
        .executes(ctx -> open(ctx, yaml, service))
        .then(Commands.literal("reload").executes(ctx -> reload(ctx, yaml)))
        .then(Commands.literal("editor").executes(ctx -> editor(ctx, yaml)))
        .then(Commands.literal("skills").executes(ctx -> skills(ctx, yaml, service, skills)));
  }

  private static int open(CommandContext<CommandSourceStack> ctx, ClassYamlRegistry yaml, ClassService service) {
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
    new ClassSelectMenu(yaml, service).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int reload(CommandContext<CommandSourceStack> ctx, ClassYamlRegistry yaml) {
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
}
