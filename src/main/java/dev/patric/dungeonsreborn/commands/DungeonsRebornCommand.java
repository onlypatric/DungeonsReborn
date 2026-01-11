package dev.patric.dungeonsreborn.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;
import dev.patric.dungeonsreborn.effects.editor.EditorServices;
import dev.patric.dungeonsreborn.effects.integration.EffectsBindings;
import dev.patric.dungeonsreborn.effects.minions.MinionManager;
import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.mobs.MobYamlRegistry;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;

public final class DungeonsRebornCommand {
  private DungeonsRebornCommand() {
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createCommand(
      String root,
      EffectsEngine engine,
      EffectsYamlAbilities yaml,
      EffectsBindings bindings,
      EditorServices editor,
      MinionManager minions,
      MobYamlRegistry mobsYaml,
      MobRegistry mobsRegistry
  ) {
    return Commands.literal(root)
        .executes(ctx -> help(ctx, root))
        .then(EffectsCommand.createCommand(engine, yaml, bindings, editor, minions))
        .then(MobsCommand.createCommand(mobsYaml, mobsRegistry))
        .then(GuiCommand.createCommand());
  }

  private static int help(CommandContext<CommandSourceStack> ctx, String root) {
    var sender = ctx.getSource().getSender();
    sender.sendMessage(Component.text("§6[DungeonsReborn] §fCommands"));
    sender.sendMessage(Component.text("§7/" + root + " effects §8(spell engine commands)"));
    sender.sendMessage(Component.text("§7/" + root + " mobs §8(mob system commands)"));
    sender.sendMessage(Component.text("§7/" + root + " gui §8(open GUI showcase)"));
    return Command.SINGLE_SUCCESS;
  }
}
