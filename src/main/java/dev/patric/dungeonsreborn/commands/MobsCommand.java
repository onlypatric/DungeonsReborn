package dev.patric.dungeonsreborn.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.mobs.MobYamlRegistry;
import dev.patric.dungeonsreborn.mobs.editor.menu.MobEditorListMenu;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;

public final class MobsCommand {
  private MobsCommand() {
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createCommand(MobYamlRegistry yaml, MobRegistry registry) {
    return Commands.literal("mobs")
        .executes(ctx -> help(ctx))
        .then(Commands.literal("reload").executes(ctx -> reload(ctx, yaml)))
        .then(Commands.literal("editor").executes(ctx -> editor(ctx, yaml, registry)))
        .then(Commands.literal("list").executes(ctx -> list(ctx, registry)))
        .then(Commands.literal("dump")
            .then(Commands.argument("uuid", StringArgumentType.word())
                .executes(ctx -> dump(ctx, registry, StringArgumentType.getString(ctx, "uuid")))))
        .then(Commands.literal("spawn")
            .then(Commands.argument("id", StringArgumentType.word())
                .executes(ctx -> spawn(ctx, registry, StringArgumentType.getString(ctx, "id")))))
        .then(Commands.literal("egg")
            .then(Commands.argument("id", StringArgumentType.word())
                .executes(ctx -> giveEgg(ctx, yaml, StringArgumentType.getString(ctx, "id")))));
  }

  private static int help(CommandContext<CommandSourceStack> ctx) {
    CommandSender sender = ctx.getSource().getSender();
    sender.sendMessage(Component.text("§6[Mobs] §fCommands"));
    sender.sendMessage(Component.text("§7/dr mobs reload §8(reload mob YAML)"));
    sender.sendMessage(Component.text("§7/dr mobs editor §8(open mob editor)"));
    sender.sendMessage(Component.text("§7/dr mobs list §8(list active mobs)"));
    sender.sendMessage(Component.text("§7/dr mobs dump <uuid> §8(show mob state snapshot)"));
    sender.sendMessage(Component.text("§7/dr mobs spawn <id> §8(spawn a mob at your location)"));
    sender.sendMessage(Component.text("§7/dr mobs egg <id> §8(give yourself a mob egg)"));
    return Command.SINGLE_SUCCESS;
  }

  private static int reload(CommandContext<CommandSourceStack> ctx, MobYamlRegistry yaml) {
    CommandSender sender = ctx.getSource().getSender();
    if (yaml == null) {
      sender.sendMessage(Component.text("§cMob YAML loader not installed."));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.mobs.reload")) {
      sender.sendMessage(Component.text("§cMissing permission: dungeonsreborn.mobs.reload"));
      return Command.SINGLE_SUCCESS;
    }
    var result = yaml.reload();
    sender.sendMessage(Component.text("§aReloaded mobs: §f" + result.loadedMobs()
        + "§a, spawns: §f" + result.loadedSpawns()
        + "§a, errors: §f" + result.errors().size()));
    return Command.SINGLE_SUCCESS;
  }

  private static int spawn(CommandContext<CommandSourceStack> ctx, MobRegistry registry, String id) {
    if (registry == null) {
      ctx.getSource().getSender().sendMessage(Component.text("§cMob registry not installed."));
      return Command.SINGLE_SUCCESS;
    }
    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
      ctx.getSource().getSender().sendMessage(Component.text("§cOnly players can spawn mobs."));
      return Command.SINGLE_SUCCESS;
    }
    if (!player.hasPermission("dungeonsreborn.mobs.spawn")) {
      player.sendMessage(Component.text("§cMissing permission: dungeonsreborn.mobs.spawn"));
      return Command.SINGLE_SUCCESS;
    }
    try {
      registry.spawn(id, player.getLocation());
      player.sendMessage(Component.text("§aSpawned mob: §f" + id));
    } catch (Exception ex) {
      player.sendMessage(Component.text("§cFailed to spawn: " + ex.getMessage()));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int list(CommandContext<CommandSourceStack> ctx, MobRegistry registry) {
    if (registry == null) {
      ctx.getSource().getSender().sendMessage(Component.text("§cMob registry not installed."));
      return Command.SINGLE_SUCCESS;
    }
    var sender = ctx.getSource().getSender();
    var counts = registry.countById();
    sender.sendMessage(Component.text("§6[Mobs] §fActive mobs: §a" + counts.values().stream().mapToInt(Integer::intValue).sum()));
    if (counts.isEmpty()) {
      sender.sendMessage(Component.text("§7(no active mobs)"));
      return Command.SINGLE_SUCCESS;
    }
    for (var entry : counts.entrySet()) {
      sender.sendMessage(Component.text("§7- §f" + entry.getKey() + " §8x§f" + entry.getValue()));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int editor(CommandContext<CommandSourceStack> ctx, MobYamlRegistry yaml, MobRegistry registry) {
    if (yaml == null || registry == null) {
      ctx.getSource().getSender().sendMessage(Component.text("§cMob editor not installed."));
      return Command.SINGLE_SUCCESS;
    }
    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
      ctx.getSource().getSender().sendMessage(Component.text("§cOnly players can open the editor."));
      return Command.SINGLE_SUCCESS;
    }
    if (!player.hasPermission("dungeonsreborn.mobs.editor")) {
      player.sendMessage(Component.text("§cMissing permission: dungeonsreborn.mobs.editor"));
      return Command.SINGLE_SUCCESS;
    }
    new MobEditorListMenu(yaml, registry).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int dump(CommandContext<CommandSourceStack> ctx, MobRegistry registry, String rawId) {
    if (registry == null) {
      ctx.getSource().getSender().sendMessage(Component.text("§cMob registry not installed."));
      return Command.SINGLE_SUCCESS;
    }
    java.util.UUID uuid;
    try {
      uuid = java.util.UUID.fromString(rawId);
    } catch (IllegalArgumentException ex) {
      ctx.getSource().getSender().sendMessage(Component.text("§cInvalid UUID: " + rawId));
      return Command.SINGLE_SUCCESS;
    }
    var snapshot = registry.snapshot(uuid);
    if (snapshot == null) {
      ctx.getSource().getSender().sendMessage(Component.text("§cNo active mob for UUID: " + rawId));
      return Command.SINGLE_SUCCESS;
    }
    ctx.getSource().getSender().sendMessage(Component.text("§6[Mobs] §fSnapshot"));
    ctx.getSource().getSender().sendMessage(Component.text("§7mobId: §f" + snapshot.mobId()));
    ctx.getSource().getSender().sendMessage(Component.text("§7variant: §f" + (snapshot.variantId() == null ? "-" : snapshot.variantId())));
    ctx.getSource().getSender().sendMessage(Component.text("§7owner: §f" + (snapshot.ownerId() == null ? "-" : snapshot.ownerId())));
    ctx.getSource().getSender().sendMessage(Component.text("§7world: §f" + snapshot.world()));
    ctx.getSource().getSender().sendMessage(Component.text("§7pos: §f" + String.format(java.util.Locale.ROOT, "%.2f, %.2f, %.2f", snapshot.x(), snapshot.y(), snapshot.z())));
    ctx.getSource().getSender().sendMessage(Component.text("§7hp: §f" + String.format(java.util.Locale.ROOT, "%.1f / %.1f", snapshot.health(), snapshot.maxHealth())));
    return Command.SINGLE_SUCCESS;
  }

  private static int giveEgg(CommandContext<CommandSourceStack> ctx, MobYamlRegistry yaml, String id) {
    if (yaml == null) {
      ctx.getSource().getSender().sendMessage(Component.text("§cMob YAML loader not installed."));
      return Command.SINGLE_SUCCESS;
    }
    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
      ctx.getSource().getSender().sendMessage(Component.text("§cOnly players can receive eggs."));
      return Command.SINGLE_SUCCESS;
    }
    if (!player.hasPermission("dungeonsreborn.mobs.egg.give")) {
      player.sendMessage(Component.text("§cMissing permission: dungeonsreborn.mobs.egg.give"));
      return Command.SINGLE_SUCCESS;
    }
    var item = yaml.eggItem(id);
    if (item == null) {
      player.sendMessage(Component.text("§cUnknown egg id: " + id));
      return Command.SINGLE_SUCCESS;
    }
    player.getInventory().addItem(item);
    player.sendMessage(Component.text("§aGiven egg: §f" + id));
    return Command.SINGLE_SUCCESS;
  }
}
