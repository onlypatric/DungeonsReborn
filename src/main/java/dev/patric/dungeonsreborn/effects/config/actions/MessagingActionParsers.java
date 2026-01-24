package dev.patric.dungeonsreborn.effects.config.actions;

import java.time.Duration;
import java.util.Map;

import dev.patric.dungeonsreborn.effects.CastContext;
import dev.patric.dungeonsreborn.effects.actions.Action;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities.NumValue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

final class MessagingActionParsers {
  private MessagingActionParsers() {
  }

  static void register(Map<String, ActionParser> registry) {
    registry.put("message", MessagingActionParsers::parseMessage);
    registry.put("action_bar", MessagingActionParsers::parseActionBar);
    registry.put("title", MessagingActionParsers::parseTitle);
    registry.put("overlay", MessagingActionParsers::parseOverlay);
  }

  private static Action parseMessage(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    String raw = ctx.requireString(node, "text", path + ".text");
    return castCtx -> {
      if (castCtx.caster() instanceof org.bukkit.entity.Player player) {
        player.sendMessage(ctx.renderText(raw, castCtx));
      }
    };
  }

  private static Action parseActionBar(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    String raw = ctx.requireString(node, "text", path + ".text");
    return castCtx -> {
      if (castCtx.caster() instanceof org.bukkit.entity.Player player) {
        player.sendActionBar(ctx.renderText(raw, castCtx));
      }
    };
  }

  private static Action parseTitle(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    String rawTitle = ctx.requireString(node, "title", path + ".title");
    String rawSubtitle = node.containsKey("subtitle") ? String.valueOf(node.get("subtitle")) : null;
    NumValue fadeInTicks = ctx.numValue(node, "fadeInTicks", 10.0, path);
    NumValue stayTicks = ctx.numValue(node, "stayTicks", 40.0, path);
    NumValue fadeOutTicks = ctx.numValue(node, "fadeOutTicks", 10.0, path);
    return castCtx -> {
      if (castCtx.caster() instanceof org.bukkit.entity.Player player) {
        Component title = ctx.renderText(rawTitle, castCtx);
        Component subtitle = rawSubtitle == null ? Component.empty() : ctx.renderText(rawSubtitle, castCtx);
        long fadeIn = Math.max(0L, ctx.evalLong(fadeInTicks, castCtx));
        long stay = Math.max(0L, ctx.evalLong(stayTicks, castCtx));
        long fadeOut = Math.max(0L, ctx.evalLong(fadeOutTicks, castCtx));
        Title.Times times = Title.Times.times(
            Duration.ofMillis(fadeIn * 50L),
            Duration.ofMillis(stay * 50L),
            Duration.ofMillis(fadeOut * 50L));
        player.showTitle(Title.title(title, subtitle, times));
      }
    };
  }

  private static Action parseOverlay(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    String rawTitle = ctx.requireString(node, "title", path + ".title");
    String rawSubtitle = node.containsKey("subtitle") ? String.valueOf(node.get("subtitle")) : null;
    NumValue fadeInTicks = ctx.numValue(node, "fadeInTicks", 10.0, path);
    NumValue stayTicks = ctx.numValue(node, "stayTicks", 40.0, path);
    NumValue fadeOutTicks = ctx.numValue(node, "fadeOutTicks", 10.0, path);
    return castCtx -> {
      org.bukkit.entity.Player player = ctx.targetPlayer(castCtx);
      if (player == null) {
        return;
      }
      Component title = ctx.renderText(rawTitle, castCtx);
      Component subtitle = rawSubtitle == null ? Component.empty() : ctx.renderText(rawSubtitle, castCtx);
      long fadeIn = Math.max(0L, ctx.evalLong(fadeInTicks, castCtx));
      long stay = Math.max(0L, ctx.evalLong(stayTicks, castCtx));
      long fadeOut = Math.max(0L, ctx.evalLong(fadeOutTicks, castCtx));
      dev.patric.dungeonsreborn.effects.actions.Actions.overlay(title, subtitle,
          Duration.ofMillis(fadeIn * 50L),
          Duration.ofMillis(stay * 50L),
          Duration.ofMillis(fadeOut * 50L))
          .execute(new CastContext(castCtx.engine(), castCtx.plugin(), castCtx.castId(), castCtx.abilityId(), castCtx.tick(),
              castCtx.state(), player, castCtx.origin(), castCtx.direction(), castCtx.itemInHand()));
    };
  }
}
