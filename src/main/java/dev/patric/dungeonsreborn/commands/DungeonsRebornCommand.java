package dev.patric.dungeonsreborn.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;
import dev.patric.dungeonsreborn.effects.editor.EditorServices;
import dev.patric.dungeonsreborn.effects.integration.EffectsBindings;
import dev.patric.dungeonsreborn.crafting.CraftingGuiSessionManager;
import dev.patric.dungeonsreborn.crafting.CraftingYamlRegistry;
import dev.patric.dungeonsreborn.advancements.AdvancementService;
import dev.patric.dungeonsreborn.effects.minions.MinionManager;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeService;
import dev.patric.dungeonsreborn.kits.KitService;
import dev.patric.dungeonsreborn.shops.ShopSessionManager;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import dev.patric.dungeonsreborn.menus.CraftingRecipeEditorMenu;
import dev.patric.dungeonsreborn.menus.CraftingTestMenu;
import dev.patric.dungeonsreborn.menus.AdminHubMenu;
import dev.patric.dungeonsreborn.menus.SystemStatusMenu;
import dev.patric.dungeonsreborn.menus.UserHubMenu;
import dev.patric.dungeonsreborn.classes.ClassService;
import dev.patric.dungeonsreborn.classes.ClassYamlRegistry;
import dev.patric.dungeonsreborn.classes.skills.ClassSkillService;
import dev.patric.dungeonsreborn.dungeons.DungeonQueueService;
import dev.patric.dungeonsreborn.dungeons.DungeonSessionManager;
import dev.patric.dungeonsreborn.dungeons.DungeonYamlRegistry;
import dev.patric.dungeonsreborn.menus.UpgradeMergeMenu;
import dev.patric.dungeonsreborn.menus.UpgradeInspectMenu;
import dev.patric.dungeonsreborn.quests.QuestGiverYamlRegistry;
import dev.patric.dungeonsreborn.quests.QuestService;
import dev.patric.dungeonsreborn.quests.QuestYamlRegistry;
import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.mobs.MobSpawnManager;
import dev.patric.dungeonsreborn.mobs.MobYamlRegistry;
import dev.patric.dungeonsreborn.party.PartyService;
import dev.patric.dungeonsreborn.system.SystemStatusStore;
import dev.patric.dungeonsreborn.locale.LocaleService;
import dev.patric.dungeonsreborn.locale.Locales;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

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
      MobSpawnManager mobSpawns,
      dev.patric.dungeonsreborn.mobs.MobSpawnerBlockStore spawnerStore,
      CraftingYamlRegistry crafting,
      CraftingGuiSessionManager craftingSessions,
      AdvancementService advancements,
      UpgradeService upgrades,
      ShopYamlRegistry shops,
      ShopSessionManager shopSessions,
      KitService kits,
      ClassYamlRegistry classRegistry,
      ClassService classService,
      ClassSkillService classSkills,
      DungeonYamlRegistry dungeonRegistry,
      DungeonQueueService dungeonQueue,
      DungeonSessionManager dungeonSessions,
      QuestService quests,
      QuestGiverYamlRegistry questGivers,
      PartyService parties,
      LocaleService locales
  ) {
    return Commands.literal(root)
        .executes(ctx -> help(ctx, root))
        .then(Commands.literal("hub").executes(ctx -> openHub(ctx, classRegistry, classService, classSkills,
            dungeonRegistry, dungeonQueue, dungeonSessions, quests, parties, crafting, craftingSessions,
            advancements, upgrades, kits)))
        .then(Commands.literal("menu").executes(ctx -> openHub(ctx, classRegistry, classService, classSkills,
            dungeonRegistry, dungeonQueue, dungeonSessions, quests, parties, crafting, craftingSessions,
            advancements, upgrades, kits)))
        .then(Commands.literal("status").executes(DungeonsRebornCommand::status))
        .then(Commands.literal("admin").executes(ctx -> openAdmin(ctx, editor, mobsYaml, mobsRegistry, quests, shops,
            crafting, craftingSessions, classRegistry, dungeonRegistry, dungeonQueue, dungeonSessions)))
        .then(EffectsCommand.createCommand(engine, yaml, bindings, editor, minions))
        .then(MobsCommand.createCommand(mobsYaml, mobsRegistry, mobSpawns, spawnerStore))
        .then(GuiCommand.createCommand())
        .then(ShopsCommand.createCommand(shops, shopSessions))
        .then(KitsCommand.createCommand(kits))
        .then(ClassesCommand.createCommand(classRegistry, classService, classSkills))
        .then(DungeonCommand.createCommand(dungeonRegistry, dungeonQueue, dungeonSessions))
        .then(QuestsCommand.createCommand(quests, questGivers, parties))
        .then(PartyCommand.createCommand(parties))
        .then(ChatCommand.createCommand(parties))
        .then(Commands.literal("locale")
            .then(Commands.literal("reload").executes(ctx -> localeReload(ctx, locales))))
        .then(Commands.literal("upgrades")
            .executes(ctx -> upgradesOpen(ctx, upgrades))
            .then(Commands.literal("reload").executes(ctx -> upgradesReload(ctx, upgrades)))
            .then(Commands.literal("inspect").executes(ctx -> upgradesInspect(ctx, upgrades)))
            .then(Commands.literal("give")
                .then(Commands.argument("upgrade", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestUpgrades(upgrades, builder))
                    .executes(ctx -> upgradesGive(ctx, upgrades, StringArgumentType.getString(ctx, "upgrade"))))
                .then(Commands.argument("upgrade", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestUpgrades(upgrades, builder))
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOnlinePlayers(ctx, builder))
                        .executes(ctx -> upgradesGive(ctx, upgrades,
                            StringArgumentType.getString(ctx, "upgrade"),
                            StringArgumentType.getString(ctx, "player")))))))
        .then(Commands.literal("crafting")
            .executes(ctx -> craftingTest(ctx, crafting, craftingSessions, advancements, quests))
            .then(Commands.literal("editor").executes(ctx -> craftingEditor(ctx, crafting, craftingSessions)))
            .then(Commands.literal("reload").executes(ctx -> craftingReload(ctx, crafting))));
  }

  private static int help(CommandContext<CommandSourceStack> ctx, String root) {
    var sender = ctx.getSource().getSender();
    var placeholders = Locales.placeholders("root", root);
    CommandMessages.send(sender, "messages.command.help.header", placeholders);
    CommandMessages.send(sender, "messages.command.help.hub", placeholders);
    CommandMessages.send(sender, "messages.command.help.status", placeholders);
    CommandMessages.send(sender, "messages.command.help.admin", placeholders);
    CommandMessages.send(sender, "messages.command.help.effects", placeholders);
    CommandMessages.send(sender, "messages.command.help.mobs", placeholders);
    CommandMessages.send(sender, "messages.command.help.gui", placeholders);
    CommandMessages.send(sender, "messages.command.help.shop", placeholders);
    CommandMessages.send(sender, "messages.command.help.kits", placeholders);
    CommandMessages.send(sender, "messages.command.help.classes", placeholders);
    CommandMessages.send(sender, "messages.command.help.classesSkills", placeholders);
    CommandMessages.send(sender, "messages.command.help.dungeon", placeholders);
    CommandMessages.send(sender, "messages.command.help.dungeonStatus", placeholders);
    CommandMessages.send(sender, "messages.command.help.dungeonJoin", placeholders);
    CommandMessages.send(sender, "messages.command.help.dungeonLeave", placeholders);
    CommandMessages.send(sender, "messages.command.help.dungeonReload", placeholders);
    CommandMessages.send(sender, "messages.command.help.localeReload", placeholders);
    CommandMessages.send(sender, "messages.command.help.dungeonValidate", placeholders);
    CommandMessages.send(sender, "messages.command.help.dungeonDebugStart", placeholders);
    CommandMessages.send(sender, "messages.command.help.dungeonDebugSkip", placeholders);
    CommandMessages.send(sender, "messages.command.help.dungeonDebugEnd", placeholders);
    CommandMessages.send(sender, "messages.command.help.quests", placeholders);
    CommandMessages.send(sender, "messages.command.help.questsGiver", placeholders);
    CommandMessages.send(sender, "messages.command.help.questsEditor", placeholders);
    CommandMessages.send(sender, "messages.command.help.party", placeholders);
    CommandMessages.send(sender, "messages.command.help.partyGui", placeholders);
    CommandMessages.send(sender, "messages.command.help.partyChat", placeholders);
    CommandMessages.send(sender, "messages.command.help.partyMessage", placeholders);
    CommandMessages.send(sender, "messages.command.help.upgrades", placeholders);
    CommandMessages.send(sender, "messages.command.help.upgradesInspect", placeholders);
    CommandMessages.send(sender, "messages.command.help.upgradesGive", placeholders);
    CommandMessages.send(sender, "messages.command.help.crafting", placeholders);
    CommandMessages.send(sender, "messages.command.help.craftingEditor", placeholders);
    CommandMessages.send(sender, "messages.command.help.craftingReload", placeholders);
    return Command.SINGLE_SUCCESS;
  }

  private static int localeReload(CommandContext<CommandSourceStack> ctx, LocaleService locales) {
    var sender = ctx.getSource().getSender();
    if (sender instanceof org.bukkit.entity.Player player && !player.hasPermission("dungeonsreborn.locale.reload")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.locale.reload"));
      return 1;
    }
    LocaleService.ReloadResult result = locales.reload();
    if (result.errors().isEmpty()) {
      CommandMessages.send(sender, "messages.locale.reload.ok", Locales.placeholders("count", result.locales()));
    } else {
      CommandMessages.send(sender, "messages.locale.reload.errors", Locales.placeholders("errors", result.errors().size()));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int openAdmin(CommandContext<CommandSourceStack> ctx,
      EditorServices editor,
      MobYamlRegistry mobsYaml,
      MobRegistry mobsRegistry,
      QuestService quests,
      ShopYamlRegistry shops,
      CraftingYamlRegistry crafting,
      CraftingGuiSessionManager craftingSessions,
      ClassYamlRegistry classes,
      DungeonYamlRegistry dungeons,
      DungeonQueueService dungeonQueue,
      DungeonSessionManager dungeonSessions) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (!player.hasPermission("dungeonsreborn.admin")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.admin"));
      return Command.SINGLE_SUCCESS;
    }
    QuestYamlRegistry questYaml = quests == null ? null : quests.registry();
    new AdminHubMenu(editor, mobsYaml, mobsRegistry, questYaml, shops, crafting, craftingSessions, classes,
        dungeons, dungeonQueue, dungeonSessions).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int status(CommandContext<CommandSourceStack> ctx) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (!player.hasPermission("dungeonsreborn.admin")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.admin"));
      return Command.SINGLE_SUCCESS;
    }
    new SystemStatusMenu(SystemStatusStore.get()).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int openHub(CommandContext<CommandSourceStack> ctx,
      ClassYamlRegistry classRegistry,
      ClassService classService,
      ClassSkillService classSkills,
      DungeonYamlRegistry dungeonRegistry,
      DungeonQueueService dungeonQueue,
      DungeonSessionManager dungeonSessions,
      QuestService quests,
      PartyService parties,
      CraftingYamlRegistry crafting,
      CraftingGuiSessionManager craftingSessions,
      AdvancementService advancements,
      UpgradeService upgrades,
      KitService kits) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    new UserHubMenu(classRegistry, classService, classSkills,
        dungeonRegistry, dungeonQueue, dungeonSessions,
        quests, parties, crafting, craftingSessions, advancements, upgrades, kits).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int upgradesOpen(CommandContext<CommandSourceStack> ctx, UpgradeService upgrades) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof org.bukkit.entity.Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (upgrades == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.upgrades")));
      return Command.SINGLE_SUCCESS;
    }
    new UpgradeMergeMenu(upgrades).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int upgradesReload(CommandContext<CommandSourceStack> ctx, UpgradeService upgrades) {
    var sender = ctx.getSource().getSender();
    if (upgrades == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.upgrades")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof org.bukkit.entity.Player player && !player.hasPermission("dungeonsreborn.upgrades.reload")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.upgrades.reload"));
      return Command.SINGLE_SUCCESS;
    }
    var result = upgrades.registry().reload();
    int migrated = upgrades.migrateOnlinePlayers();
    CommandMessages.send(sender, "messages.command.reload.dir",
        Locales.placeholders("path", upgrades.registry().upgradesDir().getPath()));
    CommandMessages.send(sender, "messages.command.reload.upgradesSummary", Locales.placeholders(
        "loaded", result.loaded(),
        "errors", result.errors().size(),
        "migrated", migrated));
    return Command.SINGLE_SUCCESS;
  }

  private static int upgradesInspect(CommandContext<CommandSourceStack> ctx, UpgradeService upgrades) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof org.bukkit.entity.Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (upgrades == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.upgrades")));
      return Command.SINGLE_SUCCESS;
    }
    if (!player.hasPermission("dungeonsreborn.upgrades.admin")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.upgrades.admin"));
      return Command.SINGLE_SUCCESS;
    }
    new UpgradeInspectMenu(upgrades).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int upgradesGive(CommandContext<CommandSourceStack> ctx, UpgradeService upgrades, String upgradeId) {
    return upgradesGive(ctx, upgrades, upgradeId, null);
  }

  private static int upgradesGive(CommandContext<CommandSourceStack> ctx, UpgradeService upgrades, String upgradeId, String targetName) {
    var sender = ctx.getSource().getSender();
    if (upgrades == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.upgrades")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof org.bukkit.entity.Player player && !player.hasPermission("dungeonsreborn.upgrades.give")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.upgrades.give"));
      return Command.SINGLE_SUCCESS;
    }
    org.bukkit.entity.Player target = null;
    if (targetName != null) {
      target = Bukkit.getPlayerExact(targetName);
    } else if (sender instanceof org.bukkit.entity.Player player) {
      target = player;
    }
    if (target == null) {
      CommandMessages.send(sender, "messages.command.targetNotFound");
      return Command.SINGLE_SUCCESS;
    }
    ItemStack item = upgrades.registry().upgradeItem(upgradeId);
    if (item == null) {
      CommandMessages.send(sender, "messages.command.unknownUpgrade", Locales.placeholders("id", upgradeId));
      return Command.SINGLE_SUCCESS;
    }
    var leftovers = target.getInventory().addItem(item);
    if (!leftovers.isEmpty()) {
      for (ItemStack stack : leftovers.values()) {
        target.getWorld().dropItemNaturally(target.getLocation(), stack);
      }
    }
    CommandMessages.send(sender, "messages.command.upgradeGiven", Locales.placeholders("player", target.getName()));
    return Command.SINGLE_SUCCESS;
  }

  private static CompletableFuture<Suggestions> suggestUpgrades(UpgradeService upgrades, SuggestionsBuilder builder) {
    if (upgrades == null) {
      return builder.buildFuture();
    }
    for (String id : upgrades.registry().upgrades().keySet()) {
      builder.suggest(id);
    }
    return builder.buildFuture();
  }

  private static CompletableFuture<Suggestions> suggestOnlinePlayers(CommandContext<CommandSourceStack> ctx,
      SuggestionsBuilder builder) {
    for (Player player : Bukkit.getOnlinePlayers()) {
      builder.suggest(player.getName());
    }
    return builder.buildFuture();
  }

  private static int craftingTest(CommandContext<CommandSourceStack> ctx, CraftingYamlRegistry crafting,
      CraftingGuiSessionManager craftingSessions, AdvancementService advancements, QuestService quests) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof org.bukkit.entity.Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    new CraftingTestMenu(crafting, craftingSessions, advancements, quests).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int craftingReload(CommandContext<CommandSourceStack> ctx, CraftingYamlRegistry crafting) {
    var sender = ctx.getSource().getSender();
    if (crafting == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.crafting")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof org.bukkit.entity.Player player && !player.hasPermission("dungeonsreborn.effects.reload")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.effects.reload"));
      return Command.SINGLE_SUCCESS;
    }
    var result = crafting.reload();
    CommandMessages.send(sender, "messages.command.reload.dir",
        Locales.placeholders("path", crafting.recipesDir().getPath()));
    CommandMessages.send(sender, "messages.command.reload.craftingSummary",
        Locales.placeholders("loaded", result.loaded(), "errors", result.errors().size()));
    return Command.SINGLE_SUCCESS;
  }

  private static int craftingEditor(CommandContext<CommandSourceStack> ctx, CraftingYamlRegistry crafting,
      CraftingGuiSessionManager craftingSessions) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof org.bukkit.entity.Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (!player.hasPermission("dungeonsreborn.crafting.editor")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.crafting.editor"));
      return Command.SINGLE_SUCCESS;
    }
    if (crafting == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.crafting")));
      return Command.SINGLE_SUCCESS;
    }
    new CraftingRecipeEditorMenu(crafting, craftingSessions).open(player);
    return Command.SINGLE_SUCCESS;
  }
}
