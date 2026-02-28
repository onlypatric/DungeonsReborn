package dev.patric.dungeonsreborn.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;
import dev.patric.dungeonsreborn.effects.editor.EditorServices;
import dev.patric.dungeonsreborn.effects.integration.EffectsBindings;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeTemplate;
import dev.patric.dungeonsreborn.crafting.CraftingDiscoveryService;
import dev.patric.dungeonsreborn.crafting.CraftingYamlRegistry;
import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.mobs.MobSpawnManager;
import dev.patric.dungeonsreborn.mobs.MobYamlRegistry;
import dev.patric.dungeonsreborn.mobs.editor.MobDebugOverlayService;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import dev.patric.dungeonsreborn.advancements.AdvancementService;
import dev.patric.dungeonsreborn.effects.minions.MinionManager;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeService;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeYamlRegistry;
import dev.patric.dungeonsreborn.kits.KitService;
import dev.patric.dungeonsreborn.kits.KitYamlRegistry;
import dev.patric.dungeonsreborn.shops.ShopSessionManager;
import dev.patric.dungeonsreborn.textures.TextureBuildResult;
import dev.patric.dungeonsreborn.textures.TextureService;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.classes.ClassAbilityBindings;
import dev.patric.dungeonsreborn.classes.ClassService;
import dev.patric.dungeonsreborn.classes.ClassYamlRegistry;
import dev.patric.dungeonsreborn.classes.skills.ClassSkillService;
import dev.patric.dungeonsreborn.dungeons.DungeonQueueService;
import dev.patric.dungeonsreborn.dungeons.DungeonSessionManager;
import dev.patric.dungeonsreborn.dungeons.DungeonYamlRegistry;
import dev.patric.dungeonsreborn.quests.QuestGiverYamlRegistry;
import dev.patric.dungeonsreborn.quests.QuestService;
import dev.patric.dungeonsreborn.quests.QuestYamlRegistry;
import dev.patric.dungeonsreborn.party.PartyService;
import dev.patric.dungeonsreborn.locale.LocaleService;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.menus.UserHubMenu;
import dev.patric.dungeonsreborn.menus.UserSettingsMenu;
import dev.patric.dungeonsreborn.menus.UpgradeApplyMenu;
import dev.patric.dungeonsreborn.menus.AdminHubMenu;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

public final class DungeonsRebornCommand {
  private DungeonsRebornCommand() {
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createCommand(
      String root,
      dev.patric.dungeonsreborn.DungeonsRebornPlugin plugin,
      EffectsEngine engine,
      EffectsYamlAbilities yaml,
      EffectsBindings bindings,
      EditorServices editor,
      MinionManager minions,
      MobYamlRegistry mobsYaml,
      MobRegistry mobsRegistry,
      MobSpawnManager mobSpawns,
      MobDebugOverlayService mobDebugOverlay,
      dev.patric.dungeonsreborn.mobs.MobSpawnerBlockStore spawnerStore,
      dev.patric.dungeonsreborn.mobs.TrialSpawnerBlockStore trialSpawnerStore,
      dev.patric.dungeonsreborn.mobs.VaultBlockStore vaultStore,
      CraftingYamlRegistry crafting,
      CraftingDiscoveryService craftingDiscovery,
      AdvancementService advancements,
      UpgradeService upgrades,
      ShopYamlRegistry shops,
      ShopSessionManager shopSessions,
      KitService kits,
      ClassYamlRegistry classRegistry,
      ClassService classService,
      ClassSkillService classSkills,
      ClassAbilityBindings classAbilityBindings,
      DungeonYamlRegistry dungeonRegistry,
      DungeonQueueService dungeonQueue,
      DungeonSessionManager dungeonSessions,
      QuestYamlRegistry questRegistry,
      QuestService quests,
      QuestGiverYamlRegistry questGivers,
      PartyService parties,
      LocaleService locales
  ) {
    return Commands.literal(root)
        .executes(ctx -> help(ctx, root))
        .then(Commands.literal("help").executes(ctx -> help(ctx, root)))
        .then(Commands.literal("input")
            .then(Commands.argument("message", StringArgumentType.greedyString())
                .executes(ctx -> inputMessage(ctx, StringArgumentType.getString(ctx, "message")))))
        .then(Commands.literal("user").executes(ctx -> openUserHub(ctx, engine, yaml, upgrades, minions, mobsRegistry,
            parties, quests, questGivers, shops, shopSessions, crafting, craftingDiscovery, dungeonRegistry,
            dungeonQueue, dungeonSessions, kits, locales)))
        .then(Commands.literal("crafting")
            .executes(ctx -> openCrafting(ctx))
            .then(Commands.literal("open")
                .executes(ctx -> openCrafting(ctx)))
            .then(Commands.literal("all")
                .executes(ctx -> openCrafting(ctx))))
        .then(Commands.literal("settings").executes(ctx -> openUserSettings(ctx, engine, locales)))
        .then(Commands.literal("admin")
            .executes(ctx -> helpAdmin(ctx, root))
            .then(Commands.literal("help").executes(ctx -> helpAdmin(ctx, root)))
            .then(Commands.literal("gui")
                .executes(ctx -> openAdminHub(ctx, yaml, upgrades, mobsYaml, mobsRegistry, shops, shopSessions, quests,
                    questGivers, crafting, craftingDiscovery, dungeonRegistry, dungeonQueue, dungeonSessions, parties,
                    advancements, kits, classRegistry)))
            .then(Commands.literal("reload").executes(ctx -> reloadAll(ctx, plugin, yaml, mobsYaml, mobsRegistry,
                crafting, advancements, upgrades, shops, kits, classRegistry, dungeonRegistry, questRegistry,
                questGivers, locales, spawnerStore)))
            .then(Commands.literal("textures")
                .executes(ctx -> texturesStats(ctx, plugin))
                .then(Commands.literal("rebuild").executes(ctx -> texturesRebuild(ctx, plugin)))
                .then(Commands.literal("stats").executes(ctx -> texturesStats(ctx, plugin)))
                .then(Commands.literal("validate").executes(ctx -> texturesValidate(ctx, plugin)))
                .then(Commands.literal("send")
                    .then(Commands.literal("all").executes(ctx -> texturesSendAll(ctx, plugin)))
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOnlinePlayers(ctx, builder))
                        .executes(ctx -> texturesSendPlayer(ctx, plugin, StringArgumentType.getString(ctx, "player"))))))
            .then(Commands.literal("locale")
                .then(Commands.literal("reload").executes(ctx -> localeReload(ctx, locales)))
                .then(Commands.literal("validate").executes(ctx -> localeValidate(ctx, locales))))
            .then(Commands.literal("advancements")
                .then(Commands.literal("reload")
                    .executes(ctx -> advancementsReload(ctx, advancements, mobsRegistry, dungeonRegistry))))
            .then(Commands.literal("crafting")
                .then(Commands.literal("discover")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestRecipes(crafting, builder))
                        .executes(ctx -> craftingDiscover(ctx, crafting, craftingDiscovery,
                            StringArgumentType.getString(ctx, "id"), null, false))
                        .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> suggestOnlinePlayers(ctx, builder))
                            .executes(ctx -> craftingDiscover(ctx, crafting, craftingDiscovery,
                                StringArgumentType.getString(ctx, "id"),
                                StringArgumentType.getString(ctx, "player"),
                                false)))))
                .then(Commands.literal("research")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestRecipes(crafting, builder))
                        .executes(ctx -> craftingDiscover(ctx, crafting, craftingDiscovery,
                            StringArgumentType.getString(ctx, "id"), null, true))
                        .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> suggestOnlinePlayers(ctx, builder))
                            .executes(ctx -> craftingDiscover(ctx, crafting, craftingDiscovery,
                                StringArgumentType.getString(ctx, "id"),
                                StringArgumentType.getString(ctx, "player"),
                                true)))))
                .then(Commands.literal("validate")
                    .executes(ctx -> craftingValidate(ctx, crafting))))
            .then(Commands.literal("give")
                .then(Commands.literal("item")
                    .then(Commands.argument("item", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestItems(yaml, builder))
                        .executes(ctx -> itemsGive(ctx, yaml, StringArgumentType.getString(ctx, "item"), null, 1))
                        .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> suggestOnlinePlayers(ctx, builder))
                            .executes(ctx -> itemsGive(ctx, yaml, StringArgumentType.getString(ctx, "item"),
                                StringArgumentType.getString(ctx, "player"), 1))
                            .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> itemsGive(ctx, yaml, StringArgumentType.getString(ctx, "item"),
                                    StringArgumentType.getString(ctx, "player"),
                                    IntegerArgumentType.getInteger(ctx, "amount")))))))
                .then(Commands.literal("upgrade")
                    .then(Commands.argument("upgrade", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestUpgrades(upgrades, builder))
                        .executes(ctx -> upgradesGive(ctx, upgrades, StringArgumentType.getString(ctx, "upgrade")))
                        .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> suggestOnlinePlayers(ctx, builder))
                            .executes(ctx -> upgradesGive(ctx, upgrades,
                                StringArgumentType.getString(ctx, "upgrade"),
                                StringArgumentType.getString(ctx, "player"))))))
                .then(Commands.literal("mob_egg")
                    .then(Commands.argument("mob", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestMobs(mobsRegistry, builder))
                        .executes(ctx -> giveMobEgg(ctx, mobsYaml, mobsRegistry,
                            StringArgumentType.getString(ctx, "mob"), null))
                        .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> suggestOnlinePlayers(ctx, builder))
                            .executes(ctx -> giveMobEgg(ctx, mobsYaml, mobsRegistry,
                                StringArgumentType.getString(ctx, "mob"),
                                StringArgumentType.getString(ctx, "player"))))))
                .then(Commands.literal("spawner")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestSpawnerBlockIds(mobsYaml, builder))
                        .executes(ctx -> giveSpawner(ctx, mobsYaml, StringArgumentType.getString(ctx, "id"), null))
                        .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> suggestOnlinePlayers(ctx, builder))
                            .executes(ctx -> giveSpawner(ctx, mobsYaml, StringArgumentType.getString(ctx, "id"),
                                StringArgumentType.getString(ctx, "player"))))))
                .then(Commands.literal("recipe")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestRecipes(crafting, builder))
                        .executes(ctx -> giveRecipe(ctx, crafting, StringArgumentType.getString(ctx, "id"), null))
                        .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> suggestOnlinePlayers(ctx, builder))
                            .executes(ctx -> giveRecipe(ctx, crafting, StringArgumentType.getString(ctx, "id"),
                                StringArgumentType.getString(ctx, "player"))))))
                .then(Commands.literal("shop_token")
                    .then(Commands.argument("tier", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestShopTokens(shops, builder))
                        .executes(ctx -> giveShopToken(ctx, shops, StringArgumentType.getString(ctx, "tier"), null, 1))
                        .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> suggestOnlinePlayers(ctx, builder))
                            .executes(ctx -> giveShopToken(ctx, shops, StringArgumentType.getString(ctx, "tier"),
                                StringArgumentType.getString(ctx, "player"), 1))
                            .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> giveShopToken(ctx, shops, StringArgumentType.getString(ctx, "tier"),
                                    StringArgumentType.getString(ctx, "player"),
                                    IntegerArgumentType.getInteger(ctx, "amount"))))))))
            .then(Commands.literal("items")
                .then(Commands.literal("give")
                    .then(Commands.argument("item", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestItems(yaml, builder))
                        .executes(ctx -> itemsGive(ctx, yaml, StringArgumentType.getString(ctx, "item"), null, 1))
                        .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> suggestOnlinePlayers(ctx, builder))
                            .executes(ctx -> itemsGive(ctx, yaml, StringArgumentType.getString(ctx, "item"),
                                StringArgumentType.getString(ctx, "player"), 1))
                            .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> itemsGive(ctx, yaml, StringArgumentType.getString(ctx, "item"),
                                    StringArgumentType.getString(ctx, "player"),
                                    IntegerArgumentType.getInteger(ctx, "amount")))))))))
            .then(EffectsCommand.createCommand(engine, yaml, bindings, editor, minions))
            .then(MobsCommand.createCommand(mobsYaml, mobsRegistry, mobSpawns, spawnerStore, trialSpawnerStore, vaultStore,
                mobDebugOverlay))
            .then(ShopsCommand.createCommand(shops, shopSessions))
            .then(KitsCommand.createAdminCommand(kits))
            .then(ClassesCommand.createAdminCommand(classRegistry, classService, classSkills, classAbilityBindings))
            .then(DungeonCommand.createAdminCommand(dungeonRegistry, dungeonQueue, dungeonSessions, parties))
            .then(QuestsCommand.createAdminCommand(quests, questGivers, parties))
            .then(PartyCommand.createCommand(parties))
            .then(ChatCommand.createCommand(parties))
            .then(Commands.literal("upgrades")
                .then(Commands.literal("open").executes(ctx -> upgradesOpen(ctx, upgrades)))
                .then(Commands.literal("reload").executes(ctx -> upgradesReload(ctx, upgrades)))
                .then(Commands.literal("debug")
                    .then(Commands.literal("scan").executes(ctx -> upgradesDebugScan(ctx, upgrades)))
                    .then(Commands.literal("validate").executes(ctx -> upgradesDebugValidate(ctx, upgrades))))
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
                .then(Commands.literal("info")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestRecipes(crafting, builder))
                        .executes(ctx -> craftingInfo(ctx, crafting, StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("reload").executes(ctx -> craftingReload(ctx, plugin, crafting))))
        .then(KitsCommand.createUserCommand(kits))
        .then(ClassesCommand.createUserCommand(classRegistry, classService, classSkills, classAbilityBindings))
        .then(DungeonCommand.createUserCommand(dungeonRegistry, dungeonQueue, dungeonSessions, parties))
        .then(QuestsCommand.createUserCommand(quests, questGivers, parties))
        .then(PartyCommand.createCommand(parties))
        .then(ChatCommand.createCommand(parties));
  }

  private static int help(CommandContext<CommandSourceStack> ctx, String root) {
    var sender = ctx.getSource().getSender();
    var placeholders = Locales.placeholders("root", root);
    CommandMessages.send(sender, "messages.command.help.header", placeholders);
    CommandMessages.send(sender, "messages.command.help.root", placeholders);
    CommandMessages.send(sender, "messages.command.help.user", placeholders);
    CommandMessages.send(sender, "messages.command.help.settings", placeholders);
    CommandMessages.send(sender, "messages.command.help.kits", placeholders);
    CommandMessages.send(sender, "messages.command.help.classes", placeholders);
    CommandMessages.send(sender, "messages.command.help.dungeon", placeholders);
    CommandMessages.send(sender, "messages.command.help.dungeonJoin", placeholders);
    CommandMessages.send(sender, "messages.command.help.dungeonLeave", placeholders);
    CommandMessages.send(sender, "messages.command.help.party", placeholders);
    CommandMessages.send(sender, "messages.command.help.partyChat", placeholders);
    CommandMessages.send(sender, "messages.command.help.partyMessage", placeholders);
    return Command.SINGLE_SUCCESS;
  }

  private static int helpAdmin(CommandContext<CommandSourceStack> ctx, String root) {
    var sender = ctx.getSource().getSender();
    var placeholders = Locales.placeholders("root", root);
    CommandMessages.send(sender, "messages.command.help.adminHeader", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminRoot", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminReload", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminLocaleReload", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminLocaleValidate", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminAdvancementsReload", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminGive", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminEffects", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminMobs", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminTextures", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminShops", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminItemsGive", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminKitsReload", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminClassesReload", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminClassesValidate", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminClassesExport", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminClassesImport", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminDungeonReload", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminDungeonValidate", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminDungeonDebug", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminQuestsReload", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminUpgradesReload", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminUpgradesGive", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminCraftingReload", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminCraftingValidate", placeholders);
    return Command.SINGLE_SUCCESS;
  }

  private static int openUserHub(CommandContext<CommandSourceStack> ctx,
      EffectsEngine engine,
      EffectsYamlAbilities yaml,
      UpgradeService upgrades,
      MinionManager minions,
      MobRegistry mobs,
      PartyService parties,
      QuestService quests,
      QuestGiverYamlRegistry questGivers,
      ShopYamlRegistry shops,
      ShopSessionManager shopSessions,
      CraftingYamlRegistry crafting,
      CraftingDiscoveryService craftingDiscovery,
      DungeonYamlRegistry dungeonRegistry,
      DungeonQueueService dungeonQueue,
      DungeonSessionManager dungeonSessions,
      KitService kits,
      LocaleService locales) {
    var sender = ctx.getSource().getSender();
    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    UserHubMenu.open(player, locales, engine, yaml, upgrades, minions, mobs, parties, quests, questGivers, shops,
        shopSessions, crafting, craftingDiscovery, dungeonRegistry, dungeonQueue, dungeonSessions, kits);
    return Command.SINGLE_SUCCESS;
  }

  private static int openAdminHub(CommandContext<CommandSourceStack> ctx,
      EffectsYamlAbilities yaml,
      UpgradeService upgrades,
      MobYamlRegistry mobsYaml,
      MobRegistry mobsRegistry,
      ShopYamlRegistry shops,
      ShopSessionManager shopSessions,
      QuestService quests,
      QuestGiverYamlRegistry questGivers,
      CraftingYamlRegistry crafting,
      CraftingDiscoveryService craftingDiscovery,
      DungeonYamlRegistry dungeonRegistry,
      DungeonQueueService dungeonQueue,
      DungeonSessionManager dungeonSessions,
      PartyService parties,
      AdvancementService advancements,
      KitService kits,
      ClassYamlRegistry classes) {
    var sender = ctx.getSource().getSender();
    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (!player.hasPermission("dungeonsreborn.admin.gui")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.admin.gui"));
      return Command.SINGLE_SUCCESS;
    }
    AdminHubMenu.open(player, yaml, upgrades, mobsRegistry, mobsYaml, kits, shops, shopSessions, quests, questGivers,
        crafting, craftingDiscovery, dungeonRegistry, dungeonQueue, dungeonSessions, parties, advancements, classes);
    return Command.SINGLE_SUCCESS;
  }

  private static int openUserSettings(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, LocaleService locales) {
    var sender = ctx.getSource().getSender();
    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    dev.patric.dungeonsreborn.gui.GuiManager.get().open(player, new UserSettingsMenu(locales, engine));
    return Command.SINGLE_SUCCESS;
  }

  private static int openCrafting(CommandContext<CommandSourceStack> ctx) {
    var sender = ctx.getSource().getSender();
    if (!(ctx.getSource().getExecutor() instanceof Player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    CommandMessages.send(sender, "messages.command.crafting.vanillaOnly");
    return Command.SINGLE_SUCCESS;
  }

  private static int inputMessage(CommandContext<CommandSourceStack> ctx, String message) {
    var sender = ctx.getSource().getSender();
    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    boolean accepted = dev.patric.dungeonsreborn.gui.GuiManager.get().submitText(player, message);
    if (!accepted) {
      CommandMessages.send(sender, "messages.gui.textInput.none");
    }
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
    GuiI18n.setDefaultLocale(Locale.forLanguageTag(locales.defaultLocale()));
    if (result.errors().isEmpty()) {
      CommandMessages.send(sender, "messages.locale.reload.ok", Locales.placeholders("count", result.locales()));
    } else {
      CommandMessages.send(sender, "messages.locale.reload.errors", Locales.placeholders("errors", result.errors().size()));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int localeValidate(CommandContext<CommandSourceStack> ctx, LocaleService locales) {
    var sender = ctx.getSource().getSender();
    if (sender instanceof org.bukkit.entity.Player player && !player.hasPermission("dungeonsreborn.locale.validate")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.locale.validate"));
      return 1;
    }
    if (locales == null) {
      CommandMessages.send(sender, "messages.locale.validate.unavailable");
      return Command.SINGLE_SUCCESS;
    }
    LocaleService.CoverageResult result = locales.validateCoverage();
    CommandMessages.send(sender, "messages.locale.validate.header",
        Locales.placeholders("total", result.totalKeys()));
    if (!result.hasMissing()) {
      CommandMessages.send(sender, "messages.locale.validate.ok");
      return Command.SINGLE_SUCCESS;
    }
    List<String> localesSorted = new ArrayList<>(result.missingByLocale().keySet());
    localesSorted.sort(String::compareTo);
    for (String locale : localesSorted) {
      List<String> missing = result.missingByLocale().get(locale);
      if (missing == null || missing.isEmpty()) {
        continue;
      }
      CommandMessages.send(sender, "messages.locale.validate.localeHeader",
          Locales.placeholders("locale", locale, "count", missing.size()));
      int shown = Math.min(missing.size(), 10);
      for (int i = 0; i < shown; i++) {
        CommandMessages.send(sender, "messages.locale.validate.entry",
            Locales.placeholders("key", missing.get(i)));
      }
      int remaining = missing.size() - shown;
      if (remaining > 0) {
        CommandMessages.send(sender, "messages.locale.validate.more",
            Locales.placeholders("count", remaining));
      }
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int advancementsReload(CommandContext<CommandSourceStack> ctx,
      AdvancementService advancements,
      MobRegistry mobsRegistry,
      DungeonYamlRegistry dungeonRegistry) {
    var sender = ctx.getSource().getSender();
    if (advancements == null || !advancements.isEnabled()) {
      CommandMessages.send(sender, "messages.advancements.reload.disabled");
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof org.bukkit.entity.Player player
        && !player.hasPermission("dungeonsreborn.advancements.reload")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.advancements.reload"));
      return 1;
    }
    advancements.reloadAll(mobsRegistry, dungeonRegistry);
    CommandMessages.send(sender, "messages.advancements.reload.ok");
    return Command.SINGLE_SUCCESS;
  }

  private static int reloadAll(CommandContext<CommandSourceStack> ctx,
      dev.patric.dungeonsreborn.DungeonsRebornPlugin plugin,
      EffectsYamlAbilities yaml,
      MobYamlRegistry mobsYaml,
      MobRegistry mobsRegistry,
      CraftingYamlRegistry crafting,
      AdvancementService advancements,
      UpgradeService upgrades,
      ShopYamlRegistry shops,
      KitService kits,
      ClassYamlRegistry classes,
      DungeonYamlRegistry dungeons,
      QuestYamlRegistry quests,
      QuestGiverYamlRegistry questGivers,
      LocaleService locales,
      dev.patric.dungeonsreborn.mobs.MobSpawnerBlockStore spawnerStore) {
    var sender = ctx.getSource().getSender();
    if (sender instanceof org.bukkit.entity.Player player && !player.hasPermission("dungeonsreborn.reload")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.reload"));
      return 1;
    }
    if (plugin != null) {
      plugin.reloadConfig();
      plugin.reloadLogging();
      plugin.reloadRuntimeConfig();
      plugin.reloadScoreboardConfig();
      plugin.reloadHeadRegistry();
    }
    TextureBuildResult texturesResult = plugin == null ? null : plugin.reloadTextures();

    LocaleService.ReloadResult localeResult = locales == null ? null : locales.reload();
    if (locales != null) {
      GuiI18n.setDefaultLocale(Locale.forLanguageTag(locales.defaultLocale()));
    }
    EffectsYamlAbilities.ReloadResult effectsResult = yaml == null ? null : yaml.reload();
    if (yaml != null) {
      yaml.syncOnlineItems();
    }
    MobYamlRegistry.ReloadResult mobsResult = mobsYaml == null ? null : mobsYaml.reload();
    if (spawnerStore != null) {
      spawnerStore.load();
    }
    CraftingYamlRegistry.ReloadResult craftingResult = crafting == null ? null : crafting.reload();
    if (plugin != null) {
      plugin.rebuildVanillaCrafting();
    }
    UpgradeYamlRegistry.ReloadResult upgradesResult = upgrades == null ? null : upgrades.registry().reload();
    ShopYamlRegistry.ReloadResult shopsResult = shops == null ? null : shops.reload();
    KitYamlRegistry.ReloadResult kitsResult = kits == null ? null : kits.registry().reload();
    ClassYamlRegistry.ReloadResult classesResult = classes == null ? null : classes.reload();
    DungeonYamlRegistry.ReloadResult dungeonsResult = dungeons == null ? null : dungeons.reload();
    QuestYamlRegistry.ReloadResult questsResult = quests == null ? null : quests.reload();
    QuestGiverYamlRegistry.ReloadResult questGiversResult = questGivers == null ? null : questGivers.reload();
    if (advancements != null && advancements.isEnabled()) {
      advancements.reloadAll(mobsRegistry, dungeons);
    }

    int errors = 0;
    errors += errors(localeResult);
    errors += errors(texturesResult);
    errors += errors(effectsResult);
    errors += errors(mobsResult);
    errors += errors(craftingResult);
    errors += errors(upgradesResult);
    errors += errors(shopsResult);
    errors += errors(kitsResult);
    errors += errors(classesResult);
    errors += errors(dungeonsResult);
    errors += errors(questsResult);
    errors += errors(questGiversResult);

    if (errors == 0) {
      CommandMessages.send(sender, "messages.command.reload.allOk");
    } else {
      CommandMessages.send(sender, "messages.command.reload.allErrors", Locales.placeholders("errors", errors));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int texturesRebuild(CommandContext<CommandSourceStack> ctx,
      dev.patric.dungeonsreborn.DungeonsRebornPlugin plugin) {
    var sender = ctx.getSource().getSender();
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.reload")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.reload"));
      return 1;
    }
    TextureService textures = plugin == null ? null : plugin.textureService();
    if (textures == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", "textures"));
      return Command.SINGLE_SUCCESS;
    }
    TextureBuildResult result = textures.rebuild();
    CommandMessages.send(sender, "messages.command.textures.rebuild.summary",
        Locales.placeholders(
            "textures", result.texturesDiscovered(),
            "models", result.modelsWritten(),
            "warnings", result.warningCount(),
            "errors", result.errorCount()));
    if (result.zipFile() != null) {
      CommandMessages.send(sender, "messages.command.textures.rebuild.zip",
          Locales.placeholders(
              "path", result.zipFile().getPath(),
              "sha1", result.zipSha1()));
    }
    String deliveryUrl = textures.deliveryUrl();
    if (!deliveryUrl.isBlank()) {
      CommandMessages.send(sender, "messages.command.textures.delivery",
          Locales.placeholders("url", deliveryUrl));
    }
    if (result.errorCount() > 0 && result.errors() != null) {
      int shown = Math.min(10, result.errors().size());
      for (int i = 0; i < shown; i++) {
        CommandMessages.send(sender, "messages.command.textures.entry",
            Locales.placeholders("message", result.errors().get(i)));
      }
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int texturesStats(CommandContext<CommandSourceStack> ctx,
      dev.patric.dungeonsreborn.DungeonsRebornPlugin plugin) {
    var sender = ctx.getSource().getSender();
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.reload")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.reload"));
      return 1;
    }
    TextureService textures = plugin == null ? null : plugin.textureService();
    if (textures == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", "textures"));
      return Command.SINGLE_SUCCESS;
    }
    TextureService.TextureStats stats = textures.stats();
    CommandMessages.send(sender, "messages.command.textures.stats.header");
    CommandMessages.send(sender, "messages.command.textures.stats.entry",
        Locales.placeholders(
            "enabled", stats.enabled(),
            "textures", stats.discoveredTextures(),
            "models", stats.modelMappings(),
            "warnings", stats.warnings(),
            "errors", stats.errors()));
    if (!stats.zipPath().isBlank()) {
      CommandMessages.send(sender, "messages.command.textures.rebuild.zip",
          Locales.placeholders(
              "path", stats.zipPath(),
              "sha1", stats.zipSha1() == null ? "" : stats.zipSha1()));
    }
    if (!stats.deliveryUrl().isBlank()) {
      CommandMessages.send(sender, "messages.command.textures.delivery",
          Locales.placeholders("url", stats.deliveryUrl()));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int texturesValidate(CommandContext<CommandSourceStack> ctx,
      dev.patric.dungeonsreborn.DungeonsRebornPlugin plugin) {
    var sender = ctx.getSource().getSender();
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.reload")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.reload"));
      return 1;
    }
    TextureService textures = plugin == null ? null : plugin.textureService();
    if (textures == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", "textures"));
      return Command.SINGLE_SUCCESS;
    }
    List<String> entries = textures.validate();
    if (entries.isEmpty()) {
      CommandMessages.send(sender, "messages.command.textures.validate.ok");
      return Command.SINGLE_SUCCESS;
    }
    CommandMessages.send(sender, "messages.command.textures.validate.summary",
        Locales.placeholders("count", entries.size()));
    int shown = Math.min(20, entries.size());
    for (int i = 0; i < shown; i++) {
      CommandMessages.send(sender, "messages.command.textures.entry",
          Locales.placeholders("message", entries.get(i)));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int texturesSendAll(CommandContext<CommandSourceStack> ctx,
      dev.patric.dungeonsreborn.DungeonsRebornPlugin plugin) {
    var sender = ctx.getSource().getSender();
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.reload")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.reload"));
      return 1;
    }
    TextureService textures = plugin == null ? null : plugin.textureService();
    if (textures == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", "textures"));
      return Command.SINGLE_SUCCESS;
    }
    int sent = textures.sendConfiguredPackToAll();
    CommandMessages.send(sender, "messages.command.textures.send.count",
        Locales.placeholders("count", sent));
    return Command.SINGLE_SUCCESS;
  }

  private static int texturesSendPlayer(CommandContext<CommandSourceStack> ctx,
      dev.patric.dungeonsreborn.DungeonsRebornPlugin plugin, String playerName) {
    var sender = ctx.getSource().getSender();
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.reload")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.reload"));
      return 1;
    }
    TextureService textures = plugin == null ? null : plugin.textureService();
    if (textures == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", "textures"));
      return Command.SINGLE_SUCCESS;
    }
    Player player = Bukkit.getPlayerExact(playerName);
    if (player == null) {
      CommandMessages.send(sender, "messages.command.effects.playerNotFound");
      return Command.SINGLE_SUCCESS;
    }
    boolean sent = textures.sendPack(player);
    CommandMessages.send(sender, sent
        ? "messages.command.textures.send.player"
        : "messages.command.textures.send.failed",
        Locales.placeholders("player", player.getName()));
    return Command.SINGLE_SUCCESS;
  }

  private static int errors(EffectsYamlAbilities.ReloadResult result) {
    return result == null || result.errors() == null ? 0 : result.errors().size();
  }

  private static int errors(TextureBuildResult result) {
    return result == null ? 0 : result.errorCount();
  }

  private static int errors(MobYamlRegistry.ReloadResult result) {
    return result == null || result.errors() == null ? 0 : result.errors().size();
  }

  private static int errors(CraftingYamlRegistry.ReloadResult result) {
    return result == null || result.errors() == null ? 0 : result.errors().size();
  }

  private static int errors(UpgradeYamlRegistry.ReloadResult result) {
    return result == null || result.errors() == null ? 0 : result.errors().size();
  }

  private static int errors(ShopYamlRegistry.ReloadResult result) {
    return result == null || result.errors() == null ? 0 : result.errors().size();
  }

  private static int errors(KitYamlRegistry.ReloadResult result) {
    return result == null || result.errors() == null ? 0 : result.errors().size();
  }

  private static int errors(ClassYamlRegistry.ReloadResult result) {
    return result == null || result.errors() == null ? 0 : result.errors().size();
  }

  private static int errors(DungeonYamlRegistry.ReloadResult result) {
    return result == null || result.errors() == null ? 0 : result.errors().size();
  }

  private static int errors(QuestYamlRegistry.ReloadResult result) {
    return result == null || result.errors() == null ? 0 : result.errors().size();
  }

  private static int errors(QuestGiverYamlRegistry.ReloadResult result) {
    return result == null || result.errors() == null ? 0 : result.errors().size();
  }

  private static int errors(LocaleService.ReloadResult result) {
    return result == null || result.errors() == null ? 0 : result.errors().size();
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

  private static int upgradesOpen(CommandContext<CommandSourceStack> ctx, UpgradeService upgrades) {
    var sender = ctx.getSource().getSender();
    if (!(sender instanceof Player player)) {
      sender.sendMessage(CommandMessages.text(sender, "messages.command.playerOnly"));
      return Command.SINGLE_SUCCESS;
    }
    if (upgrades == null) {
      sender.sendMessage(CommandMessages.text(sender, "labels.system.upgrades"));
      return Command.SINGLE_SUCCESS;
    }
    UpgradeApplyMenu.open(player, upgrades);
    return Command.SINGLE_SUCCESS;
  }


  private static int upgradesDebugScan(CommandContext<CommandSourceStack> ctx, UpgradeService upgrades) {
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
    ItemStack item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      CommandMessages.send(sender, "messages.command.items.holdMainHand");
      return Command.SINGLE_SUCCESS;
    }
    var records = ItemMarkers.getUpgradeRecords(item);
    CommandMessages.send(sender, "messages.command.upgrades.debug.recordsHeader",
        Locales.placeholders("count", String.valueOf(records.size())));
    if (records.isEmpty()) {
      CommandMessages.send(sender, "messages.command.upgrades.debug.none");
      return Command.SINGLE_SUCCESS;
    }
    for (String record : records) {
      String status;
      if (record == null || record.isBlank()) {
        status = CommandMessages.text(sender, "messages.command.upgrades.debug.status.empty");
      } else if (record.startsWith("vanilla:")) {
        status = CommandMessages.text(sender, "messages.command.upgrades.debug.status.vanilla");
      } else if (upgrades.registry().upgradeSpec(record) == null) {
        status = CommandMessages.text(sender, "messages.command.upgrades.debug.status.missing");
      } else {
        status = CommandMessages.text(sender, "messages.command.upgrades.debug.status.ok");
      }
      CommandMessages.send(sender, "messages.command.upgrades.debug.entry",
          Locales.placeholders("record", record == null ? "" : record, "status", status));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int upgradesDebugValidate(CommandContext<CommandSourceStack> ctx, UpgradeService upgrades) {
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
    ItemStack item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      CommandMessages.send(sender, "messages.command.items.holdMainHand");
      return Command.SINGLE_SUCCESS;
    }
    var issues = upgrades.validateItemUpgrades(player, item);
    CommandMessages.send(sender, "messages.command.upgrades.debug.validationHeader");
    if (issues.isEmpty()) {
      CommandMessages.send(sender, "messages.command.upgrades.debug.validationNone");
      return Command.SINGLE_SUCCESS;
    }
    for (String issue : issues) {
      CommandMessages.send(sender, "messages.command.upgrades.debug.validationEntry",
          Locales.placeholders("issue", issue));
    }
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
      CommandMessages.sendClosestMatch(sender, upgradeId, upgrades.registry().upgrades().keySet());
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

  private static int itemsGive(CommandContext<CommandSourceStack> ctx, EffectsYamlAbilities yaml, String itemId,
      String targetName, int amount) {
    var sender = ctx.getSource().getSender();
    if (yaml == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.itemsEditor")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof org.bukkit.entity.Player player && !player.hasPermission("dungeonsreborn.items.give")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.items.give"));
      return Command.SINGLE_SUCCESS;
    }
    Player target = targetName == null ? null : Bukkit.getPlayerExact(targetName);
    if (target == null) {
      if (!(ctx.getSource().getExecutor() instanceof Player player)) {
        CommandMessages.send(sender, "messages.common.playersOnly");
        return Command.SINGLE_SUCCESS;
      }
      target = player;
    }
    ItemStack item = yaml.itemTemplate(itemId);
    if (item == null) {
      CommandMessages.send(sender, "messages.command.unknownItem", Locales.placeholders("id", itemId));
      CommandMessages.sendClosestMatch(sender, itemId, yaml.loadedItemIds());
      return Command.SINGLE_SUCCESS;
    }
    item.setAmount(amount);
    var leftovers = target.getInventory().addItem(item);
    if (!leftovers.isEmpty()) {
      for (ItemStack stack : leftovers.values()) {
        target.getWorld().dropItemNaturally(target.getLocation(), stack);
      }
    }
    CommandMessages.send(sender, "messages.command.itemGiven",
        Locales.placeholders("id", itemId, "player", target.getName(), "amount", amount));
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

  private static CompletableFuture<Suggestions> suggestItems(EffectsYamlAbilities yaml, SuggestionsBuilder builder) {
    if (yaml == null) {
      return builder.buildFuture();
    }
    for (String id : yaml.loadedItemIds()) {
      builder.suggest(id);
    }
    return builder.buildFuture();
  }

  private static CompletableFuture<Suggestions> suggestRecipes(CraftingYamlRegistry crafting, SuggestionsBuilder builder) {
    if (crafting == null) {
      return builder.buildFuture();
    }
    for (String id : crafting.recipes().keySet()) {
      builder.suggest(id);
    }
    return builder.buildFuture();
  }

  private static CompletableFuture<Suggestions> suggestMobs(MobRegistry registry, SuggestionsBuilder builder) {
    if (registry == null) {
      return builder.buildFuture();
    }
    for (String id : registry.ids()) {
      builder.suggest(id);
    }
    return builder.buildFuture();
  }

  private static CompletableFuture<Suggestions> suggestSpawnerBlockIds(MobYamlRegistry yaml,
      SuggestionsBuilder builder) {
    if (yaml == null) {
      return builder.buildFuture();
    }
    for (String id : yaml.spawnerBlockIds()) {
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

  private static CompletableFuture<Suggestions> suggestShopTokens(ShopYamlRegistry shops, SuggestionsBuilder builder) {
    if (shops == null) {
      return builder.buildFuture();
    }
    builder.suggest("token");
    for (String id : shops.tokenTiers().keySet()) {
      builder.suggest(id);
    }
    return builder.buildFuture();
  }

  private static int craftingReload(CommandContext<CommandSourceStack> ctx,
      dev.patric.dungeonsreborn.DungeonsRebornPlugin plugin,
      CraftingYamlRegistry crafting) {
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
    if (plugin != null) {
      plugin.rebuildVanillaCrafting();
    }
    CommandMessages.send(sender, "messages.command.reload.dir",
        Locales.placeholders("path", crafting.recipesDir().getPath()));
    CommandMessages.send(sender, "messages.command.reload.craftingSummary",
        Locales.placeholders("loaded", result.loaded(), "errors", result.errors().size()));
    return Command.SINGLE_SUCCESS;
  }

  private static int craftingValidate(CommandContext<CommandSourceStack> ctx, CraftingYamlRegistry crafting) {
    var sender = ctx.getSource().getSender();
    if (crafting == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.crafting")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof org.bukkit.entity.Player player && !player.hasPermission("dungeonsreborn.crafting.editor")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.crafting.editor"));
      return Command.SINGLE_SUCCESS;
    }
    var errors = crafting.validate();
    if (errors.isEmpty()) {
      CommandMessages.send(sender, "messages.command.crafting.validate.ok");
      return Command.SINGLE_SUCCESS;
    }
    CommandMessages.send(sender, "messages.command.crafting.validate.errors",
        Locales.placeholders("count", errors.size()));
    for (String entry : errors) {
      CommandMessages.send(sender, "messages.command.crafting.validate.entry",
          Locales.placeholders("error", entry));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int craftingInfo(CommandContext<CommandSourceStack> ctx, CraftingYamlRegistry crafting, String id) {
    var sender = ctx.getSource().getSender();
    if (crafting == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.crafting")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.crafting.editor")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.crafting.editor"));
      return Command.SINGLE_SUCCESS;
    }
    CraftingRecipeTemplate template = crafting.recipeTemplate(id);
    if (template == null) {
      CommandMessages.send(sender, "messages.command.crafting.unknown",
          Locales.placeholders("id", id));
      CommandMessages.sendClosestMatch(sender, id, crafting.recipes().keySet());
      return Command.SINGLE_SUCCESS;
    }
    var spec = template.spec();
    CommandMessages.send(sender, "messages.command.crafting.info.header",
        Locales.placeholders("id", spec.id()));
    if (!spec.name().isBlank()) {
      CommandMessages.send(sender, "messages.command.crafting.info.name",
          Locales.placeholders("name", spec.name()));
    }
    if (!spec.description().isBlank()) {
      CommandMessages.send(sender, "messages.command.crafting.info.description",
          Locales.placeholders("description", spec.description()));
    }
    CommandMessages.send(sender, "messages.command.crafting.info.variants",
        Locales.placeholders("count", spec.variants().size()));
    CommandMessages.send(sender, "messages.command.crafting.info.outputs",
        Locales.placeholders("count", spec.outputs().size()));
    if (spec.cooldownSeconds() > 0.0) {
      CommandMessages.send(sender, "messages.command.crafting.info.cooldown",
          Locales.placeholders("seconds", String.format(java.util.Locale.ROOT, "%.1f", spec.cooldownSeconds())));
    }
    if (!spec.permissions().isEmpty()) {
      CommandMessages.send(sender, "messages.command.crafting.info.permissions",
          Locales.placeholders("count", spec.permissions().size()));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int craftingDiscover(CommandContext<CommandSourceStack> ctx, CraftingYamlRegistry crafting,
      CraftingDiscoveryService discovery, String id, String targetName, boolean research) {
    var sender = ctx.getSource().getSender();
    if (crafting == null || discovery == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.crafting")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.crafting.editor")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.crafting.editor"));
      return Command.SINGLE_SUCCESS;
    }
    Player target = targetName == null ? null : Bukkit.getPlayerExact(targetName);
    if (target == null && sender instanceof Player player) {
      target = player;
    }
    if (target == null) {
      CommandMessages.send(sender, "messages.command.targetNotFound");
      return Command.SINGLE_SUCCESS;
    }
    CraftingRecipeTemplate template = crafting.recipeTemplate(id);
    if (template == null) {
      CommandMessages.send(sender, "messages.command.crafting.unknown",
          Locales.placeholders("id", id));
      CommandMessages.sendClosestMatch(sender, id, crafting.recipes().keySet());
      return Command.SINGLE_SUCCESS;
    }
    boolean changed;
    if (research) {
      changed = discovery.beginResearch(target.getUniqueId(), template.spec());
      if (changed) {
        CommandMessages.send(sender, "messages.command.crafting.researchStarted",
            Locales.placeholders("id", template.spec().id(), "player", target.getName()));
      } else {
        CommandMessages.send(sender, "messages.command.crafting.researchAlready",
            Locales.placeholders("id", template.spec().id(), "player", target.getName()));
      }
    } else {
      changed = discovery.unlock(target.getUniqueId(), template.spec().id(), "admin");
      if (changed) {
        CommandMessages.send(sender, "messages.command.crafting.discovered",
            Locales.placeholders("id", template.spec().id(), "player", target.getName()));
      } else {
        CommandMessages.send(sender, "messages.command.crafting.discoveryAlready",
            Locales.placeholders("id", template.spec().id(), "player", target.getName()));
      }
    }
    if (changed) {
      discovery.save();
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int giveMobEgg(CommandContext<CommandSourceStack> ctx, MobYamlRegistry yaml, MobRegistry registry,
      String mobId, String targetName) {
    var sender = ctx.getSource().getSender();
    if (yaml == null || registry == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.mobsYaml")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.mobs.egg.give")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.mobs.egg.give"));
      return Command.SINGLE_SUCCESS;
    }
    Player target = targetName == null ? null : Bukkit.getPlayerExact(targetName);
    if (target == null) {
      if (!(ctx.getSource().getExecutor() instanceof Player player)) {
        CommandMessages.send(sender, "messages.common.playersOnly");
        return Command.SINGLE_SUCCESS;
      }
      target = player;
    }
    var item = yaml.eggItemForMob(mobId);
    if (item == null) {
      CommandMessages.send(sender, "messages.command.mobs.eggUnknown",
          Locales.placeholders("id", mobId));
      CommandMessages.sendClosestMatch(sender, mobId, registry.ids());
      return Command.SINGLE_SUCCESS;
    }
    var leftovers = target.getInventory().addItem(item);
    if (!leftovers.isEmpty()) {
      for (ItemStack stack : leftovers.values()) {
        target.getWorld().dropItemNaturally(target.getLocation(), stack);
      }
    }
    CommandMessages.send(sender, "messages.command.mobs.eggGiven",
        Locales.placeholders("id", mobId));
    return Command.SINGLE_SUCCESS;
  }

  private static int giveSpawner(CommandContext<CommandSourceStack> ctx, MobYamlRegistry yaml, String id,
      String targetName) {
    var sender = ctx.getSource().getSender();
    if (yaml == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.mobSpawners")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.mobs.spawner.give")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.mobs.spawner.give"));
      return Command.SINGLE_SUCCESS;
    }
    Player target = targetName == null ? null : Bukkit.getPlayerExact(targetName);
    if (target == null) {
      if (!(ctx.getSource().getExecutor() instanceof Player player)) {
        CommandMessages.send(sender, "messages.common.playersOnly");
        return Command.SINGLE_SUCCESS;
      }
      target = player;
    }
    ItemStack item = yaml.spawnerBlockItem(id);
    if (item == null) {
      CommandMessages.send(sender, "messages.command.mobs.spawnerBlockUnknown",
          Locales.placeholders("id", id));
      CommandMessages.sendClosestMatch(sender, id, yaml.spawnerBlockIds());
      return Command.SINGLE_SUCCESS;
    }
    var leftovers = target.getInventory().addItem(item);
    if (!leftovers.isEmpty()) {
      for (ItemStack stack : leftovers.values()) {
        target.getWorld().dropItemNaturally(target.getLocation(), stack);
      }
    }
    CommandMessages.send(sender, "messages.command.mobs.spawnerGiveBlock",
        Locales.placeholders("id", id));
    return Command.SINGLE_SUCCESS;
  }

  private static int giveRecipe(CommandContext<CommandSourceStack> ctx, CraftingYamlRegistry crafting, String id,
      String targetName) {
    var sender = ctx.getSource().getSender();
    if (crafting == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.crafting")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.crafting.editor")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.crafting.editor"));
      return Command.SINGLE_SUCCESS;
    }
    Player target = targetName == null ? null : Bukkit.getPlayerExact(targetName);
    if (target == null) {
      if (!(ctx.getSource().getExecutor() instanceof Player player)) {
        CommandMessages.send(sender, "messages.common.playersOnly");
        return Command.SINGLE_SUCCESS;
      }
      target = player;
    }
    CraftingRecipeTemplate template = crafting.recipeTemplate(id);
    if (template == null) {
      CommandMessages.send(sender, "messages.command.crafting.unknown",
          Locales.placeholders("id", id));
      CommandMessages.sendClosestMatch(sender, id, crafting.recipes().keySet());
      return Command.SINGLE_SUCCESS;
    }
    ItemStack item = template.outputTemplate();
    if (item == null) {
      CommandMessages.send(sender, "messages.command.crafting.outputMissing",
          Locales.placeholders("id", id));
      return Command.SINGLE_SUCCESS;
    }
    var leftovers = target.getInventory().addItem(item);
    if (!leftovers.isEmpty()) {
      for (ItemStack stack : leftovers.values()) {
        target.getWorld().dropItemNaturally(target.getLocation(), stack);
      }
    }
    CommandMessages.send(sender, "messages.command.crafting.given",
        Locales.placeholders("id", id, "player", target.getName()));
    return Command.SINGLE_SUCCESS;
  }

  private static int giveShopToken(CommandContext<CommandSourceStack> ctx, ShopYamlRegistry shops, String tier,
      String targetName, int amount) {
    var sender = ctx.getSource().getSender();
    if (shops == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.shopRegistry")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.shop.give")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.shop.give"));
      return Command.SINGLE_SUCCESS;
    }
    Player target = targetName == null ? null : Bukkit.getPlayerExact(targetName);
    if (target == null) {
      if (!(ctx.getSource().getExecutor() instanceof Player player)) {
        CommandMessages.send(sender, "messages.common.playersOnly");
        return Command.SINGLE_SUCCESS;
      }
      target = player;
    }
    ItemStack token = shops.resolveTokenItem(tier);
    if (token == null) {
      CommandMessages.send(sender, "messages.command.shops.tokenUnknown",
          Locales.placeholders("id", tier));
      java.util.ArrayList<String> options = new java.util.ArrayList<>();
      options.add("token");
      options.addAll(shops.tokenTiers().keySet());
      CommandMessages.sendClosestMatch(sender, tier, options);
      return Command.SINGLE_SUCCESS;
    }
    int clamped = Math.max(1, amount);
    token.setAmount(clamped);
    var leftovers = target.getInventory().addItem(token);
    if (!leftovers.isEmpty()) {
      for (ItemStack stack : leftovers.values()) {
        target.getWorld().dropItemNaturally(target.getLocation(), stack);
      }
    }
    CommandMessages.send(sender, "messages.command.shops.tokenGive",
        Locales.placeholders("amount", clamped, "player", target.getName()));
    return Command.SINGLE_SUCCESS;
  }

}
