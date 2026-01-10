package dev.patric.dungeonsreborn.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;

public class FlySpeedCommand {

  public static LiteralArgumentBuilder<CommandSourceStack> createCommand() {
    return Commands.literal("flyspeed")
        .then(Commands.argument("speed", FloatArgumentType.floatArg(0f, 10f))
            .executes(ctx -> run(ctx, null))
            .then(Commands.argument("target", StringArgumentType.word())
                .executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "target")))));
  }

  private static int run(CommandContext<CommandSourceStack> ctx, String targetName) {
    float speed = FloatArgumentType.getFloat(ctx, "speed");
    CommandSender sender = ctx.getSource().getSender();
    Entity executor = ctx.getSource().getExecutor();

    Player target;

    if (targetName != null) {
      target = Bukkit.getPlayerExact(targetName);
      if (target == null) {
        sender.sendMessage(Component.text("§cPlayer not online"));
        return Command.SINGLE_SUCCESS;
      }
    } else {
      if (!(executor instanceof Player player)) {
        sender.sendMessage(Component.text("§cConsole must specify a player"));
        return Command.SINGLE_SUCCESS;
      }
      target = player;
    }

    target.setFlySpeed(speed);

    if (target == executor) {
      sender.sendMessage(Component.text("§aYour fly speed is now " + speed));
    } else {
      sender.sendMessage(Component.text(
          "§aFly speed of " + target.getName() + " set to " + speed));
    }

    return Command.SINGLE_SUCCESS;
  }
}
