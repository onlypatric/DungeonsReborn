package dev.patric.dungeonsreborn.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import dev.patric.dungeonsreborn.menus.ShowcaseMenu;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;

public final class GuiCommand {
  private GuiCommand() {
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createCommand() {
    return Commands.literal("gui")
        .executes(GuiCommand::run);
  }

  private static int run(CommandContext<CommandSourceStack> ctx) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();

    if (!(executor instanceof org.bukkit.entity.Player player)) {
      sender.sendMessage(Component.text("§cOnly players can use this command"));
      return Command.SINGLE_SUCCESS;
    }

    new ShowcaseMenu().open(player);
    return Command.SINGLE_SUCCESS;
  }
}

