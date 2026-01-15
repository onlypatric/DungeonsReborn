package dev.patric.dungeonsreborn.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

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
import dev.patric.dungeonsreborn.effects.mana.ManaProvider;
import dev.patric.dungeonsreborn.effects.minions.MinionManager;
import dev.patric.dungeonsreborn.effects.minions.MinionScaling;
import dev.patric.dungeonsreborn.effects.minions.MinionSpec;
import dev.patric.dungeonsreborn.effects.editor.EditorServices;
import dev.patric.dungeonsreborn.effects.editor.menu.EditorAbilityListMenu;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.List;
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
        .then(Commands.literal("reload").executes(ctx -> reload(ctx, yaml)))
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
            .then(Commands.literal("set")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1_000_000.0))
                    .executes(ctx -> manaSet(ctx, engine, DoubleArgumentType.getDouble(ctx, "value")))))
            .then(Commands.literal("add")
                .then(Commands.argument("delta", DoubleArgumentType.doubleArg(-1_000_000.0, 1_000_000.0))
                    .executes(ctx -> manaAdd(ctx, engine, DoubleArgumentType.getDouble(ctx, "delta")))))
            .then(Commands.literal("max")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.0, 1_000_000.0))
                    .executes(ctx -> manaMax(ctx, engine, DoubleArgumentType.getDouble(ctx, "value"))))))
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
              .then(Commands.literal("passive").executes(ctx -> minionMode(ctx, minions, dev.patric.dungeonsreborn.effects.minions.MinionMode.PASSIVE))))
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
    if (editor != null) {
      root.then(Commands.literal("editor").executes(ctx -> openEditor(ctx, editor)));
    }
    return root;
  }

  private static int help(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, EffectsYamlAbilities yaml) {
    CommandSender sender = ctx.getSource().getSender();
    sender.sendMessage(Component.text("§7/dr effects list"));
    sender.sendMessage(Component.text("§7/dr effects debug <on|off> §8(current: " + (engine.isDebugEnabled() ? "on" : "off") + ")"));
    String scriptStatus = yaml == null ? "unavailable" : (yaml.isScriptDebugEnabled() ? "on" : "off");
    sender.sendMessage(Component.text("§7/dr effects debug script <on|off> §8(current: " + scriptStatus + ")"));
    String traceStatus = yaml == null ? "unavailable" : (yaml.isScriptTraceEnabled() ? "on" : "off");
    sender.sendMessage(Component.text("§7/dr effects debug script trace <on|off> §8(current: " + traceStatus + ")"));
    sender.sendMessage(Component.text("§7/dr effects tag <on|off> §8(tags held item with " + ItemMarkers.DEBUG_MARKER.asString() + ")"));
    sender.sendMessage(Component.text("§7/dr effects bind add <right|left> <ability>"));
    sender.sendMessage(Component.text("§7/dr effects bind remove <right|left> <ability>"));
    sender.sendMessage(Component.text("§7/dr effects bind list"));
    sender.sendMessage(Component.text("§7/dr effects bind clear <right|left|all>"));
    sender.sendMessage(Component.text("§7/dr effects explain <right|left>"));
    sender.sendMessage(Component.text("§7/dr effects mana <show|set|add|max>"));
    sender.sendMessage(Component.text("§7/dr effects reload §8(reload YAML abilities)"));
    sender.sendMessage(Component.text("§7/dr effects logging reload §8(reload logging levels)"));
    sender.sendMessage(Component.text("§7/dr effects stats"));
    sender.sendMessage(Component.text("§7/dr effects particles range <blocks> §8(current: " + engine.particles().defaultRange() + ")"));
    sender.sendMessage(Component.text("§7/dr effects particles queue <maxRequestsPerTick> §8(current: " + engine.particles().maxQueuedRequestsPerTick() + ")"));
    sender.sendMessage(Component.text("§7/dr effects particles budget <maxPerPlayerTick> §8(current: " + engine.particles().maxParticlesPerPlayerPerTick() + ")"));
    sender.sendMessage(Component.text("§7/dr effects particles quality <multiplier> §8(current: " + engine.particles().quality() + ")"));
    sender.sendMessage(Component.text("§7/dr effects particles stats"));
    sender.sendMessage(Component.text("§7/dr effects cast <ability> [player]"));
    sender.sendMessage(Component.text("§7/dr effects info <ability>"));
    sender.sendMessage(Component.text("§7/dr effects timings <last [player]|cast <uuid>>"));
    sender.sendMessage(Component.text("§7/dr effects types <actions|targeters|conditions>"));
    sender.sendMessage(Component.text("§7/dr effects minions <recall|dismiss|mode|list|stats|test>"));
    sender.sendMessage(Component.text("§7/dr effects script run <file>"));
    sender.sendMessage(Component.text("§7/dr effects script stats"));
    sender.sendMessage(Component.text("§7/dr effects lint [script]"));
    sender.sendMessage(Component.text("§7/dr effects editor"));
    sender.sendMessage(Component.text("§7Registered abilities: §f" + engine.abilityIds().size()));
    return Command.SINGLE_SUCCESS;
  }

  private static int openEditor(CommandContext<CommandSourceStack> ctx, EditorServices editor) {
    CommandSender sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      sender.sendMessage(Component.text("§cOnly players can use this command"));
      return Command.SINGLE_SUCCESS;
    }
    if (!editor.access().canView(player)) {
      player.sendMessage(Component.text("§cMissing permission: dungeonsreborn.editor.view"));
      return Command.SINGLE_SUCCESS;
    }
    new EditorAbilityListMenu(editor).open(player);
    return Command.SINGLE_SUCCESS;
  }

  private static int minionRecall(CommandContext<CommandSourceStack> ctx, MinionManager minions) {
    CommandSender sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      sender.sendMessage(Component.text("§cOnly players can use this command"));
      return Command.SINGLE_SUCCESS;
    }
    int count = minions.recall(player.getUniqueId(), player.getLocation());
    player.sendMessage(Component.text("§7Recalled minions: §f" + count));
    return Command.SINGLE_SUCCESS;
  }

  private static int minionDismiss(CommandContext<CommandSourceStack> ctx, MinionManager minions) {
    CommandSender sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      sender.sendMessage(Component.text("§cOnly players can use this command"));
      return Command.SINGLE_SUCCESS;
    }
    int count = minions.dismiss(player.getUniqueId());
    player.sendMessage(Component.text("§7Dismissed minions: §f" + count));
    return Command.SINGLE_SUCCESS;
  }

  private static int minionMode(CommandContext<CommandSourceStack> ctx, MinionManager minions,
                                dev.patric.dungeonsreborn.effects.minions.MinionMode mode) {
    CommandSender sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      sender.sendMessage(Component.text("§cOnly players can use this command"));
      return Command.SINGLE_SUCCESS;
    }
    minions.setMode(player.getUniqueId(), mode);
    player.sendMessage(Component.text("§7Minion mode set to §f" + mode.name().toLowerCase(Locale.ROOT)));
    return Command.SINGLE_SUCCESS;
  }

  private static int minionTest(CommandContext<CommandSourceStack> ctx, MinionManager minions,
                                String mobId, int count, int durationTicks, double radius) {
    CommandSender sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      sender.sendMessage(Component.text("§cOnly players can use this command"));
      return Command.SINGLE_SUCCESS;
    }
    String id = "test_" + mobId;
    MinionSpec spec = new MinionSpec(id, mobId, count, durationTicks, player.getUniqueId(), radius,
        MinionScaling.NONE, java.util.Map.of(), java.util.Set.of(), true, null, java.util.List.of(), java.util.List.of());
    minions.summon(spec, player.getLocation());
    player.sendMessage(Component.text("§7Summoned test minions: §f" + mobId));
    return Command.SINGLE_SUCCESS;
  }

  private static int minionList(CommandContext<CommandSourceStack> ctx, MinionManager minions) {
    CommandSender sender = ctx.getSource().getSender();
    Map<java.util.UUID, List<java.util.UUID>> snapshot = minions.ownersSnapshot();
    if (snapshot.isEmpty()) {
      sender.sendMessage(Component.text("§7No active minions."));
      return Command.SINGLE_SUCCESS;
    }
    sender.sendMessage(Component.text("§7Active minions by owner:"));
    for (Map.Entry<java.util.UUID, List<java.util.UUID>> entry : snapshot.entrySet()) {
      java.util.UUID ownerId = entry.getKey();
      Player player = Bukkit.getPlayer(ownerId);
      String name = player != null ? player.getName() : ownerId.toString();
      sender.sendMessage(Component.text("§f- " + name + " §7x§f" + entry.getValue().size()));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int minionStats(CommandContext<CommandSourceStack> ctx, MinionManager minions) {
    CommandSender sender = ctx.getSource().getSender();
    sender.sendMessage(Component.text("§7Minion stats"));
    sender.sendMessage(Component.text("§f- active: §a" + minions.activeCount()));
    sender.sendMessage(Component.text("§f- spawned: §a" + minions.spawnedCount()));
    sender.sendMessage(Component.text("§f- despawned: §a" + minions.despawnedCount()));
    return Command.SINGLE_SUCCESS;
  }

  private static int explain(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, EffectsBindings bindings, InteractTrigger trigger) {
    var sender = ctx.getSource().getSender();
    if (bindings == null) {
      sender.sendMessage(Component.text("§cBindings integration not installed."));
      return Command.SINGLE_SUCCESS;
    }
    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
      sender.sendMessage(Component.text("§cOnly players can use this command"));
      return Command.SINGLE_SUCCESS;
    }
    for (String line : bindings.explain(player, trigger)) {
      sender.sendMessage(Component.text("§7" + line));
    }
    sender.sendMessage(Component.text("§7Registered abilities: §f" + engine.abilityIds().size()));
    return Command.SINGLE_SUCCESS;
  }

  private static int reload(CommandContext<CommandSourceStack> ctx, EffectsYamlAbilities yaml) {
    var sender = ctx.getSource().getSender();
    if (yaml == null) {
      sender.sendMessage(Component.text("§cYAML loader not installed."));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.effects.reload")) {
      sender.sendMessage(Component.text("§cMissing permission: dungeonsreborn.effects.reload"));
      return Command.SINGLE_SUCCESS;
    }
    var result = yaml.reload();
    int updated = yaml.syncOnlineItems();
    sender.sendMessage(Component.text("§7File: §f" + yaml.file().getPath()));
    sender.sendMessage(Component.text("§aReloaded YAML abilities: §f" + result.loadedAbilities()
        + "§a loaded, item bindings: §f" + result.loadedItemBindings()
        + "§a, errors: §f" + result.errors().size()));
    if (updated > 0) {
      sender.sendMessage(Component.text("§aUpdated §f" + updated + " §aitems for online players."));
    }
    if (!result.errors().isEmpty()) {
      int shown = Math.min(10, result.errors().size());
      for (int i = 0; i < shown; i++) {
        sender.sendMessage(Component.text("§c- " + result.errors().get(i)));
      }
      if (result.errors().size() > shown) {
        sender.sendMessage(Component.text("§7... +" + (result.errors().size() - shown) + " more"));
      }
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int loggingReload(CommandContext<CommandSourceStack> ctx, EffectsEngine engine) {
    CommandSender sender = ctx.getSource().getSender();
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.effects.reload")) {
      sender.sendMessage(Component.text("§cMissing permission: dungeonsreborn.effects.reload"));
      return Command.SINGLE_SUCCESS;
    }
    var plugin = engine.plugin();
    plugin.reloadConfig();
    if (plugin instanceof DungeonsRebornPlugin dungeonsReborn && dungeonsReborn.serviceLog() != null) {
      dungeonsReborn.serviceLog().reloadFromConfig(plugin.getConfig().getConfigurationSection("logging"));
      dungeonsReborn.reloadRuntimeConfig();
      sender.sendMessage(Component.text("§aReloaded logging levels from config.yml"));
    } else {
      sender.sendMessage(Component.text("§cLogging configuration not available."));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int stats(CommandContext<CommandSourceStack> ctx, EffectsEngine engine) {
    var sender = ctx.getSource().getSender();
    var e = engine.stats();
    var p = engine.particles().stats();
    sender.sendMessage(Component.text("§6[Effects] §fEngine stats"));
    sender.sendMessage(Component.text("§7tick=§f" + e.tick() + " §7lastTick=§f" + (e.lastTickNanos() / 1_000_000.0) + "ms"));
    sender.sendMessage(Component.text("§7scheduled: §ftick=" + e.scheduledTickTasks() + " §7rt=" + e.scheduledRealTimeTasks()));
    sender.sendMessage(Component.text("§7tracked: §fcasts=" + e.trackedCastRecords() + " §7cooldowns=" + e.cooldownPlayers() + " §7immunities=" + e.immunityEntities()));
    sender.sendMessage(Component.text("§6[Effects] §fParticles"));
    sender.sendMessage(Component.text(
        "§7queue=§f" + p.queuedRequests()
            + "§7/§f" + p.maxQueuedRequestsPerTick()
            + " §7quality=§f" + p.quality()
            + " §7range=§f" + p.defaultRange()));
    sender.sendMessage(Component.text(
        "§7lastFlush=§f" + (p.lastFlushNanos() / 1_000_000.0) + "ms"
            + " §7requests=§f" + p.lastFlushRequests()
            + " §7sent=§f" + p.lastFlushParticlesSent()
            + " §7dropped(budget)=§f" + p.lastFlushParticlesDroppedByBudget()
            + " §7dropped(queue)=§f" + p.lastDroppedRequestsByQueueCap()));
    return Command.SINGLE_SUCCESS;
  }

  private static int debug(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, boolean enabled) {
    engine.setDebug(enabled);
    ctx.getSource().getSender().sendMessage(Component.text("§aEffects debug " + (enabled ? "enabled" : "disabled")));
    return Command.SINGLE_SUCCESS;
  }

  private static int debugScript(CommandContext<CommandSourceStack> ctx, EffectsYamlAbilities yaml, boolean enabled) {
    CommandSender sender = ctx.getSource().getSender();
    if (yaml == null) {
      sender.sendMessage(Component.text("§cYAML loader not installed."));
      return Command.SINGLE_SUCCESS;
    }
    yaml.setScriptDebug(enabled);
    sender.sendMessage(Component.text("§aEffects script debug " + (enabled ? "enabled" : "disabled")));
    return Command.SINGLE_SUCCESS;
  }

  private static int debugScriptTrace(CommandContext<CommandSourceStack> ctx, EffectsYamlAbilities yaml, boolean enabled) {
    CommandSender sender = ctx.getSource().getSender();
    if (yaml == null) {
      sender.sendMessage(Component.text("§cYAML loader not installed."));
      return Command.SINGLE_SUCCESS;
    }
    yaml.setScriptTrace(enabled);
    sender.sendMessage(Component.text("§aEffects script trace " + (enabled ? "enabled" : "disabled")));
    return Command.SINGLE_SUCCESS;
  }

  private static int tag(CommandContext<CommandSourceStack> ctx, boolean enabled) {
    CommandSender sender = ctx.getSource().getSender();
    Entity executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      sender.sendMessage(Component.text("§cOnly players can use this command"));
      return Command.SINGLE_SUCCESS;
    }
    var item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      sender.sendMessage(Component.text("§cHold an item in your main hand"));
      return Command.SINGLE_SUCCESS;
    }
    ItemMarkers.set(item, ItemMarkers.DEBUG_MARKER, enabled);
    player.getInventory().setItemInMainHand(item);
    player.updateInventory();
    sender.sendMessage(Component.text("§aTag " + (enabled ? "enabled" : "disabled") + " on held item"));
    return Command.SINGLE_SUCCESS;
  }

  private static int bindAdd(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, org.bukkit.NamespacedKey key, String abilityId) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      sender.sendMessage(Component.text("§cOnly players can use this command"));
      return Command.SINGLE_SUCCESS;
    }
    try {
      if (!engine.hasAbility(abilityId)) {
        sender.sendMessage(Component.text("§cUnknown ability: " + abilityId));
        return Command.SINGLE_SUCCESS;
      }
    } catch (IllegalArgumentException ex) {
      sender.sendMessage(Component.text("§cInvalid ability id: " + ex.getMessage()));
      return Command.SINGLE_SUCCESS;
    }

    var item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      sender.sendMessage(Component.text("§cHold an item in your main hand"));
      return Command.SINGLE_SUCCESS;
    }
    try {
      ItemMarkers.addToStringList(item, key, abilityId);
    } catch (IllegalArgumentException ex) {
      sender.sendMessage(Component.text("§cInvalid ability id: " + ex.getMessage()));
      return Command.SINGLE_SUCCESS;
    }
    player.getInventory().setItemInMainHand(item);
    player.updateInventory();
    sender.sendMessage(Component.text("§aBound §f" + abilityId + " §ato held item (" + key.getKey() + ")"));
    return Command.SINGLE_SUCCESS;
  }

  private static int bindRemove(CommandContext<CommandSourceStack> ctx, org.bukkit.NamespacedKey key, String abilityId) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      sender.sendMessage(Component.text("§cOnly players can use this command"));
      return Command.SINGLE_SUCCESS;
    }
    var item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      sender.sendMessage(Component.text("§cHold an item in your main hand"));
      return Command.SINGLE_SUCCESS;
    }
    ItemMarkers.removeFromStringList(item, key, abilityId);
    player.getInventory().setItemInMainHand(item);
    player.updateInventory();
    sender.sendMessage(Component.text("§aUnbound §f" + abilityId + " §afrom held item (" + key.getKey() + ")"));
    return Command.SINGLE_SUCCESS;
  }

  private static int bindList(CommandContext<CommandSourceStack> ctx) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      sender.sendMessage(Component.text("§cOnly players can use this command"));
      return Command.SINGLE_SUCCESS;
    }
    var item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      sender.sendMessage(Component.text("§cHold an item in your main hand"));
      return Command.SINGLE_SUCCESS;
    }
    List<String> right = ItemMarkers.getStringList(item, ItemMarkers.RIGHT_CLICK_ABILITIES);
    List<String> left = ItemMarkers.getStringList(item, ItemMarkers.LEFT_CLICK_ABILITIES);
    sender.sendMessage(Component.text("§7Right-click abilities: §f" + (right.isEmpty() ? "(none)" : String.join(", ", right))));
    sender.sendMessage(Component.text("§7Left-click abilities: §f" + (left.isEmpty() ? "(none)" : String.join(", ", left))));
    return Command.SINGLE_SUCCESS;
  }

  private static int bindClear(CommandContext<CommandSourceStack> ctx, org.bukkit.NamespacedKey key) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      sender.sendMessage(Component.text("§cOnly players can use this command"));
      return Command.SINGLE_SUCCESS;
    }
    var item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      sender.sendMessage(Component.text("§cHold an item in your main hand"));
      return Command.SINGLE_SUCCESS;
    }
    ItemMarkers.setStringList(item, key, List.of());
    player.getInventory().setItemInMainHand(item);
    player.updateInventory();
    sender.sendMessage(Component.text("§aCleared bindings (" + key.getKey() + ") on held item"));
    return Command.SINGLE_SUCCESS;
  }

  private static int bindClearAll(CommandContext<CommandSourceStack> ctx) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      sender.sendMessage(Component.text("§cOnly players can use this command"));
      return Command.SINGLE_SUCCESS;
    }
    var item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      sender.sendMessage(Component.text("§cHold an item in your main hand"));
      return Command.SINGLE_SUCCESS;
    }
    ItemMarkers.setStringList(item, ItemMarkers.RIGHT_CLICK_ABILITIES, List.of());
    ItemMarkers.setStringList(item, ItemMarkers.LEFT_CLICK_ABILITIES, List.of());
    player.getInventory().setItemInMainHand(item);
    player.updateInventory();
    sender.sendMessage(Component.text("§aCleared all bindings on held item"));
    return Command.SINGLE_SUCCESS;
  }

  private static int manaShow(CommandContext<CommandSourceStack> ctx, EffectsEngine engine) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      sender.sendMessage(Component.text("§cOnly players can use this command"));
      return Command.SINGLE_SUCCESS;
    }
    ManaProvider provider = engine.manaProvider();
    if (provider == null) {
      sender.sendMessage(Component.text("§cNo mana provider installed."));
      return Command.SINGLE_SUCCESS;
    }
    sender.sendMessage(Component.text("§bMana: §f" + format(provider.get(player)) + "§7/§f" + format(provider.getMax(player))));
    return Command.SINGLE_SUCCESS;
  }

  private static int manaSet(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, double value) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      sender.sendMessage(Component.text("§cOnly players can use this command"));
      return Command.SINGLE_SUCCESS;
    }
    ManaProvider provider = engine.manaProvider();
    if (provider == null) {
      sender.sendMessage(Component.text("§cNo mana provider installed."));
      return Command.SINGLE_SUCCESS;
    }
    provider.set(player, value);
    sender.sendMessage(Component.text("§aMana set to §f" + format(provider.get(player)) + "§7/§f" + format(provider.getMax(player))));
    return Command.SINGLE_SUCCESS;
  }

  private static int manaAdd(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, double delta) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      sender.sendMessage(Component.text("§cOnly players can use this command"));
      return Command.SINGLE_SUCCESS;
    }
    ManaProvider provider = engine.manaProvider();
    if (provider == null) {
      sender.sendMessage(Component.text("§cNo mana provider installed."));
      return Command.SINGLE_SUCCESS;
    }
    provider.set(player, provider.get(player) + delta);
    sender.sendMessage(Component.text("§aMana: §f" + format(provider.get(player)) + "§7/§f" + format(provider.getMax(player))));
    return Command.SINGLE_SUCCESS;
  }

  private static int manaMax(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, double value) {
    var sender = ctx.getSource().getSender();
    var executor = ctx.getSource().getExecutor();
    if (!(executor instanceof Player player)) {
      sender.sendMessage(Component.text("§cOnly players can use this command"));
      return Command.SINGLE_SUCCESS;
    }
    ManaProvider provider = engine.manaProvider();
    if (provider == null) {
      sender.sendMessage(Component.text("§cNo mana provider installed."));
      return Command.SINGLE_SUCCESS;
    }
    provider.setMax(player, value);
    sender.sendMessage(Component.text("§aMana max set to §f" + format(provider.getMax(player))));
    return Command.SINGLE_SUCCESS;
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
    ctx.getSource().getSender().sendMessage(Component.text("§aParticles default range set to " + blocks));
    return Command.SINGLE_SUCCESS;
  }

  private static int particlesQueue(CommandContext<CommandSourceStack> ctx, EffectsEngine engine) {
    int max = IntegerArgumentType.getInteger(ctx, "maxRequestsPerTick");
    engine.particles().setMaxQueuedRequestsPerTick(max);
    ctx.getSource().getSender().sendMessage(Component.text("§aParticles queue cap set to " + max + " requests per tick"));
    return Command.SINGLE_SUCCESS;
  }

  private static int particlesBudget(CommandContext<CommandSourceStack> ctx, EffectsEngine engine) {
    int max = IntegerArgumentType.getInteger(ctx, "maxPerPlayerTick");
    engine.particles().setMaxParticlesPerPlayerPerTick(max);
    ctx.getSource().getSender().sendMessage(Component.text("§aParticles budget set to " + max + " per player per tick"));
    return Command.SINGLE_SUCCESS;
  }

  private static int particlesQuality(CommandContext<CommandSourceStack> ctx, EffectsEngine engine) {
    double q = DoubleArgumentType.getDouble(ctx, "multiplier");
    engine.particles().setQuality(q);
    ctx.getSource().getSender().sendMessage(Component.text("§aParticles quality set to " + q));
    return Command.SINGLE_SUCCESS;
  }

  private static int particlesStats(CommandContext<CommandSourceStack> ctx, EffectsEngine engine) {
    var sender = ctx.getSource().getSender();
    var p = engine.particles().stats();
    sender.sendMessage(Component.text("§6[Effects] §fParticles stats"));
    sender.sendMessage(Component.text(
        "§7queue=§f" + p.queuedRequests()
            + "§7/§f" + p.maxQueuedRequestsPerTick()
            + " §7budget=§f" + p.maxParticlesPerPlayerPerTick()
            + " §7quality=§f" + p.quality()
            + " §7range=§f" + p.defaultRange()));
    sender.sendMessage(Component.text(
        "§7lastFlush=§f" + (p.lastFlushNanos() / 1_000_000.0) + "ms"
            + " §7requests=§f" + p.lastFlushRequests()
            + " §7sent=§f" + p.lastFlushParticlesSent()
            + " §7dropped(budget)=§f" + p.lastFlushParticlesDroppedByBudget()
            + " §7dropped(queue)=§f" + p.lastDroppedRequestsByQueueCap()));
    return Command.SINGLE_SUCCESS;
  }

  private static int list(CommandContext<CommandSourceStack> ctx, EffectsEngine engine) {
    CommandSender sender = ctx.getSource().getSender();
    if (engine.abilityIds().isEmpty()) {
      sender.sendMessage(Component.text("§7No abilities registered."));
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
    sender.sendMessage(Component.text("§aAbilities (§f" + parts.size() + "§a): §f" + String.join("§7, §f", parts)));
    return Command.SINGLE_SUCCESS;
  }

  private static int lint(CommandContext<CommandSourceStack> ctx, EffectsYamlAbilities yaml, String scriptPath) {
    CommandSender sender = ctx.getSource().getSender();
    if (yaml == null) {
      sender.sendMessage(Component.text("§cYAML loader not installed."));
      return Command.SINGLE_SUCCESS;
    }
    EffectsYamlAbilities.LintResult result = scriptPath == null
        ? yaml.lintScripts()
        : yaml.lintScriptFile(scriptPath);
    sender.sendMessage(Component.text("§6[Effects] §fDSL lint"));
    sender.sendMessage(Component.text("§7Scripts: §f" + result.scripts() + " §7Errors: §f" + result.errors().size()));
    if (!result.errors().isEmpty()) {
      int shown = Math.min(10, result.errors().size());
      for (int i = 0; i < shown; i++) {
        sender.sendMessage(Component.text("§c- " + result.errors().get(i)));
      }
      if (result.errors().size() > shown) {
        sender.sendMessage(Component.text("§7... +" + (result.errors().size() - shown) + " more"));
      }
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int scriptRun(CommandContext<CommandSourceStack> ctx, EffectsYamlAbilities yaml, String filePath) {
    CommandSender sender = ctx.getSource().getSender();
    if (yaml == null) {
      sender.sendMessage(Component.text("§cYAML loader not installed."));
      return Command.SINGLE_SUCCESS;
    }
    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
      sender.sendMessage(Component.text("§cOnly players can run scripts"));
      return Command.SINGLE_SUCCESS;
    }
    try {
      yaml.runScriptFile(player, filePath);
      sender.sendMessage(Component.text("§aRan script: §f" + filePath));
    } catch (IllegalArgumentException ex) {
      sender.sendMessage(Component.text("§cScript error: " + ex.getMessage()));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int scriptStats(CommandContext<CommandSourceStack> ctx, EffectsYamlAbilities yaml) {
    CommandSender sender = ctx.getSource().getSender();
    if (yaml == null) {
      sender.sendMessage(Component.text("§cYAML loader not installed."));
      return Command.SINGLE_SUCCESS;
    }
    var stats = yaml.scriptMetrics();
    if (stats.isEmpty()) {
      sender.sendMessage(Component.text("§7No script metrics recorded."));
      return Command.SINGLE_SUCCESS;
    }
    int shown = Math.min(10, stats.size());
    sender.sendMessage(Component.text("§6[Effects] §fScript metrics (top " + shown + ")"));
    for (int i = 0; i < shown; i++) {
      var entry = stats.get(i);
      long execs = entry.executions();
      double avgMs = execs == 0 ? 0.0 : (entry.totalNanos() / 1_000_000.0) / execs;
      sender.sendMessage(Component.text(
          "§7- §f" + entry.scriptId()
              + " §7exec=" + execs
              + " §7avg=" + String.format(java.util.Locale.ROOT, "%.2fms", avgMs)
              + " §7errors=" + entry.errors()));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int info(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, String abilityId) {
    var sender = ctx.getSource().getSender();
    try {
      if (!engine.hasAbility(abilityId)) {
        sender.sendMessage(Component.text("§cUnknown ability: " + abilityId));
        return Command.SINGLE_SUCCESS;
      }
    } catch (IllegalArgumentException ex) {
      sender.sendMessage(Component.text("§c" + ex.getMessage()));
      return Command.SINGLE_SUCCESS;
    }

    var spec = engine.abilitySpec(abilityId);
    if (spec == null) {
      sender.sendMessage(Component.text("§cNo spec found for: " + abilityId));
      return Command.SINGLE_SUCCESS;
    }

    sender.sendMessage(Component.text("Ability ", NamedTextColor.GREEN).append(Component.text(spec.id(), NamedTextColor.WHITE)));
    if (spec.name() != null && !spec.name().isBlank()) {
      sender.sendMessage(Component.text("Name: ", NamedTextColor.GRAY).append(richText(spec.name())));
    }
    if (spec.description() != null && !spec.description().isBlank()) {
      String[] lines = spec.description().split("\\R", -1);
      if (lines.length > 0) {
        sender.sendMessage(Component.text("Description: ", NamedTextColor.GRAY).append(richText(lines[0])));
        for (int i = 1; i < lines.length; i++) {
          sender.sendMessage(Component.text("  ").append(richText(lines[i])));
        }
      }
    }
    if (spec.cooldownTicks() != null && spec.cooldownTicks() > 0) {
      sender.sendMessage(Component.text(
          "§7Cooldown: §f" + spec.cooldownTicks() + "t §8(key=" + (spec.cooldownKey() == null ? spec.id() : spec.cooldownKey()) + ")"));
    }
    if (!spec.costs().isEmpty()) {
      sender.sendMessage(Component.text("§7Costs: §f" + spec.costs().size()));
    }
    if (!spec.requirements().isEmpty()) {
      sender.sendMessage(Component.text("§7Requirements: §f" + spec.requirements().size()));
    }
    if (!spec.interactBindings().isEmpty()) {
      sender.sendMessage(Component.text("§7Interact triggers: §f" + spec.interactBindings().size()));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int timingsLast(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, String playerName) {
    var sender = ctx.getSource().getSender();
    Player player;
    if (playerName != null) {
      player = Bukkit.getPlayerExact(playerName);
      if (player == null) {
        sender.sendMessage(Component.text("§cPlayer not online"));
        return Command.SINGLE_SUCCESS;
      }
    } else {
      var executor = ctx.getSource().getExecutor();
      if (!(executor instanceof Player p)) {
        sender.sendMessage(Component.text("§cConsole must specify a player"));
        return Command.SINGLE_SUCCESS;
      }
      player = p;
    }

    var record = engine.lastCastRecord(player.getUniqueId());
    if (record == null) {
      sender.sendMessage(Component.text("§7No cast record found."));
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
      sender.sendMessage(Component.text("§cInvalid UUID"));
      return Command.SINGLE_SUCCESS;
    }
    var record = engine.castRecord(castId);
    if (record == null) {
      sender.sendMessage(Component.text("§7No cast record found."));
      return Command.SINGLE_SUCCESS;
    }
    return timingsRender(sender, engine, record);
  }

  private static int timingsRender(CommandSender sender, EffectsEngine engine, EffectsEngine.CastRecord record) {
    var timings = record.state().timings();
    if (timings.isEmpty()) {
      sender.sendMessage(Component.text("§7No timings recorded for castId=" + record.castId() + " (wrap actions with Actions.timed(...))"));
      return Command.SINGLE_SUCCESS;
    }

    long totalNanos = 0L;
    for (var t : timings.values()) {
      totalNanos += t.nanos();
    }

    var entries = new java.util.ArrayList<java.util.Map.Entry<String, dev.patric.dungeonsreborn.effects.CastState.Timing>>(timings.entrySet());
    entries.sort((a, b) -> Long.compare(b.getValue().nanos(), a.getValue().nanos()));

    sender.sendMessage(Component.text("§aTimings for §f" + record.abilityId() + " §7(castId=" + record.castId() + ")"));
    sender.sendMessage(Component.text("§7Total recorded: §f" + formatMs(totalNanos) + "ms §8(" + entries.size() + " keys)"));

    int shown = 0;
    for (var e : entries) {
      if (shown++ >= 10) {
        break;
      }
      var t = e.getValue();
      double ms = t.nanos() / 1_000_000.0;
      double avg = t.count() <= 0 ? 0.0 : (ms / t.count());
      sender.sendMessage(Component.text("§f" + e.getKey() + "§7: §f" + String.format(java.util.Locale.ROOT, "%.3f", ms) + "ms"
          + " §8(" + t.count() + "x, avg " + String.format(java.util.Locale.ROOT, "%.3f", avg) + "ms)"));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static String formatMs(long nanos) {
    return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1_000_000.0);
  }

  private static int listTypes(CommandContext<CommandSourceStack> ctx, EffectsEngine engine, String kind) {
    var sender = ctx.getSource().getSender();
    switch (kind) {
      case "actions" -> sender.sendMessage(Component.text("§aAction types (§f" + engine.actionTypes().ids().size() + "§a): §f"
          + String.join("§7, §f", engine.actionTypes().ids())));
      case "targeters" -> sender.sendMessage(Component.text("§aTargeter types (§f" + engine.targeterTypes().ids().size() + "§a): §f"
          + String.join("§7, §f", engine.targeterTypes().ids())));
      case "conditions" -> sender.sendMessage(Component.text("§aCondition types (§f" + engine.conditionTypes().ids().size() + "§a): §f"
          + String.join("§7, §f", engine.conditionTypes().ids())));
      default -> sender.sendMessage(Component.text("§cUnknown kind"));
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

    try {
      EffectsEngine.CastResult result = engine.cast(abilityId, target);
      sender.sendMessage(Component.text("§aCast §f" + result.abilityId() + " §aon §f" + target.getName()
          + " §7(castId=" + result.castId() + ", tick=" + result.tickStarted() + ")"));
    } catch (IllegalArgumentException ex) {
      sender.sendMessage(Component.text("§c" + ex.getMessage()));
    } catch (Exception ex) {
      sender.sendMessage(Component.text("§cCast failed: " + ex.getClass().getSimpleName()));
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
}
