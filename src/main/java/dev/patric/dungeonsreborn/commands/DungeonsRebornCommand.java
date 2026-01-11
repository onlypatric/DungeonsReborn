package dev.patric.dungeonsreborn.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;
import dev.patric.dungeonsreborn.effects.editor.EditorServices;
import dev.patric.dungeonsreborn.effects.integration.EffectsBindings;
import dev.patric.dungeonsreborn.crafting.CraftingGuiSessionManager;
import dev.patric.dungeonsreborn.crafting.CraftingYamlRegistry;
import dev.patric.dungeonsreborn.effects.minions.MinionManager;
import dev.patric.dungeonsreborn.menus.CraftingRecipeEditorMenu;
import dev.patric.dungeonsreborn.menus.CraftingTestMenu;
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
      MobRegistry mobsRegistry,
      CraftingYamlRegistry crafting,
      CraftingGuiSessionManager craftingSessions
  ) {
    return Commands.literal(root)
        .executes(ctx -> help(ctx, root))
        .then(EffectsCommand.createCommand(engine, yaml, bindings, editor, minions))
        .then(MobsCommand.createCommand(mobsYaml, mobsRegistry))
        .then(GuiCommand.createCommand())
        .then(Commands.literal("crafting")
            .executes(ctx -> craftingTest(ctx, crafting, craftingSessions))
            .then(Commands.literal("editor").executes(ctx -> craftingEditor(ctx, crafting, craftingSessions)))
            .then(Commands.literal("reload").executes(ctx -> craftingReload(ctx, crafting))));
  }

  private static int help(CommandContext<CommandSourceStack> ctx, String root) {
    var sender = ctx.getSource().getSender();
    sender.sendMessage(Component.text("§6[DungeonsReborn] §fCommands"));
    sender.sendMessage(Component.text("§7/" + root + " effects §8(spell engine commands)"));
    sender.sendMessage(Component.text("§7/" + root + " mobs §8(mob system commands)"));
    sender.sendMessage(Component.text("§7/" + root + " gui §8(open GUI showcase)"));
    sender.sendMessage(Component.text("§7/" + root + " crafting §8(open crafting GUI test)"));
    sender.sendMessage(Component.text("§7/" + root + " crafting editor §8(open crafting recipe editor)"));
    sender.sendMessage(Component.text("§7/" + root + " crafting reload §8(reload crafting recipes)"));
    return Command.SINGLE_SUCCESS;
  }

  private static int craftingTest(CommandContext<CommandSourceStack> ctx, CraftingYamlRegistry crafting,
      CraftingGuiSessionManager craftingSessions) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof org.bukkit.entity.Player player)) {
      sender.sendMessage(Component.text("§cOnly players can use this command"));
      return Command.SINGLE_SUCCESS;
    }
    new CraftingTestMenu(crafting, craftingSessions).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int craftingReload(CommandContext<CommandSourceStack> ctx, CraftingYamlRegistry crafting) {
    var sender = ctx.getSource().getSender();
    if (crafting == null) {
      sender.sendMessage(Component.text("§cCrafting registry not installed."));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof org.bukkit.entity.Player player && !player.hasPermission("dungeonsreborn.effects.reload")) {
      sender.sendMessage(Component.text("§cMissing permission: dungeonsreborn.effects.reload"));
      return Command.SINGLE_SUCCESS;
    }
    var result = crafting.reload();
    sender.sendMessage(Component.text("§aReloaded crafting recipes: §f" + result.loaded()
        + "§a, errors: §f" + result.errors().size()));
    return Command.SINGLE_SUCCESS;
  }

  private static int craftingEditor(CommandContext<CommandSourceStack> ctx, CraftingYamlRegistry crafting,
      CraftingGuiSessionManager craftingSessions) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof org.bukkit.entity.Player player)) {
      sender.sendMessage(Component.text("§cOnly players can use this command"));
      return Command.SINGLE_SUCCESS;
    }
    if (!player.hasPermission("dungeonsreborn.crafting.editor")) {
      sender.sendMessage(Component.text("§cMissing permission: dungeonsreborn.crafting.editor"));
      return Command.SINGLE_SUCCESS;
    }
    if (crafting == null) {
      sender.sendMessage(Component.text("§cCrafting registry not installed."));
      return Command.SINGLE_SUCCESS;
    }
    new CraftingRecipeEditorMenu(crafting, craftingSessions).open(player);
    return Command.SINGLE_SUCCESS;
  }
}
