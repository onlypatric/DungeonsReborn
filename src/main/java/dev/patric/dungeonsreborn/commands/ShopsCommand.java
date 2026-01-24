package dev.patric.dungeonsreborn.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.util.concurrent.CompletableFuture;

import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.shops.ShopItems;
import dev.patric.dungeonsreborn.shops.ShopSpec;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ShopsCommand {
  private ShopsCommand() {
  }

  public static LiteralArgumentBuilder<CommandSourceStack> createCommand(ShopYamlRegistry shops) {
    return Commands.literal("shop")
        .executes(ctx -> help(ctx))
        .then(Commands.literal("reload").executes(ctx -> reload(ctx, shops)))
        .then(Commands.literal("list").executes(ctx -> list(ctx, shops)))
        .then(Commands.literal("info")
            .then(Commands.argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestShopIds(shops, builder))
                .executes(ctx -> info(ctx, shops, StringArgumentType.getString(ctx, "id")))))
        .then(Commands.literal("give")
            .then(Commands.argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestShopIds(shops, builder))
                .executes(ctx -> give(ctx, shops, StringArgumentType.getString(ctx, "id")))
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(ShopsCommand::suggestOnlinePlayers)
                    .executes(ctx -> give(ctx, shops,
                        StringArgumentType.getString(ctx, "id"),
                        StringArgumentType.getString(ctx, "player"))))))
        .then(Commands.literal("token")
            .executes(ctx -> giveToken(ctx, shops, 1, null))
            .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                .executes(ctx -> giveToken(ctx, shops, IntegerArgumentType.getInteger(ctx, "amount"), null))
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(ShopsCommand::suggestOnlinePlayers)
                    .executes(ctx -> giveToken(ctx, shops,
                        IntegerArgumentType.getInteger(ctx, "amount"),
                        StringArgumentType.getString(ctx, "player"))))))
        ;
  }

  private static int help(CommandContext<CommandSourceStack> ctx) {
    var sender = ctx.getSource().getSender();
    CommandMessages.send(sender, "messages.command.shops.help.reload");
    CommandMessages.send(sender, "messages.command.shops.help.list");
    CommandMessages.send(sender, "messages.command.shops.help.info");
    CommandMessages.send(sender, "messages.command.shops.help.give");
    CommandMessages.send(sender, "messages.command.shops.help.token");
    return Command.SINGLE_SUCCESS;
  }

  private static int reload(CommandContext<CommandSourceStack> ctx,
      ShopYamlRegistry shops) {
    var sender = ctx.getSource().getSender();
    if (shops == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.shopRegistry")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof org.bukkit.entity.Player player && !player.hasPermission("dungeonsreborn.shop.reload")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.shop.reload"));
      return Command.SINGLE_SUCCESS;
    }
    var result = shops.reload();
    CommandMessages.send(sender, "messages.command.reload.file",
        Locales.placeholders("path", shops.file().getPath()));
    CommandMessages.send(sender, "messages.command.shops.reloadSummary",
        Locales.placeholders("loaded", result.loaded(), "errors", result.errors().size()));
    return Command.SINGLE_SUCCESS;
  }

  private static int list(CommandContext<CommandSourceStack> ctx, ShopYamlRegistry shops) {
    var sender = ctx.getSource().getSender();
    if (shops == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.shopRegistry")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.shop.admin")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.shop.admin"));
      return Command.SINGLE_SUCCESS;
    }
    CommandMessages.send(sender, "messages.command.shops.list.header");
    for (ShopSpec spec : shops.shops().values()) {
      CommandMessages.send(sender, "messages.command.shops.list.entry",
          Locales.placeholders("id", spec.id(), "trades", spec.trades().size()));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int info(CommandContext<CommandSourceStack> ctx, ShopYamlRegistry shops, String id) {
    var sender = ctx.getSource().getSender();
    if (shops == null) {
      CommandMessages.send(sender, "messages.command.systemUnavailable",
          Locales.placeholders("system", CommandMessages.text(sender, "labels.system.shopRegistry")));
      return Command.SINGLE_SUCCESS;
    }
    if (sender instanceof Player player && !player.hasPermission("dungeonsreborn.shop.admin")) {
      CommandMessages.send(sender, "messages.command.missingPermission",
          Locales.placeholders("permission", "dungeonsreborn.shop.admin"));
      return Command.SINGLE_SUCCESS;
    }
    ShopSpec spec = shops.shop(id);
    if (spec == null) {
      CommandMessages.send(sender, "messages.command.shops.unknown",
          Locales.placeholders("id", id));
      CommandMessages.sendClosestMatch(sender, id, shops.shops().keySet());
      return Command.SINGLE_SUCCESS;
    }
    CommandMessages.send(sender, "messages.command.shops.info.header",
        Locales.placeholders("id", spec.id()));
    CommandMessages.send(sender, "messages.command.shops.info.title",
        Locales.placeholders("title", spec.title()));
    if (spec.permission() != null) {
      CommandMessages.send(sender, "messages.command.shops.info.permission",
          Locales.placeholders("permission", spec.permission()));
    }
    CommandMessages.send(sender, "messages.command.shops.info.trades",
        Locales.placeholders("count", spec.trades().size()));
    if (spec.stock() != null) {
      CommandMessages.send(sender, "messages.command.shops.info.stock",
          Locales.placeholders("min", spec.stock().min(),
              "max", spec.stock().max(),
              "seconds", spec.stock().restockSeconds()));
    }
    return Command.SINGLE_SUCCESS;
  }

  private static int give(CommandContext<CommandSourceStack> ctx, ShopYamlRegistry shops, String id) {
    return give(ctx, shops, id, null);
  }

  private static int give(CommandContext<CommandSourceStack> ctx, ShopYamlRegistry shops, String id, String targetName) {
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
    Player target = targetName != null
        ? org.bukkit.Bukkit.getPlayerExact(targetName)
        : (sender instanceof Player player ? player : null);
    if (target == null) {
      CommandMessages.send(sender, "messages.command.targetNotFound");
      return Command.SINGLE_SUCCESS;
    }
    ShopSpec spec = shops.shop(id);
    if (spec == null) {
      CommandMessages.send(sender, "messages.command.shops.unknown",
          Locales.placeholders("id", id));
      CommandMessages.sendClosestMatch(sender, id, shops.shops().keySet());
      return Command.SINGLE_SUCCESS;
    }
    ItemStack item = ShopItems.shopOpenItem(spec, shops);
    var leftovers = target.getInventory().addItem(item);
    if (!leftovers.isEmpty()) {
      for (ItemStack stack : leftovers.values()) {
        target.getWorld().dropItemNaturally(target.getLocation(), stack);
      }
    }
    CommandMessages.send(sender, "messages.command.shops.give",
        Locales.placeholders("player", target.getName()));
    return Command.SINGLE_SUCCESS;
  }

  private static int giveToken(CommandContext<CommandSourceStack> ctx, ShopYamlRegistry shops, int amount,
      String targetName) {
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
    Player target = targetName != null
        ? Bukkit.getPlayerExact(targetName)
        : (sender instanceof Player player ? player : null);
    if (target == null) {
      CommandMessages.send(sender, "messages.command.targetNotFound");
      return Command.SINGLE_SUCCESS;
    }
    if (shops.tokenSpec() == null || shops.tokenSpec().item() == null) {
      CommandMessages.send(sender, "messages.command.shops.tokenMissing");
      return Command.SINGLE_SUCCESS;
    }
    int clamped = Math.max(1, amount);
    ItemStack token = shops.tokenSpec().item().clone();
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

  private static CompletableFuture<Suggestions> suggestShopIds(ShopYamlRegistry shops, SuggestionsBuilder builder) {
    if (shops == null) {
      return builder.buildFuture();
    }
    for (String id : shops.shops().keySet()) {
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
}
