package dev.patric.dungeonsreborn.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;
import dev.patric.dungeonsreborn.effects.editor.EditorServices;
import dev.patric.dungeonsreborn.effects.integration.EffectsBindings;
import dev.patric.dungeonsreborn.crafting.CraftingGuiSessionManager;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeTemplate;
import dev.patric.dungeonsreborn.crafting.CraftingYamlRegistry;
import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.mobs.MobSpawnManager;
import dev.patric.dungeonsreborn.mobs.MobSpawnSpec;
import dev.patric.dungeonsreborn.mobs.MobSpawnerItems;
import dev.patric.dungeonsreborn.mobs.MobYamlRegistry;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import dev.patric.dungeonsreborn.advancements.AdvancementService;
import dev.patric.dungeonsreborn.effects.minions.MinionManager;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeService;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeYamlRegistry;
import dev.patric.dungeonsreborn.kits.KitService;
import dev.patric.dungeonsreborn.kits.KitYamlRegistry;
import dev.patric.dungeonsreborn.shops.ShopSessionManager;
import dev.patric.dungeonsreborn.menus.CraftingRecipeEditorMenu;
import dev.patric.dungeonsreborn.menus.CraftingTestMenu;
import dev.patric.dungeonsreborn.menus.AdminHubMenu;
import dev.patric.dungeonsreborn.menus.CraftablesIndexMenu;
import dev.patric.dungeonsreborn.menus.ItemIndexMenu;
import dev.patric.dungeonsreborn.menus.MobIndexMenu;
import dev.patric.dungeonsreborn.menus.QuestGiverIndexMenu;
import dev.patric.dungeonsreborn.menus.ShopIndexMenu;
import dev.patric.dungeonsreborn.menus.SystemStatusMenu;
import dev.patric.dungeonsreborn.menus.UpgradeIndexMenu;
import dev.patric.dungeonsreborn.menus.UserHubMenu;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.classes.ClassAbilityBindings;
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
      dev.patric.dungeonsreborn.DungeonsRebornPlugin plugin,
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
        .executes(ctx -> openHub(ctx, classRegistry, classService, classSkills,
            dungeonRegistry, dungeonQueue, dungeonSessions, quests, questGivers, parties, crafting, craftingSessions,
            advancements, upgrades, kits, mobsRegistry, yaml, shops))
        .then(Commands.literal("help").executes(ctx -> help(ctx, root)))
        .then(Commands.literal("index")
            .then(Commands.literal("mobs").executes(ctx -> openMobIndex(ctx, mobsRegistry)))
            .then(Commands.literal("items").executes(ctx -> openItemIndex(ctx, yaml)))
            .then(Commands.literal("upgrades").executes(ctx -> openUpgradeIndex(ctx, upgrades)))
            .then(Commands.literal("craftables").executes(ctx -> openCraftablesIndex(ctx, crafting)))
            .then(Commands.literal("shops").executes(ctx -> openShopIndex(ctx, shops)))
            .then(Commands.literal("questgivers").executes(ctx -> openQuestGiverIndex(ctx, questGivers))))
        .then(Commands.literal("hub").executes(ctx -> openHub(ctx, classRegistry, classService, classSkills,
            dungeonRegistry, dungeonQueue, dungeonSessions, quests, questGivers, parties, crafting, craftingSessions,
            advancements, upgrades, kits, mobsRegistry, yaml, shops)))
        .then(Commands.literal("menu").executes(ctx -> openHub(ctx, classRegistry, classService, classSkills,
            dungeonRegistry, dungeonQueue, dungeonSessions, quests, questGivers, parties, crafting, craftingSessions,
            advancements, upgrades, kits, mobsRegistry, yaml, shops)))
        .then(Commands.literal("admin")
            .executes(ctx -> openAdmin(ctx, editor, mobsYaml, mobsRegistry, quests, shops,
                crafting, craftingSessions, classRegistry, upgrades, dungeonRegistry, dungeonQueue, dungeonSessions))
            .then(Commands.literal("help").executes(ctx -> helpAdmin(ctx, root)))
            .then(Commands.literal("status").executes(DungeonsRebornCommand::status))
            .then(Commands.literal("reload").executes(ctx -> reloadAll(ctx, plugin, yaml, mobsYaml, mobsRegistry,
                crafting, advancements, upgrades, shops, kits, classRegistry, dungeonRegistry, questRegistry,
                questGivers, locales, spawnerStore)))
            .then(Commands.literal("locale")
                .then(Commands.literal("reload").executes(ctx -> localeReload(ctx, locales))))
            .then(Commands.literal("advancements")
                .then(Commands.literal("reload")
                    .executes(ctx -> advancementsReload(ctx, advancements, mobsRegistry, dungeonRegistry))))
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
            .then(MobsCommand.createCommand(mobsYaml, mobsRegistry, mobSpawns, spawnerStore))
            .then(ShopsCommand.createCommand(shops, shopSessions))
            .then(KitsCommand.createAdminCommand(kits))
            .then(ClassesCommand.createAdminCommand(classRegistry, classService, classSkills, classAbilityBindings))
            .then(DungeonCommand.createAdminCommand(dungeonRegistry, dungeonQueue, dungeonSessions))
            .then(QuestsCommand.createAdminCommand(quests, questGivers, parties))
            .then(PartyCommand.createCommand(parties))
            .then(ChatCommand.createCommand(parties))
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
                .then(Commands.literal("info")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestRecipes(crafting, builder))
                        .executes(ctx -> craftingInfo(ctx, crafting, StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("editor").executes(ctx -> craftingEditor(ctx, crafting, craftingSessions)))
                .then(Commands.literal("reload").executes(ctx -> craftingReload(ctx, crafting))))
        .then(GuiCommand.createCommand())
        .then(KitsCommand.createUserCommand(kits))
        .then(ClassesCommand.createUserCommand(classRegistry, classService, classSkills, classAbilityBindings))
        .then(DungeonCommand.createUserCommand(dungeonRegistry, dungeonQueue, dungeonSessions))
        .then(QuestsCommand.createUserCommand(quests, questGivers, parties))
        .then(PartyCommand.createCommand(parties))
        .then(ChatCommand.createCommand(parties))
        .then(Commands.literal("upgrades")
            .executes(ctx -> upgradesOpen(ctx, upgrades)))
        .then(Commands.literal("crafting")
            .executes(ctx -> craftingTest(ctx, crafting, craftingSessions, advancements, quests)));
  }

  private static int help(CommandContext<CommandSourceStack> ctx, String root) {
    var sender = ctx.getSource().getSender();
    var placeholders = Locales.placeholders("root", root);
    CommandMessages.send(sender, "messages.command.help.header", placeholders);
    CommandMessages.send(sender, "messages.command.help.root", placeholders);
    CommandMessages.send(sender, "messages.command.help.hub", placeholders);
    CommandMessages.send(sender, "messages.command.help.index", placeholders);
    CommandMessages.send(sender, "messages.command.help.gui", placeholders);
    CommandMessages.send(sender, "messages.command.help.kits", placeholders);
    CommandMessages.send(sender, "messages.command.help.classes", placeholders);
    CommandMessages.send(sender, "messages.command.help.classesSkills", placeholders);
    CommandMessages.send(sender, "messages.command.help.dungeon", placeholders);
    CommandMessages.send(sender, "messages.command.help.dungeonStatus", placeholders);
    CommandMessages.send(sender, "messages.command.help.dungeonJoin", placeholders);
    CommandMessages.send(sender, "messages.command.help.dungeonLeave", placeholders);
    CommandMessages.send(sender, "messages.command.help.quests", placeholders);
    CommandMessages.send(sender, "messages.command.help.questsGiver", placeholders);
    CommandMessages.send(sender, "messages.command.help.party", placeholders);
    CommandMessages.send(sender, "messages.command.help.partyGui", placeholders);
    CommandMessages.send(sender, "messages.command.help.partyChat", placeholders);
    CommandMessages.send(sender, "messages.command.help.partyMessage", placeholders);
    CommandMessages.send(sender, "messages.command.help.upgrades", placeholders);
    CommandMessages.send(sender, "messages.command.help.crafting", placeholders);
    return Command.SINGLE_SUCCESS;
  }

  private static int helpAdmin(CommandContext<CommandSourceStack> ctx, String root) {
    var sender = ctx.getSource().getSender();
    var placeholders = Locales.placeholders("root", root);
    CommandMessages.send(sender, "messages.command.help.adminHeader", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminRoot", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminStatus", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminReload", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminLocaleReload", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminAdvancementsReload", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminGive", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminEffects", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminMobs", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminShops", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminItemsGive", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminKitsReload", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminClassesReload", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminClassesEditor", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminDungeonReload", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminDungeonValidate", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminDungeonDebug", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminQuestsReload", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminQuestsEditor", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminUpgradesReload", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminUpgradesInspect", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminUpgradesGive", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminCraftingEditor", placeholders);
    CommandMessages.send(sender, "messages.command.help.adminCraftingReload", placeholders);
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
    }

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

  private static int errors(EffectsYamlAbilities.ReloadResult result) {
    return result == null || result.errors() == null ? 0 : result.errors().size();
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

  private static int openAdmin(CommandContext<CommandSourceStack> ctx,
      EditorServices editor,
      MobYamlRegistry mobsYaml,
      MobRegistry mobsRegistry,
      QuestService quests,
      ShopYamlRegistry shops,
      CraftingYamlRegistry crafting,
      CraftingGuiSessionManager craftingSessions,
      ClassYamlRegistry classes,
      UpgradeService upgrades,
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
    new AdminHubMenu(editor, mobsYaml, mobsRegistry, questYaml, shops, crafting, craftingSessions, classes, upgrades,
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
      QuestGiverYamlRegistry questGivers,
      PartyService parties,
      CraftingYamlRegistry crafting,
      CraftingGuiSessionManager craftingSessions,
      AdvancementService advancements,
      UpgradeService upgrades,
      KitService kits,
      MobRegistry mobsRegistry,
      EffectsYamlAbilities yaml,
      ShopYamlRegistry shops) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    new UserHubMenu(classRegistry, classService, classSkills,
        dungeonRegistry, dungeonQueue, dungeonSessions,
        quests, questGivers, parties, crafting, craftingSessions, advancements, upgrades, kits,
        mobsRegistry, yaml, shops).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int openMobIndex(CommandContext<CommandSourceStack> ctx, MobRegistry mobs) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (mobs == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.mobsRegistry")));
      return Command.SINGLE_SUCCESS;
    }
    new MobIndexMenu(mobs).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int openItemIndex(CommandContext<CommandSourceStack> ctx, EffectsYamlAbilities yaml) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (yaml == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.itemsEditor")));
      return Command.SINGLE_SUCCESS;
    }
    new ItemIndexMenu(yaml).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int openUpgradeIndex(CommandContext<CommandSourceStack> ctx, UpgradeService upgrades) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (upgrades == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.upgrades")));
      return Command.SINGLE_SUCCESS;
    }
    new UpgradeIndexMenu(upgrades).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int openCraftablesIndex(CommandContext<CommandSourceStack> ctx, CraftingYamlRegistry crafting) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (crafting == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.crafting")));
      return Command.SINGLE_SUCCESS;
    }
    new CraftablesIndexMenu(crafting).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int openShopIndex(CommandContext<CommandSourceStack> ctx, ShopYamlRegistry shops) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (shops == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.shops")));
      return Command.SINGLE_SUCCESS;
    }
    new ShopIndexMenu(shops).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int openQuestGiverIndex(CommandContext<CommandSourceStack> ctx, QuestGiverYamlRegistry questGivers) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (questGivers == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.questGivers")));
      return Command.SINGLE_SUCCESS;
    }
    new QuestGiverIndexMenu(questGivers).open(player);
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

  private static CompletableFuture<Suggestions> suggestSpawnIds(MobSpawnManager spawns, SuggestionsBuilder builder) {
    if (spawns == null) {
      return builder.buildFuture();
    }
    for (String id : spawns.spawnIds()) {
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
