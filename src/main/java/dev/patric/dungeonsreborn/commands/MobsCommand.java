package dev.patric.dungeonsreborn.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.concurrent.CompletableFuture;

import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.mobs.MobSpawnManager;
import dev.patric.dungeonsreborn.mobs.MobSpawnSpec;
import dev.patric.dungeonsreborn.mobs.MobSpawnerBlockStore;
import dev.patric.dungeonsreborn.mobs.MobSpawnerItems;
import dev.patric.dungeonsreborn.mobs.MobYamlRegistry;
import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.locale.Locales;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

public final class MobsCommand {
  private MobsCommand() {
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createCommand(MobYamlRegistry yaml, MobRegistry registry,
      MobSpawnManager spawns, MobSpawnerBlockStore spawnerStore) {
    return Commands.literal("mobs")
        .executes(ctx -> help(ctx))
        .then(Commands.literal("reload").executes(ctx -> reload(ctx, yaml)))
        .then(Commands.literal("list").executes(ctx -> list(ctx, registry)))
        .then(Commands.literal("spawners").executes(ctx -> spawners(ctx, spawns)))
        .then(Commands.literal("loot")
            .then(Commands.literal("list").executes(ctx -> lootList(ctx, yaml)))
            .then(Commands.literal("info")
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestLootPools(yaml, builder))
                    .executes(ctx -> lootInfo(ctx, yaml, StringArgumentType.getString(ctx, "id"))))))
        .then(Commands.literal("spawner")
            .then(Commands.literal("give")
                .then(Commands.argument("mob", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestMobs(registry, builder))
                    .executes(ctx -> spawnerGive(ctx, yaml, registry, StringArgumentType.getString(ctx, "mob"), null))
                    .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> spawnerGive(ctx, yaml, registry,
                            StringArgumentType.getString(ctx, "mob"),
                            StringArgumentType.getString(ctx, "id"))))))
            .then(Commands.literal("give-block")
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestSpawnerBlocks(yaml, builder))
                    .executes(ctx -> spawnerGiveBlock(ctx, yaml, StringArgumentType.getString(ctx, "id")))))
            .then(Commands.literal("give-id")
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestSpawnIds(spawns, builder))
                    .executes(ctx -> spawnerGiveId(ctx, yaml, spawns, StringArgumentType.getString(ctx, "id")))))
            .then(Commands.literal("create")
                .then(Commands.argument("id", StringArgumentType.word())
                    .then(Commands.argument("mob", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestMobs(registry, builder))
                        .executes(ctx -> spawnerCreate(ctx, yaml, registry,
                            StringArgumentType.getString(ctx, "id"),
                            StringArgumentType.getString(ctx, "mob"))))))
            .then(Commands.literal("remove")
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestSpawnIds(spawns, builder))
                    .executes(ctx -> spawnerRemove(ctx, yaml, spawns, spawnerStore, StringArgumentType.getString(ctx, "id")))))
            .then(Commands.literal("pause")
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestSpawnIds(spawns, builder))
                    .executes(ctx -> spawnerToggle(ctx, yaml, spawns, StringArgumentType.getString(ctx, "id"), false))))
            .then(Commands.literal("resume")
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestSpawnIds(spawns, builder))
                    .executes(ctx -> spawnerToggle(ctx, yaml, spawns, StringArgumentType.getString(ctx, "id"), true))))
            .then(Commands.literal("spawn")
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestSpawnIds(spawns, builder))
                    .executes(ctx -> spawnerSpawn(ctx, spawns, StringArgumentType.getString(ctx, "id"))))))
        .then(Commands.literal("dump")
            .then(Commands.argument("uuid", StringArgumentType.word())
                .executes(ctx -> dump(ctx, registry, StringArgumentType.getString(ctx, "uuid")))))
        .then(Commands.literal("spawn")
            .then(Commands.argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestMobs(registry, builder))
                .executes(ctx -> spawn(ctx, registry, StringArgumentType.getString(ctx, "id")))))
        .then(Commands.literal("egg")
            .then(Commands.argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestMobs(registry, builder))
                .executes(ctx -> giveEgg(ctx, yaml, registry, StringArgumentType.getString(ctx, "id")))));
  }

  private static int help(CommandContext<CommandSourceStack> ctx) {
    CommandSender sender = ctx.getSource().getSender();
    CommandMessages.send(sender, "messages.command.mobs.help.header");
    CommandMessages.send(sender, "messages.command.mobs.help.reload");
    CommandMessages.send(sender, "messages.command.mobs.help.editor");
    CommandMessages.send(sender, "messages.command.mobs.help.list");
    CommandMessages.send(sender, "messages.command.mobs.help.spawners");
    CommandMessages.send(sender, "messages.command.mobs.help.lootList");
    CommandMessages.send(sender, "messages.command.mobs.help.lootInfo");
    CommandMessages.send(sender, "messages.command.mobs.help.spawnerGive");
    CommandMessages.send(sender, "messages.command.mobs.help.spawnerGiveBlock");
    CommandMessages.send(sender, "messages.command.mobs.help.spawnerGiveId");
    CommandMessages.send(sender, "messages.command.mobs.help.spawnerCreate");
    CommandMessages.send(sender, "messages.command.mobs.help.spawnerRemove");
    CommandMessages.send(sender, "messages.command.mobs.help.spawnerPause");
    CommandMessages.send(sender, "messages.command.mobs.help.spawnerResume");
    CommandMessages.send(sender, "messages.command.mobs.help.spawnerSpawn");
    CommandMessages.send(sender, "messages.command.mobs.help.dump");
    CommandMessages.send(sender, "messages.command.mobs.help.spawn");
    CommandMessages.send(sender, "messages.command.mobs.help.egg");
    return Command.SINGLE_SUCCESS;
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

  private static CompletableFuture<Suggestions> suggestSpawnerBlocks(MobYamlRegistry yaml, SuggestionsBuilder builder) {
    if (yaml == null) {
      return builder.buildFuture();
    }
    for (String id : yaml.spawnerBlockIds()) {
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

  private static CompletableFuture<Suggestions> suggestLootPools(MobYamlRegistry yaml, SuggestionsBuilder builder) {
    if (yaml == null) {
      return builder.buildFuture();
    }
    for (String id : yaml.lootPoolIds()) {
      builder.suggest(id);
    }
    return builder.buildFuture();
  }

  private static int reload(CommandContext<CommandSourceStack> ctx, MobYamlRegistry yaml) {
    CommandSender sender = ctx.getSource().getSender();
    if (yaml == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.mobsYaml")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.mobs.reload")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.mobs.reload"));
      return Command.SINGLE_SUCCESS;
    }
    var result = yaml.reload();
    CommandMessages.send(sender, "messages.command.reload.file",
        Locales.placeholders("path", yaml.file().getPath()));
    CommandMessages.send(sender, "messages.command.mobs.reloadSummary",
        Locales.placeholders("mobs", result.loadedMobs(),
            "spawns", result.loadedSpawns(),
            "errors", result.errors().size()));
    return Command.SINGLE_SUCCESS;
  }

  private static int spawn(CommandContext<CommandSourceStack> ctx, MobRegistry registry, String id) {
    if (registry == null) {
      CommandMessages.send(ctx.getSource().getSender(), "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(ctx.getSource().getSender(), "labels.system.mobsRegistry")));
      return Command.SINGLE_SUCCESS;
    }
    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
      CommandMessages.send(ctx.getSource().getSender(), "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (!player.hasPermission("dungeonsreborn.mobs.spawn")) {
      CommandMessages.send(player, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.mobs.spawn"));
      return Command.SINGLE_SUCCESS;
    }
    String normalized = Ids.normalize(id);
    if (!registry.has(normalized)) {
      CommandMessages.send(player, "messages.command.mobs.unknownMob",
          Locales.placeholders("id", id));
      CommandMessages.sendClosestMatch(player, id, registry.ids());
      return Command.SINGLE_SUCCESS;
    }
    try {
      registry.spawn(normalized, player.getLocation());
      CommandMessages.send(player, "messages.command.mobs.spawned",
          Locales.placeholders("id", id));
    } catch (Exception ex) {
      CommandMessages.send(player, "messages.command.mobs.spawnFailed",
          Locales.placeholders("error", ex.getMessage()));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int list(CommandContext<CommandSourceStack> ctx, MobRegistry registry) {
    if (registry == null) {
      CommandMessages.send(ctx.getSource().getSender(), "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(ctx.getSource().getSender(), "labels.system.mobsRegistry")));
      return Command.SINGLE_SUCCESS;
    }
    var sender = ctx.getSource().getSender();
    var counts = registry.countById();
    CommandMessages.send(sender, "messages.command.mobs.activeHeader",
        Locales.placeholders("count", counts.values().stream().mapToInt(Integer::intValue).sum()));
    if (counts.isEmpty()) {
      CommandMessages.send(sender, "messages.command.mobs.activeNone");
      return Command.SINGLE_SUCCESS;
    }
    for (var entry : counts.entrySet()) {
      CommandMessages.send(sender, "messages.command.mobs.activeEntry",
          Locales.placeholders("id", entry.getKey(), "count", entry.getValue()));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int spawners(CommandContext<CommandSourceStack> ctx, MobSpawnManager spawns) {
    if (spawns == null) {
      CommandMessages.send(ctx.getSource().getSender(), "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(ctx.getSource().getSender(), "labels.system.mobSpawners")));
      return Command.SINGLE_SUCCESS;
    }
    var sender = ctx.getSource().getSender();
    var snapshots = spawns.snapshots();
    CommandMessages.send(sender, "messages.command.mobs.spawnersHeader",
        Locales.placeholders("count", snapshots.size()));
    if (snapshots.isEmpty()) {
      CommandMessages.send(sender, "messages.command.mobs.spawnersNone");
      return Command.SINGLE_SUCCESS;
    }
    for (var snapshot : snapshots) {
      String cap = snapshot.maxAlive() <= 0 ? "inf" : String.valueOf(snapshot.maxAlive());
      String next = snapshot.nextSpawnSeconds() < 0 ? "paused" : snapshot.nextSpawnSeconds() + "s";
      String enabled = snapshot.enabled() ? "" : " §c(paused)";
      CommandMessages.send(sender, "messages.command.mobs.spawnersEntry",
          Locales.placeholders("id", snapshot.id(),
              "mob", snapshot.mobId(),
              "alive", snapshot.alive(),
              "cap", cap,
              "next", next,
              "world", snapshot.world(),
              "status", enabled));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int spawnerGive(CommandContext<CommandSourceStack> ctx, MobYamlRegistry yaml, MobRegistry registry,
      String mobId, String desiredId) {
    var sender = ctx.getSource().getSender();
    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (registry == null || yaml == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.mobsRegistry")));
      return Command.SINGLE_SUCCESS;
    }
    if (!player.hasPermission("dungeonsreborn.mobs.spawner.give")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.mobs.spawner.give"));
      return Command.SINGLE_SUCCESS;
    }
    String normalized = Ids.normalize(mobId);
    if (!registry.has(normalized)) {
      CommandMessages.send(sender, "messages.command.mobs.unknownMob",
          Locales.placeholders("id", mobId));
      CommandMessages.sendClosestMatch(sender, mobId, registry.ids());
      return Command.SINGLE_SUCCESS;
    }
    ItemStack item = MobSpawnerItems.createSpawnerItem(normalized, desiredId);
    java.util.Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
    if (!leftovers.isEmpty()) {
      for (ItemStack stack : leftovers.values()) {
        player.getWorld().dropItemNaturally(player.getLocation(), stack);
      }
    }
    CommandMessages.send(player, "messages.command.mobs.spawnerGiveMob",
        Locales.placeholders("id", mobId));
    return Command.SINGLE_SUCCESS;
  }

  private static int spawnerGiveBlock(CommandContext<CommandSourceStack> ctx, MobYamlRegistry yaml, String id) {
    var sender = ctx.getSource().getSender();
    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (yaml == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.mobSpawners")));
      return Command.SINGLE_SUCCESS;
    }
    if (!player.hasPermission("dungeonsreborn.mobs.spawner.give")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.mobs.spawner.give"));
      return Command.SINGLE_SUCCESS;
    }
    ItemStack item = yaml.spawnerBlockItem(id);
    if (item == null) {
      CommandMessages.send(sender, "messages.command.mobs.spawnerBlockUnknown",
          Locales.placeholders("id", id));
      CommandMessages.sendClosestMatch(sender, id, yaml.spawnerBlockIds());
      return Command.SINGLE_SUCCESS;
    }
    java.util.Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
    if (!leftovers.isEmpty()) {
      for (ItemStack stack : leftovers.values()) {
        player.getWorld().dropItemNaturally(player.getLocation(), stack);
      }
    }
    CommandMessages.send(player, "messages.command.mobs.spawnerGiveBlock",
        Locales.placeholders("id", id));
    return Command.SINGLE_SUCCESS;
  }

  private static int spawnerGiveId(CommandContext<CommandSourceStack> ctx, MobYamlRegistry yaml, MobSpawnManager spawns,
      String spawnId) {
    var sender = ctx.getSource().getSender();
    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (yaml == null || spawns == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.mobSpawners")));
      return Command.SINGLE_SUCCESS;
    }
    if (!player.hasPermission("dungeonsreborn.mobs.spawner.give")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.mobs.spawner.give"));
      return Command.SINGLE_SUCCESS;
    }
    MobSpawnSpec spec = spawns.spawnSpec(spawnId);
    if (spec == null) {
      CommandMessages.send(sender, "messages.command.mobs.spawnerUnknown",
          Locales.placeholders("id", spawnId));
      CommandMessages.sendClosestMatch(sender, spawnId, spawns.spawnIds());
      return Command.SINGLE_SUCCESS;
    }
    ItemStack item = MobSpawnerItems.createSpawnerItem(spec.mobId(), spec.id());
    java.util.Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
    if (!leftovers.isEmpty()) {
      for (ItemStack stack : leftovers.values()) {
        player.getWorld().dropItemNaturally(player.getLocation(), stack);
      }
    }
    CommandMessages.send(player, "messages.command.mobs.spawnerGiveId",
        Locales.placeholders("id", spec.id()));
    return Command.SINGLE_SUCCESS;
  }

  private static int spawnerCreate(CommandContext<CommandSourceStack> ctx, MobYamlRegistry yaml, MobRegistry registry,
      String id, String mobId) {
    var sender = ctx.getSource().getSender();
    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
      CommandMessages.send(sender, "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (registry == null || yaml == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.mobsRegistry")));
      return Command.SINGLE_SUCCESS;
    }
    if (!player.hasPermission("dungeonsreborn.mobs.spawner.admin")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.mobs.spawner.admin"));
      return Command.SINGLE_SUCCESS;
    }
    String normalized = Ids.normalize(mobId);
    if (!registry.has(normalized)) {
      CommandMessages.send(sender, "messages.command.mobs.unknownMob",
          Locales.placeholders("id", mobId));
      CommandMessages.sendClosestMatch(sender, mobId, registry.ids());
      return Command.SINGLE_SUCCESS;
    }
    try {
      String created = yaml.createSpawn(id, normalized, player.getLocation());
      CommandMessages.send(sender, "messages.command.mobs.spawnerCreated",
          Locales.placeholders("id", created));
    } catch (Exception ex) {
      CommandMessages.send(sender, "messages.command.mobs.spawnerCreateFailed",
          Locales.placeholders("error", ex.getMessage()));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int spawnerRemove(CommandContext<CommandSourceStack> ctx, MobYamlRegistry yaml, MobSpawnManager spawns,
      MobSpawnerBlockStore spawnerStore, String id) {
    var sender = ctx.getSource().getSender();
    if (yaml == null || spawns == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.mobSpawners")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.mobs.spawner.admin")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.mobs.spawner.admin"));
      return Command.SINGLE_SUCCESS;
    }
    var spec = spawns.spawnSpec(id);
    if (spec != null) {
      org.bukkit.World world = org.bukkit.Bukkit.getWorld(spec.worldName());
      if (world != null && spec.location() != null) {
        org.bukkit.block.Block block = world.getBlockAt(spec.location());
        if (block.getType() == org.bukkit.Material.SPAWNER
            && id.equals(dev.patric.dungeonsreborn.mobs.MobSpawnerMarkers.getSpawnerId(block))) {
          block.setType(org.bukkit.Material.AIR, false);
        }
      }
    }
    int removed = spawns.despawnSpawn(id);
    boolean ok = yaml.removeSpawn(id);
    if (spawnerStore != null) {
      spawnerStore.removeBySpawnId(id);
    }
    if (!ok) {
      CommandMessages.send(sender, "messages.command.mobs.spawnerUnknown",
          Locales.placeholders("id", id));
      CommandMessages.sendClosestMatch(sender, id, spawns.spawnIds());
      return Command.SINGLE_SUCCESS;
    }
    CommandMessages.send(sender, "messages.command.mobs.spawnerRemoved",
        Locales.placeholders("id", id, "removed", removed));
    return Command.SINGLE_SUCCESS;
  }

  private static int spawnerToggle(CommandContext<CommandSourceStack> ctx, MobYamlRegistry yaml, MobSpawnManager spawns,
      String id, boolean enabled) {
    var sender = ctx.getSource().getSender();
    if (yaml == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.mobSpawners")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.mobs.spawner.admin")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.mobs.spawner.admin"));
      return Command.SINGLE_SUCCESS;
    }
    boolean ok = yaml.setSpawnEnabled(id, enabled);
    if (!ok) {
      CommandMessages.send(sender, "messages.command.mobs.spawnerUnknown",
          Locales.placeholders("id", id));
      if (spawns != null) {
        CommandMessages.sendClosestMatch(sender, id, spawns.spawnIds());
      }
      return Command.SINGLE_SUCCESS;
    }
    CommandMessages.send(sender, enabled ? "messages.command.mobs.spawnerResumed" : "messages.command.mobs.spawnerPaused",
        Locales.placeholders("id", id));
    return Command.SINGLE_SUCCESS;
  }

  private static int spawnerSpawn(CommandContext<CommandSourceStack> ctx, MobSpawnManager spawns, String id) {
    var sender = ctx.getSource().getSender();
    if (spawns == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.mobSpawners")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.mobs.spawner.admin")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.mobs.spawner.admin"));
      return Command.SINGLE_SUCCESS;
    }
    boolean ok = spawns.spawnOnce(id);
    if (!ok) {
      CommandMessages.send(sender, "messages.command.mobs.spawnerSpawnFailed",
          Locales.placeholders("id", id));
      CommandMessages.sendClosestMatch(sender, id, spawns.spawnIds());
      return Command.SINGLE_SUCCESS;
    }
    CommandMessages.send(sender, "messages.command.mobs.spawnerForced",
        Locales.placeholders("id", id));
    return Command.SINGLE_SUCCESS;
  }

  private static int dump(CommandContext<CommandSourceStack> ctx, MobRegistry registry, String rawId) {
    if (registry == null) {
      CommandMessages.send(ctx.getSource().getSender(), "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(ctx.getSource().getSender(), "labels.system.mobsRegistry")));
      return Command.SINGLE_SUCCESS;
    }
    java.util.UUID uuid;
    try {
      uuid = java.util.UUID.fromString(rawId);
    } catch (IllegalArgumentException ex) {
      CommandMessages.send(ctx.getSource().getSender(), "messages.command.mobs.invalidUuid",
          Locales.placeholders("id", rawId));
      return Command.SINGLE_SUCCESS;
    }
    var snapshot = registry.snapshot(uuid);
    if (snapshot == null) {
      CommandMessages.send(ctx.getSource().getSender(), "messages.command.mobs.noActiveMob",
          Locales.placeholders("id", rawId));
      return Command.SINGLE_SUCCESS;
    }
    var sender = ctx.getSource().getSender();
    CommandMessages.send(sender, "messages.command.mobs.snapshot.header");
    CommandMessages.send(sender, "messages.command.mobs.snapshot.mobId",
        Locales.placeholders("value", snapshot.mobId()));
    CommandMessages.send(sender, "messages.command.mobs.snapshot.variant",
        Locales.placeholders("value", snapshot.variantId() == null ? "-" : snapshot.variantId()));
    CommandMessages.send(sender, "messages.command.mobs.snapshot.owner",
        Locales.placeholders("value", snapshot.ownerId() == null ? "-" : snapshot.ownerId()));
    CommandMessages.send(sender, "messages.command.mobs.snapshot.world",
        Locales.placeholders("value", snapshot.world()));
    CommandMessages.send(sender, "messages.command.mobs.snapshot.pos",
        Locales.placeholders("value", String.format(java.util.Locale.ROOT, "%.2f, %.2f, %.2f",
            snapshot.x(), snapshot.y(), snapshot.z())));
    CommandMessages.send(sender, "messages.command.mobs.snapshot.hp",
        Locales.placeholders("value", String.format(java.util.Locale.ROOT, "%.1f / %.1f",
            snapshot.health(), snapshot.maxHealth())));
    return Command.SINGLE_SUCCESS;
  }

  private static int giveEgg(CommandContext<CommandSourceStack> ctx, MobYamlRegistry yaml, MobRegistry registry, String id) {
    if (yaml == null) {
      CommandMessages.send(ctx.getSource().getSender(), "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(ctx.getSource().getSender(), "labels.system.mobsYaml")));
      return Command.SINGLE_SUCCESS;
    }
    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
      CommandMessages.send(ctx.getSource().getSender(), "messages.common.playersOnly");
      return Command.SINGLE_SUCCESS;
    }
    if (!player.hasPermission("dungeonsreborn.mobs.egg.give")) {
      CommandMessages.send(player, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.mobs.egg.give"));
      return Command.SINGLE_SUCCESS;
    }
    var item = yaml.eggItem(id);
    if (item == null) {
      CommandMessages.send(player, "messages.command.mobs.eggUnknown",
          Locales.placeholders("id", id));
      if (registry != null) {
        CommandMessages.sendClosestMatch(player, id, registry.ids());
      }
      return Command.SINGLE_SUCCESS;
    }
    player.getInventory().addItem(item);
    CommandMessages.send(player, "messages.command.mobs.eggGiven",
        Locales.placeholders("id", id));
    return Command.SINGLE_SUCCESS;
  }

  private static int lootList(CommandContext<CommandSourceStack> ctx, MobYamlRegistry yaml) {
    var sender = ctx.getSource().getSender();
    if (yaml == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.mobLoot")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.mobs.editor")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.mobs.editor"));
      return Command.SINGLE_SUCCESS;
    }
    var pools = yaml.lootPoolIds();
    CommandMessages.send(sender, "messages.command.mobs.loot.header",
        Locales.placeholders("count", pools.size()));
    if (pools.isEmpty()) {
      CommandMessages.send(sender, "messages.command.mobs.loot.none");
      return Command.SINGLE_SUCCESS;
    }
    for (String id : pools) {
      CommandMessages.send(sender, "messages.command.mobs.loot.entry", Locales.placeholders("id", id));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int lootInfo(CommandContext<CommandSourceStack> ctx, MobYamlRegistry yaml, String id) {
    var sender = ctx.getSource().getSender();
    if (yaml == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.mobLoot")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.mobs.editor")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.mobs.editor"));
      return Command.SINGLE_SUCCESS;
    }
    var pool = yaml.lootPool(id);
    if (pool == null) {
      CommandMessages.send(sender, "messages.command.mobs.loot.unknown",
          Locales.placeholders("id", id));
      CommandMessages.sendClosestMatch(sender, id, yaml.lootPoolIds());
      return Command.SINGLE_SUCCESS;
    }
    String clear = pool.clearVanilla()
        ? CommandMessages.text(sender, "messages.common.true")
        : CommandMessages.text(sender, "messages.common.false");
    CommandMessages.send(sender, "messages.command.mobs.loot.info.header",
        Locales.placeholders("id", id));
    CommandMessages.send(sender, "messages.command.mobs.loot.info.clearVanilla",
        Locales.placeholders("value", clear));
    CommandMessages.send(sender, "messages.command.mobs.loot.info.rolls",
        Locales.placeholders("rolls", pool.rolls(), "bonus", pool.bonusRolls()));
    CommandMessages.send(sender, "messages.command.mobs.loot.info.guaranteed",
        Locales.placeholders("count", pool.guaranteed().size()));
    CommandMessages.send(sender, "messages.command.mobs.loot.info.drops",
        Locales.placeholders("count", pool.drops().size()));
    return Command.SINGLE_SUCCESS;
  }
}
