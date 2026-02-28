package dev.patric.dungeonsreborn.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import dev.patric.dungeonsreborn.DungeonsRebornPlugin;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;
import dev.patric.dungeonsreborn.effects.integration.EffectsBindings;
import dev.patric.dungeonsreborn.effects.integration.InteractTrigger;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.effects.items.ItemTemplateDiff;
import dev.patric.dungeonsreborn.effects.items.ItemTemplateSnapshot;
import dev.patric.dungeonsreborn.effects.mana.ManaProvider;
import dev.patric.dungeonsreborn.effects.minions.MinionManager;
import dev.patric.dungeonsreborn.effects.minions.MinionScaling;
import dev.patric.dungeonsreborn.effects.minions.MinionSpec;
import dev.patric.dungeonsreborn.effects.combat.CombatEventDispatcher;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.effects.editor.EditorServices;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class EffectsCommand {
  private EffectsCommand() {
  }

  private static final MiniMessage MINI = MiniMessage.miniMessage();
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  private static Component richText(String raw) {
    if (raw == null) {
      return Component.empty();
    }
    if (raw.indexOf('§') >= 0) {
      return LEGACY.deserialize(raw);
    }
    try {
      return MINI.deserialize(raw);
    } catch (Exception ignored) {
      return LEGACY.deserialize(raw.replace('&', '§'));
    }
  }

  private static String plainText(String raw) {
    return PlainTextComponentSerializer.plainText().serialize(richText(raw));
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createCommand(EffectsEngine engine) {
    return createCommand(engine, null);
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createCommand(EffectsEngine engine, EffectsYamlAbilities yaml) {
    return createCommand(engine, yaml, null);
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createCommand(EffectsEngine engine, EffectsYamlAbilities yaml, EffectsBindings bindings) {
    return createCommand(engine, yaml, bindings, null);
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createCommand(EffectsEngine engine, EffectsYamlAbilities yaml, EffectsBindings bindings, EditorServices editor) {
    return createCommand(engine, yaml, bindings, editor, null);
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createCommand(EffectsEngine engine, EffectsYamlAbilities yaml, EffectsBindings bindings, EditorServices editor, MinionManager minions) {
    LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("effects")
        .executes(ctx -> help(ctx, engine, yaml))
        .then(Commands.literal("reload").executes(ctx -> reload(ctx, engine, yaml)))
        .then(Commands.literal("logging")
            .then(Commands.literal("reload").executes(ctx -> loggingReload(ctx, engine))))
        .then(Commands.literal("stats").executes(ctx -> stats(ctx, engine)))
        .then(Commands.literal("debug")
            .then(Commands.literal("on").executes(ctx -> debug(ctx, engine, true)))
            .then(Commands.literal("off").executes(ctx -> debug(ctx, engine, false)))
            .then(Commands.literal("script")
                .then(Commands.literal("on").executes(ctx -> debugScript(ctx, yaml, true)))
                .then(Commands.literal("off").executes(ctx -> debugScript(ctx, yaml, false)))
                .then(Commands.literal("trace")
                    .then(Commands.literal("on").executes(ctx -> debugScriptTrace(ctx, yaml, true)))
                    .then(Commands.literal("off").executes(ctx -> debugScriptTrace(ctx, yaml, false))))))
        .then(Commands.literal("combat")
            .then(Commands.literal("status").executes(ctx -> combatStatus(ctx, engine)))
            .then(Commands.literal("metrics").executes(ctx -> combatMetrics(ctx, engine)))
            .then(Commands.literal("migrate").executes(ctx -> combatMigrate(ctx, yaml)))
            .then(Commands.literal("debug")
                .then(Commands.literal("on").executes(ctx -> combatDebug(ctx, engine, true)))
                .then(Commands.literal("off").executes(ctx -> combatDebug(ctx, engine, false)))
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestOnlinePlayers(ctx, builder))
                    .executes(ctx -> combatTrace(ctx, engine, StringArgumentType.getString(ctx, "player")))))
            .then(Commands.literal("trace")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestOnlinePlayers(ctx, builder))
                    .executes(ctx -> combatTrace(ctx, engine, StringArgumentType.getString(ctx, "player")))))
            .then(Commands.literal("simulate")
                .then(Commands.argument("ability", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestAbilities(engine, ctx, builder))
                    .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOnlinePlayers(ctx, builder))
                        .executes(ctx -> combatSimulate(ctx, engine,
                            StringArgumentType.getString(ctx, "ability"),
                            StringArgumentType.getString(ctx, "target")))))))
        .then(Commands.literal("explain")
            .then(Commands.literal("right").executes(ctx -> explain(ctx, engine, bindings, InteractTrigger.RIGHT_CLICK)))
            .then(Commands.literal("left").executes(ctx -> explain(ctx, engine, bindings, InteractTrigger.LEFT_CLICK))))
        .then(Commands.literal("particles")
            .then(Commands.literal("stats").executes(ctx -> particlesStats(ctx, engine)))
            .then(Commands.literal("range")
                .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(0.0, 256.0))
                    .executes(ctx -> particlesRange(ctx, engine))))
            .then(Commands.literal("queue")
                .then(Commands.argument("maxRequestsPerTick", IntegerArgumentType.integer(0, 250_000))
                    .executes(ctx -> particlesQueue(ctx, engine))))
            .then(Commands.literal("budget")
                .then(Commands.argument("maxPerPlayerTick", IntegerArgumentType.integer(0, 50_000))
                    .executes(ctx -> particlesBudget(ctx, engine))))
            .then(Commands.literal("quality")
                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.0, 10.0))
                    .executes(ctx -> particlesQuality(ctx, engine)))))
        .then(Commands.literal("tag")
            .then(Commands.literal("on").executes(ctx -> tag(ctx, true)))
            .then(Commands.literal("off").executes(ctx -> tag(ctx, false))))
        .then(Commands.literal("item")
            .then(Commands.literal("inspect")
                .then(Commands.argument("itemId", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestItems(yaml, ctx, builder))
                    .executes(ctx -> itemInspect(ctx, yaml, StringArgumentType.getString(ctx, "itemId")))))
            .then(Commands.literal("diff")
                .executes(ctx -> itemDiff(ctx, yaml, null))
                .then(Commands.argument("itemId", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestItems(yaml, ctx, builder))
                    .executes(ctx -> itemDiff(ctx, yaml, StringArgumentType.getString(ctx, "itemId")))))
            .then(Commands.literal("report")
                .executes(ctx -> itemReport(ctx, yaml))))
        .then(Commands.literal("bind")
            .then(Commands.literal("add")
                .then(Commands.literal("right")
                    .then(Commands.argument("ability", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestAbilities(engine, ctx, builder))
                        .executes(ctx -> bindAdd(ctx, engine, ItemMarkers.RIGHT_CLICK_ABILITIES, StringArgumentType.getString(ctx, "ability")))))
                .then(Commands.literal("left")
                    .then(Commands.argument("ability", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestAbilities(engine, ctx, builder))
                        .executes(ctx -> bindAdd(ctx, engine, ItemMarkers.LEFT_CLICK_ABILITIES, StringArgumentType.getString(ctx, "ability"))))))
            .then(Commands.literal("remove")
                .then(Commands.literal("right")
                    .then(Commands.argument("ability", StringArgumentType.word())
                        .executes(ctx -> bindRemove(ctx, ItemMarkers.RIGHT_CLICK_ABILITIES, StringArgumentType.getString(ctx, "ability")))))
                .then(Commands.literal("left")
                    .then(Commands.argument("ability", StringArgumentType.word())
                        .executes(ctx -> bindRemove(ctx, ItemMarkers.LEFT_CLICK_ABILITIES, StringArgumentType.getString(ctx, "ability"))))))
            .then(Commands.literal("list").executes(EffectsCommand::bindList))
            .then(Commands.literal("clear")
                .then(Commands.literal("right").executes(ctx -> bindClear(ctx, ItemMarkers.RIGHT_CLICK_ABILITIES)))
                .then(Commands.literal("left").executes(ctx -> bindClear(ctx, ItemMarkers.LEFT_CLICK_ABILITIES)))
                .then(Commands.literal("all").executes(EffectsCommand::bindClearAll))))
        .then(Commands.literal("mana")
            .then(Commands.literal("show").executes(ctx -> manaShow(ctx, engine)))
            .then(Commands.literal("state").executes(ctx -> manaState(ctx, engine, null))
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestOnlinePlayers(ctx, builder))
                    .executes(ctx -> manaState(ctx, engine, StringArgumentType.getString(ctx, "player")))))
            .then(Commands.literal("stats").executes(ctx -> manaStats(ctx, engine)))
            .then(Commands.literal("set")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1_000_000.0))
                    .executes(ctx -> manaSet(ctx, engine, DoubleArgumentType.getDouble(ctx, "value")))))
            .then(Commands.literal("add")
                .then(Commands.argument("delta", DoubleArgumentType.doubleArg(-1_000_000.0, 1_000_000.0))
                    .executes(ctx -> manaAdd(ctx, engine, DoubleArgumentType.getDouble(ctx, "delta")))))
            .then(Commands.literal("max")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.0, 1_000_000.0))
                    .executes(ctx -> manaMax(ctx, engine, DoubleArgumentType.getDouble(ctx, "value")))))
            .then(Commands.literal("pulse")
                .then(Commands.argument("resource", StringArgumentType.word())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0, 1_000_000.0))
                        .then(Commands.argument("times", IntegerArgumentType.integer(1, 1000))
                            .then(Commands.argument("periodTicks", IntegerArgumentType.integer(1, 1200))
                                .executes(ctx -> manaPulse(ctx, engine,
                                    StringArgumentType.getString(ctx, "resource"),
                                    DoubleArgumentType.getDouble(ctx, "amount"),
                                    IntegerArgumentType.getInteger(ctx, "times"),
                                    IntegerArgumentType.getInteger(ctx, "periodTicks")))))))))
        .then(Commands.literal("cast")
            .then(Commands.argument("ability", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestAbilities(engine, ctx, builder))
                .executes(ctx -> cast(ctx, engine, null))
                .then(Commands.argument("target", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestOnlinePlayers(ctx, builder))
                    .executes(ctx -> cast(ctx, engine, StringArgumentType.getString(ctx, "target"))))))
        .then(Commands.literal("info")
            .then(Commands.argument("ability", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestAbilities(engine, ctx, builder))
                .executes(ctx -> info(ctx, engine, StringArgumentType.getString(ctx, "ability")))))
        .then(Commands.literal("timings")
            .then(Commands.literal("last")
                .executes(ctx -> timingsLast(ctx, engine, null))
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestOnlinePlayers(ctx, builder))
                    .executes(ctx -> timingsLast(ctx, engine, StringArgumentType.getString(ctx, "player")))))
            .then(Commands.literal("cast")
                .then(Commands.argument("castId", StringArgumentType.word())
                    .executes(ctx -> timingsCast(ctx, engine, StringArgumentType.getString(ctx, "castId"))))))
        .then(Commands.literal("types")
            .then(Commands.literal("actions").executes(ctx -> listTypes(ctx, engine, "actions")))
            .then(Commands.literal("targeters").executes(ctx -> listTypes(ctx, engine, "targeters")))
            .then(Commands.literal("conditions").executes(ctx -> listTypes(ctx, engine, "conditions"))))
        .then(Commands.literal("list")
            .executes(ctx -> list(ctx, engine)))
        .then(Commands.literal("script")
            .then(Commands.literal("run")
                .then(Commands.argument("file", StringArgumentType.greedyString())
                    .executes(ctx -> scriptRun(ctx, yaml, StringArgumentType.getString(ctx, "file")))))
            .then(Commands.literal("stats").executes(ctx -> scriptStats(ctx, yaml))))
        .then(Commands.literal("lint")
            .executes(ctx -> lint(ctx, yaml, null))
            .then(Commands.argument("script", StringArgumentType.greedyString())
                .executes(ctx -> lint(ctx, yaml, StringArgumentType.getString(ctx, "script")))));
    if (minions != null) {
      root.then(Commands.literal("minions")
          .then(Commands.literal("recall").executes(ctx -> minionRecall(ctx, minions)))
          .then(Commands.literal("dismiss").executes(ctx -> minionDismiss(ctx, minions)))
          .then(Commands.literal("mode")
              .then(Commands.literal("aggressive").executes(ctx -> minionMode(ctx, minions, dev.patric.dungeonsreborn.effects.minions.MinionMode.AGGRESSIVE)))
              .then(Commands.literal("defensive").executes(ctx -> minionMode(ctx, minions, dev.patric.dungeonsreborn.effects.minions.MinionMode.DEFENSIVE)))
              .then(Commands.literal("passive").executes(ctx -> minionMode(ctx, minions, dev.patric.dungeonsreborn.effects.minions.MinionMode.PASSIVE)))
              .then(Commands.literal("follow").executes(ctx -> minionMode(ctx, minions, dev.patric.dungeonsreborn.effects.minions.MinionMode.FOLLOW)))
              .then(Commands.literal("guard").executes(ctx -> minionMode(ctx, minions, dev.patric.dungeonsreborn.effects.minions.MinionMode.GUARD)))
              .then(Commands.literal("hold").executes(ctx -> minionMode(ctx, minions, dev.patric.dungeonsreborn.effects.minions.MinionMode.HOLD)))
              .then(Commands.literal("assist").executes(ctx -> minionMode(ctx, minions, dev.patric.dungeonsreborn.effects.minions.MinionMode.ASSIST)))
              .then(Commands.literal("avoid").executes(ctx -> minionMode(ctx, minions, dev.patric.dungeonsreborn.effects.minions.MinionMode.AVOID))))
          .then(Commands.literal("list").executes(ctx -> minionList(ctx, minions)))
          .then(Commands.literal("stats").executes(ctx -> minionStats(ctx, minions)))
          .then(Commands.literal("test")
              .then(Commands.argument("mob", StringArgumentType.word())
                  .executes(ctx -> minionTest(ctx, minions,
                      StringArgumentType.getString(ctx, "mob"), 1, 200, 1.5))
                  .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                      .executes(ctx -> minionTest(ctx, minions,
                          StringArgumentType.getString(ctx, "mob"),
                          IntegerArgumentType.getInteger(ctx, "count"), 200, 1.5))
                      .then(Commands.argument("durationTicks", IntegerArgumentType.integer(1, 200_000))
                          .executes(ctx -> minionTest(ctx, minions,
                              StringArgumentType.getString(ctx, "mob"),
                              IntegerArgumentType.getInteger(ctx, "count"),
                              IntegerArgumentType.getInteger(ctx, "durationTicks"), 1.5))
                          .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.0, 20.0))
                              .executes(ctx -> minionTest(ctx, minions,
                                  StringArgumentType.getString(ctx, "mob"),
                                  IntegerArgumentType.getInteger(ctx, "count"),
                                  IntegerArgumentType.getInteger(ctx, "durationTicks"),
                                  DoubleArgumentType.getDouble(ctx, "radius")))))))));
    }
    return root;
  }

  private static int help(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, EffectsYamlAbilities yaml) {
    CommandSender sender = ctx.getSource().getSender();
    CommandMessages.send(sender, "messages.command.effects.help.list");
    String debugStatus = engine.isDebugEnabled()
        ? CommandMessages.text(sender, "messages.command.effects.status.on")
        : CommandMessages.text(sender, "messages.command.effects.status.off");
    CommandMessages.send(sender, "messages.command.effects.help.debug",
        Locales.placeholders("status", debugStatus));
    String scriptStatus = yaml == null
        ? CommandMessages.text(sender, "messages.command.effects.status.unavailable")
        : (yaml.isScriptDebugEnabled()
            ? CommandMessages.text(sender, "messages.command.effects.status.on")
            : CommandMessages.text(sender, "messages.command.effects.status.off"));
    CommandMessages.send(sender, "messages.command.effects.help.debugScript",
        Locales.placeholders("status", scriptStatus));
    String traceStatus = yaml == null
        ? CommandMessages.text(sender, "messages.command.effects.status.unavailable")
        : (yaml.isScriptTraceEnabled()
            ? CommandMessages.text(sender, "messages.command.effects.status.on")
            : CommandMessages.text(sender, "messages.command.effects.status.off"));
    CommandMessages.send(sender, "messages.command.effects.help.debugScriptTrace",
        Locales.placeholders("status", traceStatus));
    CommandMessages.send(sender, "messages.command.effects.help.tag",
        Locales.placeholders("marker", ItemMarkers.DEBUG_MARKER.asString()));
    CommandMessages.send(sender, "messages.command.effects.help.itemInspect");
    CommandMessages.send(sender, "messages.command.effects.help.itemDiff");
    CommandMessages.send(sender, "messages.command.effects.help.itemReport");
    CommandMessages.send(sender, "messages.command.effects.help.bindAdd");
    CommandMessages.send(sender, "messages.command.effects.help.bindRemove");
    CommandMessages.send(sender, "messages.command.effects.help.bindList");
    CommandMessages.send(sender, "messages.command.effects.help.bindClear");
    CommandMessages.send(sender, "messages.command.effects.help.explain");
    CommandMessages.send(sender, "messages.command.effects.help.mana");
    CommandMessages.send(sender, "messages.command.effects.help.reload");
    CommandMessages.send(sender, "messages.command.effects.help.loggingReload");
    CommandMessages.send(sender, "messages.command.effects.help.stats");
    CommandMessages.send(sender, "messages.command.effects.help.particlesRange",
        Locales.placeholders("value", String.valueOf(engine.particles().defaultRange())));
    CommandMessages.send(sender, "messages.command.effects.help.particlesQueue",
        Locales.placeholders("value", String.valueOf(engine.particles().maxQueuedRequestsPerTick())));
    CommandMessages.send(sender, "messages.command.effects.help.particlesBudget",
        Locales.placeholders("value", String.valueOf(engine.particles().maxParticlesPerPlayerPerTick())));
    CommandMessages.send(sender, "messages.command.effects.help.particlesQuality",
        Locales.placeholders("value", String.valueOf(engine.particles().quality())));
    CommandMessages.send(sender, "messages.command.effects.help.particlesStats");
    CommandMessages.send(sender, "messages.command.effects.help.cast");
    CommandMessages.send(sender, "messages.command.effects.help.info");
    CommandMessages.send(sender, "messages.command.effects.help.timings");
    CommandMessages.send(sender, "messages.command.effects.help.types");
    CommandMessages.send(sender, "messages.command.effects.help.minions");
    CommandMessages.send(sender, "messages.command.effects.help.scriptRun");
    CommandMessages.send(sender, "messages.command.effects.help.scriptStats");
    CommandMessages.send(sender, "messages.command.effects.help.lint");
    CommandMessages.send(sender, "messages.command.effects.help.registeredAbilities",
        Locales.placeholders("count", String.valueOf(engine.abilityIds().size())));
    return Command.SINGLE_SUCCESS;
  }

  private static int minionRecall(CommandContext<CommandSourceStack> ctx, MinionManager minions) {
    CommandSender sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    int count = minions.recall(player.getUniqueId(), player.getLocation());
    CommandMessages.send(player, "messages.command.effects.minions.recalled",
        Locales.placeholders("count", String.valueOf(count)));
    return Command.SINGLE_SUCCESS;
  }

  private static int minionDismiss(CommandContext<CommandSourceStack> ctx, MinionManager minions) {
    CommandSender sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    int count = minions.dismiss(player.getUniqueId());
    CommandMessages.send(player, "messages.command.effects.minions.dismissed",
        Locales.placeholders("count", String.valueOf(count)));
    return Command.SINGLE_SUCCESS;
  }

  private static int minionMode(CommandContext<CommandSourceStack> ctx, MinionManager minions,
                                dev.patric.dungeonsreborn.effects.minions.MinionMode mode) {
    CommandSender sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    minions.setMode(player.getUniqueId(), mode);
    CommandMessages.send(player, "messages.command.effects.minions.mode",
        Locales.placeholders("mode", mode.name().toLowerCase(Locale.ROOT)));
    return Command.SINGLE_SUCCESS;
  }

  private static int minionTest(CommandContext<CommandSourceStack> ctx, MinionManager minions,
                                String mobId, int count, int durationTicks, double radius) {
    CommandSender sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    String id = "test_" + mobId;
    MinionSpec spec = new MinionSpec(id, mobId, count, durationTicks, player.getUniqueId(), radius,
        dev.patric.dungeonsreborn.effects.minions.MinionSummonSpec.DEFAULT,
        MinionScaling.NONE, java.util.Map.of(), java.util.Set.of(), true, false, null,
        dev.patric.dungeonsreborn.effects.minions.MinionTargetRules.DEFAULT,
        java.util.List.of(), java.util.List.of(),
        java.util.Map.of(),
        dev.patric.dungeonsreborn.effects.minions.MinionOwnerScalingSpec.NONE,
        dev.patric.dungeonsreborn.effects.minions.MinionScalingLimits.NONE,
        null, null, false, false, false, false,
        null, null, null, 0L);
    minions.summon(spec, player.getLocation());
    CommandMessages.send(player, "messages.command.effects.minions.test",
        Locales.placeholders("mob", mobId));
    return Command.SINGLE_SUCCESS;
  }

  private static int minionList(CommandContext<CommandSourceStack> ctx, MinionManager minions) {
    CommandSender sender = ctx.getSource().getSender();
    Map<java.util.UUID, List<java.util.UUID>> snapshot = minions.ownersSnapshot();
    if (snapshot.isEmpty()) {
      CommandMessages.send(sender, "messages.command.effects.minions.none");
      return Command.SINGLE_SUCCESS;
    }
    CommandMessages.send(sender, "messages.command.effects.minions.listHeader");
    for (Map.Entry<java.util.UUID, List<java.util.UUID>> entry : snapshot.entrySet()) {
      java.util.UUID ownerId = entry.getKey();
      Player player = Bukkit.getPlayer(ownerId);
      String name = player != null ? player.getName() : ownerId.toString();
      CommandMessages.send(sender, "messages.command.effects.minions.listEntry",
          Locales.placeholders("owner", name, "count", String.valueOf(entry.getValue().size())));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int minionStats(CommandContext<CommandSourceStack> ctx, MinionManager minions) {
    CommandSender sender = ctx.getSource().getSender();
    CommandMessages.send(sender, "messages.command.effects.minions.statsHeader");
    CommandMessages.send(sender, "messages.command.effects.minions.statsActive",
        Locales.placeholders("count", String.valueOf(minions.activeCount())));
    CommandMessages.send(sender, "messages.command.effects.minions.statsSpawned",
        Locales.placeholders("count", String.valueOf(minions.spawnedCount())));
    CommandMessages.send(sender, "messages.command.effects.minions.statsDespawned",
        Locales.placeholders("count", String.valueOf(minions.despawnedCount())));
    return Command.SINGLE_SUCCESS;
  }

  private static int explain(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, EffectsBindings bindings, InteractTrigger trigger) {
    var sender = ctx.getSource().getSender();
    if (bindings == null) {
      CommandMessages.send(sender, "messages.command.effects.bindingsMissing");
      return Command.SINGLE_SUCCESS;
    }
    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    for (String line : bindings.explain(player, trigger)) {
      CommandMessages.send(sender, "messages.command.effects.explain.line",
          Locales.placeholders("line", line));
    }
    CommandMessages.send(sender, "messages.command.effects.explain.registeredAbilities",
        Locales.placeholders("count", String.valueOf(engine.abilityIds().size())));
    return Command.SINGLE_SUCCESS;
  }

  private static int reload(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, EffectsYamlAbilities yaml) {
    var sender = ctx.getSource().getSender();
    if (yaml == null) {
      CommandMessages.send(sender, "messages.command.effects.yamlMissing");
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.effects.reload")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.effects.reload"));
      return Command.SINGLE_SUCCESS;
    }
    if (engine.plugin() instanceof DungeonsRebornPlugin plugin) {
      plugin.reloadTextures();
    }
    var result = yaml.reload();
    int updated = yaml.syncOnlineItems();
    CommandMessages.send(sender, "messages.command.effects.reload.file",
        Locales.placeholders("path", yaml.file().getPath()));
    CommandMessages.send(sender, "messages.command.effects.reload.summary",
        Locales.placeholders(
            "abilities", String.valueOf(result.loadedAbilities()),
            "bindings", String.valueOf(result.loadedItemBindings()),
            "errors", String.valueOf(result.errors().size())));
    if (updated > 0) {
      CommandMessages.send(sender, "messages.command.effects.reload.updated",
          Locales.placeholders("count", String.valueOf(updated)));
    }
    if (!result.errors().isEmpty()) {
      int shown = Math.min(10, result.errors().size());
      for (int i = 0; i < shown; i++) {
        CommandMessages.send(sender, "messages.command.effects.reload.errorEntry",
            Locales.placeholders("error", result.errors().get(i)));
      }
      if (result.errors().size() > shown) {
        CommandMessages.send(sender, "messages.command.effects.reload.more",
            Locales.placeholders("count", String.valueOf(result.errors().size() - shown)));
      }
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int loggingReload(CommandContext<CommandSourceStack> ctx, EffectsEngine engine) {
    CommandSender sender = ctx.getSource().getSender();
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.effects.reload")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.effects.reload"));
      return Command.SINGLE_SUCCESS;
    }
    var plugin = engine.plugin();
    plugin.reloadConfig();
    if (plugin instanceof DungeonsRebornPlugin dungeonsReborn && dungeonsReborn.serviceLog() != null) {
      dungeonsReborn.serviceLog().reloadFromConfig(plugin.getConfig().getConfigurationSection("logging"));
      dungeonsReborn.reloadRuntimeConfig();
      CommandMessages.send(sender, "messages.command.effects.loggingReload.ok");
    } else {
      CommandMessages.send(sender, "messages.command.effects.loggingReload.unavailable");
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int stats(CommandContext<CommandSourceStack> ctx, EffectsEngine engine) {
    var sender = ctx.getSource().getSender();
    var e = engine.stats();
    var p = engine.particles().stats();
    CommandMessages.send(sender, "messages.command.effects.stats.header");
    CommandMessages.send(sender, "messages.command.effects.stats.tick",
        Locales.placeholders(
            "tick", String.valueOf(e.tick()),
            "lastTickMs", String.valueOf(e.lastTickNanos() / 1_000_000.0)));
    CommandMessages.send(sender, "messages.command.effects.stats.scheduled",
        Locales.placeholders(
            "tick", String.valueOf(e.scheduledTickTasks()),
            "rt", String.valueOf(e.scheduledRealTimeTasks())));
    CommandMessages.send(sender, "messages.command.effects.stats.tracked",
        Locales.placeholders(
            "casts", String.valueOf(e.trackedCastRecords()),
            "cooldowns", String.valueOf(e.cooldownPlayers()),
            "immunities", String.valueOf(e.immunityEntities())));
    CommandMessages.send(sender, "messages.command.effects.stats.particlesHeader");
    CommandMessages.send(sender, "messages.command.effects.stats.particlesQueue",
        Locales.placeholders(
            "queued", String.valueOf(p.queuedRequests()),
            "max", String.valueOf(p.maxQueuedRequestsPerTick()),
            "quality", String.valueOf(p.quality()),
            "range", String.valueOf(p.defaultRange())));
    CommandMessages.send(sender, "messages.command.effects.stats.particlesFlush",
        Locales.placeholders(
            "lastFlushMs", String.valueOf(p.lastFlushNanos() / 1_000_000.0),
            "requests", String.valueOf(p.lastFlushRequests()),
            "sent", String.valueOf(p.lastFlushParticlesSent()),
            "droppedBudget", String.valueOf(p.lastFlushParticlesDroppedByBudget()),
            "droppedQueue", String.valueOf(p.lastDroppedRequestsByQueueCap())));
    return Command.SINGLE_SUCCESS;
  }

  private static int debug(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, boolean enabled) {
    engine.setDebug(enabled);
    CommandMessages.send(ctx.getSource().getSender(), "messages.command.effects.debug",
        Locales.placeholders("status",
            enabled
                ? CommandMessages.text(ctx.getSource().getSender(), "messages.command.effects.status.enabled")
                : CommandMessages.text(ctx.getSource().getSender(), "messages.command.effects.status.disabled")));
    return Command.SINGLE_SUCCESS;
  }

  private static int debugScript(CommandContext<CommandSourceStack> ctx, EffectsYamlAbilities yaml, boolean enabled) {
    CommandSender sender = ctx.getSource().getSender();
    if (yaml == null) {
      CommandMessages.send(sender, "messages.command.effects.yamlMissing");
      return Command.SINGLE_SUCCESS;
    }
    yaml.setScriptDebug(enabled);
    CommandMessages.send(sender, "messages.command.effects.debugScript",
        Locales.placeholders("status",
            enabled
                ? CommandMessages.text(sender, "messages.command.effects.status.enabled")
                : CommandMessages.text(sender, "messages.command.effects.status.disabled")));
    return Command.SINGLE_SUCCESS;
  }

  private static int debugScriptTrace(CommandContext<CommandSourceStack> ctx, EffectsYamlAbilities yaml, boolean enabled) {
    CommandSender sender = ctx.getSource().getSender();
    if (yaml == null) {
      CommandMessages.send(sender, "messages.command.effects.yamlMissing");
      return Command.SINGLE_SUCCESS;
    }
    yaml.setScriptTrace(enabled);
    CommandMessages.send(sender, "messages.command.effects.debugScriptTrace",
        Locales.placeholders("status",
            enabled
                ? CommandMessages.text(sender, "messages.command.effects.status.enabled")
                : CommandMessages.text(sender, "messages.command.effects.status.disabled")));
    return Command.SINGLE_SUCCESS;
  }

  private static int tag(CommandContext<CommandSourceStack> ctx, boolean enabled) {
    CommandSender sender = ctx.getSource().getSender();
    Entity executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    var item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      CommandMessages.send(sender, "messages.command.items.holdMainHand");
      return Command.SINGLE_SUCCESS;
    }
    ItemMarkers.set(item, ItemMarkers.DEBUG_MARKER, enabled);
    player.getInventory().setItemInMainHand(item);
    player.updateInventory();
    CommandMessages.send(sender, "messages.command.effects.tag",
        Locales.placeholders("status",
            enabled
                ? CommandMessages.text(sender, "messages.command.effects.status.enabled")
                : CommandMessages.text(sender, "messages.command.effects.status.disabled")));
    return Command.SINGLE_SUCCESS;
  }

  private static int itemInspect(CommandContext<CommandSourceStack> ctx, EffectsYamlAbilities yaml, String itemId) {
    CommandSender sender = ctx.getSource().getSender();
    if (!requirePermission(sender, "dungeonsreborn.effects.reload")) {
      return Command.SINGLE_SUCCESS;
    }
    if (yaml == null) {
      CommandMessages.send(sender, "messages.command.effects.yamlMissing");
      return Command.SINGLE_SUCCESS;
    }
    ItemTemplateSnapshot snapshot = yaml.itemTemplateSnapshot(itemId);
    if (snapshot == null) {
      CommandMessages.send(sender, "messages.command.effects.items.unknown",
          Locales.placeholders("id", itemId));
      return Command.SINGLE_SUCCESS;
    }
    CommandMessages.send(sender, "messages.command.effects.items.inspect.header",
        Locales.placeholders("id", snapshot.id(), "version", String.valueOf(snapshot.version())));
    if (snapshot.baseItem() != null) {
      CommandMessages.send(sender, "messages.command.effects.items.inspect.material",
          Locales.placeholders("value", String.valueOf(snapshot.baseItem().getType())));
      CommandMessages.send(sender, "messages.command.effects.items.inspect.name",
          Locales.placeholders("value", plainItemName(snapshot.baseItem())));
      List<String> lore = plainItemLore(snapshot.baseItem());
      CommandMessages.send(sender, "messages.command.effects.items.inspect.loreLines",
          Locales.placeholders("count", String.valueOf(lore.size())));
    }
    if (snapshot.durabilityRange() != null) {
      CommandMessages.send(sender, "messages.command.effects.items.inspect.durability",
          Locales.placeholders(
              "min", String.valueOf(snapshot.durabilityRange().minDamage()),
              "max", String.valueOf(snapshot.durabilityRange().maxDamage())));
    }
    if (snapshot.tierSpec() != null) {
      CommandMessages.send(sender, "messages.command.effects.items.inspect.tier",
          Locales.placeholders(
              "id", snapshot.tierSpec().id(),
              "scale", String.valueOf(snapshot.tierSpec().scale())));
    }
    if (snapshot.rarityId() != null) {
      CommandMessages.send(sender, "messages.command.effects.items.inspect.rarity",
          Locales.placeholders("value", snapshot.rarityId()));
    }
    if (snapshot.baseStats() != null && !snapshot.baseStats().isEmpty()) {
      CommandMessages.send(sender, "messages.command.effects.items.inspect.baseStats",
          Locales.placeholders("value", formatStats(snapshot.baseStats().values())));
    }
    if (snapshot.affixPool() != null) {
      CommandMessages.send(sender, "messages.command.effects.items.inspect.affixPool",
          Locales.placeholders("value", formatAffixPool(snapshot.affixPool())));
    }
    if (snapshot.hooks() != null && !snapshot.hooks().isEmpty()) {
      int hookCount = snapshot.hooks().values().stream().mapToInt(List::size).sum();
      CommandMessages.send(sender, "messages.command.effects.items.inspect.hooks",
          Locales.placeholders("count", String.valueOf(hookCount)));
    }
    ItemStack rolled = yaml.itemTemplate(snapshot.id());
    if (rolled != null) {
      CommandMessages.send(sender, "messages.command.effects.items.inspect.rolledStats",
          Locales.placeholders("value", formatStats(ItemMarkers.getItemStats(rolled))));
      CommandMessages.send(sender, "messages.command.effects.items.inspect.rolledAffixes",
          Locales.placeholders("value", String.join(", ", ItemMarkers.getItemAffixes(rolled))));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int itemDiff(CommandContext<CommandSourceStack> ctx, EffectsYamlAbilities yaml, String itemId) {
    CommandSender sender = ctx.getSource().getSender();
    if (!requirePermission(sender, "dungeonsreborn.effects.reload")) {
      return Command.SINGLE_SUCCESS;
    }
    if (yaml == null) {
      CommandMessages.send(sender, "messages.command.effects.yamlMissing");
      return Command.SINGLE_SUCCESS;
    }
    Entity executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    ItemStack item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      CommandMessages.send(sender, "messages.command.items.holdMainHand");
      return Command.SINGLE_SUCCESS;
    }
    String resolvedId = itemId != null ? itemId : ItemMarkers.getItemId(item);
    if (resolvedId == null || resolvedId.isBlank()) {
      CommandMessages.send(sender, "messages.command.effects.items.heldMissingId");
      return Command.SINGLE_SUCCESS;
    }
    ItemTemplateSnapshot snapshot = yaml.itemTemplateSnapshot(resolvedId);
    if (snapshot == null) {
      CommandMessages.send(sender, "messages.command.effects.items.unknown",
          Locales.placeholders("id", resolvedId));
      return Command.SINGLE_SUCCESS;
    }
    List<String> diffs = ItemTemplateDiff.diff(snapshot, item);
    CommandMessages.send(sender, "messages.command.effects.items.diff.header",
        Locales.placeholders("id", resolvedId));
    if (diffs.isEmpty()) {
      CommandMessages.send(sender, "messages.command.effects.items.diff.none");
      return Command.SINGLE_SUCCESS;
    }
    for (String diff : diffs) {
      CommandMessages.send(sender, "messages.command.effects.items.diff.entry",
          Locales.placeholders("diff", diff));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int itemReport(CommandContext<CommandSourceStack> ctx, EffectsYamlAbilities yaml) {
    CommandSender sender = ctx.getSource().getSender();
    if (!requirePermission(sender, "dungeonsreborn.effects.reload")) {
      return Command.SINGLE_SUCCESS;
    }
    if (yaml == null) {
      CommandMessages.send(sender, "messages.command.effects.yamlMissing");
      return Command.SINGLE_SUCCESS;
    }
    List<String> errors = yaml.itemTemplateErrors();
    CommandMessages.send(sender, "messages.command.effects.items.report.header");
    if (errors == null || errors.isEmpty()) {
      CommandMessages.send(sender, "messages.command.effects.items.report.none");
      return Command.SINGLE_SUCCESS;
    }
    int shown = Math.min(20, errors.size());
    for (int i = 0; i < shown; i++) {
      CommandMessages.send(sender, "messages.command.effects.items.report.entry",
          Locales.placeholders("error", errors.get(i)));
    }
    if (errors.size() > shown) {
      CommandMessages.send(sender, "messages.command.effects.items.report.more",
          Locales.placeholders("count", String.valueOf(errors.size() - shown)));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int bindAdd(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, org.bukkit.NamespacedKey key, String abilityId) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    try {
      if (!engine.hasAbility(abilityId)) {
        CommandMessages.send(sender, "messages.command.effects.abilityUnknown",
            Locales.placeholders("id", abilityId));
        return Command.SINGLE_SUCCESS;
      }
    } catch (IllegalArgumentException ex) {
      CommandMessages.send(sender, "messages.command.effects.abilityInvalid",
          Locales.placeholders("message", ex.getMessage()));
      return Command.SINGLE_SUCCESS;
    }

    var item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      CommandMessages.send(sender, "messages.command.items.holdMainHand");
      return Command.SINGLE_SUCCESS;
    }
    try {
      ItemMarkers.addToStringList(item, key, abilityId);
    } catch (IllegalArgumentException ex) {
      CommandMessages.send(sender, "messages.command.effects.abilityInvalid",
          Locales.placeholders("message", ex.getMessage()));
      return Command.SINGLE_SUCCESS;
    }
    player.getInventory().setItemInMainHand(item);
    player.updateInventory();
    CommandMessages.send(sender, "messages.command.effects.bind.add",
        Locales.placeholders("id", abilityId, "key", key.getKey()));
    return Command.SINGLE_SUCCESS;
  }

  private static int bindRemove(CommandContext<CommandSourceStack> ctx, org.bukkit.NamespacedKey key, String abilityId) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    var item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      CommandMessages.send(sender, "messages.command.items.holdMainHand");
      return Command.SINGLE_SUCCESS;
    }
    ItemMarkers.removeFromStringList(item, key, abilityId);
    player.getInventory().setItemInMainHand(item);
    player.updateInventory();
    CommandMessages.send(sender, "messages.command.effects.bind.remove",
        Locales.placeholders("id", abilityId, "key", key.getKey()));
    return Command.SINGLE_SUCCESS;
  }

  private static int bindList(CommandContext<CommandSourceStack> ctx) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    var item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      CommandMessages.send(sender, "messages.command.items.holdMainHand");
      return Command.SINGLE_SUCCESS;
    }
    List<String> right = ItemMarkers.getStringList(item, ItemMarkers.RIGHT_CLICK_ABILITIES);
    List<String> left = ItemMarkers.getStringList(item, ItemMarkers.LEFT_CLICK_ABILITIES);
    CommandMessages.send(sender, "messages.command.effects.bind.list.right",
        Locales.placeholders("value",
            right.isEmpty()
                ? CommandMessages.text(sender, "messages.command.effects.bind.list.none")
                : String.join(", ", right)));
    CommandMessages.send(sender, "messages.command.effects.bind.list.left",
        Locales.placeholders("value",
            left.isEmpty()
                ? CommandMessages.text(sender, "messages.command.effects.bind.list.none")
                : String.join(", ", left)));
    return Command.SINGLE_SUCCESS;
  }

  private static int bindClear(CommandContext<CommandSourceStack> ctx, org.bukkit.NamespacedKey key) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    var item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      CommandMessages.send(sender, "messages.command.items.holdMainHand");
      return Command.SINGLE_SUCCESS;
    }
    ItemMarkers.setStringList(item, key, List.of());
    player.getInventory().setItemInMainHand(item);
    player.updateInventory();
    CommandMessages.send(sender, "messages.command.effects.bind.clear",
        Locales.placeholders("key", key.getKey()));
    return Command.SINGLE_SUCCESS;
  }

  private static int bindClearAll(CommandContext<CommandSourceStack> ctx) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    var item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      CommandMessages.send(sender, "messages.command.items.holdMainHand");
      return Command.SINGLE_SUCCESS;
    }
    ItemMarkers.setStringList(item, ItemMarkers.RIGHT_CLICK_ABILITIES, List.of());
    ItemMarkers.setStringList(item, ItemMarkers.LEFT_CLICK_ABILITIES, List.of());
    player.getInventory().setItemInMainHand(item);
    player.updateInventory();
    CommandMessages.send(sender, "messages.command.effects.bind.clearAll");
    return Command.SINGLE_SUCCESS;
  }

  private static int manaShow(CommandContext<CommandSourceStack> ctx, EffectsEngine engine) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    ManaProvider provider = engine.manaProvider();
    if (provider == null) {
      CommandMessages.send(sender, "messages.command.effects.mana.missingProvider");
      return Command.SINGLE_SUCCESS;
    }
    CommandMessages.send(sender, "messages.command.effects.mana.show",
        Locales.placeholders(
            "current", format(provider.get(player)),
            "max", format(provider.getMax(player))));
    return Command.SINGLE_SUCCESS;
  }

  private static int manaSet(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, double value) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    ManaProvider provider = engine.manaProvider();
    if (provider == null) {
      CommandMessages.send(sender, "messages.command.effects.mana.missingProvider");
      return Command.SINGLE_SUCCESS;
    }
    provider.set(player, value);
    CommandMessages.send(sender, "messages.command.effects.mana.set",
        Locales.placeholders(
            "current", format(provider.get(player)),
            "max", format(provider.getMax(player))));
    return Command.SINGLE_SUCCESS;
  }

  private static int manaAdd(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, double delta) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    ManaProvider provider = engine.manaProvider();
    if (provider == null) {
      CommandMessages.send(sender, "messages.command.effects.mana.missingProvider");
      return Command.SINGLE_SUCCESS;
    }
    provider.set(player, provider.get(player) + delta);
    CommandMessages.send(sender, "messages.command.effects.mana.add",
        Locales.placeholders(
            "current", format(provider.get(player)),
            "max", format(provider.getMax(player))));
    return Command.SINGLE_SUCCESS;
  }

  private static int manaMax(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, double value) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    ManaProvider provider = engine.manaProvider();
    if (provider == null) {
      CommandMessages.send(sender, "messages.command.effects.mana.missingProvider");
      return Command.SINGLE_SUCCESS;
    }
    provider.setMax(player, value);
    CommandMessages.send(sender, "messages.command.effects.mana.max",
        Locales.placeholders("max", format(provider.getMax(player))));
    return Command.SINGLE_SUCCESS;
  }

  private static int manaState(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, String playerName) {
    var sender = ctx.getSource().getSender();
    ManaProvider provider = engine.manaProvider();
    if (provider == null) {
      CommandMessages.send(sender, "messages.command.effects.mana.missingProvider");
      return Command.SINGLE_SUCCESS;
    }
    Player player = resolvePlayer(ctx, playerName);
    if (player == null) {
      CommandMessages.send(sender, "messages.command.effects.playerNotFound");
      return Command.SINGLE_SUCCESS;
    }
    CommandMessages.send(sender, "messages.command.effects.mana.state.header",
        Locales.placeholders("player", player.getName()));
    boolean detailed = provider instanceof dev.patric.dungeonsreborn.effects.mana.SessionManaProvider;
    for (String resourceId : provider.resourceIds()) {
      double current = provider.get(player, resourceId);
      double max = provider.getMax(player, resourceId);
      if (detailed) {
        var session = (dev.patric.dungeonsreborn.effects.mana.SessionManaProvider) provider;
        String details = Locales.text(sender instanceof Player p ? p : null,
            "messages.command.effects.mana.state.details",
            Locales.placeholders(
                "base", format(session.baseMax(player, resourceId)),
                "bonus", format(session.maxBonus(player, resourceId)),
                "classBonus", format(session.classMaxBonus(player, resourceId)),
                "regen", format(session.regenBonus(player, resourceId)),
                "classRegen", format(session.classRegenBonus(player, resourceId))));
        CommandMessages.send(sender, "messages.command.effects.mana.state.entryDetailed",
            Locales.placeholders(
                "resource", resourceId,
                "current", format(current),
                "max", format(max),
                "details", details));
      } else {
        CommandMessages.send(sender, "messages.command.effects.mana.state.entry",
            Locales.placeholders(
                "resource", resourceId,
                "current", format(current),
                "max", format(max)));
      }
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int manaStats(CommandContext<CommandSourceStack> ctx, EffectsEngine engine) {
    var sender = ctx.getSource().getSender();
    CommandMessages.send(sender, "messages.command.effects.mana.stats.header");
    CommandMessages.send(sender, "messages.command.effects.mana.stats.regen",
        Locales.placeholders(
            "period", String.valueOf(engine.manaRegenPeriodTicks()),
            "amount", format(engine.manaRegenAmount()),
            "delayCast", String.valueOf(engine.manaRegenDelayTicks()),
            "delayCombat", String.valueOf(engine.manaCombatDelayTicks()),
            "capPerTick", format(engine.manaRegenMaxPerTick())));
    CommandMessages.send(sender, "messages.command.effects.mana.stats.timed",
        Locales.placeholders(
            "enabled", String.valueOf(engine.manaTimedGrantEnabled()),
            "period", String.valueOf(engine.manaTimedGrantPeriodTicks()),
            "amount", format(engine.manaTimedGrantAmount()),
            "resource", String.valueOf(engine.manaTimedGrantResource())));
    CommandMessages.send(sender, "messages.command.effects.mana.stats.antiExploit",
        Locales.placeholders("capPerTick", format(engine.manaGainMaxPerTick())));
    return Command.SINGLE_SUCCESS;
  }

  private static int manaPulse(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, String resourceId,
      double amount, int times, int periodTicks) {
    var sender = ctx.getSource().getSender();
    Player player = resolvePlayer(ctx, null);
    if (player == null) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (engine.manaProvider() == null) {
      CommandMessages.send(sender, "messages.command.effects.mana.missingProvider");
      return Command.SINGLE_SUCCESS;
    }
    AtomicInteger remaining = new AtomicInteger(times);
    EffectsEngine.ScheduledHandle[] handleRef = new EffectsEngine.ScheduledHandle[1];
    handleRef[0] = engine.runRepeating(0L, periodTicks, () -> {
      if (remaining.getAndDecrement() <= 0) {
        if (handleRef[0] != null) {
          handleRef[0].cancel();
        }
        return;
      }
      engine.grantResource(player, resourceId, amount);
    });
    CommandMessages.send(sender, "messages.command.effects.mana.pulse",
        Locales.placeholders(
            "resource", resourceId,
            "amount", format(amount),
            "times", String.valueOf(times),
            "period", String.valueOf(periodTicks)));
    return Command.SINGLE_SUCCESS;
  }

  private static Player resolvePlayer(CommandContext<CommandSourceStack> ctx, String playerName) {
    if (playerName != null && !playerName.isBlank()) {
      return Bukkit.getPlayerExact(playerName);
    }
    var executor = ctx.getSource().getExecutor();
    if (executor instanceof Player player) {
      return player;
    }
    return null;
  }

  private static String format(double v) {
    if (Math.abs(v - Math.round(v)) < 1e-9) {
      return String.valueOf((long) Math.round(v));
    }
    return String.format(java.util.Locale.ROOT, "%.2f", v);
  }

  private static int particlesRange(CommandContext<CommandSourceStack> ctx, EffectsEngine engine) {
    double blocks = DoubleArgumentType.getDouble(ctx, "blocks");
    engine.particles().setDefaultRange(blocks);
    CommandMessages.send(ctx.getSource().getSender(), "messages.command.effects.particles.range",
        Locales.placeholders("value", String.valueOf(blocks)));
    return Command.SINGLE_SUCCESS;
  }

  private static int particlesQueue(CommandContext<CommandSourceStack> ctx, EffectsEngine engine) {
    int max = IntegerArgumentType.getInteger(ctx, "maxRequestsPerTick");
    engine.particles().setMaxQueuedRequestsPerTick(max);
    CommandMessages.send(ctx.getSource().getSender(), "messages.command.effects.particles.queue",
        Locales.placeholders("value", String.valueOf(max)));
    return Command.SINGLE_SUCCESS;
  }

  private static int particlesBudget(CommandContext<CommandSourceStack> ctx, EffectsEngine engine) {
    int max = IntegerArgumentType.getInteger(ctx, "maxPerPlayerTick");
    engine.particles().setMaxParticlesPerPlayerPerTick(max);
    CommandMessages.send(ctx.getSource().getSender(), "messages.command.effects.particles.budget",
        Locales.placeholders("value", String.valueOf(max)));
    return Command.SINGLE_SUCCESS;
  }

  private static int particlesQuality(CommandContext<CommandSourceStack> ctx, EffectsEngine engine) {
    double q = DoubleArgumentType.getDouble(ctx, "multiplier");
    engine.particles().setQuality(q);
    CommandMessages.send(ctx.getSource().getSender(), "messages.command.effects.particles.quality",
        Locales.placeholders("value", String.valueOf(q)));
    return Command.SINGLE_SUCCESS;
  }

  private static int particlesStats(CommandContext<CommandSourceStack> ctx, EffectsEngine engine) {
    var sender = ctx.getSource().getSender();
    var p = engine.particles().stats();
    CommandMessages.send(sender, "messages.command.effects.particles.stats.header");
    CommandMessages.send(sender, "messages.command.effects.particles.stats.queue",
        Locales.placeholders(
            "queued", String.valueOf(p.queuedRequests()),
            "maxQueued", String.valueOf(p.maxQueuedRequestsPerTick()),
            "budget", String.valueOf(p.maxParticlesPerPlayerPerTick()),
            "quality", String.valueOf(p.quality()),
            "range", String.valueOf(p.defaultRange())));
    CommandMessages.send(sender, "messages.command.effects.particles.stats.flush",
        Locales.placeholders(
            "lastFlushMs", String.valueOf(p.lastFlushNanos() / 1_000_000.0),
            "requests", String.valueOf(p.lastFlushRequests()),
            "sent", String.valueOf(p.lastFlushParticlesSent()),
            "droppedBudget", String.valueOf(p.lastFlushParticlesDroppedByBudget()),
            "droppedQueue", String.valueOf(p.lastDroppedRequestsByQueueCap())));
    return Command.SINGLE_SUCCESS;
  }

  private static int list(CommandContext<CommandSourceStack> ctx, EffectsEngine engine) {
    CommandSender sender = ctx.getSource().getSender();
    if (engine.abilityIds().isEmpty()) {
      CommandMessages.send(sender, "messages.command.effects.list.none");
      return Command.SINGLE_SUCCESS;
    }
    var parts = new java.util.ArrayList<String>(engine.abilityIds().size());
    for (String id : engine.abilityIds()) {
      var spec = engine.abilitySpec(id);
      if (spec != null && spec.name() != null && !spec.name().isBlank() && !spec.name().equalsIgnoreCase(id)) {
        parts.add(id + "§7(" + plainText(spec.name()) + "§7)");
      } else {
        parts.add(id);
      }
    }
    CommandMessages.send(sender, "messages.command.effects.list.header",
        Locales.placeholders("count", String.valueOf(parts.size()), "list", String.join("§7, §f", parts)));
    return Command.SINGLE_SUCCESS;
  }

  private static int lint(CommandContext<CommandSourceStack> ctx, EffectsYamlAbilities yaml, String scriptPath) {
    CommandSender sender = ctx.getSource().getSender();
    if (yaml == null) {
      CommandMessages.send(sender, "messages.command.effects.yamlMissing");
      return Command.SINGLE_SUCCESS;
    }
    EffectsYamlAbilities.LintResult result = scriptPath == null
        ? yaml.lintScripts()
        : yaml.lintScriptFile(scriptPath);
    CommandMessages.send(sender, "messages.command.effects.lint.header");
    CommandMessages.send(sender, "messages.command.effects.lint.summary",
        Locales.placeholders(
            "scripts", String.valueOf(result.scripts()),
            "errors", String.valueOf(result.errors().size())));
    if (!result.errors().isEmpty()) {
      int shown = Math.min(10, result.errors().size());
      for (int i = 0; i < shown; i++) {
        CommandMessages.send(sender, "messages.command.effects.lint.errorEntry",
            Locales.placeholders("error", result.errors().get(i)));
      }
      if (result.errors().size() > shown) {
        CommandMessages.send(sender, "messages.command.effects.lint.more",
            Locales.placeholders("count", String.valueOf(result.errors().size() - shown)));
      }
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int scriptRun(CommandContext<CommandSourceStack> ctx, EffectsYamlAbilities yaml, String filePath) {
    CommandSender sender = ctx.getSource().getSender();
    if (yaml == null) {
      CommandMessages.send(sender, "messages.command.effects.yamlMissing");
      return Command.SINGLE_SUCCESS;
    }
    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
      CommandMessages.send(sender, "messages.command.effects.script.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    try {
      yaml.runScriptFile(player, filePath);
      CommandMessages.send(sender, "messages.command.effects.script.run",
          Locales.placeholders("path", filePath));
    } catch (IllegalArgumentException ex) {
      CommandMessages.send(sender, "messages.command.effects.script.error",
          Locales.placeholders("message", ex.getMessage()));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int scriptStats(CommandContext<CommandSourceStack> ctx, EffectsYamlAbilities yaml) {
    CommandSender sender = ctx.getSource().getSender();
    if (yaml == null) {
      CommandMessages.send(sender, "messages.command.effects.yamlMissing");
      return Command.SINGLE_SUCCESS;
    }
    var stats = yaml.scriptMetrics();
    if (stats.isEmpty()) {
      CommandMessages.send(sender, "messages.command.effects.script.stats.none");
      return Command.SINGLE_SUCCESS;
    }
    int shown = Math.min(10, stats.size());
    CommandMessages.send(sender, "messages.command.effects.script.stats.header",
        Locales.placeholders("count", String.valueOf(shown)));
    for (int i = 0; i < shown; i++) {
      var entry = stats.get(i);
      long execs = entry.executions();
      double avgMs = execs == 0 ? 0.0 : (entry.totalNanos() / 1_000_000.0) / execs;
      CommandMessages.send(sender, "messages.command.effects.script.stats.entry",
          Locales.placeholders(
              "id", entry.scriptId(),
              "execs", String.valueOf(execs),
              "avgMs", String.format(java.util.Locale.ROOT, "%.2fms", avgMs),
              "errors", String.valueOf(entry.errors())));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int info(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, String abilityId) {
    var sender = ctx.getSource().getSender();
    try {
      if (!engine.hasAbility(abilityId)) {
        CommandMessages.send(sender, "messages.command.effects.abilityUnknown",
            Locales.placeholders("id", abilityId));
        return Command.SINGLE_SUCCESS;
      }
    } catch (IllegalArgumentException ex) {
      CommandMessages.send(sender, "messages.command.effects.abilityInvalid",
          Locales.placeholders("message", ex.getMessage()));
      return Command.SINGLE_SUCCESS;
    }

    var spec = engine.abilitySpec(abilityId);
    if (spec == null) {
      CommandMessages.send(sender, "messages.command.effects.abilityNoSpec",
          Locales.placeholders("id", abilityId));
      return Command.SINGLE_SUCCESS;
    }

    CommandMessages.send(sender, "messages.command.effects.info.ability",
        Locales.placeholders("id", spec.id()));
    if (spec.name() != null && !spec.name().isBlank()) {
      String label = CommandMessages.text(sender, "messages.command.effects.info.label.name");
      sender.sendMessage(Component.text(label, NamedTextColor.GRAY).append(richText(spec.name())));
    }
    if (spec.description() != null && !spec.description().isBlank()) {
      String[] lines = spec.description().split("\\R", -1);
      if (lines.length > 0) {
        String label = CommandMessages.text(sender, "messages.command.effects.info.label.description");
        sender.sendMessage(Component.text(label, NamedTextColor.GRAY).append(richText(lines[0])));
        for (int i = 1; i < lines.length; i++) {
          sender.sendMessage(Component.text("  ").append(richText(lines[i])));
        }
      }
    }
    if (spec.cooldownTicks() != null && spec.cooldownTicks() > 0) {
      CommandMessages.send(sender, "messages.command.effects.info.cooldown",
          Locales.placeholders(
              "ticks", String.valueOf(spec.cooldownTicks()),
              "key", spec.cooldownKey() == null ? spec.id() : spec.cooldownKey()));
    }
    if (!spec.costs().isEmpty()) {
      CommandMessages.send(sender, "messages.command.effects.info.costs",
          Locales.placeholders("count", String.valueOf(spec.costs().size())));
    }
    if (!spec.requirements().isEmpty()) {
      CommandMessages.send(sender, "messages.command.effects.info.requirements",
          Locales.placeholders("count", String.valueOf(spec.requirements().size())));
    }
    if (!spec.interactBindings().isEmpty()) {
      CommandMessages.send(sender, "messages.command.effects.info.interacts",
          Locales.placeholders("count", String.valueOf(spec.interactBindings().size())));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int timingsLast(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, String playerName) {
    var sender = ctx.getSource().getSender();
    Player player;
    if (playerName != null) {
      player = Bukkit.getPlayerExact(playerName);
      if (player == null) {
        CommandMessages.send(sender, "messages.command.effects.playerNotOnline");
        return Command.SINGLE_SUCCESS;
      }
    } else {
      var executor = ctx.getSource().getExecutor();
      if (!(executor instanceof Player p)) {
        CommandMessages.send(sender, "messages.command.effects.consoleNeedsPlayer");
        return Command.SINGLE_SUCCESS;
      }
      player = p;
    }

    var record = engine.lastCastRecord(player.getUniqueId());
    if (record == null) {
      CommandMessages.send(sender, "messages.command.effects.timings.noCastRecord");
      return Command.SINGLE_SUCCESS;
    }
    return timingsRender(sender, engine, record);
  }

  private static int timingsCast(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, String castIdStr) {
    var sender = ctx.getSource().getSender();
    java.util.UUID castId;
    try {
      castId = java.util.UUID.fromString(castIdStr);
    } catch (IllegalArgumentException ex) {
      CommandMessages.send(sender, "messages.command.effects.invalidUuid");
      return Command.SINGLE_SUCCESS;
    }
    var record = engine.castRecord(castId);
    if (record == null) {
      CommandMessages.send(sender, "messages.command.effects.timings.noCastRecord");
      return Command.SINGLE_SUCCESS;
    }
    return timingsRender(sender, engine, record);
  }

  private static int timingsRender(CommandSender sender, EffectsEngine engine, EffectsEngine.CastRecord record) {
    var timings = record.state().timings();
    if (timings.isEmpty()) {
      CommandMessages.send(sender, "messages.command.effects.timings.none",
          Locales.placeholders("castId", record.castId().toString()));
      return Command.SINGLE_SUCCESS;
    }

    long totalNanos = 0L;
    for (var t : timings.values()) {
      totalNanos += t.nanos();
    }

    var entries = new java.util.ArrayList<java.util.Map.Entry<String, dev.patric.dungeonsreborn.effects.CastState.Timing>>(timings.entrySet());
    entries.sort((a, b) -> Long.compare(b.getValue().nanos(), a.getValue().nanos()));

    CommandMessages.send(sender, "messages.command.effects.timings.header",
        Locales.placeholders("ability", record.abilityId(), "castId", record.castId().toString()));
    CommandMessages.send(sender, "messages.command.effects.timings.total",
        Locales.placeholders(
            "ms", formatMs(totalNanos),
            "keys", String.valueOf(entries.size())));

    int shown = 0;
    for (var e : entries) {
      if (shown++ >= 10) {
        break;
      }
      var t = e.getValue();
      double ms = t.nanos() / 1_000_000.0;
      double avg = t.count() <= 0 ? 0.0 : (ms / t.count());
      CommandMessages.send(sender, "messages.command.effects.timings.entry",
          Locales.placeholders(
              "key", e.getKey(),
              "ms", String.format(java.util.Locale.ROOT, "%.3f", ms),
              "count", String.valueOf(t.count()),
              "avg", String.format(java.util.Locale.ROOT, "%.3f", avg)));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static String formatMs(long nanos) {
    return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1_000_000.0);
  }

  private static int listTypes(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, String kind) {
    var sender = ctx.getSource().getSender();
    switch (kind) {
      case "actions" -> CommandMessages.send(sender, "messages.command.effects.types.actions",
          Locales.placeholders(
              "count", String.valueOf(engine.actionTypes().ids().size()),
              "list", String.join("§7, §f", engine.actionTypes().ids())));
      case "targeters" -> CommandMessages.send(sender, "messages.command.effects.types.targeters",
          Locales.placeholders(
              "count", String.valueOf(engine.targeterTypes().ids().size()),
              "list", String.join("§7, §f", engine.targeterTypes().ids())));
      case "conditions" -> CommandMessages.send(sender, "messages.command.effects.types.conditions",
          Locales.placeholders(
              "count", String.valueOf(engine.conditionTypes().ids().size()),
              "list", String.join("§7, §f", engine.conditionTypes().ids())));
      default -> CommandMessages.send(sender, "messages.command.effects.types.unknown");
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int cast(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, String targetName) {
    String abilityId = StringArgumentType.getString(ctx, "ability");
    CommandSender sender = ctx.getSource().getSender();
    Entity executor = ctx.getSource().getExecutor();

    Player target;
    if (targetName != null) {
      target = Bukkit.getPlayerExact(targetName);
      if (target == null) {
        CommandMessages.send(sender, "messages.command.effects.playerNotOnline");
        return Command.SINGLE_SUCCESS;
      }
    } else {
      if (!(executor instanceof Player player)) {
        CommandMessages.send(sender, "messages.command.effects.consoleNeedsPlayer");
        return Command.SINGLE_SUCCESS;
      }
      target = player;
    }

    try {
      EffectsEngine.CastResult result = engine.cast(abilityId, target);
      CommandMessages.send(sender, "messages.command.effects.cast.success",
          Locales.placeholders(
              "ability", result.abilityId(),
              "player", target.getName(),
              "castId", result.castId().toString(),
              "tick", String.valueOf(result.tickStarted())));
    } catch (IllegalArgumentException ex) {
      CommandMessages.send(sender, "messages.command.effects.cast.error",
          Locales.placeholders("message", ex.getMessage()));
    } catch (Exception ex) {
      CommandMessages.send(sender, "messages.command.effects.cast.failed",
          Locales.placeholders("error", ex.getClass().getSimpleName()));
      ex.printStackTrace();
    }

    return Command.SINGLE_SUCCESS;
  }

  private static Suggestions suggestAbilities(EffectsEngine engine, SuggestionsBuilder builder) {
    String remaining = builder.getRemainingLowerCase();
    for (String id : engine.abilityIds()) {
      if (remaining.isEmpty() || id.startsWith(remaining)) {
        builder.suggest(id);
      }
    }
    return builder.build();
  }

  private static Suggestions suggestOnlinePlayers(SuggestionsBuilder builder) {
    String remaining = builder.getRemainingLowerCase();
    for (Player p : Bukkit.getOnlinePlayers()) {
      String name = p.getName();
      if (remaining.isEmpty() || name.toLowerCase().startsWith(remaining)) {
        builder.suggest(name);
      }
    }
    return builder.build();
  }

  private static CompletableFuture<Suggestions> suggestAbilities(EffectsEngine engine, CommandContext<CommandSourceStack> ctx,
      SuggestionsBuilder builder) {
    return CompletableFuture.completedFuture(suggestAbilities(engine, builder));
  }

  private static CompletableFuture<Suggestions> suggestOnlinePlayers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
    return CompletableFuture.completedFuture(suggestOnlinePlayers(builder));
  }

  private static CompletableFuture<Suggestions> suggestItems(EffectsYamlAbilities yaml, CommandContext<CommandSourceStack> ctx,
      SuggestionsBuilder builder) {
    if (yaml == null) {
      return CompletableFuture.completedFuture(builder.build());
    }
    String remaining = builder.getRemainingLowerCase();
    for (String id : yaml.loadedItemIds()) {
      if (remaining.isEmpty() || id.startsWith(remaining)) {
        builder.suggest(id);
      }
    }
    return CompletableFuture.completedFuture(builder.build());
  }

  private static int combatStatus(CommandContext<CommandSourceStack> ctx, EffectsEngine engine) {
    CommandSender sender = ctx.getSource().getSender();
    CombatEventDispatcher dispatcher = engine.combatDispatcher();
    sender.sendMessage(Component.text("[Combat] " + dispatcher.status(), NamedTextColor.YELLOW));
    return Command.SINGLE_SUCCESS;
  }

  private static int combatMetrics(CommandContext<CommandSourceStack> ctx, EffectsEngine engine) {
    CommandSender sender = ctx.getSource().getSender();
    sender.sendMessage(Component.text("[Combat] " + engine.combatDispatcher().metrics().summary(), NamedTextColor.YELLOW));
    return Command.SINGLE_SUCCESS;
  }

  private static int combatMigrate(CommandContext<CommandSourceStack> ctx, EffectsYamlAbilities yaml) {
    CommandSender sender = ctx.getSource().getSender();
    if (yaml == null) {
      CommandMessages.send(sender, "messages.command.effects.yamlMissing");
      return 0;
    }
    var report = yaml.migrateCombatSchema(true);
    sender.sendMessage(Component.text(
        "[Combat] migrate scanned=" + report.filesScanned()
            + " changedFiles=" + report.filesChanged()
            + " changedNodes=" + report.nodesChanged()
            + " unresolved=" + report.unresolvedNodes(),
        report.unresolvedNodes() > 0 ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
    int limit = Math.min(20, report.details().size());
    for (int i = 0; i < limit; i++) {
      sender.sendMessage(Component.text(" - " + report.details().get(i), NamedTextColor.GRAY));
    }
    if (report.details().size() > limit) {
      sender.sendMessage(Component.text(" ... +" + (report.details().size() - limit) + " more", NamedTextColor.DARK_GRAY));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int combatDebug(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, boolean enabled) {
    CommandSender sender = ctx.getSource().getSender();
    var c = engine.plugin().getConfig();
    boolean async = c.getBoolean("effects.combat.asyncPlanner.enabled", true);
    int queue = c.getInt("effects.combat.asyncPlanner.queueCapacity", 12000);
    long ttl = c.getLong("effects.combat.asyncPlanner.planTtlTicks", 1L);
    int dispatchCap = c.getInt("effects.combat.guardrails.maxEventDispatchPerTick", 2000);
    int packetCap = c.getInt("effects.combat.guardrails.maxDamagePacketsPerTick", 4000);
    int projectileCap = c.getInt("effects.combat.projectiles.guardrails.maxProjectileEventsPerTick", 6000);
    int travelStepCap = c.getInt("effects.combat.projectiles.guardrails.maxTravelStepDispatchPerTick", 1200);
    String degrade = c.getString("effects.combat.guardrails.degradePolicy", "DROP_LOW_PRIORITY");
    engine.configureCombat(true, enabled, async, queue, ttl, dispatchCap, packetCap, degrade);
    engine.configureProjectileCombat(projectileCap, travelStepCap);
    sender.sendMessage(Component.text("[Combat] Debug " + (enabled ? "enabled" : "disabled"), NamedTextColor.GREEN));
    return Command.SINGLE_SUCCESS;
  }

  private static int combatTrace(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, String playerName) {
    CommandSender sender = ctx.getSource().getSender();
    Player player = Bukkit.getPlayerExact(playerName);
    if (player == null) {
      sender.sendMessage(Component.text("Player not found: " + playerName, NamedTextColor.RED));
      return 0;
    }
    var record = engine.lastCastRecord(player.getUniqueId());
    var failure = engine.lastCastFailure(player.getUniqueId());
    sender.sendMessage(Component.text("[Combat] trace player=" + player.getName(), NamedTextColor.YELLOW));
    sender.sendMessage(Component.text("  lastCast=" + (record == null ? "none" : (record.abilityId() + "@" + record.castId())), NamedTextColor.GRAY));
    sender.sendMessage(Component.text("  lastFailure=" + (failure == null ? "none" : (failure.type() + ":" + failure.reason())), NamedTextColor.GRAY));
    return Command.SINGLE_SUCCESS;
  }

  private static int combatSimulate(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, String abilityId, String targetName) {
    CommandSender sender = ctx.getSource().getSender();
    if (!(sender instanceof Player caster)) {
      sender.sendMessage(Component.text("Only players can run combat simulate.", NamedTextColor.RED));
      return 0;
    }
    Player target = Bukkit.getPlayerExact(targetName);
    if (target == null) {
      sender.sendMessage(Component.text("Target not found: " + targetName, NamedTextColor.RED));
      return 0;
    }
    if (!engine.hasAbility(abilityId)) {
      sender.sendMessage(Component.text("Ability not found: " + abilityId, NamedTextColor.RED));
      return 0;
    }
    engine.castWithContext(abilityId, caster, caster.getEyeLocation(), caster.getEyeLocation().getDirection(),
        caster.getInventory().getItemInMainHand(), c -> c.state().put("yaml_last_entity", target));
    sender.sendMessage(Component.text("[Combat] simulate cast sent: " + abilityId + " -> " + target.getName(), NamedTextColor.GREEN));
    return Command.SINGLE_SUCCESS;
  }

  private static boolean requirePermission(CommandSender sender, String permission) {
    if (sender instanceof Player player && !player.hasPermission(permission)) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", permission));
      return false;
    }
    return true;
  }

  private static String plainItemName(ItemStack item) {
    if (item == null) {
      return "";
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null || meta.displayName() == null) {
      return "";
    }
    return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
  }

  private static List<String> plainItemLore(ItemStack item) {
    if (item == null) {
      return List.of();
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null || meta.lore() == null || meta.lore().isEmpty()) {
      return List.of();
    }
    List<String> out = new java.util.ArrayList<>();
    for (Component line : meta.lore()) {
      out.add(line == null ? "" : PlainTextComponentSerializer.plainText().serialize(line));
    }
    return out;
  }

  private static String formatStats(Map<String, Double> stats) {
    if (stats == null || stats.isEmpty()) {
      return "none";
    }
    StringBuilder out = new StringBuilder();
    for (var entry : stats.entrySet()) {
      if (out.length() > 0) {
        out.append(", ");
      }
      out.append(entry.getKey()).append('=')
          .append(String.format(java.util.Locale.ROOT, "%.3f", entry.getValue()));
    }
    return out.toString();
  }

  private static String formatAffixPool(dev.patric.dungeonsreborn.effects.items.ItemAffixPool pool) {
    if (pool == null) {
      return "none";
    }
    StringBuilder out = new StringBuilder();
    out.append("rolls=").append(pool.rolls())
        .append(", dupes=").append(pool.allowDuplicates())
        .append(", affixes=");
    int shown = 0;
    for (dev.patric.dungeonsreborn.effects.items.ItemAffixSpec spec : pool.affixes()) {
      if (shown++ >= 6) {
        out.append("...");
        break;
      }
      if (shown > 1) {
        out.append(' ');
      }
      out.append(spec.id());
    }
    return out.toString();
  }
}
